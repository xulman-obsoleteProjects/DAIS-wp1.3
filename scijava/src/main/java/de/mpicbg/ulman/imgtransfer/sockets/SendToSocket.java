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
import org.zeromq.ZMQ;
import java.nio.ByteBuffer;

public class SendToSocket implements Socket
{
	final ZMQ.Socket socket;
	final Buffer sender;

	public
	SendToSocket(final ZMQ.Socket _socket, final Buffer _sender)
	{
		socket = _socket;
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

		sender.send(buf, arrayRead, offset, length);
		buf.rewind();
		socket.sendByteBuffer(buf, sendOnlyFlags);
	}
}
