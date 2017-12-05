package de.mpicbg.ulman.imgtransfer;

import org.zeromq.ZMQ;

import java.nio.ByteBuffer;

class ArrayReceiver {
	/**
	 * A timeout interval used while waiting for next (not the first one)
	 * "packet/message/chunk of data". That is, a waiting time applied only once
	 * connection got established. Can be considered as a timeout before
	 * connection is declared to be broken.
	 *
	 * Shouldn't be negative. Default is 30 seconds.
	 */
	private static int timeOut = 30;

	/// sets this.timeOut
	public static
	void setConnectionBrokenTimeout(final int seconds)
	{
		timeOut = seconds < 0 ? 30 : seconds;
	}

	/// reads current this.timeOut
	public static
	int getConnectionBrokenTimeout()
	{
		return timeOut;
	}

	/**
	 * This one checks periodically (until timeout period) if
	 * there is some incoming data (a message sent with no SNDMORE
	 * flag, hence message consists of just one, first part)
	 * reported on the socket.
	 *
	 * If \e timeOut is negative, only one check is made without any
	 * delay and exception might be triggered unless data is available.
	 * Units are seconds.
	 *
	 * It finishes "nicely" if there is some, or finishes
	 * with an exception complaining about timeout.
	 */
	static
	void waitForFirstMessage(final ZMQ.Socket socket, final int timeOut)
	{
		int timeWaited = 0;
		while (timeWaited < timeOut && (socket.getEvents() & 1) != 1)
		//TODO: determine proper constant for getEvents()
		//TODO: expected return value is 1. bit set according to tests
		{
			//if nothing found, wait a while before another checking attempt
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			++timeWaited;
		}

		if ((socket.getEvents() & 1) != 1)
			throw new RuntimeException("Reached timeout for the first incoming data.");
	}

	static
	void waitForFirstMessage(final ZMQ.Socket socket)
	{ waitForFirstMessage(socket, timeOut); }

	/**
	 * This one checks periodically (until timeout period) if
	 * there is next part of some incoming data (a message sent
	 * with SNDMORE flag) reported on the socket.
	 *
	 * It finishes "nicely" if there is some, or finishes
	 * with an exception complaining about timeout.
	 */
	static
	void waitForNextMessage(final ZMQ.Socket socket)
	{
		int timeWaited = 0;
		while (timeWaited < timeOut && !socket.hasReceiveMore())
		{
			//if nothing found, wait a while before another checking attempt
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			++timeWaited;
		}

		if (!socket.hasReceiveMore())
			throw new RuntimeException("Reached timeout for the next incoming data.");
	}


	void receiveArray(final Object array, final ZMQ.Socket socket) {
		if(array instanceof byte[]) receiveBytes((byte[]) array, socket);
		if(array instanceof short[]) receiveShorts((short[]) array, socket);
		if(array instanceof float[]) receiveFloats((float[]) array, socket);
		if(array instanceof double[]) receiveDoubles((double[]) array, socket);
	}

	static
	void receiveBytes(final byte[] data, final ZMQ.Socket socket)
	{
		final ByteBuffer buf = ByteBuffer.allocateDirect(data.length);
		//are there any further messages pending?
		waitForNextMessage(socket);
		socket.recvByteBuffer(buf, 0);
		buf.rewind();
		buf.get(data);
	}

	static
	void receiveShorts(final short[] data, final ZMQ.Socket socket)
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
		waitForNextMessage(socket);
		socket.recvByteBuffer(buf, 0); //blocking read since we got over waitForNextMessage()
		buf.rewind();
		buf.asShortBuffer().get(data, 0, firstBlockLen);

		if (lastBlockLen > 0)
		{
			//make buffer ready for receiving the second part
			buf.limit(TypeSize*lastBlockLen);
			buf.rewind();

			//get the data
			waitForNextMessage(socket);
			socket.recvByteBuffer(buf, 0);

			buf.rewind(); //recvByteBuffer() has changed the position!
			buf.asShortBuffer().get(data, firstBlockLen, lastBlockLen);
		}
	}

	static
	void receiveFloats(final float[] data, final ZMQ.Socket socket)
	{
		final int TypeSize = 4;

		if (data.length < 1024)
		{
			//array that is short enough to be hosted entirely with byte[] array,
			//will be sent in one shot
			//NB: the else branch below cannot handle when data.length < 3,
			//    and why to split the short arrays anyways?
			final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*data.length);
			waitForNextMessage(socket);
			socket.recvByteBuffer(buf, 0);
			buf.rewind();
			buf.asFloatBuffer().get(data, 0, data.length);
		}
		else
		{
			//float array, when seen as byte array, may exceed byte array's max length
			final int firstBlocksLen = data.length/TypeSize + (data.length%TypeSize != 0? 1 : 0);
			final int lastBlockLen = data.length - (TypeSize-1)*firstBlocksLen;

			final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*firstBlocksLen);
			for (int p=0; p < (TypeSize-1); ++p)
			{
				waitForNextMessage(socket);
				socket.recvByteBuffer(buf, 0);
				buf.rewind();
				buf.asFloatBuffer().get(data, p*firstBlocksLen, firstBlocksLen);
				buf.rewind();
			}

			if (lastBlockLen > 0)
			{
				buf.limit(TypeSize*lastBlockLen);
				buf.rewind();
				waitForNextMessage(socket);
				socket.recvByteBuffer(buf, 0);
				buf.rewind();
				buf.asFloatBuffer().get(data, (TypeSize-1)*firstBlocksLen, lastBlockLen);
			}
		}
	}

	static
	void receiveDoubles(final double[] data, final ZMQ.Socket socket)
	{
		final int TypeSize = 8;

		if (data.length < 1024)
		{
			//array that is short enough to be hosted entirely with byte[] array,
			//will be sent in one shot
			//NB: the else branch below cannot handle when data.length < 7,
			//    and why to split the short arrays anyways?
			final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*data.length);
			waitForNextMessage(socket);
			socket.recvByteBuffer(buf, 0);
			buf.rewind();
			buf.asDoubleBuffer().get(data, 0, data.length);
		}
		else
		{
			final int firstBlocksLen = data.length/TypeSize + (data.length%TypeSize != 0? 1 : 0);
			final int lastBlockLen = data.length - (TypeSize-1)*firstBlocksLen;

			final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*firstBlocksLen);
			for (int p=0; p < (TypeSize-1); ++p)
			{
				waitForNextMessage(socket);
				socket.recvByteBuffer(buf, 0);
				buf.rewind();
				buf.asDoubleBuffer().get(data, p*firstBlocksLen, firstBlocksLen);
				buf.rewind();
			}

			if (lastBlockLen > 0)
			{
				buf.limit(TypeSize*lastBlockLen);
				buf.rewind();
				waitForNextMessage(socket);
				socket.recvByteBuffer(buf, 0);
				buf.rewind();
				buf.asDoubleBuffer().get(data, (TypeSize-1)*firstBlocksLen, lastBlockLen);
			}
		}
	}
}
