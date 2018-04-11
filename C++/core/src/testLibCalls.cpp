#include <iostream>

#include "ReceiveOneImage.h"

int main(void)
{
	std::cout << "hi from tester\n";

	imgParams_t imgParams;
	ReceiveOneImage(imgParams, 54545);

	return (0);
}
