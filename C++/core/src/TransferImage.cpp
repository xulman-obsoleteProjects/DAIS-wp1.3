#include <stdexcept>
#include <iostream>
#include <sstream>
#include <list>
#include <string>
#include <chrono>
#include <thread>
#include <zmq.hpp>

#include "TransferImage_Utils.h"
#include "TransferImage.h"

//short-cut to throwing runtime_error exceptions
using std::runtime_error;

namespace DAIS
{

//some communication constants (to prevent from re-allocating them repeatedly)
const std::string strCanGet("can get");
const std::string strReady("ready");
const std::string strDone("done");

//aux internal functions to send/receive initial handshake message
void Handshake_GiveImage(const imgParams_t& imgParams,connectionParams_t& cnnParams);
void Handshake_GetImage(imgParams_t& imgParams,connectionParams_t& cnnParams);

void StartSendingOneImage(const imgParams_t& imgParams,connectionParams_t& cnnParams,
                          const char* addr, const int timeOut)
{
	//init the context and get the socket
	cnnParams.context  = new zmq::context_t(1);
	cnnParams.socket   = new zmq::socket_t(*(cnnParams.context), ZMQ_PAIR);
	cnnParams.addr     = std::string("tcp://")+std::string(addr);
	cnnParams.timeOut  = timeOut;
	cnnParams.isSender = true;

	//connects the socket with the given address
	cnnParams.socket->connect(cnnParams.addr);

	//the common routine to implement the initial handshake
	Handshake_GiveImage(imgParams,cnnParams);
}

void StartReceivingOneImage(imgParams_t& imgParams,connectionParams_t& cnnParams,
                            const int port, const int timeOut)
{
	//init the context and get the socket
	cnnParams.context  = new zmq::context_t(1);
	cnnParams.socket   = new zmq::socket_t(*(cnnParams.context), ZMQ_PAIR);
	cnnParams.port     = port;
	cnnParams.timeOut  = timeOut;
	cnnParams.isSender = false;

	//binds the socket to the given port
	char chrString[128];
	sprintf(chrString,"tcp://*:%d",port);
	cnnParams.socket->bind(chrString);

	//the common routine to implement the initial handshake
	Handshake_GetImage(imgParams,cnnParams);
}


void StartServingOneImage(const imgParams_t& imgParams,connectionParams_t& cnnParams,
                          const int port, const int timeOut)
{
	//init the context and get the socket
	cnnParams.context  = new zmq::context_t(1);
	cnnParams.socket   = new zmq::socket_t(*(cnnParams.context), ZMQ_PAIR);
	cnnParams.port     = port;
	cnnParams.timeOut  = timeOut;
	cnnParams.isSender = true;

	//binds the socket to the given port
	char chrString[128];
	sprintf(chrString,"tcp://*:%d",port);
	cnnParams.socket->bind(chrString);

	//wait for the connection-requesting message
	waitForFirstMessage(cnnParams,"No connection requested yet.");

	//analyse incoming message
	//read income message and check if this is really an "invitation packet"
	zmq::message_t msg(1024);
	int recLength = cnnParams.socket->recv(msg).value();
	char* msgString = msg.data<char>();

	//check sanity of the received buffer
	if (recLength < 7)
		throw new runtime_error("Received (near) empty connection request message. Stopping.");
	if (recLength == 1024)
		throw new runtime_error("Couldn't read complete connection request message. Stopping.");
	if (msgString[0] != 'c' ||
	    msgString[1] != 'a' ||
	    msgString[2] != 'n' ||
	    msgString[3] != ' ' ||
	    msgString[4] != 'g' ||
	    msgString[5] != 'e' ||
	    msgString[6] != 't')
		throw new runtime_error("Protocol error, expected connection request.");

	//the common routine to implement the initial handshake
	Handshake_GiveImage(imgParams,cnnParams);
}

void StartRequestingOneImage(imgParams_t& imgParams,connectionParams_t& cnnParams,
                             const char* addr, const int timeOut)
{
	//init the context and get the socket
	cnnParams.context  = new zmq::context_t(1);
	cnnParams.socket   = new zmq::socket_t(*(cnnParams.context), ZMQ_PAIR);
	cnnParams.addr     = std::string("tcp://")+std::string(addr);
	cnnParams.timeOut  = timeOut;
	cnnParams.isSender = false;

	//connects the socket with the given address
	cnnParams.socket->connect(cnnParams.addr);

	//sends the connection-requesting message
	zmq::message_t msgCanGet(strCanGet.c_str(),strCanGet.size());
	cnnParams.socket->send(msgCanGet,zmq::send_flags::none);

	//the common routine to implement the initial handshake
	Handshake_GetImage(imgParams,cnnParams);
}


void Handshake_GiveImage(const imgParams_t& imgParams,connectionParams_t& cnnParams)
{
	//build the initial handshake header/message...
	std::ostringstream hdrMsg;
	hdrMsg << "v1 dimNumber " << imgParams.dim;
	for (int i=0; i < imgParams.dim; ++i)
		hdrMsg << " " << imgParams.sizes[i];

	hdrMsg << " " << imgParams.voxelType << " " << imgParams.backendType << " ";
	//...and convert it into a string
	std::string hdrStr(hdrMsg.str());

	std::cout << "Sending: " << hdrStr << "\n";
	zmq::message_t msgHeader(hdrStr.c_str(),hdrStr.size());
	cnnParams.socket->send(msgHeader,zmq::send_flags::none);

	waitForFirstMessage(cnnParams,"Timeout when waiting for \"start sending green light\".");

	//read income message and check if the receiving party is ready to receive our data
	zmq::message_t msg(1024);
	int recLength = cnnParams.socket->recv(msg).value();
	char* msgString = msg.data<char>();

	//check sanity of the received buffer
	if (recLength < 5)
		throw new runtime_error("Received (near) empty initial (handshake) message. Stopping.");
	if (recLength == 1024)
		throw new runtime_error("Couldn't read complete initial (handshake) message. Stopping.");
	if (msgString[0] != 'r' ||
	    msgString[1] != 'e' ||
	    msgString[2] != 'a' ||
	    msgString[3] != 'd' ||
	    msgString[4] != 'y')
		throw new runtime_error("Protocol error, expected initial confirmation from the receiver.");
}

void Handshake_GetImage(imgParams_t& imgParams,connectionParams_t& cnnParams)
{
	//attempt to receive the first message, while waiting up to timeOut seconds
	waitForFirstMessage(cnnParams,"No connection established yet.");
	/*
	//replace the previous waitForFirstMessage() with this block if you
	//want to have verbose reporting, etc.
	int timeWaited = 0;
	while (timeWaited < cnnParams.timeOut
	  && (cnnParams.socket->getsockopt<int>(ZMQ_EVENTS) & ZMQ_EVENT_CONNECTED) != ZMQ_EVENT_CONNECTED)
	{
		std::this_thread::sleep_for(std::chrono::seconds(1));
		if (timeWaited % 10)
			std::cout << "Waiting already " << timeWaited << " seconds.\n";
		++timeWaited;
	}
	//still no connection?
	if ((cnnParams.socket->getsockopt<int>(ZMQ_EVENTS) & ZMQ_EVENT_CONNECTED) != ZMQ_EVENT_CONNECTED)
		throw new runtime_error("No connection established yet.");
	*/

	//ok, there is a connection; receive first message
	zmq::message_t msg(1024);
	int recLength = cnnParams.socket->recv(msg).value();
	char* msgString = msg.data<char>();

	//check sanity of the received buffer
	if (recLength <= 0)
		throw new runtime_error("Received empty initial (handshake) message. Stopping.");
	if (recLength == 1024)
		throw new runtime_error("Couldn't read complete initial (handshake) message. Stopping.");

	//parse it into the imgParams structure, or complain
	std::cout << "Received: " << msgString << "\n";
	std::istringstream hdrMsg(msgString);

	//parse by empty space
	std::string token;
	hdrMsg >> token;
	if (token.find("v1") != 0)
		throw new runtime_error("Protocol error: Expected 'v1' version.");

	hdrMsg >> token >> imgParams.dim;
	if (token.find("dimNumber") != 0)
		throw new runtime_error("Protocol error: Expected 'dimNumber' token.");

	imgParams.sizes = new int[imgParams.dim];
	for (int i=0; i < imgParams.dim; ++i)
		hdrMsg >> imgParams.sizes[i];

	hdrMsg >> imgParams.voxelType;
	if (imgParams.voxelType.find("Type") == std::string::npos)
		throw new runtime_error("Protocol error: Expected voxel type hint.");

	hdrMsg >> imgParams.backendType;
	if (imgParams.backendType.find("Img") == std::string::npos)
		throw new runtime_error("Protocol error: Expected image storage hint.");
}


void SendMetadata(connectionParams_t& cnnParams,const std::list<std::string>& metaData)
{
	std::ostringstream smsg;
	//init the metadata message
	smsg << "metadata" << mdMsgSep;

	//populate the metadata message
	std::list<std::string>::const_iterator it = metaData.begin();
	while (it != metaData.end())
	{
		smsg << *it << mdMsgSep;
		it++;
	}

	//finish the metadata message
	smsg << "endmetadata";

	//convert the metadata into a string and send it
	std::string msg(smsg.str());
	zmq::message_t msgMetaData(msg.c_str(),msg.size());
	cnnParams.socket->send(msgMetaData,zmq::send_flags::sndmore);
}

void ReceiveMetadata(connectionParams_t& cnnParams,std::list<std::string>& metaData)
{
	//sends flag that we're free to go, first comes the image metadata
	zmq::message_t msgReady(strReady.c_str(),strReady.size());
	cnnParams.socket->send(msgReady,zmq::send_flags::none);

	waitForFirstMessage(cnnParams,"Timeout when waiting for metadata.");
	zmq::message_t msg;
	if (!cnnParams.socket->recv(msg).has_value())
		throw new runtime_error("Empty metadata received.");

	//"convert" to std::string (likely by making extra copy of it)
	//NB: haven't find how to discard/dispose the msg :(
	std::string smsg(msg.data<char>());

	//first token needs to be "metadata"
	if (smsg.find("metadata") != 0)
		throw new runtime_error("Protocol error, expected metadata part from the receiver.");

	//parse the string:
	//pos shows beginning of the current token in the smsg
	int start_pos=0;
	int end_pos = smsg.find(mdMsgSep,start_pos);

	//there should always be at least one separator
	if (end_pos == std::string::npos)
		throw new runtime_error("Protocol error, received likely corrupted metadata part.");

	while (end_pos < smsg.size())
	{
		//update the start pos to be after the Message Separator
		start_pos = end_pos + mdMsgSepLen;

		//and find the end of the current token
		end_pos = smsg.find(mdMsgSep,start_pos);
		if (end_pos == std::string::npos) break;
		//NB: will skip over the last token because this one is not ended with the separator

		//now, current token/message lives within [start_pos,end_pos-1]
		metaData.push_back(smsg.substr(start_pos,end_pos-start_pos));
	}
}


template <typename VT>
void TransmitOneArrayImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,VT* const data)
{
	//the length of the corresponding/input basic type array
	const size_t arrayLength   = imgParams.howManyVoxels();
	const size_t arrayElemSize = imgParams.howManyBytesPerVoxel();

	TransmitChunkFromOneImage(cnnParams,data,arrayLength,arrayElemSize);
}

