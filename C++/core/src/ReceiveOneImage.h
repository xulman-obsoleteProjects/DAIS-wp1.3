#ifndef ReceiveOneImageH
#define ReceiveOneImageH

#include <list>
#include <string>

#include "ImgParams.h"

/**
 * Waits not longer than timeOut seconds on local port for the first message
 * of the new image transfer, and fills the output imgParams image configuration
 * data. Throws exceptions if something goes wrong: timeOut, unable to parse,
 * or wrong format/protocol error...
 *
 * Returns handle on the established connection.
 * Use this handle in the following functions.
 */
void* StartReceivingOneImage(imgParams_t& imgParams, const int port, const int timeOut = 60);

/**
 * After seeing the initial handshake header, after calling StartReceivingOneImage(),
 * this one fill the list of strings with transmitted metadata.
 */
void ReceiveMetadata(void* connectionHandle,std::list<std::string>& metaData);

/**
 * After the metadata has arrived, use this function to fill the output data
 * array; the array has to be allocated already (consider imgParams::howManyBytes()
 * or imgParams::howManyVoxels()).
 *
 * It is assumed that the storage type of the original image is 'ArrayImg'
 * -- the image is transmitted in one shot (from user's perspective).
 */
template <typename VT>
void ReceiveOneArrayImage(void* connectionHandle,VT* const data);

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
void ReceiveOnePlanarImage(void* connectionHandle,const imgParams_t& imgParams,VT* const data);

/**
 * After the metadata has arrived, use this function to fill the output data
 * array; the array has to be allocated already to hold exactly one plane
 * -- see the discussion in ReceiveOnePlanarImage().
 */
template <typename VT>
void ReceiveNextPlaneFromOneImage(void* connectionHandle,VT* const data);

/**
 * Signals the transmission was received well, closes the socket.
 * It also frees the memory allocated with the connectionHandle pointer,
 * so be aware that this pointer is afterwards not useful anymore.
 */
void FinishReceivingOneImage(void* connectionHandle);

#endif
