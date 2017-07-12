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
import net.imglib2.img.array.ArrayImgFactory;
//import net.imglib2.img.array.ArrayImgs; -- see the hing in packAndSendArrayImg()
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.cell.CellImg;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.img.planar.PlanarImg;
import net.imglib2.img.planar.PlanarImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;

import java.util.StringTokenizer;
import java.nio.ByteBuffer;
import org.zeromq.ZMQ;

public class ImgPacker<T extends NativeType<T>>
{
	/**
	 *
	 */
	@SuppressWarnings("unchecked")
	public
	void packAndSend(final ImgPlus<T> imgP, final ZMQ.Socket socket)
	{
		//"buffer" for the first and human-readable payload:
		//protocol version
		String msg = new String("v1");

		//dimensionality data
		msg += " dimNumber " + imgP.numDimensions();
		for (int i=0; i < imgP.numDimensions(); ++i)
			msg += " " + imgP.dimension(i);

		//decipher the voxel type
		msg += " " + TypeId.of(imgP.firstElement()));

		//check we can handle the storage model of this image,
		//and try to send everything (first the human readable payload, then raw voxel data)
		Img<T> img = getUnderlyingImg(imgP);
		if (img instanceof ArrayImg)
		{
			msg += " ArrayImg ";
			socket.send(msg.getBytes(), ZMQ.SNDMORE);

			//send metadata and voxel data afterwards
			packAndSendPlusData(imgP, socket);
			packAndSendArrayImg((ArrayImg<T,? extends ArrayDataAccess<?>>)img, socket);
		}
		else
		if (img instanceof PlanarImg)
		{
			//possibly add additional configuration hints to 'msg'
			msg += " PlanarImg "; //+((PlanarImg<T,?>)img).numSlices()+" ";
			//NB: The number of planes is deterministically given by the image size/dimensions.
			//    Hence, it is not necessary to provide such hint... 
			socket.send(msg.getBytes(), ZMQ.SNDMORE);

			//send metadata and voxel data afterwards
			packAndSendPlusData(imgP, socket);
			packAndSendPlanarImg((PlanarImg<T,? extends ArrayDataAccess<?>>)img, socket);
		}
		else
		if (img instanceof CellImg)
		{
			msg += " CellImg ";
			throw new RuntimeException("Cannot send CellImg images yet.");
			//possibly add additional configuration hints to 'msg'
			//socket.send(msg.getBytes(), ZMQ.SNDMORE);

			//send metadata and voxel data afterwards
			//packAndSendPlusData(imgP, socket);
			//packAndSendCellImg((CellImg<T,?>)img, socket);
		}
		else
			throw new RuntimeException("Cannot determine the type of image, cannot send it.");
	}


	@SuppressWarnings({ "unchecked", "rawtypes" })
	public
	ImgPlus<?> receiveAndUnpack(final String header, final ZMQ.Socket socket)
	{
		StringTokenizer headerST = new StringTokenizer(header, " ");
		if (! headerST.nextToken().startsWith("v1"))
			throw new RuntimeException("Unknown protocol, expecting protocol v1.");

		if (! headerST.nextToken().startsWith("dimNumber"))
			throw new RuntimeException("Incorrect protocol, expecting dimNumber.");
		final int n = Integer.valueOf(headerST.nextToken());

		//fill the dimensionality data
		final int[] dims = new int[n];
		for (int i=0; i < n; ++i)
			dims[i] = Integer.valueOf(headerST.nextToken());

		final String typeStr = new String(headerST.nextToken());
		final String backendStr = new String(headerST.nextToken());

		//envelope/header message is (mostly) parsed,
		//start creating the output image of the appropriate type
		Img<? extends NativeType<?>> img = null;

		//create appropriate type and image variables
		if (typeStr.startsWith("ByteType"))
		{
			img = createImg(dims, backendStr, new ByteType());
		}
		else
		if (typeStr.startsWith("UnsignedByteType"))
		{
			img = createImg(dims, backendStr, new UnsignedByteType());
		}
		else
		if (typeStr.startsWith("ShortType"))
		{
			img = createImg(dims, backendStr, new ShortType());
		}
		else
		if (typeStr.startsWith("UnsignedShortType"))
		{
			img = createImg(dims, backendStr, new UnsignedShortType());
		}
		else
		if (typeStr.startsWith("FloatType"))
		{
			img = createImg(dims, backendStr, new FloatType());
		}
		else
		if (typeStr.startsWith("DoubleType"))
		{
			img = createImg(dims, backendStr, new DoubleType());
		}
		else
			throw new RuntimeException("Unsupported voxel type, sorry.");

		if (img == null)
			throw new RuntimeException("Unsupported image backend type, sorry.");

		//the core Img is prepared, lets extend it with metadata and fill with voxel values afterwards
		//create the ImgPlus from it -- there is fortunately no deep coping
		ImgPlus<?> imgP = new ImgPlus<>(img);
		receiveAndUnpackPlusData((ImgPlus)imgP, socket);

		//populate with voxel data
		if (backendStr.startsWith("ArrayImg"))
		{
			receiveAndUnpackArrayImg((ArrayImg)img, socket);
		}
		else
		if (backendStr.startsWith("PlanarImg"))
		{
			//read possible additional configuration hints from 'header'
			//final int Slices = Integer.valueOf(headerST.nextToken());
			//and fine-tune the img
			receiveAndUnpackPlanarImg((PlanarImg)img, socket);
		}
		else
		if (backendStr.startsWith("CellImg"))
		{
			//read possible additional configuration hints from 'header'
			//and fine-tune the img
			throw new RuntimeException("Cannot receive CellImg images yet.");
			//receiveAndUnpackCellImg((CellImg)img, socket);
		}
		else
			throw new RuntimeException("Unsupported image backend type, sorry.");

		return imgP;
	}

	private <T extends NativeType<T>> Img<T> createImg(int[] dims, String backendStr, T type) {
		if (backendStr.startsWith("ArrayImg"))
			return new ArrayImgFactory<T>().create(dims, type);
		if (backendStr.startsWith("PlanarImg"))
			return new PlanarImgFactory<T>().create(dims, type);
		if (backendStr.startsWith("CellImg"))
			return new CellImgFactory<T>().create(dims, type);
		throw new RuntimeException("Unsupported image backend type, sorry.");
	}


	// -------- support for the transmission of the image metadata --------
	private
	void packAndSendPlusData(final ImgPlus<T> imgP, final ZMQ.Socket socket)
	{
		//TODO: use mPack because metadata are of various types (including Strings)
		//send single message with ZMQ.SNDMORE
	}

	private
	void receiveAndUnpackPlusData(final ImgPlus<T> imgP, final ZMQ.Socket socket)
	{
		//TODO: use mPack because metadata are of various types (including Strings)
		//read single message
	}


	// -------- support for the transmission of the payload/voxel data --------
	private
	void packAndSendArrayImg(final ArrayImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket)
	{
		if (img.size() == 0)
			throw new RuntimeException("Refusing to send an empty image...");

/*
		//create a buffer to hold the whole image (limit: img must not have more than 2GB of voxels)
		float[] array = new float[width * height];

		//create an image on top of this buffer
		Img<FloatType> wrappedArray = ArrayImgs.floats(array, width, height);

		//copy any image (that fits to the limit) to this image
		//and the voxel data will be available in the 'array' -- ready for transmission
		copy(img, wrappedArray);
*/

		switch (TypeId.of(img.firstElement()))
		{
		case BYTE:
		case UNSIGNED_BYTE:
			{
			final byte[] data = (byte[])img.update(null).getCurrentStorageArray();
			packAndSendBytes(data, socket, false);
			}
			break;
		case SHORT:
		case UNSIGNED_SHORT:
			{
			final short[] data = (short[])img.update(null).getCurrentStorageArray();
			packAndSendShorts(data, socket, false);
			}
			break;
		case FLOAT:
			{
			final float[] data = (float[])img.update(null).getCurrentStorageArray();
			packAndSendFloats(data, socket, false);
			}
			break;
		case DOUBLE:
			{
			final double[] data = (double[])img.update(null).getCurrentStorageArray();
			packAndSendDoubles(data, socket, false);
			}
			break;
		default:
			throw new RuntimeException("Unsupported voxel type, sorry.");
		}
	}

	private
	void receiveAndUnpackArrayImg(final ArrayImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket)
	{
		if (img.size() == 0)
			throw new RuntimeException("Refusing to receive an empty image...");

		switch (TypeId.of(img.firstElement()))
		{
		case BYTE:
		case UNSIGNED_BYTE:
			{
			final byte[] data = (byte[])img.update(null).getCurrentStorageArray();
			receiveAndUnpackBytes(data, socket);
			}
			break;
		case SHORT:
		case UNSIGNED_SHORT:
			{
			final short[] data = (short[])img.update(null).getCurrentStorageArray();
			receiveAndUnpackShorts(data, socket);
			}
			break;
		case FLOAT:
			{
			final float[] data = (float[])img.update(null).getCurrentStorageArray();
			receiveAndUnpackFloats(data, socket);
			}
			break;
		case DOUBLE:
			{
			final double[] data = (double[])img.update(null).getCurrentStorageArray();
			receiveAndUnpackDoubles(data, socket);
			}
			break;
		default:
			throw new RuntimeException("Unsupported voxel type, sorry.");
		}
	}

	private
	void packAndSendPlanarImg(final PlanarImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket)
	{
		if (img.size() == 0)
			throw new RuntimeException("Refusing to send an empty image...");

		switch (TypeId.of(img.firstElement()))
		{
		case BYTE:
		case UNSIGNED_BYTE:
			for (int slice = 0; slice < img.numSlices()-1; ++slice)
			{
				final byte[] data = (byte[])img.getPlane(slice).getCurrentStorageArray();
				packAndSendBytes(data, socket, true);
			}
			{
				final byte[] data = (byte[])img.getPlane(img.numSlices()-1).getCurrentStorageArray();
				packAndSendBytes(data, socket, false);
			}
			break;
		case SHORT:
		case UNSIGNED_SHORT:
			for (int slice = 0; slice < img.numSlices()-1; ++slice)
			{
				final short[] data = (short[])img.getPlane(slice).getCurrentStorageArray();
				packAndSendShorts(data, socket, true);
			}
			{
				final short[] data = (short[])img.getPlane(img.numSlices()-1).getCurrentStorageArray();
				packAndSendShorts(data, socket, false);
			}
			break;
		case FLOAT:
			for (int slice = 0; slice < img.numSlices()-1; ++slice)
			{
				final float[] data = (float[])img.getPlane(slice).getCurrentStorageArray();
				packAndSendFloats(data, socket, true);
			}
			{
				final float[] data = (float[])img.getPlane(img.numSlices()-1).getCurrentStorageArray();
				packAndSendFloats(data, socket, false);
			}
			break;
		case DOUBLE:
			for (int slice = 0; slice < img.numSlices()-1; ++slice)
			{
				final double[] data = (double[])img.getPlane(slice).getCurrentStorageArray();
				packAndSendDoubles(data, socket, true);
			}
			{
				final double[] data = (double[])img.getPlane(img.numSlices()-1).getCurrentStorageArray();
				packAndSendDoubles(data, socket, false);
			}
			break;
		default:
			throw new RuntimeException("Unsupported voxel type, sorry.");
		}
	}

	private
	void receiveAndUnpackPlanarImg(final PlanarImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket)
	{
		if (img.size() == 0)
			throw new RuntimeException("Refusing to receive an empty image...");

		switch (TypeId.of(img.firstElement()))
		{
		case BYTE:
		case UNSIGNED_BYTE:
			for (int slice = 0; slice < img.numSlices()-1; ++slice)
			{
				final byte[] data = (byte[])img.getPlane(slice).getCurrentStorageArray();
				receiveAndUnpackBytes(data, socket);
			}
			{
				final byte[] data = (byte[])img.getPlane(img.numSlices()-1).getCurrentStorageArray();
				receiveAndUnpackBytes(data, socket);
			}
			break;
		case SHORT:
		case UNSIGNED_SHORT:
			for (int slice = 0; slice < img.numSlices()-1; ++slice)
			{
				final short[] data = (short[])img.getPlane(slice).getCurrentStorageArray();
				receiveAndUnpackShorts(data, socket);
			}
			{
				final short[] data = (short[])img.getPlane(img.numSlices()-1).getCurrentStorageArray();
				receiveAndUnpackShorts(data, socket);
			}
			break;
		case FLOAT:
			for (int slice = 0; slice < img.numSlices()-1; ++slice)
			{
				final float[] data = (float[])img.getPlane(slice).getCurrentStorageArray();
				receiveAndUnpackFloats(data, socket);
			}
			{
				final float[] data = (float[])img.getPlane(img.numSlices()-1).getCurrentStorageArray();
				receiveAndUnpackFloats(data, socket);
			}
			break;
		case DOUBLE:
			for (int slice = 0; slice < img.numSlices()-1; ++slice)
			{
				final double[] data = (double[])img.getPlane(slice).getCurrentStorageArray();
				receiveAndUnpackDoubles(data, socket);
			}
			{
				final double[] data = (double[])img.getPlane(img.numSlices()-1).getCurrentStorageArray();
				receiveAndUnpackDoubles(data, socket);
			}
			break;
		default:
			throw new RuntimeException("Unsupported voxel type, sorry.");
		}
	}

	/**
	 * This one checks periodically (until timeout period) if
	 * there is some some incoming data reported on the socket.
	 * It finishes "nicely" if there is some, or finishes
	 * with an expection complaining about timeout.
	 */
	private
	void waitForVoxels(final ZMQ.Socket socket)
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


	// -------- basic types storage vs. ByteType un/packagers --------
	private
	void packAndSendBytes(final byte[] data, final ZMQ.Socket socket, boolean comingMore)
	{
		final ByteBuffer buf = ByteBuffer.allocateDirect(data.length);
		buf.put(data);
		buf.rewind();
		socket.sendByteBuffer(buf, (comingMore? ZMQ.SNDMORE : 0));
	}

	private
	void packAndSendShorts(final short[] data, final ZMQ.Socket socket, boolean comingMore)
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

	private
	void packAndSendFloats(final float[] data, final ZMQ.Socket socket, boolean comingMore)
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

	private
	void packAndSendDoubles(final double[] data, final ZMQ.Socket socket, boolean comingMore)
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

	private
	void receiveAndUnpackBytes(final byte[] data, final ZMQ.Socket socket)
	{
		final ByteBuffer buf = ByteBuffer.allocateDirect(data.length);
		//are there any further messages pending?
		waitForVoxels(socket);
		socket.recvByteBuffer(buf, 0);
		buf.rewind();
		buf.get(data);
	}

	private
	void receiveAndUnpackShorts(final short[] data, final ZMQ.Socket socket)
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

	private
	void receiveAndUnpackFloats(final float[] data, final ZMQ.Socket socket)
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

	private
	void receiveAndUnpackDoubles(final double[] data, final ZMQ.Socket socket)
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

	private enum TypeId {
		BYTE(ByteType.class),
		UNSIGNED_BYTE(UnsignedByteType.class),
		SHORT(ShortType.class),
		UNSIGNED_SHORT(UnsignedShortType.class),
		FLOAT(FloatType.class),
		DOUBLE(DoubleType.class);

		private final Class<?> aClass;

		TypeId(Class<?> aClass) {
			this.aClass = aClass;
		}

		@Override
		public String toString() {
			return aClass.getSimpleName();
		}

		public static TypeId of(final Object type) {
			for(TypeId id : TypeId.values())
				if(id.aClass.isInstance(type))
					return id;
			throw new IllegalArgumentException("Unsupported voxel type, sorry.");
		}

	}
}
