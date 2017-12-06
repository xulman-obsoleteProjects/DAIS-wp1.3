package de.mpicbg.ulman.imgtransfer;

import org.zeromq.ZMQ;

import de.mpicbg.ulman.imgtransfer.buffers.*;
import java.nio.ByteBuffer;

class ArraySender
{
	// -------- basic types storage vs. ByteType un/packagers --------

	/**
	 * This is basically, the connector between the array of some basic
	 * type (e.g., float[]) and the specific view of the ByteBuffer (e.g.,
	 * ByteBuffer.asFloatBuffer()).
	 */
	final Sender arrayToBuffer;

	/**
	 * The length of the corresponding/input basic type array
	 * (note that we get Object instead of, e.g., float[]) in the constructor)
	 */
	final int arrayLength;

	///how many bytes the basic type occupies (e.g., float = 4 B)
	final int arrayElemSize;

	///socket to write to
	final ZMQ.Socket socket;

	/**
	 * constructor that caches type of the array (in this.arrayToBuffer), size of one
	 * array element (in this.arrayElemSize), and length of the array (in this.arrayLength)
	 *
	 * the \e array is read into ByteBuffer which is read into the \e socket
	 */
	ArraySender(final Object array, final ZMQ.Socket _socket)
	{
		if (array instanceof byte[])
		{
			arrayToBuffer = new ByteSender();
			arrayLength = ((byte[])array).length;
			arrayElemSize = 1;
		}
		else
		if (array instanceof short[])
		{
			arrayToBuffer = new ShortSender();
			arrayLength = ((short[])array).length;
			arrayElemSize = 2;
		}
		else
		if (array instanceof float[])
		{
			arrayToBuffer = new FloatSender();
			arrayLength = ((float[])array).length;
			arrayElemSize = 4;
		}
		else
		if (array instanceof double[])
		{
			arrayToBuffer = new DoubleSender();
			arrayLength = ((double[])array).length;
			arrayElemSize = 8;
		}
		else
			throw new RuntimeException("Does not recognize this array type.");

		socket = _socket;
	}

	void sendArray(final Object array, boolean comingMore)
	{
		//will do the template data->socket pushing using this.arrayToBuffer.send()
		//final ByteBuffer buf = ByteBuffer.allocateDirect(arrayLength);
		//arrayToBuffer.send(buf, array, 0, 10);

		if (arrayLength < 1024 || arrayElemSize == 1)
		{
			//array that is short enough to be hosted entirely with byte[] array,
			//will be sent in one shot
			//NB: the else branch below cannot handle when arrayLength < arrayElemSize,
			//    and why to split the short arrays anyways?
			final ByteBuffer buf = ByteBuffer.allocateDirect(arrayElemSize*arrayLength);
			arrayToBuffer.send(buf, array, 0, arrayLength);
			buf.rewind();
			socket.sendByteBuffer(buf, (comingMore? ZMQ.SNDMORE : 0));
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
				arrayToBuffer.send(buf, array, p*firstBlocksLen, firstBlocksLen);
				buf.rewind();
				socket.sendByteBuffer(buf, (comingMore || lastBlockLen > 0 || p < arrayElemSize-2 ? ZMQ.SNDMORE : 0));
			}

			if (lastBlockLen > 0)
			{
				buf.limit(arrayElemSize*lastBlockLen);
				buf.rewind();
				arrayToBuffer.send(buf, array, (arrayElemSize-1)*firstBlocksLen, lastBlockLen);
				buf.rewind();
				socket.sendByteBuffer(buf, (comingMore? ZMQ.SNDMORE : 0));
			}
		}
	}
}
