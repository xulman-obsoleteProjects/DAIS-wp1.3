/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2017, Vladimír Ulman
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.mpicbg.ulman.imgtransfer.sockets;

import de.mpicbg.ulman.imgtransfer.buffers.Buffer;
import de.mpicbg.ulman.imgtransfer.ArrayPacker;
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

		ArrayPacker.waitForNextMessage(socket);
		socket.recvByteBuffer(buf, 0);
		buf.rewind();
		sender.recv(buf, arrayWrite, offset, length);
	}
}