template <typename VT>
void TransmitOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,VT* const data)
{
	//for "degraded" cases, use directly the "ArrayImg-sibbling-function"
	if (imgParams.dim < 3)
	{
		TransmitOneArrayImage(cnnParams,imgParams,data);
		return;
	}
	//here: imgParams.dim >= 3

	//the first two dimensions represent one plane in the PlanarImg storage type
	//
	//let's introduce a point/position in the remaining dimensions and walk through
	//them, which is to increase 2nd dim/axis and if we reach end, we increase 3rd and reset 2nd,
	//if 3rd reaches end, increases 4th and resets 3rd,.... in this order we will be receiving
	//the planes
	nDimWalker_t planeWalker(imgParams.sizes+2,imgParams.dim-2);

	//for creating essentially the ArrayImg in the input data buffer by
	//shifting the data pointer in planeSize steps,
	//
	//SEE BELOW FOR GENERAL CASE OF PLACING THE OUTPUT DATA
	const long planeSize = imgParams.sizes[0] * imgParams.sizes[1];
	long offset=0;

	//cached....
	const size_t arrayElemSize = imgParams.howManyBytesPerVoxel();

	//essentially iterate over the planes and for each
	//call TransmitChunkFromOneImage() with appropriately adjusted data pointer
	do {
		//std::cout << "doing pos="; planeWalker.printPos();
		//std::cout << ", SND_MORE=" << (planeWalker.remainingSteps>0) << "\n";

		//NOW JUST PLACING JUST A PLANE ONE AFTER ANOTHER (and we need no planeWalker...)
		//
		//ONE MAY WANT TO PLACE THE CURRENT PLANE SOMEWHERE (instead of at data+offset),
		//THE PLANE WE ARE FETCHING NOW IS AT [0,0, planeWalker.pos[] ] POSITION
		TransmitChunkFromOneImage(cnnParams,data+offset,planeSize,arrayElemSize,
		  (planeWalker.remainingSteps>0));
		offset += planeSize;
	} while (planeWalker.nextStep());
}


