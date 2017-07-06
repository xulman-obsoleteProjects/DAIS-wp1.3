/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman;

import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.img.WrappedImg;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.cell.CellImg;
import net.imglib2.img.planar.PlanarImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;

import java.nio.ByteBuffer;
import org.zeromq.ZMQ;

public class ImgPacker<T extends NativeType<T>>
{
	/**
	 *
	 */
	@SuppressWarnings("unchecked")
	public
	void packAndSend(final ImgPlus<T> imgP, final ZMQ.Socket socket) throws Exception
	{
		//"buffer" for the first and human-readable payload:
		//protocol version
		String msg = new String("v1");

		//pack the image metadata (including its dimensions)
		msg += plusDataToString(imgP);

		//decipher the voxel type
		msg += " " + typeIDToString(typeToTypeID(imgP.firstElement()));

		//check we can handle the storage model of this image,
		//and try to send everything (first the human readable payload, then raw voxel data)
		Img<T> img = getUnderlyingImg(imgP);
		if (img instanceof ArrayImg)
		{
			msg += " ArrayImg ";
			socket.send(msg.getBytes(), ZMQ.SNDMORE);
			packAndSendArrayImg((ArrayImg<T, ? extends ArrayDataAccess<?>>)img, socket);
		}
		else
		if (img instanceof PlanarImg)
		{
			msg += " PlanarImg ";
			throw new Exception("Cannot send PlaneImg images yet.");
			//socket.send(msg.getBytes(), ZMQ.SNDMORE);
			//packAndSendArrayImg((PlanarImg<T,?>)img, socket);
		}
		else
		if (img instanceof CellImg)
		{
			msg += " CellImg ";
			throw new Exception("Cannot send CellImg images yet.");
			//socket.send(msg.getBytes(), ZMQ.SNDMORE);
			//packAndSendArrayImg((CellImg<T,?>)img, socket);
		}
		else
			throw new Exception("Cannot determine the type of image, cannot send it.");
	}


	// -------- support for the transmission of the envelope/initial message --------
	private
	String plusDataToString(final ImgPlus<T> imgP)
	{
		String msg = new String();

		//dimensionality data
		msg += " dimNumber " + imgP.numDimensions();
		for (int i=0; i < imgP.numDimensions(); ++i)
			msg += " " + imgP.dimension(i);

		//further meta data
		msg += " imgPlusData__START";
		//TODO: use mPack because metadata are of various types (including Strings)
		msg += " imgPlusData__END";

		return msg;
	}


