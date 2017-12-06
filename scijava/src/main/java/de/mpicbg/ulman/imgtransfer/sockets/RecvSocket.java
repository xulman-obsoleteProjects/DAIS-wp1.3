/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman.imgtransfer.sockets;

import de.mpicbg.ulman.imgtransfer.buffers.Sender;
import de.mpicbg.ulman.imgtransfer.ArrayReceiver;
import org.zeromq.ZMQ;
import java.nio.ByteBuffer;

public class RecvSocket implements Socket
{
	final ZMQ.Socket socket;
	final Sender sender;

	public
	RecvSocket(final ZMQ.Socket _socket, final Sender _sender)
	{
		socket = _socket;
		sender = _sender;
	}


	public
	void transmit(final Object arrayWrite, int offset, int length,
	              final ByteBuffer auxBuf, final int sendOnlyFlags)
	{
		ArrayReceiver.waitForNextMessage(socket);
		socket.recvByteBuffer(auxBuf, 0);
		auxBuf.rewind();
		sender.recv(auxBuf, arrayWrite, offset, length);
	}
}
