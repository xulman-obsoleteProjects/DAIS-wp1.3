#include <stdexcept>
#include <iostream>
#include <sstream>
#include <list>
#include <string>
#include <zmq.hpp>

#include "ImgParams.h"
#include "ReceiveOneImage.h"

//short-cut to throwing runtime_error exceptions
using std::runtime_error;

/**
 * Waits not longer than timeOut seconds on local port for the first message
 * of the new image transfer, and fills the output imgParams image configuration
 * data. Throws exceptions if something goes wrong: timeOut, unable to parse,
 * or wrong format/protocol error...
 */
void StartReceivingOneImage(imgParams_t& imgParams,connectionParams_t& cnnParams,
                            const int port, const int timeOut)
{
	//init the context and get the socket
	cnnParams.context = new zmq::context_t(1);
	cnnParams.socket  = new zmq::socket_t(*(cnnParams.context), ZMQ_PAIR);
	cnnParams.port    = port;

	//binds the socket to the given port
	char chrString[1024];
	sprintf(chrString,"tcp://*:%d",port);
	cnnParams.socket->bind(chrString);

	//attempt to receive the first message, while waiting up to timeOut seconds
	//TODO add waitForIncomingData()
	int recLength = cnnParams.socket->recv((void*)chrString,1024,0);

	//check sanity of the received buffer
	if (recLength <= 0)
		throw new runtime_error("Received empty initial (handshake) message. Stopping.");
	if (recLength == 1024)
		throw new runtime_error("Couldn't read complete initial (handshake) message. Stopping.");

	//parse it into the imgParams structure, or complain
	std::cout << "Received: " << chrString << "\n";
	std::istringstream hdrMsg(chrString);

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


void ReceiveMetadata(connectionParams_t& cnnParams,std::list<std::string>& metaData)
{
	//sends flag that we're free to go, first comes the image metadata
	char readyMsg[] = "ready";
	cnnParams.socket->send(readyMsg,5,0);

	//TODO waitForFirstMessage()
	zmq::message_t msg;
	if (!cnnParams.socket->recv(&msg))
		throw new runtime_error("Haven't received metadata at all.");

	//"convert" to std::string (likely by making extra copy of it)
	//NB: haven't find how to discard/dispose the msg :(
	std::string smsg(msg.data<char>());

	//first token needs to be "metadata"
	if (smsg.find("metadata") != 0)
		throw new runtime_error("Protocol error, expected metadata part from the receiver.");

	//meta data Message Separator
	const char mdMsgSep[] = "__QWE__";
	const int mdMsgSepLen = 7;

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
void ReceiveOneArrayImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,VT* const data)
{
	//the length of the corresponding/input basic type array
	const size_t arrayLength   = imgParams.howManyVoxels();
	const size_t arrayElemSize = imgParams.howManyBytesPerVoxel();

	ReceiveChunkFromOneImage(cnnParams,data,arrayLength,arrayElemSize);
}

template <typename VT>
void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,VT* const data)
{
	//for "degraded" cases, use directly the "ArrayImg-sibbling-function"
	if (imgParams.dim < 3)
	{
		ReceiveOneArrayImage(cnnParams,imgParams,data);
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
	//call ReceiveChunkFromOneImage() with appropriatelly adjusted data pointer
	do {
		//std::cout << "doing pos="; planeWalker.printPos(); std::cout << "\n";

		//NOW JUST PLACING JUST A PLANE ONE AFTER ANOTHER (and we need no planeWalker...)
		//
		//ONE MAY WANT TO PLACE THE CURRENT PLANE SOMEWHERE (instead of at data+offset),
		//THE PLANE WE ARE FETCHING NOW IS AT [0,0, planeWalker.pos[] ] POSITION
		ReceiveChunkFromOneImage(cnnParams,data+offset,planeSize,arrayElemSize);
		offset += planeSize;
	} while (planeWalker.nextStep());
}


template <typename VT>
void ReceiveChunkFromOneImage(connectionParams_t& cnnParams,VT* const data,
                              const size_t arrayLength, const size_t arrayElemSize)
{
	if (arrayLength < 1024 || arrayElemSize == 1)
	{
		//array that is short enough to be hosted entirely with byte[] array,
		//will be sent in one shot
		//NB: the else branch below cannot handle when arrayLength < arrayElemSize,
		//    and why to split the short arrays anyways?

		//TODO waitForNextMessage()
		cnnParams.socket->recv((void*)data,arrayLength);
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
			//TODO waitForNextMessage()
			cnnParams.socket->recv((void*)(data+offset),firstBlocksLen*arrayElemSize);
			SwapEndianness(data+offset,firstBlocksLen);
			offset += firstBlocksLen;
		}

		if (lastBlockLen > 0)
		{
			//TODO waitForNextMessage()
			cnnParams.socket->recv((void*)(data+offset),lastBlockLen*arrayElemSize);
			SwapEndianness(data+offset,lastBlockLen);
		}
	}
}


void FinishReceivingOneImage(connectionParams_t& cnnParams)
{
	//flag all is received and we're closing
	char doneMsg[] = "done";
	cnnParams.socket->send(doneMsg,4,0);
	cnnParams.clear();
}


//-------- explicit instantiations --------
//char
template void ReceiveOneArrayImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,char* const data);
template void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,char* const data);

//unsigned char
template void ReceiveOneArrayImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,unsigned char* const data);
template void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,unsigned char* const data);

//short
template void ReceiveOneArrayImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,short* const data);
template void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,short* const data);

//unsigned short
template void ReceiveOneArrayImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,unsigned short* const data);
template void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,unsigned short* const data);

//int
template void ReceiveOneArrayImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,int* const data);
template void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,int* const data);

//unsigned int
template void ReceiveOneArrayImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,unsigned int* const data);
template void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,unsigned int* const data);

//long
template void ReceiveOneArrayImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,long* const data);
template void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,long* const data);

//unsigned long
template void ReceiveOneArrayImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,unsigned long* const data);
template void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,unsigned long* const data);

//float
template void ReceiveOneArrayImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,float* const data);
template void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,float* const data);

//double
template void ReceiveOneArrayImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,double* const data);
template void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,double* const data);
