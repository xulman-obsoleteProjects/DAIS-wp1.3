#ifndef ReceiveOneImageH
#define ReceiveOneImageH

#include "ImgParams.h"

/**
 * Waits not longer than timeOut seconds on local port for the first message
 * of the new image transfer, and fills the output imgParams image configuration
 * data. Throws exceptions if something goes wrong: timeOut, unable to parse,
 * or wrong format/protocol error...
 */
void ReceiveOneImage(imgParams_t& imgParams, const int port, const int timeOut = 60);


#endif
