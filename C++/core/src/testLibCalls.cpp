#include <iostream>

#include "ReceiveOneImage.h"

int main(void)
{
	std::cout << "hi from tester\n";

	try {
		imgParams_t imgParams;
		ReceiveOneImage(imgParams, 54545);



	}
	catch (std::exception* e)
	{
		std::cout << e->what() << std::endl;
	}

	return (0);
}
