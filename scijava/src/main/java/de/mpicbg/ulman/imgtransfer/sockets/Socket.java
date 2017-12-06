/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman.imgtransfer.sockets;

import java.nio.ByteBuffer;

public interface Socket
{
	/**
	 * Uni-directional handler to (repeatedly) send-or-receive basic-type array to/from somewhere.
	 *
	 * The basic-type might be first wrapped into a caller-supplied ByteBuffer (to obtain
	 * independence on the basic type), but this might not always be necessary (will depend
	 * on the other end-point of this transmission). The ByteBuffer is understood therefore
	 * only as a auxiliary intermediate buffer and is a parameter here so that caller can
	 * re-use it.
	 *
	 * It is assumed that only a portion at \e offset of \e length is considered from the
	 * input/output \e array.
	 *
	 * Implementing classes are expected to create the opposite end-point, e.g. a network
	 * socket, to the \e array, and implement the transmission between the two.
	 */
	void transmit(final Object array, int offset, int length,
	              final ByteBuffer auxBuf, final int sendOnlyFlags);
}
