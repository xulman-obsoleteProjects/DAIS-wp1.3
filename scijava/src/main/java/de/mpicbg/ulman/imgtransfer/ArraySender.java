package de.mpicbg.ulman.imgtransfer;

import org.zeromq.ZMQ;

import java.nio.ByteBuffer;

class ArraySender {
	// -------- basic types storage vs. ByteType un/packagers --------

	static
	void sendArray(final Object array, final ZMQ.Socket socket, boolean comingMore)
	{
		if(array instanceof byte[]) sendBytes((byte[]) array, socket, comingMore);
		if(array instanceof short[]) sendShorts((short[]) array, socket, comingMore);
		if(array instanceof float[]) sendFloats((float[]) array, socket, comingMore);
		if(array instanceof double[]) sendDoubles((double[]) array, socket, comingMore);
	}

	static
	void sendBytes(final byte[] data, final ZMQ.Socket socket, boolean comingMore)
	{
		final ByteBuffer buf = ByteBuffer.allocateDirect(data.length);
		buf.put(data);
		buf.rewind();
		socket.sendByteBuffer(buf, (comingMore? ZMQ.SNDMORE : 0));
	}

	static
	void sendShorts(final short[] data, final ZMQ.Socket socket, boolean comingMore)
	{
		//the data array might be as much as twice longer than what a byte[] array can store,
		//we have to copy half by half (each is up to byte[] array max capacity)
		//
		//but we keep addressing in the units of shorts :(
		//while in bytes the length of the data is always perfectly divisible by two,
		//it might not be the case in units of shorts
		final int TypeSize = 2;
		final int firstBlockLen = data.length/TypeSize + (data.length%TypeSize != 0? 1 : 0);
		final int lastBlockLen = data.length - (TypeSize-1)*firstBlockLen;
		//NB: firstBlockLen >= lastBlockLen

		final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*firstBlockLen);
		buf.asShortBuffer().put(data, 0, firstBlockLen);
		buf.rewind();
		socket.sendByteBuffer(buf, (comingMore || lastBlockLen > 0? ZMQ.SNDMORE : 0));

		if (lastBlockLen > 0)
		{
			final ByteBuffer buff = ByteBuffer.allocateDirect(TypeSize*lastBlockLen);
			buff.asShortBuffer().put(data, firstBlockLen, lastBlockLen);
			buff.rewind();
			socket.sendByteBuffer(buff, (comingMore? ZMQ.SNDMORE : 0));
		}
	}

	static
	void sendFloats(final float[] data, final ZMQ.Socket socket, boolean comingMore)
	{
		final int TypeSize = 4;

		if (data.length < 1024)
		{
			//array that is short enough to be hosted entirely with byte[] array,
			//will be sent in one shot
			//NB: the else branch below cannot handle when data.length < 3,
			//    and why to split the short arrays anyways?
			final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*data.length);
			buf.asFloatBuffer().put(data, 0, data.length);
			buf.rewind();
			socket.sendByteBuffer(buf, (comingMore? ZMQ.SNDMORE : 0));
		}
		else
		{
			//float array, when seen as byte array, may exceed byte array's max length
			final int firstBlocksLen = data.length/TypeSize + (data.length%TypeSize != 0? 1 : 0);
			final int lastBlockLen = data.length - (TypeSize-1)*firstBlocksLen;

			for (int p=0; p < (TypeSize-1); ++p)
			{
				final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*firstBlocksLen);
				buf.asFloatBuffer().put(data, p*firstBlocksLen, firstBlocksLen);
				buf.rewind();
				socket.sendByteBuffer(buf, (comingMore || lastBlockLen > 0 || p < TypeSize-2 ? ZMQ.SNDMORE : 0));
			}

			if (lastBlockLen > 0)
			{
				final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*lastBlockLen);
				buf.asFloatBuffer().put(data, (TypeSize-1)*firstBlocksLen, lastBlockLen);
				buf.rewind();
				socket.sendByteBuffer(buf, (comingMore? ZMQ.SNDMORE : 0));
			}
		}
	}

	static
	void sendDoubles(final double[] data, final ZMQ.Socket socket, boolean comingMore)
	{
		final int TypeSize = 8;

		if (data.length < 1024)
		{
			//array that is short enough to be hosted entirely with byte[] array,
			//will be sent in one shot
			//NB: the else branch below cannot handle when data.length < 7,
			//    and why to split the short arrays anyways?
			final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*data.length);
			buf.asDoubleBuffer().put(data, 0, data.length);
			buf.rewind();
			socket.sendByteBuffer(buf, (comingMore? ZMQ.SNDMORE : 0));
		}
		else
		{
			final int firstBlocksLen = data.length/TypeSize + (data.length%TypeSize != 0? 1 : 0);
			final int lastBlockLen = data.length - (TypeSize-1)*firstBlocksLen;

			for (int p=0; p < (TypeSize-1); ++p)
			{
				final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*firstBlocksLen);
				buf.asDoubleBuffer().put(data, p*firstBlocksLen, firstBlocksLen);
				buf.rewind();
				socket.sendByteBuffer(buf, (comingMore || lastBlockLen > 0 || p < TypeSize-2 ? ZMQ.SNDMORE : 0));
			}

			if (lastBlockLen > 0)
			{
				final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*lastBlockLen);
				buf.asDoubleBuffer().put(data, (TypeSize-1)*firstBlocksLen, lastBlockLen);
				buf.rewind();
				socket.sendByteBuffer(buf, (comingMore? ZMQ.SNDMORE : 0));
			}
		}
	}
}
