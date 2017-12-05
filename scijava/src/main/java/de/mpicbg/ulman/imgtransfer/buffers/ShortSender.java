/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman.imgtransfer.buffers;

import java.nio.ByteBuffer;

public class ShortSender implements Sender
{
	public
	void send(final ByteBuffer bufWrite, final Object arrayRead, int offset, int length)
	{ bufWrite.asShortBuffer().put((short[])arrayRead, offset, length); }

	public
	void recv(final ByteBuffer bufRead,  final Object arrayWrite, int offset, int length)
	{ bufRead.asShortBuffer().get((short[])arrayWrite, offset, length); }
}
