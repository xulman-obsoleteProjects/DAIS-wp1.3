#ifndef ReceiveOneImageH
#define ReceiveOneImageH

#include <list>
#include <string>
#include <zmq.hpp>

#include "ImgParams.h"

/**
 * A helper structure to act as a handle for state-less functions.
 * The structure holds references on ZeroMQ internals plus clear()
 * function.
 */
typedef struct connectionParams
{
	zmq::context_t* context = NULL;
	zmq::socket_t*  socket  = NULL;
	int port = 0;

	//returns the attributes to the initial state in a way polite for ZeroMQ
	void clear()
	{
		if (port != 0)
		{
			char chrString[1024];
			sprintf(chrString,"tcp://*:%d",port);
			//socket->unbind(chrString); -- ZeroMQ complains... hmm
			port = 0;
		}

		if (socket != NULL)
		{
			socket->close();
			delete socket;
			socket = NULL;
		}

		if (context != NULL)
		{
			delete context;
			context = NULL;
		}
	}
} connectionParams_t;


/**
 * Waits not longer than timeOut seconds on local port for the first message
 * of the new image transfer, and fills the output imgParams image configuration
 * data. Throws exceptions if something goes wrong: timeOut, unable to parse,
 * or wrong format/protocol error...
 *
 * Returns handle on the established connection.
 * Use this handle in the following functions.
 */
void StartReceivingOneImage(imgParams_t& imgParams,connectionParams_t& cnnParams,
                            const int port, const int timeOut = 60);

/**
 * After seeing the initial handshake header, after calling StartReceivingOneImage(),
 * this one fill the list of strings with transmitted metadata.
 */
void ReceiveMetadata(connectionParams_t& cnnParams,std::list<std::string>& metaData);

/**
 * After the metadata has arrived, use this function to fill the output data
 * array; the array has to be allocated already (consider imgParams::howManyBytes()
 * or imgParams::howManyVoxels()).
 *
 * It is assumed that the storage type of the original image is 'ArrayImg'
 * -- the image is transmitted in one shot (from user's perspective).
 */
template <typename VT>
void ReceiveOneArrayImage(connectionParams_t& cnnParams,VT* const data);

/**
 * After the metadata has arrived, use this function to fill the output data
 * array; the array has to be allocated already (consider imgParams::howManyBytes()
 * or imgParams::howManyVoxels()).
 *
 * It is assumed that the storage type of the original image is 'PlanarImg'
 * -- the image is transmitted in multiple shots (from user's perspective).
 * There is always one shot for one plane, and normally one can save every
 * plane independently -- not necessarily in a long consecutive array. This
 * function however assumes that output image is represented as one long
 * consecutive array and saves the planes one after one (for which it needs
 * to know the image geometry via imgParams).
 *
 * Use ReceiveNextPlaneFromOneImage() if you want to have a control where every
 * plane is saved.
 */
template <typename VT>
void ReceiveOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,VT* const data);

/**
 * After the metadata has arrived, use this function to fill the output data
 * array; the array has to be allocated already to hold exactly one plane
 * -- see the discussion in ReceiveOnePlanarImage().
 */
template <typename VT>
void ReceiveNextPlaneFromOneImage(connectionParams_t& cnnParams,VT* const data);

/**
 * Signals the transmission was received well, and closes the socket
 * via cnnParams.clear(). This also frees the memory allocated
 * inside the cnnParams making its content not useful anymore.
 */
void FinishReceivingOneImage(connectionParams_t& cnnParams);

#endif
