/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman.imgtransfer.sockets;

import de.mpicbg.ulman.imgtransfer.buffers.Buffer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class SendToOutputStream implements Socket
{
	final OutputStream os;
	final Buffer sender;

	public
	SendToOutputStream(final OutputStream _os, final Buffer _sender)
	{
		os = _os;
		sender = _sender;
	}


	public
	void transmit(final Object arrayRead, int offset, int length,
	              final int sendOnlyFlags)
	{
		//ZMQ.Socket does not copy ByteBuffer into its own space;
		//at the same time, it does not signal back if the data was transfered;
		//so, we need to create an extra ByteBuffer for every individual transfer
		final ByteBuffer buf = ByteBuffer.allocateDirect(sender.getElemSize()*length);

		try {
			sender.send(buf, arrayRead, offset, length);
			buf.rewind();
			//VERY QUICK AND VERY DIRTY! (and inefficient)
			while (buf.hasRemaining())
				os.write(buf.get());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