	// -------- support for the transmission of the payload/voxel data --------
	private
	void packAndSendArrayImg(final ArrayImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket) throws Exception
	{
		if (img.size() == 0)
			throw new Exception("Refusing to send an empty image...");

		switch (typeToTypeID(img.firstElement()))
		{
		case 1: //ByteType
		case 2: //UnsignedByteType
			{
			final byte[] data = (byte[])img.update(null).getCurrentStorageArray();

			final ByteBuffer buf = ByteBuffer.allocateDirect(data.length);
			buf.put(data);
			buf.rewind();
			socket.sendByteBuffer(buf, 0);
			}
			break;
		case 3: //ShortType
		case 4: //UnsignedShortType
			{
			final short[] data = (short[])img.update(null).getCurrentStorageArray();
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
			socket.sendByteBuffer(buf, (lastBlockLen > 0? ZMQ.SNDMORE : 0));

			if (lastBlockLen > 0)
			{
				buf.limit(TypeSize*lastBlockLen);
				buf.rewind();
				buf.asShortBuffer().put(data, firstBlockLen, lastBlockLen);
				buf.rewind();
				socket.sendByteBuffer(buf, 0);
			}
			}
			break;
		case 5: //FloatType
			{
			final float[] data = (float[])img.update(null).getCurrentStorageArray();
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
				socket.sendByteBuffer(buf, 0);
			}
			else
			{
				//float array, when seen as byte array, may exceed byte array's max length
				final int firstBlocksLen = data.length/TypeSize + (data.length%TypeSize != 0? 1 : 0);
				final int lastBlockLen = data.length - (TypeSize-1)*firstBlocksLen;

				final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*firstBlocksLen);
				for (int p=0; p < (TypeSize-1); ++p)
				{
					//buf.rewind(); -- sendByteBuffer should not change the position as it works on a buf.duplicate()
					buf.asFloatBuffer().put(data, p*firstBlocksLen, firstBlocksLen);
					buf.rewind();
					socket.sendByteBuffer(buf, (lastBlockLen > 0 || p < TypeSize-2 ? ZMQ.SNDMORE : 0));
				}

				if (lastBlockLen > 0)
				{
					buf.limit(TypeSize*lastBlockLen);
					buf.rewind();
					buf.asFloatBuffer().put(data, (TypeSize-1)*firstBlocksLen, lastBlockLen);
					buf.rewind();
					socket.sendByteBuffer(buf, 0);
				}
			}
			}
			break;
		case 6: //DoubleType
			{
			final double[] data = (double[])img.update(null).getCurrentStorageArray();
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
				socket.sendByteBuffer(buf, 0);
			}
			else
			{
				final int firstBlocksLen = data.length/TypeSize + (data.length%TypeSize != 0? 1 : 0);
				final int lastBlockLen = data.length - (TypeSize-1)*firstBlocksLen;

				final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*firstBlocksLen);
				for (int p=0; p < (TypeSize-1); ++p)
				{
					//buf.rewind(); -- sendByteBuffer should not change the position as it works on a buf.duplicate()
					buf.asDoubleBuffer().put(data, p*firstBlocksLen, firstBlocksLen);
					buf.rewind();
					socket.sendByteBuffer(buf, (lastBlockLen > 0 || p < TypeSize-2 ? ZMQ.SNDMORE : 0));
				}

				if (lastBlockLen > 0)
				{
					buf.limit(TypeSize*lastBlockLen);
					buf.rewind();
					buf.asDoubleBuffer().put(data, (TypeSize-1)*firstBlocksLen, lastBlockLen);
					buf.rewind();
					socket.sendByteBuffer(buf, 0);
				}
			}
			}
			break;
		default:
			throw new Exception("Unsupported voxel type, sorry.");
		}
	}


	// -------- the types war --------
	/*
	 * Keeps unwrapping the input image \e img
	 * until it gets to the underlying pure imglib2.Img.
	 */
	@SuppressWarnings("unchecked")
	private <Q> Img<Q> getUnderlyingImg(final Img<Q> img)
	{
		if (img instanceof Dataset)
			return (Img<Q>) getUnderlyingImg( ((Dataset)img).getImgPlus() );
		else if (img instanceof WrappedImg)
			return getUnderlyingImg( ((WrappedImg<Q>)img).getImg() );
		else
			return img;
	}

	/*
	 * Must be kept synchronized with typeToTypeID() !
	 */
	private
	String typeIDToString(final int ID) throws Exception
	{
		switch (ID)
		{
		case 1:
			return new String("ByteType");
		case 2:
			return new String("UnsignedByteType");
		case 3:
			return new String("ShortType");
		case 4:
			return new String("UnsignedShortType");
		case 5:
			return new String("FloatType");
		case 6:
			return new String("DoubleType");
		default:
			throw new Exception("Unsupported voxel type, sorry.");
		}
	}

	/*
	 * Must be kept synchronized with typeIDToString() !
	 */
	private
	int typeToTypeID(final T type) throws Exception
	{
		if (type instanceof ByteType) return 1;
		else
		if (type instanceof UnsignedByteType) return 2;
		else
		if (type instanceof ShortType) return 3;
		else
		if (type instanceof UnsignedShortType) return 4;
		else
		if (type instanceof FloatType) return 5;
		else
		if (type instanceof DoubleType) return 6;
		else
			throw new Exception("Unsupported voxel type, sorry.");
	}
}
