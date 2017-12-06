/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman.imgtransfer.sockets;

import org.zeromq.ZMQ;
import java.nio.ByteBuffer;

public class SendSocket implements Socket
{
	public
	void transmit(final ZMQ.Socket socket, final ByteBuffer bufRead, final int flags)
	{
		bufRead.rewind();
		socket.sendByteBuffer(bufRead, flags);
	}
}
