/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman.imgtransfer.sockets;

public interface Socket
{
	/**
	 * Uni-directional handler to (repeatedly) send-or-receive basic-type array to/from somewhere.
	 *
	 * It is assumed that only a portion at \e offset of \e length of the
	 * input/output \e array is transmitted.
	 *
	 * Implementing classes are expected to create the opposite end-point, e.g. a network
	 * socket, to the \e array, and implement the transmission between the two.
	 */
	void transmit(final Object array, int offset, int length,
	              final int sendOnlyFlags);
}
