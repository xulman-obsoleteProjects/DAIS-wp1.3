package de.mpicbg.ulman.imgtransfer;

import org.zeromq.ZMQ;

import de.mpicbg.ulman.imgtransfer.buffers.*;
import de.mpicbg.ulman.imgtransfer.sockets.*;
import java.nio.ByteBuffer;

public class ArrayReceiver
{
	/**
	 * A timeout interval used while waiting for next (not the first one)
	 * "packet/message/chunk of data". That is, a waiting time applied only once
	 * connection got established. Can be considered as a timeout before
	 * connection is declared to be broken.
	 *
	 * Shouldn't be negative. Default is 30 seconds.
	 */
	private static int timeOut = 60;

	/// sets this.timeOut
	public static
	void setConnectionBrokenTimeout(final int seconds)
	{
		timeOut = seconds < 0 ? 60 : seconds;
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
	public static
	void waitForFirstMessage(final ZMQ.Socket socket, final int _timeOut)
	{
		int timeWaited = 0;
		while (timeWaited < _timeOut && (socket.getEvents() & 1) != 1)
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

	public static
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
	public static
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
	 * The length of the corresponding/input basic type array
	 * (note that we get Object instead of, e.g., float[]) in the constructor)
	 */
	final int arrayLength;

	///how many bytes the basic type occupies (e.g., float = 4 B)
	final int arrayElemSize;

	/**
	 * This is basically a connector between the ByteBuffer and \e socket,
	 * it either pushes data from the buffer to the socket, or the opposite.
	 *
	 * This is similar to the constructor's arrayVsBuffer.
	 */
	final Socket arrayVsSocket;


	///constant for the constructor: tells that we want a sender
	public final static int FROM_ARRAY_TO_SOCKET = 1;
	///constant for the constructor: tells that we want a receiver
	public final static int FROM_SOCKET_TO_ARRAY = 2;


	/**
	 * Constructor that caches type of the \e sampleArray (inside this.arrayVsSocket),
	 * size of one array element (in this.arrayElemSize),
	 * and length of the array (in this.arrayLength). The content of the \e sampleArray
	 * is irrelevant for now, only its parameters (elemnent type and its size) are
	 * important and must be representative of the arrays that will be sent/received
	 * later on with this object.
	 *
	 * Depending on the \e direction, the \e socket is either read into ByteBuffer which
	 * is read into the \e array, or the \e array is read into ByteBuffer which is read
	 * into the \e socket.
	 */
	ArrayReceiver(final Object sampleArray, final ZMQ.Socket socket, final int direction)
	{
		/**
		 * This is basically the connector between the array of some basic
		 * type (e.g., float[]) and the specific view of the ByteBuffer (e.g.,
		 * ByteBuffer.asFloatBuffer()).
		 *
		 * This is similar to the \e this.arrayVsSocket
		 */
		Sender arrayVsBuffer;

		if (sampleArray instanceof byte[])
		{
			arrayVsBuffer = new ByteSender();
			arrayLength = ((byte[])sampleArray).length;
			arrayElemSize = 1;
		}
		else
		if (sampleArray instanceof short[])
		{
			arrayVsBuffer = new ShortSender();
			arrayLength = ((short[])sampleArray).length;
			arrayElemSize = 2;
		}
		else
		if (sampleArray instanceof float[])
		{
			arrayVsBuffer = new FloatSender();
			arrayLength = ((float[])sampleArray).length;
			arrayElemSize = 4;
		}
		else
		if (sampleArray instanceof double[])
		{
			arrayVsBuffer = new DoubleSender();
			arrayLength = ((double[])sampleArray).length;
			arrayElemSize = 8;
		}
		else
			throw new RuntimeException("Does not recognize this array type.");

		switch (direction)
		{
		case FROM_ARRAY_TO_SOCKET:
			arrayVsSocket = new SendSocket(socket, arrayVsBuffer);
			break;
		case FROM_SOCKET_TO_ARRAY:
			arrayVsSocket = new RecvSocket(socket, arrayVsBuffer);
			break;
		default:
			throw new RuntimeException("Does not recognize the job.");
		}
	}

	void transmitArray(final Object array, boolean comingMore)
	{
		if (arrayLength < 1024 || arrayElemSize == 1)
		{
			//array that is short enough to be hosted entirely with byte[] array,
			//will be sent in one shot
			//NB: the else branch below cannot handle when arrayLength < arrayElemSize,
			//    and why to split the short arrays anyways?
			final ByteBuffer buf = ByteBuffer.allocateDirect(arrayElemSize*arrayLength);
			arrayVsSocket.transmit(array, 0, arrayLength,
			                        buf, (comingMore? ZMQ.SNDMORE : 0));
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
				arrayVsSocket.transmit(array, p*firstBlocksLen, firstBlocksLen,
				  buf, (comingMore || lastBlockLen > 0 || p < arrayElemSize-2 ? ZMQ.SNDMORE : 0));
				buf.rewind();
			}

			if (lastBlockLen > 0)
			{
				buf.limit(arrayElemSize*lastBlockLen);
				buf.rewind();
				arrayVsSocket.transmit(array, (arrayElemSize-1)*firstBlocksLen, lastBlockLen,
				                        buf, (comingMore? ZMQ.SNDMORE : 0));
			}
		}
	}
}
