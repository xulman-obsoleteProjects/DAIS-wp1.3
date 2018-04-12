#include <iostream>

#include "ImgParams.h"
#include "ReceiveOneImage.h"

template <typename VT>
void getData(connectionParams_t& cnnParams,const imgParams_t& imgParams,VT* data)
{
	// see which img receiver we need to call
	if (imgParams.backendType.find("Array") != std::string::npos)
	{
		//ArrayImg
		ReceiveOneArrayImage<VT>(cnnParams,(VT*)data);
	}
	else
	{
		//PlanarImg -- convenient (in fact, converts to ArrayImg)
		ReceiveOnePlanarImage<VT>(cnnParams,imgParams,(VT*)data);

		//or:
		//PlanarImg -- "the hard way"
		//TODO
	}
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

			default:
				std::cout << "ups, not ready yet for some other voxel type...\n";
		}

		//close the connection, calls also cnnParams.clear()
		FinishReceivingOneImage(cnnParams);


/*
		//write first 20 pixels just for the fun of the test...
		for (int i=0; i < 20; ++i)
			std::cout << (int)data[i] << ",";
*/

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
