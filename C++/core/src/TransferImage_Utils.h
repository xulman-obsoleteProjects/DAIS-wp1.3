#ifndef TransferImageUtilsH
#define TransferImageUtilsH

#include <stdexcept>
#include <string>
#include <zmq.hpp>

#include <iostream>

namespace DAIS
{

/**
 * Simple structure to hold parsed content of the initial message.
 * It therefore contains: image geometry, voxel type, backend type.
 *
 * Currently, only two backends are supported: ArrayImg and PlanarImg.
 * ArrayImg is represented as a single buffer containing the whole image,
 * PlanarImg is a sequence of 2D slices, that populate the remaining
 * imgParams.dim-2 dimensions of the original image.
 */
typedef struct imgParams
{
	//dimensionality of the image,
	//also defines lengths of the underlying arrays
	int dim = 0;

	//the array with sizes along all dimensions
	int* sizes = NULL;

	//string-ified representation of the voxel type in the format:
	//UnsignedShortType or alike
	std::string voxelType;

	//string-ified representation of the image storage backend
	std::string backendType;

	//-----------
	//convenience enum-ed variants of the voxelType variable
	//BTW: useful for switch-statements
	typedef enum voxelType {
		Byte,
		UnsignedByte,
		Short,
		UnsignedShort,
		Int,
		UnsignedInt,
		Long,
		UnsignedLong,
		Float,
		Double
	} voxelTypes;

	//convenience convertor function: enum-ed variant of the voxelType variable
	voxelTypes enumVoxelType() const
	{
		voxelTypes vt;
		if (voxelType.find("UnsignedByte") != std::string::npos) vt = UnsignedByte;
		else
		if (voxelType.find("Byte") != std::string::npos) vt = Byte;
		else
		if (voxelType.find("UnsignedShort") != std::string::npos) vt = UnsignedShort;
		else
		if (voxelType.find("Short") != std::string::npos) vt = Short;
		else
		if (voxelType.find("UnsignedInt") != std::string::npos) vt = UnsignedInt;
		else
		if (voxelType.find("Int") != std::string::npos) vt = Int;
		else
		if (voxelType.find("UnsignedLong") != std::string::npos) vt = UnsignedLong;
		else
		if (voxelType.find("Long") != std::string::npos) vt = Long;
		else
		if (voxelType.find("Float") != std::string::npos) vt = Float;
		else
		if (voxelType.find("Double") != std::string::npos) vt = Double;
		else
			//undetermined type, complain!
			throw new std::runtime_error(
			  (std::string("Couldn't recognize voxel type: ")+voxelType).c_str() );

		return vt;
	}

	//-----------
	//convenience calculator function: how many Voxels is this image?
	long howManyVoxels() const
	{
		if (dim == 0) return 0;

		long cnt = sizes[0];
		for (int i=1; i < dim; ++i)
			cnt *= sizes[i];

		return cnt;
	}

	//convenience calculator function: how many Bytes occupies one Voxel
	long howManyBytesPerVoxel() const
	{
		long voxelSize;
		switch (enumVoxelType())
		{
			//enumVoxelType() will throw exception if voxel type is not understood
			case Byte:
			case UnsignedByte:
				voxelSize = 1;
				break;
			case Short:
			case UnsignedShort:
				voxelSize = 2;
				break;
			case Int:
			case UnsignedInt:
				voxelSize = 4;
				break;
			case Long:
			case UnsignedLong:
				voxelSize = 8;
				break;
			case Float:
				voxelSize = 4;
				break;
			case Double:
				voxelSize = 8;
				break;
		}

		return voxelSize;
	}

	//convenience calculator function: how many Bytes is this image?
	long howManyBytes() const
	{
		return (howManyBytesPerVoxel() * howManyVoxels());
	}

	//-----------
	//performs clean up inside the structure
	void clear()
	{
		if (sizes != NULL)
		{
			delete[] sizes;
			sizes = NULL;
		}
	}

	//imgParams(const int n): dim(n)
	//{ sizes = new int[n]; }

	~imgParams()
	{ clear(); }

} imgParams_t;


/**
 * A helper structure to act as a handle for state-less functions.
 * The structure holds references on ZeroMQ internals plus clear()
 * function.
 */
typedef struct connectionParams
{
	//ZeroMQ internals/handles relevant for this connection
	zmq::context_t* context = NULL;
	zmq::socket_t*  socket  = NULL;

	//connection details: tcp://localhost:this.port if isSender==false
	int port = 0;
	//connection details: tcp://this.addr           if isSender==true
	std::string addr;

	/**
	 * A timeout interval used while waiting for next (not the first one)
	 * "packet/message/chunk of data". That is, a waiting time applied only once
	 * connection got established. Can be considered as a timeout before
	 * connection is declared to be broken.
	 *
	 * Shouldn't be negative. Default is 30 seconds.
	 */
	int timeOut = 60;

	//direction of this connection: from array to socket is when isSender==true
	bool isSender = false;

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

		if (addr.size() > 0)
		{
			socket->disconnect(addr);
			addr.clear();
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


//flip incoming data between big-endian (network standard) and little-endian (Intel native/CPU standard)
inline void SwapEndianness(char* const, const long)
{ //intentionally empty
}
inline void SwapEndianness(unsigned char* const, const long)
{ //intentionally empty
}

inline void SwapEndianness(unsigned short* const data, const long len)
{
	for (long i=0; i < len; ++i)
		data[i] = (unsigned short)((data[i] << 8) | (data[i] >> 8));
}
inline void SwapEndianness(short* const data, const long len)
{
	SwapEndianness(reinterpret_cast<unsigned short*>(data),len);
}

inline void SwapEndianness(unsigned int* const data, const long len)
{
	for (long i=0; i < len; ++i)
		data[i] = (data[i] << 24) | (data[i] << 8 & 0x00FF0000) | (data[i] >> 8 & 0x0000FF00) | (data[i] >> 24);
}
inline void SwapEndianness(int* const data, const long len)
{
	SwapEndianness(reinterpret_cast<unsigned int*>(data),len);
}

inline void SwapEndianness(unsigned long* const data, const long len)
{
	for (long i=0; i < len; ++i)
		data[i] = (data[i] << 56)
		        | (data[i] << 40 & 0x00FF000000000000) | (data[i] << 24 & 0x0000FF0000000000) | (data[i] <<  8 & 0x000000FF00000000)
		        | (data[i] >>  8 & 0x00000000FF000000) | (data[i] >> 24 & 0x0000000000FF0000) | (data[i] >> 40 & 0x000000000000FF00)
		        | (data[i] >> 56);
}
inline void SwapEndianness(long* const data, const long len)
{
	SwapEndianness(reinterpret_cast<unsigned long*>(data),len);
}

inline void SwapEndianness(float* const data, const long len)
{
	SwapEndianness(reinterpret_cast<unsigned int*>(data),len);
}
inline void SwapEndianness(double* const data, const long len)
{
	SwapEndianness(reinterpret_cast<unsigned long*>(data),len);
}


/**
 * Helper struct to aid iterating full n-dimensional space.
 *
 * See definition of TransmitOnePlanarImage() on how this structure
 * can be utilized.
 */
typedef struct nDimWalker
{
	//n axis/dimensions available
	int n = 0;

	//every i-th axis is [0,sizes[i]] interval
	int* sizes = NULL;
	//current position in this space
	int* pos   = NULL;

	//how many steps before the whole space is swept entirely
	//NB: right after constructor(), it tells how many iterations+1 there will be
	long remainingSteps = 0;

	//adjusts pos and returns false there is no next step
	bool nextStep(void)
	{
		--remainingSteps;
		pos[0]++; //next step

		//check for "overflows"
		int i=0;
		while (i < n && pos[i] == sizes[i])
		{
			pos[i]=0;
			pos[i+1]++;
			++i;
		}

		return i < n;
	}

	//give space params: dimension in _n and sizes of axes in _sizes[]
	nDimWalker(const int* _sizes,const int _n)
	{
		n = _n;
		sizes = new int[n];
		pos   = new int[n];

		remainingSteps=1;

		for (int i=0; i < n; ++i)
		{
			sizes[i]=_sizes[i];
			pos[i]=0;
			remainingSteps *= sizes[i];
		}
		--remainingSteps;
	}

	~nDimWalker()
	{
		if (sizes != NULL) delete[] sizes;
		if (pos != NULL)   delete[] pos;
	}

	//just prints, e.g., "[10,20]"
	void printPos(void)
	{
		std::cout << "[";
		for (int i=0; i < n-1; ++i)
			std::cout << pos[i] << ",";
		std::cout << pos[n-1] << "]";
	}
} nDimWalker_t;


/**
 * Waits given _timeOut seconds on the current connection cnnParams for a message.
 * If not message appears in time, the function throws runtime_error with the errMsg
 * string. One can use this function everytime one wants to know whether successive
 * cnnParams.socket->recv() will block (no message arriving) or not (something has
 * arrived already).
 */
void waitForFirstMessage(connectionParams_t& cnnParams, const char* errMsg, const int _timeOut);

// calls the above function with timeOut taken from inside cnnParams
void waitForFirstMessage(connectionParams_t& cnnParams, const char* errMsg);

/**
 * ZeroMQ allows to send "multi-part" messages, that is, when SND_MORE flag is used
 * with socket->send(). Use this function to wait for any subsequent part of
 * such message -- not for the first part (for which the waitForFirstMessage() should
 * be used). However, sometimes the sending party will not start sending anything
 * until all sending material is cumulated and ready to be sent away in full -- in
 * this case, the waitForFirstMessage() will complain before this one.
 */
void waitForNextMessage(connectionParams_t& cnnParams);

}
#endif