template <typename VT>
void TransmitChunkFromOneImage(connectionParams_t& cnnParams,VT* const data,
                              const size_t arrayLength, const size_t arrayElemSize,
                              const bool comingMore)
{
	if (arrayLength < 1024 || arrayElemSize == 1)
	{
		//array that is short enough to be hosted entirely with byte[] array,
		//will be sent in one shot
		//NB: the else branch below cannot handle when arrayLength < arrayElemSize,
		//    and why to split the short arrays anyways?

		if (cnnParams.isSender)
		{
			SwapEndianness(data,arrayLength);
			cnnParams.socket->send(zmq::const_buffer((void*)data,arrayLength*arrayElemSize),
			  (comingMore? zmq::send_flags::sndmore : zmq::send_flags::none));
			SwapEndianness(data,arrayLength);
		}
		else
		{
			waitForNextMessage(cnnParams);
			cnnParams.socket->recv(zmq::mutable_buffer((void*)data,arrayLength*arrayElemSize));
			SwapEndianness(data,arrayLength);
		}
	}
	else
	{
		//for example: float array, when seen as byte array, may exceed byte array's max length;
		//we, therefore, split into arrayElemSize-1 blocks of firstBlocksLen items long from
		//the original basic type array, and into one block of lastBlockLen items long
		const size_t firstBlocksLen = arrayLength/arrayElemSize + (arrayLength%arrayElemSize != 0? 1 : 0);
		const size_t lastBlockLen   = arrayLength - (arrayElemSize-1)*firstBlocksLen;
		//NB: firstBlockLen >= lastBlockLen

		long offset=0;

		for (int p=0; p < (arrayElemSize-1); ++p)
		{
			if (cnnParams.isSender)
			{
				SwapEndianness(data+offset,firstBlocksLen);
				cnnParams.socket->send(zmq::const_buffer((void*)(data+offset),firstBlocksLen*arrayElemSize),
				  (comingMore || lastBlockLen > 0 || p < arrayElemSize-2 ? zmq::send_flags::sndmore : zmq::send_flags::none));
				SwapEndianness(data+offset,firstBlocksLen);
			}
			else
			{
				waitForNextMessage(cnnParams);
				cnnParams.socket->recv(zmq::mutable_buffer((void*)(data+offset),firstBlocksLen*arrayElemSize));
				SwapEndianness(data+offset,firstBlocksLen);
			}
			offset += firstBlocksLen;
		}

		if (lastBlockLen > 0)
		{
			if (cnnParams.isSender)
			{
				SwapEndianness(data+offset,lastBlockLen);
				cnnParams.socket->send(zmq::const_buffer((void*)(data+offset),lastBlockLen*arrayElemSize),
				  (comingMore? zmq::send_flags::sndmore : zmq::send_flags::none));
				SwapEndianness(data+offset,lastBlockLen);
			}
			else
			{
				waitForNextMessage(cnnParams);
				cnnParams.socket->recv(zmq::mutable_buffer((void*)(data+offset),lastBlockLen*arrayElemSize));
				SwapEndianness(data+offset,lastBlockLen);
			}
		}
	}
}


