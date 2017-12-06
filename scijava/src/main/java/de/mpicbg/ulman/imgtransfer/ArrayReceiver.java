package de.mpicbg.ulman.imgtransfer;

import org.zeromq.ZMQ;

import de.mpicbg.ulman.imgtransfer.buffers.*;
import java.nio.ByteBuffer;

class ArrayReceiver
{
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

	//-------------------

	/**
	 * This is basically, the connector between the array of some basic
	 * type (e.g., float[]) and the specific view of the ByteBuffer (e.g.,
	 * ByteBuffer.asFloatBuffer()).
	 */
	final Sender arrayFromBuffer;

	/**
	 * The length of the corresponding/input basic type array
	 * (note that we get Object instead of, e.g., float[]) in the constructor)
	 */
	final int arrayLength;

	///how many bytes the basic type occupies (e.g., float = 4 B)
	final int arrayElemSize;

	///socket to read from
	final ZMQ.Socket socket;

	/**
	 * constructor that caches type of the array (in this.arrayFromBuffer), size of one
	 * array element (in this.arrayElemSize), and length of the array (in this.arrayLength)
	 *
	 * the \e socket is read into ByteBuffer which is read into the \e array
	 */
	ArrayReceiver(final Object array, final ZMQ.Socket _socket)
	{
		if (array instanceof byte[])
		{
			arrayFromBuffer = new ByteSender();
			arrayLength = ((byte[])array).length;
			arrayElemSize = 1;
		}
		else
		if (array instanceof short[])
		{
			arrayFromBuffer = new ShortSender();
			arrayLength = ((short[])array).length;
			arrayElemSize = 2;
		}
		else
		if (array instanceof float[])
		{
			arrayFromBuffer = new FloatSender();
			arrayLength = ((float[])array).length;
			arrayElemSize = 4;
		}
		else
		if (array instanceof double[])
		{
			arrayFromBuffer = new DoubleSender();
			arrayLength = ((double[])array).length;
			arrayElemSize = 8;
		}
		else
			throw new RuntimeException("Does not recognize this array type.");

		socket = _socket;
	}

	void receiveArray(final Object array) {
		//will do the template socket->data pushing using this.arrayFromBuffer.recv()

		if (arrayLength < 1024 || arrayElemSize == 1)
		{
			//array that is short enough to be hosted entirely with byte[] array,
			//will be sent in one shot
			//NB: the else branch below cannot handle when arrayLength < arrayElemSize,
			//    and why to split the short arrays anyways?
			final ByteBuffer buf = ByteBuffer.allocateDirect(arrayElemSize*arrayLength);
			waitForNextMessage(socket);
			socket.recvByteBuffer(buf, 0);
			buf.rewind();
			arrayFromBuffer.recv(buf, array, 0, arrayLength);
		}
		else
		{
			//for example: float array, when seen as byte array, may exceed byte array's max length;
			//we, therefore, split into arrayElemSize-1 blocks of firstBlocksLen items long from
			//the original basic type array, and into one block of lastBlockLen items long
			final int firstBlocksLen = arrayLength/arrayElemSize + (arrayLength%arrayElemSize != 0? 1 : 0);
			final int lastBlockLen   = arrayLength - (arrayElemSize-1)*firstBlocksLen;
			//NB: firstBlockLen >= lastBlockLen

			final ByteBuffer buf = ByteBuffer.allocateDirect(arrayElemSize*firstBlocksLen);
			for (int p=0; p < (arrayElemSize-1); ++p)
			{
				waitForNextMessage(socket);
				socket.recvByteBuffer(buf, 0);
				buf.rewind();
				arrayFromBuffer.recv(buf, array, p*firstBlocksLen, firstBlocksLen);
				buf.rewind();
			}

			if (lastBlockLen > 0)
			{
				buf.limit(arrayElemSize*lastBlockLen);
				buf.rewind();
				waitForNextMessage(socket);
				socket.recvByteBuffer(buf, 0);
				buf.rewind();
				arrayFromBuffer.recv(buf, array, (arrayElemSize-1)*firstBlocksLen, lastBlockLen);
			}
		}
	}
}
