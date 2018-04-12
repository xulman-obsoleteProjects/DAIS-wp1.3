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
	std::cout << "metadata: XX" << smsg << "XX\n"; //REMOVE DEBUG

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
void ReceiveOneArrayImage(connectionParams_t& cnnParams,VT* const data)
{
}

template <typename VT>
void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,VT* const data)
{
}

template <typename VT>
void ReceiveNextPlaneFromOneImage(connectionParams_t& cnnParams,VT* const data)
{
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
template void ReceiveOneArrayImage(connectionParams_t& cnnParams,char* const data);
template void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,char* const data);
template void ReceiveNextPlaneFromOneImage(connectionParams_t& cnnParams,char* const data);

//unsigned char
template void ReceiveOneArrayImage(connectionParams_t& cnnParams,unsigned char* const data);
template void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,unsigned char* const data);
template void ReceiveNextPlaneFromOneImage(connectionParams_t& cnnParams,unsigned char* const data);

//short
template void ReceiveOneArrayImage(connectionParams_t& cnnParams,short* const data);
template void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,short* const data);
template void ReceiveNextPlaneFromOneImage(connectionParams_t& cnnParams,short* const data);

//unsigned short
template void ReceiveOneArrayImage(connectionParams_t& cnnParams,unsigned short* const data);
template void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,unsigned short* const data);
template void ReceiveNextPlaneFromOneImage(connectionParams_t& cnnParams,unsigned short* const data);

//long
template void ReceiveOneArrayImage(connectionParams_t& cnnParams,long* const data);
template void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,long* const data);
template void ReceiveNextPlaneFromOneImage(connectionParams_t& cnnParams,long* const data);

//unsigned long
template void ReceiveOneArrayImage(connectionParams_t& cnnParams,unsigned long* const data);
template void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,unsigned long* const data);
template void ReceiveNextPlaneFromOneImage(connectionParams_t& cnnParams,unsigned long* const data);

//float
template void ReceiveOneArrayImage(connectionParams_t& cnnParams,float* const data);
template void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,float* const data);
template void ReceiveNextPlaneFromOneImage(connectionParams_t& cnnParams,float* const data);

//double
template void ReceiveOneArrayImage(connectionParams_t& cnnParams,double* const data);
template void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,double* const data);
template void ReceiveNextPlaneFromOneImage(connectionParams_t& cnnParams,double* const data);