void FinishSendingOneImage(connectionParams_t& cnnParams)
{
	//wait for confirmation from the receiver
	waitForFirstMessage(cnnParams,"Timeout when waiting for the confirmation of a complete transfer.");

	//read income message and check if the receiving party is ready to receive our data
	zmq::message_t msg(1024);
	int recLength = cnnParams.socket->recv(msg).value();
	char* msgString = msg.data<char>();

	//check sanity of the received buffer
	if (recLength < 4)
		throw new runtime_error("Received (near) empty final (handshake) message. Stopping.");
	if (recLength == 1024)
		throw new runtime_error("Couldn't read complete final (handshake) message. Stopping.");
	if (msgString[0] != 'd' ||
	    msgString[1] != 'o' ||
	    msgString[2] != 'n' ||
	    msgString[3] != 'e')
		throw new runtime_error("Protocol error, expected final confirmation from the receiver.");

	cnnParams.clear();
}

void FinishReceivingOneImage(connectionParams_t& cnnParams)
{
	//flag all is received and we're closing
	zmq::message_t msgDone(strDone.c_str(),strDone.size());
	cnnParams.socket->send(msgDone,zmq::send_flags::none);
	cnnParams.clear();
}

//-----------
void waitForFirstMessage(connectionParams_t& cnnParams, const char* errMsg, const int _timeOut)
{
	int timeWaited = 0;
	while (timeWaited < _timeOut
	  && (cnnParams.socket->getsockopt<int>(ZMQ_EVENTS) & ZMQ_EVENT_CONNECTED) != ZMQ_EVENT_CONNECTED)
	{
		std::this_thread::sleep_for(std::chrono::seconds(1));
		++timeWaited;
	}

	//still no connection?
	if ((cnnParams.socket->getsockopt<int>(ZMQ_EVENTS) & ZMQ_EVENT_CONNECTED) != ZMQ_EVENT_CONNECTED)
	{
		if (errMsg == NULL)
			throw new runtime_error("Reached timeout for the first incoming data.");
		else
			throw new runtime_error(errMsg);
	}
}

