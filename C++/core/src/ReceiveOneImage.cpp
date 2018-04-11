#include <zmq.hpp>
#include <string>
#include <iostream>

#include "ImgParams.h"

/**
 * Waits not longer than timeOut seconds on local port for the first message
 * of the new image transfer, and fills the output imgParams image configuration
 * data. Throws exceptions if something goes wrong: timeOut, unable to parse,
 * or wrong format/protocol error...
 */
void ReceiveOneImage(imgParams_t& imgParams, const int port, const int timeOut)
{
	//init the context and the get socket
	zmq::context_t context(1);
	zmq::socket_t socket(context, ZMQ_PAIR);

	//binds the socket to the given port
	char chrString[1024];
	sprintf(chrString,"tcp://*:%d",port);
	socket.bind(chrString);

	//attempt to receive the first message, while waiting up to timeOut seconds
	//TODO
	int recLength = socket.recv((void*)chrString,1024,0);

	//parse it into the imgParams structure or complain
	if (recLength > 0)
		std::cout << "Received: " << chrString << "\n";
	else
		std::cout << "Nothing came...\n";


	imgParams.dim = 3;





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

