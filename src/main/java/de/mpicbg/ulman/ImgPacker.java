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
			//socket.send(msg.getBytes(), ZMQ.SNDMORE);
			System.out.println(msg);
			packAndSendArrayImg((ArrayImg<T,?>)img, socket);
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
	void packAndSendArrayImg(final ArrayImg<T,?> img, final ZMQ.Socket socket)
	{
		System.out.println("Sending ArrayImg...");

		/*
		float[] data;

		int len = 1000;
		ByteBuffer buf = ByteBuffer.allocateDirect( len );
		buf.asFloatBuffer().put( data, offset, length);
		writerSocket.sendByteBuffer(buf, 0);
		buf.rewind();
		*/
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
