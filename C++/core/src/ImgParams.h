#ifndef ImgParamsH
#define ImgParamsH

#include <stdexcept>
#include <string>

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
		Long,
		UnsignedLong,
		Float,
		Double
	} voxelTypes;

	//convenience convertor function: enum-ed variant of the voxelType variable
	voxelTypes enumVoxelType() const
	{
		voxelTypes vt;
		if (voxelType.find("Byte") != std::string::npos) vt = Byte;
		else
		if (voxelType.find("UnsignedByte") != std::string::npos) vt = UnsignedByte;
		else
		if (voxelType.find("Short") != std::string::npos) vt = Short;
		else
		if (voxelType.find("UnsignedShort") != std::string::npos) vt = UnsignedShort;
		else
		if (voxelType.find("Long") != std::string::npos) vt = Long;
		else
		if (voxelType.find("UnsignedLong") != std::string::npos) vt = UnsignedLong;
		else
		if (voxelType.find("Float") != std::string::npos) vt = Float;
		else
		if (voxelType.find("Double") != std::string::npos) vt = Double;
		else
			//undetermined type, complain!
			throw new std::runtime_error::runtime_error(
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
			case Long:
			case UnsignedLong:
				voxelSize = 4;
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

#endif
