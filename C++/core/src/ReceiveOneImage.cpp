#include <stdexcept>
#include <iostream>
#include <sstream>
#include <list>
#include <string>
#include <zmq.hpp>

#include "ImgParams.h"

//short-cut to throwing runtime_error exceptions
using std::runtime_error;

/**
 * Waits not longer than timeOut seconds on local port for the first message
 * of the new image transfer, and fills the output imgParams image configuration
 * data. Throws exceptions if something goes wrong: timeOut, unable to parse,
 * or wrong format/protocol error...
 */
void* StartReceivingOneImage(imgParams_t& imgParams, const int port, const int timeOut)
{
	//init the context and the get socket
	zmq::context_t* s_ctx = new zmq::context_t(1);
	zmq::socket_t* socket = new zmq::socket_t(*s_ctx, ZMQ_PAIR);
	//zmq::socket_t& socket = *socketPtr;
	//NB: we need to create the socket object in the global space,
	//    so that we can return (a functional) pointer on it

	//binds the socket to the given port
	char chrString[1024];
	sprintf(chrString,"tcp://*:%d",port);
	socket->bind(chrString);

	//attempt to receive the first message, while waiting up to timeOut seconds
	//TODO add waitForIncomingData()
	int recLength = socket->recv((void*)chrString,1024,0);

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

	return reinterpret_cast<void*>(socket);
}

void ReceiveMetadata(void* connectionHandle,std::list<std::string>& metaData)
{
	zmq::socket_t& socket = *(reinterpret_cast<zmq::socket_t*>(connectionHandle));

	 //wait for socket

	 /*
    std::cout << "Connecting to hello world server..." << std::endl;
    socket.connect ("tcp://localhost:5555");

    //  Do 10 requests, waiting each time for a response
    for (int request_nbr = 0; request_nbr != 10; request_nbr++) {
        zmq::message_t request (5);
        memcpy (request.data (), "Hello", 5);
        std::cout << "Sending Hello " << request_nbr << "..." << std::endl;
        socket.send (request);

        //  Get the reply.
        zmq::message_t reply;
        socket.recv (&reply);
        std::cout << "Received World " << request_nbr << std::endl;
    }
	 */
}

template <typename VT>
void ReceiveOneArrayImage(void* connectionHandle,VT* const data)
{
	zmq::socket_t& socket = *(reinterpret_cast<zmq::socket_t*>(connectionHandle));
}

template <typename VT>
void ReceiveOnePlanarImage(void* connectionHandle,const imgParams_t& imgParams,VT* const data)
{
	zmq::socket_t& socket = *(reinterpret_cast<zmq::socket_t*>(connectionHandle));
}

template <typename VT>
void ReceiveNextPlaneFromOneImage(void* connectionHandle,VT* const data)
{
	zmq::socket_t& socket = *(reinterpret_cast<zmq::socket_t*>(connectionHandle));
}

void FinishReceivingOneImage(void* connectionHandle)
{
	zmq::socket_t& socket = *(reinterpret_cast<zmq::socket_t*>(connectionHandle));

	//flag all is received and we're closing
	char done[] = "done";
	socket.send(done,4,0);
	socket.close();

	//TODO: does not recognize this pointer... maybe...
	delete reinterpret_cast<zmq::socket_t*>(connectionHandle);
}


//-------- explicit instantiations --------
//char
template void ReceiveOneArrayImage(void* connectionHandle,char* const data);
template void ReceiveOnePlanarImage(void* connectionHandle,const imgParams_t& imgParams,char* const data);
template void ReceiveNextPlaneFromOneImage(void* connectionHandle,char* const data);

//unsigned char
template void ReceiveOneArrayImage(void* connectionHandle,unsigned char* const data);
template void ReceiveOnePlanarImage(void* connectionHandle,const imgParams_t& imgParams,unsigned char* const data);
template void ReceiveNextPlaneFromOneImage(void* connectionHandle,unsigned char* const data);

//short
template void ReceiveOneArrayImage(void* connectionHandle,short* const data);
template void ReceiveOnePlanarImage(void* connectionHandle,const imgParams_t& imgParams,short* const data);
template void ReceiveNextPlaneFromOneImage(void* connectionHandle,short* const data);

//unsigned short
template void ReceiveOneArrayImage(void* connectionHandle,unsigned short* const data);
template void ReceiveOnePlanarImage(void* connectionHandle,const imgParams_t& imgParams,unsigned short* const data);
template void ReceiveNextPlaneFromOneImage(void* connectionHandle,unsigned short* const data);

//long
template void ReceiveOneArrayImage(void* connectionHandle,long* const data);
template void ReceiveOnePlanarImage(void* connectionHandle,const imgParams_t& imgParams,long* const data);
template void ReceiveNextPlaneFromOneImage(void* connectionHandle,long* const data);

//unsigned long
template void ReceiveOneArrayImage(void* connectionHandle,unsigned long* const data);
template void ReceiveOnePlanarImage(void* connectionHandle,const imgParams_t& imgParams,unsigned long* const data);
template void ReceiveNextPlaneFromOneImage(void* connectionHandle,unsigned long* const data);

//float
template void ReceiveOneArrayImage(void* connectionHandle,float* const data);
template void ReceiveOnePlanarImage(void* connectionHandle,const imgParams_t& imgParams,float* const data);
template void ReceiveNextPlaneFromOneImage(void* connectionHandle,float* const data);

//double
template void ReceiveOneArrayImage(void* connectionHandle,double* const data);
template void ReceiveOnePlanarImage(void* connectionHandle,const imgParams_t& imgParams,double* const data);
template void ReceiveNextPlaneFromOneImage(void* connectionHandle,double* const data);
