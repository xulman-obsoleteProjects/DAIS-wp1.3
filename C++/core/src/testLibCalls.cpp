#include <iostream>

#include "ImgParams.h"
#include "ReceiveOneImage.h"

template <typename VT>
void getData(connectionParams_t& cnnParams,const imgParams_t& imgParams,VT* const data)
{
	// see which img receiver we need to call
	if (imgParams.backendType.find("Array") != std::string::npos)
	{
		//ArrayImg
		ReceiveOneArrayImage<VT>(cnnParams,imgParams,data);
	}
	else
	{
		//PlanarImg -- convenient (in fact, converts to ArrayImg)
		ReceiveOneArrayImage<VT>(cnnParams,imgParams,data);
		//ReceiveOnePlanarImage<VT>(cnnParams,imgParams,data);

		//or:
		//PlanarImg -- "the hard way"
		//TODO
	}

	//print first 20 values just to see that something has been transmitted
	for (long i=0; i < 20; ++i)
		std::cout << (int)data[i] << ",";
	std::cout << std::endl;
}

int main(void)
{
	std::cout << "<hi from tester>\n";

	try {
		//init the connection and possibly wait for the header information
		imgParams_t imgParams;
		connectionParams_t cnnParams;
		StartReceivingOneImage(imgParams,cnnParams,54545);

		//aha, so this is what we will receive -- do what you need to get ready for that
		std::cout << "Going to receive an image: ";
		for (int i=1; i < imgParams.dim; ++i)
		          std::cout << imgParams.sizes[i-1] << " x ";
		std::cout << imgParams.sizes[imgParams.dim-1] << "\n";
		std::cout << "VT     : " << imgParams.voxelType << "\n"
		          << "backend: " << imgParams.backendType << "\n";

		std::cout << "array length : " << imgParams.howManyVoxels() << " voxels\n";
		std::cout << "array memSize: " << imgParams.howManyBytes() << " Bytes\n";

		//get metadata
		std::list<std::string> metaData;
		ReceiveMetadata(cnnParams,metaData);

		std::cout << "--metadata--\n";
		std::list<std::string>::const_iterator it = metaData.begin();
		while (it != metaData.end())
		{
			std::cout << *it << "\n";
			it++;
		}
		std::cout << "--metadata--\n";

		//prepare an array to hold the pixel data... we will cast it later
		//(or we would need to create here a template function in which
		// the correct-type 'data' variable would be created and used)
		char* data = new char[imgParams.howManyBytes()];

		//now, fill the image data array
		switch (imgParams.enumVoxelType())
		{
			case imgParams::voxelTypes::Byte:
				getData(cnnParams,imgParams,(char*)data);
				break;
			case imgParams::voxelTypes::UnsignedByte:
				getData(cnnParams,imgParams,(unsigned char*)data);
				break;

			case imgParams::voxelTypes::Short:
				getData(cnnParams,imgParams,(short*)data);
				break;
			case imgParams::voxelTypes::UnsignedShort:
				getData(cnnParams,imgParams,(unsigned short*)data);
				break;

			case imgParams::voxelTypes::Long:
				getData(cnnParams,imgParams,(long*)data);
				break;
			case imgParams::voxelTypes::UnsignedLong:
				getData(cnnParams,imgParams,(unsigned long*)data);
				break;

			case imgParams::voxelTypes::Float:
				getData(cnnParams,imgParams,(float*)data);
				break;
			case imgParams::voxelTypes::Double:
				getData(cnnParams,imgParams,(double*)data);
				break;

			default:
				std::cout << "ups, not ready yet for voxel type: " << imgParams.voxelType << "\n";
		}

		//close the connection, calls also cnnParams.clear()
		FinishReceivingOneImage(cnnParams);

		//free the memory! we're not in Java :)
		imgParams.clear();
		delete[] data;
	}
	catch (std::exception* e)
	{
		std::cout << "Transmission problem: " << e->what() << std::endl;
	}

	std::cout << "</hi from tester>\n";
	return (0);
}
