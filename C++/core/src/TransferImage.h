#ifndef TransferImageH
#define TransferImageH

#include <list>
#include <string>

#include "TransferImage_Utils.h"

namespace DAIS
{

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
 * The function works in conjuction with StartReceivingOneImage().
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
 * The same as StartSendingOneImage() except that the connection has to come
 * from the other peer. This function thus listens on given port.
 *
 * The function works in conjuction with StartRequestingOneImage().
 */
void StartServingOneImage(const imgParams_t& imgParams,connectionParams_t& cnnParams,
                          const int port, const int timeOut = 60);

/**
 * Waits not longer than timeOut seconds on local port for the initial handshake
 * message of the new image transfer, and fills the output imgParams image geometry
 * data. Throws exceptions if something goes wrong: timeOut, unable to parse,
 * or wrong format/protocol error...
 *
 * Populates the handle cnnParams on the established connection.
 * Use this handle in the following functions.
 *
 * The function works in conjuction with StartSendingOneImage().
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

/**
 * The same as StartReceivingOneImage() except that this one initiates the
 * connection to the other peer. This function thus requires addr(ess).
 *
 * The function works in conjuction with StartRequestingOneImage().
 */
void StartRequestingOneImage(imgParams_t& imgParams,connectionParams_t& cnnParams,
                             const char* addr, const int timeOut = 60);

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


/**
 * Utility class to facilitate repetitive transmission of (possibly
 * different in content, in size, in voxel types) images. When an
 * object is created, no connection is made -- the connection is
 * established not sooner before the first transmission attempt,
 * unless some one opens it explicitly via connect(). One then
 * keeps sending images via sendImage().
 *
 * The class is using the sequential multiple-images transfer protocol
 * of DAIS wp1.3, that is, the "v0" headers are used. This class
 * is sending the "next image" avizo header message right after
 * the current image was transferred, while the Java implementation
 * is sending the avizo only right before the next image is transferred.
 * The latter, however, prevents the receiving side from obtaining the
 * current image until some next image transmission occurs. To prevent
 * from this, here the avizo is sent right away, even if the very last
 * image is transferred... (in any case violating the original protocol).
 *
 *
 * Consider a sequence of images is expected to be transfered -- the multiImage
 * scenario of the protocol. After receiving an image, the receiving side
 * needs to make a decision whether this was the last image or whether it
 * shall start waiting for another image (risking that waiting will end up
 * with complaining exception if nothing arrives within the timeout period).
 * This class is designed to announce transmission of a next image right
 * after the end of the transmission of the current image(*), even when no
 * such image is provided to this class (unless user flags differently
 * via the 'lastImg' parameter of the sendImage() method). This is more
 * like a possibly infinite sequence of events that will appear, hence the
 * name of the class.
 *
 * (*) The receiving side will therefore make the just received image avail-
 * able to its user together with the information about availability of some
 * next image, and it may immediately start waiting for that image.
 *
 * In contrast, the multiImage protocol assumes that the receiver blocks after
 * an image is obtained while waiting for a "v0" header that would tell it if
 * another image is available or not. This way, receiver will update
 * its user with correct/reliable information to make decision whether to
 * wait for another image or not. This is more like a predetermined
 * sequence of known and fixed length, hence the name of the sibling class.
 */
class ImagesAsEventsSender
{
public:
	ImagesAsEventsSender(const char* addr, const int timeOut,
	                     const char* imgsName = NULL)
	{
		//backup all transfer metadata
		this->addr = std::string("tcp://")+std::string(addr);
		this->timeOut = timeOut;
		isConnected = false;

		//backup all image metadata
		metaData.push_back(std::string("imagename"));
		if (imgsName != NULL)
			metaData.push_back(std::string(imgsName));
		else
			metaData.push_back(std::string("sent from C++ world"));
	}

	~ImagesAsEventsSender()
	{
		disconnect();
		//NB: does also clean up
	}

	/** Just connects to the peer, does not wait for any connection
	    confirmation. It can be called repetitively -- it will not connect
	    existing/established connection */
	void connect();

	/** Disconnects and clears() the connection params this->cnnParams.
	    Will not disconnect/clear if already disconnected/cleared,
	    safe to be called repetitively. */
	void disconnect();

	/** own version of the sendImage that does send "v0" header before
	    transfers the image and immediately sends "v0" header that
	    announces next image (or announces end of the sequence if
	    the param 'lastImg' is true) */
	template <typename VT>
	void sendImage(const imgParams_t& imgParams, VT* const data,
	               const bool lastImg = false);

	const std::string& getURL()
	{ return addr; }

	int getTimeOutInSeconds()
	{ return timeOut; }

	int getIsConnected()
	{ return isConnected; }

protected:
	std::string addr;
	int timeOut; //in seconds

	bool isConnected = false;

	connectionParams_t cnnParams;
	std::list<std::string> metaData;

	template <typename VT>
	void sendOneImage(const imgParams_t& imgParams, VT* const data);
	void sendV0header(const std::string msg);
};


/** the sibling class toe ImagesAsEventsSender, see its docs */
class ImagesAsFixedSequenceSender : public ImagesAsEventsSender
{
public:
	ImagesAsFixedSequenceSender(const char* addr, const int timeOut,
	                            const char* imgsName = NULL)
	  : ImagesAsEventsSender(addr,timeOut,imgsName)
	{}

	~ImagesAsFixedSequenceSender()
	{}

	/** connect that does not send v0 header immediately */
	void connect();

	/** own version of the sendImage that does send "v0" header before
	    the image transfer occurs */
	template <typename VT>
	void sendImage(const imgParams_t& imgParams, VT* const data,
	               const bool lastImg = false);
};

}
#endif