inline void waitForFirstMessage(connectionParams_t& cnnParams, const char* errMsg)
{
	waitForFirstMessage(cnnParams,errMsg,cnnParams.timeOut);
}

void waitForNextMessage(connectionParams_t& cnnParams)
{
	int timeWaited = 0;
	while (timeWaited < cnnParams.timeOut
	  && cnnParams.socket->getsockopt<int>(ZMQ_RCVMORE) != 1) //&& !socket.hasReceiveMore()
	{
		std::this_thread::sleep_for(std::chrono::seconds(1));
		++timeWaited;
	}

	//still no connection?
	if (cnnParams.socket->getsockopt<int>(ZMQ_RCVMORE) != 1)
		throw new runtime_error("Reached timeout for the next incoming data.");
}


//-------- explicit instantiations --------
template void TransmitOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,char*           const data);
template void TransmitOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,unsigned char*  const data);
template void TransmitOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,short*          const data);
template void TransmitOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,unsigned short* const data);
template void TransmitOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,int*            const data);
template void TransmitOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,unsigned int*   const data);
template void TransmitOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,long*           const data);
template void TransmitOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,unsigned long*  const data);
template void TransmitOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,float*          const data);
template void TransmitOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,double*         const data);


//-------- class ImagesAsEventsSender --------
void ImagesAsEventsSender::connect()
{
	if (isConnected) return;

	//init the context and get the socket
	cnnParams.context  = new zmq::context_t(1);
	cnnParams.socket   = new zmq::socket_t(*(cnnParams.context), ZMQ_PAIR);
	cnnParams.addr     = this->addr;
	cnnParams.timeOut  = this->timeOut;
	cnnParams.isSender = true;

	//connects the socket with the given address
	cnnParams.socket->connect(cnnParams.addr);

	//send the "v0" initiator-header
	sendV0header("v0 expect 99999999");

	isConnected = true;
}

void ImagesAsEventsSender::disconnect()
{
	if (!isConnected) return;

	cnnParams.clear();
	isConnected = false;
}


template <typename VT>
void ImagesAsEventsSender::sendImage(const imgParams_t& imgParams, VT* const data,
                                     const bool lastImg)
{
	connect();
	//NB: makes sure internally it does not connect over connected link
	//NB: sends "introductory" v0 header

	sendOneImage(imgParams,data);

	//send the "v0" separator-header
	if (lastImg)
	{
		sendV0header("v0 hangup");
		disconnect();
	}
	else
	{
		sendV0header("v0 don't hangup!");
	}
}


void ImagesAsEventsSender::sendV0header(const std::string headerMsg)
{
	zmq::message_t msg(headerMsg.c_str(),headerMsg.size());
	cnnParams.socket->send(msg,zmq::send_flags::none);
}


