#include <stdexcept>
#include <iostream>
#include <i3d/image3d.h>
#include <TransferImage.h>

/*
Image loading happens in two stages. In the first stage in main(),
connection is established and image geometry is retrieved. Based on the
image geometry (at least) the receiving buffer is prepared (here it is the
instance of i3d::Image3d<VOXELTYPE>) and image is "downloaded", both
happens in recvAndStore() which is essentially the second stage. The
necessity for the second stage stems also from the fact that in C++ we need
to know the exact template parameters, which we cannot learn earlier than
after receiving the image geometry (incl voxel type).
*/

//the main workhorse that is aware of underlying voxel type
template <typename VT,class I3DVT>
void recvAndStore(connectionParams_t& cnnParams,imgParams_t& imgParams,const char* fileName);

int main(int argc,char** argv)
{
	if (argc != 2)
	{
		std::cout << "You need to give output filename, e.g., test.tif\n";
		return -1;
	}

	std::cout << "Waiting on port 54545 for an image...\n";
	try {
		//init the connection and wait for the header information
		imgParams_t imgParams;
		connectionParams_t cnnParams;
		StartReceivingOneImage(imgParams,cnnParams,54545);

		//continue processing inside the recvAndStore() (which knows already the voxel type)
		switch (imgParams.enumVoxelType())
		{
			case imgParams::voxelTypes::Byte:
			case imgParams::voxelTypes::UnsignedByte:
				recvAndStore<unsigned char,i3d::GRAY8>(cnnParams,imgParams,argv[1]);
				break;

			case imgParams::voxelTypes::Short:
			case imgParams::voxelTypes::UnsignedShort:
				recvAndStore<unsigned short,i3d::GRAY16>(cnnParams,imgParams,argv[1]);
				break;

			case imgParams::voxelTypes::Float:
				recvAndStore<float,float>(cnnParams,imgParams,argv[1]);
				break;
			case imgParams::voxelTypes::Double:
				recvAndStore<double,double>(cnnParams,imgParams,argv[1]);
				break;

			default:
				std::cerr << "I cannot handle " << imgParams.voxelType << "\n";
				return -1;
				break;
		}
	}
	catch (std::exception* e)
	{
		std::cerr << "Transmission problem: " << e->what() << std::endl;
	}

	return 0;
}


template <typename VT,class I3DVT>
void recvAndStore(connectionParams_t& cnnParams,imgParams_t& imgParams,const char* fileName)
{
	if (imgParams.dim > 3)
		throw new std::runtime_error::runtime_error("I can only handle up to 3 dimensions (3D image).");

	//determine the size of a 3D that "wraps" around the transmitted one
	size_t xSize = imgParams.sizes[0];
	size_t ySize = imgParams.dim > 1 ? imgParams.sizes[1] : 1;
	size_t zSize = imgParams.dim > 2 ? imgParams.sizes[2] : 1;

	//create and allocate the i3dlib's image
	i3d::Image3d<I3DVT> img;
	img.MakeRoom(xSize,ySize,zSize);
	img.SetResolution(i3d::Resolution(1.f));

	//get metadata
	std::list<std::string> metaData;
	ReceiveMetadata(cnnParams,metaData);

	//if you would like to see the image name
	std::list<std::string>::const_iterator it = metaData.begin();
	while (it != metaData.end() && it->find("imagename") == std::string::npos) it++;
	if (it == metaData.end())
		throw new std::runtime_error::runtime_error("I have not found 'imagename' in its metadata.");
	it++;
	if (it == metaData.end())
		throw new std::runtime_error::runtime_error("I have not found 'imagename' in its metadata.");

	const std::string& imageName = *it;
	std::cout << "Image name is: " << imageName << "\n";

	TransmitOneImage(cnnParams,imgParams,(VT*)img.GetFirstVoxelAddr());

	FinishReceivingOneImage(cnnParams);
	imgParams.clear();

	//now process the i3dlib's image
	img.SaveImage(fileName);
}
