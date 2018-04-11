#ifndef ImgParamsH
#define ImgParamsH

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

	//-----------
	//performs clean up inside the structure
	void clean()
	{
		if (sizes != NULL) delete[] sizes;
	}

	//imgParams(const int n): dim(n)
	//{ sizes = new int[n]; }

	~imgParams()
	{ clean(); }

} imgParams_t;

#endif