template <typename VT>
void ImagesAsEventsSender::sendOneImage(const imgParams_t& imgParams, VT* const data)
{
	Handshake_GiveImage(imgParams,cnnParams);
	SendMetadata(cnnParams,metaData);
	TransmitOneImage(cnnParams,imgParams,data);

	//wait for confirmation from the receiver
	waitForFirstMessage(cnnParams,"Timeout when waiting for the confirmation of a complete transfer.");

	//read incoming message and check if the receiving party did managed to receive our data
	zmq::message_t msg(1024);
	int recLength = cnnParams.socket->recv(msg).value();
	char* msgString = msg.data<char>();

	//check sanity of the received buffer
	if (recLength < 4)
		throw new runtime_error("Received (near) empty final (handshake) message. Stopping.");
	if (recLength == 1024)
		throw new runtime_error("Couldn't read complete final (handshake) message. Stopping.");
	if (msgString[0] != 'd' ||
		 msgString[1] != 'o' ||
		 msgString[2] != 'n' ||
		 msgString[3] != 'e')
		throw new runtime_error("Protocol error, expected final confirmation from the receiver.");
}


void ImagesAsFixedSequenceSender::connect()
{
	if (isConnected) return;

	//init the context and get the socket
	cnnParams.context  = new zmq::context_t(1);
	cnnParams.socket   = new zmq::socket_t(*(cnnParams.context), ZMQ_PAIR);
	cnnParams.addr     = this->addr;
	cnnParams.timeOut  = this->timeOut;
	cnnParams.isSender = true;

	//connects the socket with the given address
	cnnParams.socket->connect(cnnParams.addr);

	isConnected = true;
}


template <typename VT>
void ImagesAsFixedSequenceSender::sendImage(const imgParams_t& imgParams, VT* const data,
                                            const bool lastImg)
{
	connect();
	//NB: makes sure internally it does not connect over connected link
	//NB: no V0 header in connect()

	//send the "v0" image announcer
	sendV0header("v0 expect 99999999");

	sendOneImage(imgParams,data);

	//send the "v0" separator-header
	if (lastImg)
	{
		sendV0header("v0 hangup");
		disconnect();
	}
}

template void ImagesAsEventsSender::sendImage(const imgParams_t& imgParams, char*           const data, const bool lastImg);
template void ImagesAsEventsSender::sendImage(const imgParams_t& imgParams, unsigned char*  const data, const bool lastImg);
template void ImagesAsEventsSender::sendImage(const imgParams_t& imgParams, short*          const data, const bool lastImg);
template void ImagesAsEventsSender::sendImage(const imgParams_t& imgParams, unsigned short* const data, const bool lastImg);
template void ImagesAsEventsSender::sendImage(const imgParams_t& imgParams, int*            const data, const bool lastImg);
template void ImagesAsEventsSender::sendImage(const imgParams_t& imgParams, unsigned int*   const data, const bool lastImg);
template void ImagesAsEventsSender::sendImage(const imgParams_t& imgParams, long*           const data, const bool lastImg);
template void ImagesAsEventsSender::sendImage(const imgParams_t& imgParams, unsigned long*  const data, const bool lastImg);
template void ImagesAsEventsSender::sendImage(const imgParams_t& imgParams, float*          const data, const bool lastImg);
template void ImagesAsEventsSender::sendImage(const imgParams_t& imgParams, double*         const data, const bool lastImg);

template void ImagesAsFixedSequenceSender::sendImage(const imgParams_t& imgParams, char*           const data, const bool lastImg);
template void ImagesAsFixedSequenceSender::sendImage(const imgParams_t& imgParams, unsigned char*  const data, const bool lastImg);
template void ImagesAsFixedSequenceSender::sendImage(const imgParams_t& imgParams, short*          const data, const bool lastImg);
template void ImagesAsFixedSequenceSender::sendImage(const imgParams_t& imgParams, unsigned short* const data, const bool lastImg);
template void ImagesAsFixedSequenceSender::sendImage(const imgParams_t& imgParams, int*            const data, const bool lastImg);
template void ImagesAsFixedSequenceSender::sendImage(const imgParams_t& imgParams, unsigned int*   const data, const bool lastImg);
template void ImagesAsFixedSequenceSender::sendImage(const imgParams_t& imgParams, long*           const data, const bool lastImg);
template void ImagesAsFixedSequenceSender::sendImage(const imgParams_t& imgParams, unsigned long*  const data, const bool lastImg);
template void ImagesAsFixedSequenceSender::sendImage(const imgParams_t& imgParams, float*          const data, const bool lastImg);
template void ImagesAsFixedSequenceSender::sendImage(const imgParams_t& imgParams, double*         const data, const bool lastImg);

}
