/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman.imgtransfer.sockets;

import de.mpicbg.ulman.imgtransfer.buffers.Buffer;
import de.mpicbg.ulman.imgtransfer.ArrayReceiver;
import org.zeromq.ZMQ;
import java.nio.ByteBuffer;

public class RecvFromSocket implements Socket
{
	final ZMQ.Socket socket;
	final Buffer sender;

	//a handle on a local buffer to be potentially re-used
	ByteBuffer buf = null;

	public
	RecvFromSocket(final ZMQ.Socket _socket, final Buffer _sender)
	{
		socket = _socket;
		sender = _sender;
	}


	public
	void transmit(final Object arrayWrite, int offset, int length,
	              final int sendOnlyFlags)
	{
		//the optimal length of the aux ByteBuffer for the current data
		final int arrayLength = sender.getElemSize()*length;

		if (buf != null)
		{
			//buffer exist, is long enough?
			//NB: must be limited to the exact length, otherwise ZMQ waits to fill it...
			if (buf.limit() > arrayLength) buf.limit(arrayLength);
			buf.rewind();
		}
		if (buf == null || buf.limit() < arrayLength)
		{
			//no buffer, or one with an inadequate length
			buf = ByteBuffer.allocateDirect(arrayLength);
		}

		ArrayReceiver.waitForNextMessage(socket);
		socket.recvByteBuffer(buf, 0);
		buf.rewind();
		sender.recv(buf, arrayWrite, offset, length);
	}
}
