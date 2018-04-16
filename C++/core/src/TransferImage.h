#ifndef TransferImageH
#define TransferImageH

#include <list>
#include <string>

#include "TransferImage_Utils.h"

/**
 * Given the input image, here represented just with its geometry in imgParams,
 * the function attempt to establish connection with addr and sends the initial
 * handshake message. It waits for the confirmation response but no longer than
 * timeOut seconds. Throws exceptions if something goes wrong: timeOut, no
 * confirmation...
 *
 * Populates the handle cnnParams on the established connection.
 * Use this handle in the following functions.
 *
 * Expected sequence of calls is as follows:

imgParams_t imgParams;
imgParams.foo set according to an existing image to be transfered
imgParams.backendType = std::string("PlanarImg"); //IMPORTANT to support Java
unsigned short* data = point on raw/pixel data of your image

connectionParams_t cnnParams;
StartSendingOneImage(imgParams,cnnParams,"localhost:54545");

//IMPORTANT, should exist: 'imagename' and 'some name with allowed whitespaces'
std::list<std::string> metaData;
metaData.push_back(std::string("imagename"));
metaData.push_back(std::string("sent from C++ world"));
SendMetadata(cnnParams,metaData);

TransmitOneImage(cnnParams,imgParams,data);

FinishSendingOneImage(cnnParams);

imgParams.clear();
delete[] data;
 */
void StartSendingOneImage(const imgParams_t& imgParams,connectionParams_t& cnnParams,
                          const char* addr, const int timeOut = 60);

/**
 * Waits not longer than timeOut seconds on local port for the initial handshake
 * message of the new image transfer, and fills the output imgParams image geometry
 * data. Throws exceptions if something goes wrong: timeOut, unable to parse,
 * or wrong format/protocol error...
 *
 * Populates the handle cnnParams on the established connection.
 * Use this handle in the following functions.
 *
 * Expected sequence of calls is as follows:

imgParams_t imgParams;
connectionParams_t cnnParams;
StartReceivingOneImage(imgParams,cnnParams,54545);

std::list<std::string> metaData;
ReceiveMetadata(cnnParams,metaData);

char* data = new char[imgParams.howManyBytes()];

if (imgParams.enumVoxelType() == imgParams::voxelTypes::UnsignedShort)
	TransmitOneImage(cnnParams,imgParams,(unsigned short*)data);

FinishReceivingOneImage(cnnParams);

imgParams.clear();
delete[] data;
 */
void StartReceivingOneImage(imgParams_t& imgParams,connectionParams_t& cnnParams,
                            const int port, const int timeOut = 60);

//meta data Message Separator
const char mdMsgSep[] = "__QWE__";
const int mdMsgSepLen = 7;

/**
 * After seeing the initial handshake, after calling StartSendingOneImage(),
 * this sends the metadata message. The metadata is filled from the list (metaData)
 * of strings.
 *
 * See StartSendingOneImage() for an overview of necessary calls.
 */
void SendMetadata(connectionParams_t& cnnParams,const std::list<std::string>& metaData);

/**
 * After seeing the initial handshake header, after calling StartReceivingOneImage(),
 * this one fill the list of strings with transmitted metadata.
 *
 * See StartReceivingOneImage() for an overview of necessary calls.
 */
void ReceiveMetadata(connectionParams_t& cnnParams,std::list<std::string>& metaData);

/**
 * After the metadata has arrived, use this function to fill the output data
 * array; the array has to be allocated already (consider imgParams::howManyBytes()
 * or imgParams::howManyVoxels()).
 *
 * It is assumed that the storage type of the original image is 'ArrayImg'
 * -- the image is transmitted in one shot (from user's perspective).
 *
 * See StartSendingOneImage() for an overview of necessary calls.
 * See StartReceivingOneImage() for an overview of necessary calls.
 */
template <typename VT>
void TransmitOneArrayImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,VT* const data);

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
 * consecutive array and saves the planes one after one (for which it also
 * needs to know the image geometry via imgParams).
 *
 * Use TransmitChunkFromOneImage() and nDimWalker struct below if you want
 * to have a control where every plane is saved. Also, the source code of
 * this function provides additional hints (in upper case letters).
 *
 * See StartSendingOneImage() for an overview of necessary calls.
 * See StartReceivingOneImage() for an overview of necessary calls.
 */
template <typename VT>
void TransmitOnePlanarImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,VT* const data);

// convenience wrapper to call the appropriate one image's raw data transmitter
template <typename VT>
void TransmitOneImage(connectionParams_t& cnnParams,const imgParams_t& imgParams,VT* const data)
{
	if (imgParams.backendType.find("Array") != std::string::npos)
		TransmitOneArrayImage<VT>(cnnParams,imgParams,data);
	else
		TransmitOnePlanarImage<VT>(cnnParams,imgParams,data);
}

/**
 * After the metadata has arrived, use this function to fill the output data
 * array. The function takes care of one 'shot' of the transmission, refer to
 * TransmitOneArrayImage() and TransmitOnePlanarImage() to understand what 'shot'
 * means.
 *
 * The output array has to be allocated already. In particular, it should hold that
 * the array is arrayLength items long and each item consumes arrayElemSize Bytes.
 *
 * The last comingMore flag signals if a multi-part ZeroMQ message should be sent,
 * that is, if this call will be followed with another call of this function.
 */
template <typename VT>
void TransmitChunkFromOneImage(connectionParams_t& cnnParams,VT* const data,
                              const size_t arrayLength, const size_t arrayElemSize,
                              const bool comingMore = false);

/**
 * Waits for the signal that the transmission was received well,
 * and closes the socket via cnnParams.clear(). This also frees the
 * memory allocated inside the cnnParams making its content not useful anymore.
 *
 * See StartReceivingOneImage() for an overview of necessary calls.
 */
void FinishSendingOneImage(connectionParams_t& cnnParams);

/**
 * Signals the transmission was received well, and closes the socket
 * via cnnParams.clear(). This also frees the memory allocated
 * inside the cnnParams making its content not useful anymore.
 *
 * See StartSendingOneImage() for an overview of necessary calls.
 */
void FinishReceivingOneImage(connectionParams_t& cnnParams);
#endif
