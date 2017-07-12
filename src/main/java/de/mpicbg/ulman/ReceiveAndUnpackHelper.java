package de.mpicbg.ulman;

import org.zeromq.ZMQ;

import java.nio.ByteBuffer;

class ReceiveAndUnpackHelper {
	/**
	 * This one checks periodically (until timeout period) if
	 * there is some some incoming data reported on the socket.
	 * It finishes "nicely" if there is some, or finishes
	 * with an expection complaining about timeout.
	 */
	private static void waitForVoxels(final ZMQ.Socket socket)
	{
		final int timeOut = 20; //user param later!

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
			throw new RuntimeException("Time out for incomming voxel data.");
	}

	static void receiveAndUnpackBytes(final byte[] data, final ZMQ.Socket socket)
	{
		final ByteBuffer buf = ByteBuffer.allocateDirect(data.length);
		//are there any further messages pending?
		waitForVoxels(socket);
		socket.recvByteBuffer(buf, 0);
		buf.rewind();
		buf.get(data);
	}

	static void receiveAndUnpackShorts(final short[] data, final ZMQ.Socket socket)
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
		waitForVoxels(socket);
		socket.recvByteBuffer(buf, 0); //blocking read since we got over waitForVoxels()
		buf.rewind();
		buf.asShortBuffer().get(data, 0, firstBlockLen);

		if (lastBlockLen > 0)
		{
			//make buffer ready for receiving the second part
			buf.limit(TypeSize*lastBlockLen);
			buf.rewind();

			//get the data
			waitForVoxels(socket);
			socket.recvByteBuffer(buf, 0);

			buf.rewind(); //recvByteBuffer() has changed the position!
			buf.asShortBuffer().get(data, firstBlockLen, lastBlockLen);
		}
	}

	static void receiveAndUnpackFloats(final float[] data, final ZMQ.Socket socket)
	{
		final int TypeSize = 4;

		if (data.length < 1024)
		{
			//array that is short enough to be hosted entirely with byte[] array,
			//will be sent in one shot
			//NB: the else branch below cannot handle when data.length < 3,
			//    and why to split the short arrays anyways?
			final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*data.length);
			waitForVoxels(socket);
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
				waitForVoxels(socket);
				socket.recvByteBuffer(buf, 0);
				buf.rewind();
				buf.asFloatBuffer().get(data, p*firstBlocksLen, firstBlocksLen);
				buf.rewind();
			}

			if (lastBlockLen > 0)
			{
				buf.limit(TypeSize*lastBlockLen);
				buf.rewind();
				waitForVoxels(socket);
				socket.recvByteBuffer(buf, 0);
				buf.rewind();
				buf.asFloatBuffer().get(data, (TypeSize-1)*firstBlocksLen, lastBlockLen);
			}
		}
	}

	static void receiveAndUnpackDoubles(final double[] data, final ZMQ.Socket socket)
	{
		final int TypeSize = 8;

		if (data.length < 1024)
		{
			//array that is short enough to be hosted entirely with byte[] array,
			//will be sent in one shot
			//NB: the else branch below cannot handle when data.length < 7,
			//    and why to split the short arrays anyways?
			final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*data.length);
			waitForVoxels(socket);
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
				waitForVoxels(socket);
				socket.recvByteBuffer(buf, 0);
				buf.rewind();
				buf.asDoubleBuffer().get(data, p*firstBlocksLen, firstBlocksLen);
				buf.rewind();
			}

			if (lastBlockLen > 0)
			{
				buf.limit(TypeSize*lastBlockLen);
				buf.rewind();
				waitForVoxels(socket);
				socket.recvByteBuffer(buf, 0);
				buf.rewind();
				buf.asDoubleBuffer().get(data, (TypeSize-1)*firstBlocksLen, lastBlockLen);
			}
		}
	}
}
