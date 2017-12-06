/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman.imgtransfer.buffers;

import java.nio.ByteBuffer;

public class FloatBuffer implements Buffer
{
	public
	void send(final ByteBuffer bufWrite, final Object arrayRead, int offset, int length)
	{ bufWrite.asFloatBuffer().put((float[])arrayRead, offset, length); }

	public
	void recv(final ByteBuffer bufRead,  final Object arrayWrite, int offset, int length)
	{ bufRead.asFloatBuffer().get((float[])arrayWrite, offset, length); }

	public
	int getElemSize()
	{ return 4; }

	public
	int getElemCount(final Object array)
	{ return ((float[])array).length; }
}
