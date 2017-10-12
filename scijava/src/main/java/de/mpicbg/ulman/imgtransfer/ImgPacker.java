/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman.imgtransfer;

import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.img.WrappedImg;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
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

import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import java.io.IOException;

public class ImgPacker
{
	/// sends/pushes an image over network to someone who is receiving it
	public static <T extends NativeType<T>>
	void sendImage(final ImgPlus<T> imgP, final String addr,
	               final int timeOut, final ProgressCallback log)
	throws IOException
	{
		if (log != null) log.info("sender started");

		//init the communication side
		ZMQ.Context zmqContext = ZMQ.context(1);
		ZMQ.Socket writerSocket = null;
		try {
			writerSocket = zmqContext.socket(ZMQ.PAIR);
			if (writerSocket == null)
				throw new Exception("cannot obtain local socket");

			//peer to send data out
			writerSocket.connect(addr);

			//send the image
			packAndSend((ImgPlus) imgP, writerSocket, timeOut, log);

			if (log != null) log.info("sender finished");
		}
		catch (ZMQException e) {
			throw new IOException("sender crashed, ZeroMQ error: " + e.getMessage());
		}
		catch (Exception e) {
			throw new IOException("sender error: " + e.getMessage());
		}
		finally {
			if (log != null) log.info("sender cleaning");
			if (writerSocket != null)
			{
				writerSocket.disconnect(addr);
				writerSocket.close();
			}
			zmqContext.close();
			zmqContext.term();
		}
	}

	/// sends/pushes an image over network to someone who is receiving it
	public static <T extends NativeType<T>>
	void sendImage(final ImgPlus<T> imgP, final String addr,
	               final int timeOut)
	throws IOException
	{ sendImage(imgP, addr, timeOut, null); }


	/// receives an image over network from someone who is sending/pushing it
	public static <T extends NativeType<T>>
	ImgPlus<?> receiveImage(final int portNo,
	                        final int timeOut, final ProgressCallback log)
	throws IOException
	{
		if (log != null) log.info("receiver started");
		ImgPlus<?> imgP = null;

		//init the communication side
		ZMQ.Context zmqContext = ZMQ.context(1);
		ZMQ.Socket listenerSocket = null;
		try {
			listenerSocket = zmqContext.socket(ZMQ.PAIR);
			if (listenerSocket == null)
				throw new Exception("cannot obtain local socket");

			//port to listen for incoming data
			listenerSocket.bind("tcp://*:" + portNo);
			if (log != null) log.info("receiver waiting");

			//"an entry point" for the input data
			byte[] incomingData = null;

			//"busy wait" up to the given period of time
			int timeAlreadyWaited = 0;
			while (timeAlreadyWaited < timeOut && incomingData == null)
			{
				if (timeAlreadyWaited % 10 == 0 && timeAlreadyWaited > 0)
					if (log != null) log.info("receiver waiting already " + timeAlreadyWaited + " seconds");

				//check if there is some data from a sender
				incomingData = listenerSocket.recv(ZMQ.NOBLOCK);

				//if nothing found, wait a while before another checking attempt
				if (incomingData == null) Thread.sleep(1000);

				++timeAlreadyWaited;
			}

			//process incoming data if there is some...
			if (incomingData != null) {
				imgP = receiveAndUnpack(new String(incomingData), listenerSocket, log);
				//NB: this guy returns the ImgPlus that we desire...
			}

			if (log != null) log.info("receiver finished");
		}
		catch (ZMQException e) {
			throw new IOException("receiver crashed, ZeroMQ error: " + e.getMessage());
		}
		catch (Exception e) {
			throw new IOException("receiver error: " + e.getMessage());
		}
		finally {
			if (log != null) log.info("receiver cleaning");
			if (listenerSocket != null)
			{
				listenerSocket.unbind("tcp://*:" + portNo);
				listenerSocket.close();
			}
			zmqContext.close();
			zmqContext.term();
		}

		return imgP;
	}

	/// receives an image over network from someone who is sending/pushing it
	public static <T extends NativeType<T>>
	ImgPlus<?> receiveImage(final int portNo,
	                        final int timeOut)
	throws IOException
	{ return receiveImage(portNo, timeOut, null); }


	/// just like sendImage() but connection is initiated from the receiver
	public static <T extends NativeType<T>>
	void serveImage(final ImgPlus<T> imgP, final int portNo,
	                final int timeOut, final ProgressCallback log)
	throws IOException
	{
		if (log != null) log.info("server started");

		//init the communication side
		ZMQ.Context zmqContext = ZMQ.context(1);
		ZMQ.Socket listenerSocket = null;
		try {
			listenerSocket = zmqContext.socket(ZMQ.PAIR);
			if (listenerSocket == null)
				throw new Exception("cannot obtain local socket");

			//port to listen for incoming data
			listenerSocket.bind("tcp://*:" + portNo);
			if (log != null) log.info("server waiting");

			//"an entry point" for the input data
			byte[] incomingData = null;

			//"busy wait" up to the given period of time
			int timeAlreadyWaited = 0;
			while (timeAlreadyWaited < timeOut && incomingData == null)
			{
				if (timeAlreadyWaited % 10 == 0 && timeAlreadyWaited > 0)
					if (log != null) log.info("server waiting already " + timeAlreadyWaited + " seconds");

				//check if there is some data from a sender
				incomingData = listenerSocket.recv(ZMQ.NOBLOCK);

				//if nothing found, wait a while before another checking attempt
				if (incomingData == null) Thread.sleep(1000);

				++timeAlreadyWaited;
			}

			//process incoming data if there is some...
			if (incomingData != null && new String(incomingData).startsWith("can get"))
			{
				packAndSend((ImgPlus) imgP, listenerSocket, timeOut, log);
			}
			else
			{
				if (incomingData != null)
					throw new RuntimeException("Protocol error, expected initial ping from the receiver.");
			}

			if (log != null) log.info("server finished");
		}
		catch (ZMQException e) {
			throw new IOException("server crashed, ZeroMQ error: " + e.getMessage());
		}
		catch (Exception e) {
			throw new IOException("server error: " + e.getMessage());
		}
		finally {
			if (log != null) log.info("server cleaning");
			if (listenerSocket != null)
			{
				listenerSocket.unbind("tcp://*:" + portNo);
				listenerSocket.close();
			}
			zmqContext.close();
			zmqContext.term();
		}
	}

	/// just like sendImage() but connection is initiated from the receiver
	public static <T extends NativeType<T>>
	void serveImage(final ImgPlus<T> imgP, final int portNo,
	                final int timeOut)
	throws IOException
	{ serveImage(imgP, portNo, timeOut, null); }


	/// just like receiveImage() but initiate the connection
	public static <T extends NativeType<T>>
	ImgPlus<?> requestImage(final String addr,
	                        final int timeOut, final ProgressCallback log)
	throws IOException
	{
		if (log != null) log.info("receiver started");
		ImgPlus<?> imgP = null;

		//init the communication side
		ZMQ.Context zmqContext = ZMQ.context(1);
		ZMQ.Socket writerSocket = null;
		try {
			writerSocket = zmqContext.socket(ZMQ.PAIR);
			if (writerSocket == null)
				throw new Exception("cannot obtain local socket");

			//peer to send data out
			writerSocket.connect(addr);

			//send the request
			if (log != null) log.info("receiver request sent");
			writerSocket.send("can get");

			//wait for connection to happen...
			//wait for reply (already with image data)

			//wait for the input image:
			//"an entry point" for the input data
			byte[] incomingData = null;

			//"busy wait" up to the given period of time
			int timeAlreadyWaited = 0;
			while (timeAlreadyWaited < timeOut && incomingData == null)
			{
				if (timeAlreadyWaited % 10 == 0 && timeAlreadyWaited > 0)
					if (log != null) log.info("receiver waiting already " + timeAlreadyWaited + " seconds");

				//check if there is some data from a sender
				incomingData = writerSocket.recv(ZMQ.NOBLOCK);

				//if nothing found, wait a while before another checking attempt
				if (incomingData == null) Thread.sleep(1000);

				++timeAlreadyWaited;
			}

			//process incoming data if there is some...
			if (incomingData != null)
			{
				imgP = receiveAndUnpack(new String(incomingData), writerSocket, log);
			}

			if (log != null) log.info("receiver finished");
		}
		catch (ZMQException e) {
			throw new IOException("receiver crashed, ZeroMQ error: " + e.getMessage());
		}
		catch (Exception e) {
			throw new IOException("receiver error: " + e.getMessage());
		}
		finally {
			if (log != null) log.info("receiver cleaning");
			if (writerSocket != null)
			{
				writerSocket.disconnect(addr);
				writerSocket.close();
			}
			zmqContext.close();
			zmqContext.term();
		}

		return imgP;
	}

	/// just like receiveImage() but initiate the connection
	public static <T extends NativeType<T>>
	ImgPlus<?> requestImage(final String addr,
	                        final int timeOut)
	throws IOException
	{ return requestImage(addr, timeOut, null); }


	// -------- transmission of the image, sockets --------
	///list of supported voxel types: so far only scalar images are supported
	@SuppressWarnings("rawtypes")
	private static List<Class<? extends NativeType>> SUPPORTED_VOXEL_CLASSES =
			Arrays.asList(ByteType.class, UnsignedByteType.class, ShortType.class,
					UnsignedShortType.class, FloatType.class, DoubleType.class);

	@SuppressWarnings("unchecked")
	public static <T extends NativeType<T>>
	void packAndSend(final ImgPlus<T> imgP, final ZMQ.Socket socket,
	                 final int timeOut, final ProgressCallback log)
	{
		Class<?> voxelClass = imgP.firstElement().getClass();
		if(!SUPPORTED_VOXEL_CLASSES.contains(voxelClass))
			throw new IllegalArgumentException("Unsupported voxel type, sorry.");

		//"buffer" for the first and human-readable payload:
		//protocol version
		String msg = new String("v1");

		//dimensionality data
		msg += " dimNumber " + imgP.numDimensions();
		for (int i=0; i < imgP.numDimensions(); ++i)
			msg += " " + imgP.dimension(i);

		//decipher the voxel type
		msg += " " + voxelClass.getSimpleName();

		//check we can handle the storage model of this image,
		//and try to send everything (first the human readable payload, then raw voxel data)
		Img<T> img = getUnderlyingImg(imgP);
		if (img instanceof ArrayImg)
		{
			msg += " ArrayImg ";

			//send header, metadata and voxel data afterwards
			if (log != null) log.info("sending header: "+msg);
			packAndSendHeader(msg, socket, timeOut);
			if (log != null) log.info("sending the image...");
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

			//TODO: if cell image will also need not to add extra header hints,
			//      we can move the 4 lines before this 3-branches-if

			//send header, metadata and voxel data afterwards
			if (log != null) log.info("sending header: "+msg);
			packAndSendHeader(msg, socket, timeOut);
			if (log != null) log.info("sending the image...");
			packAndSendPlusData(imgP, socket);
			packAndSendPlanarImg((PlanarImg<T,? extends ArrayDataAccess<?>>)img, socket);
		}
		else
		if (img instanceof CellImg)
		{
			//possibly add additional configuration hints to 'msg'
			msg += " CellImg ";
			throw new RuntimeException("Cannot send CellImg images yet.");

			//send header, metadata and voxel data afterwards
			//if (log != null) log.info("sending header: "+msg);
			//packAndSendHeader(msg, socket, timeOut);
			//if (log != null) log.info("sending the image...");
			//packAndSendPlusData(imgP, socket);
			//packAndSendCellImg((CellImg<T,?>)img, socket);
		}
		else
			throw new RuntimeException("Cannot determine the type of image, cannot send it.");

		//wait for confirmation from the receiver
		ArrayReceiver.waitForFirstMessage(socket);
		msg = socket.recvStr();
		if (! msg.startsWith("done"))
			throw new RuntimeException("Protocol error, expected final confirmation from the receiver.");
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static
	ImgPlus<?> receiveAndUnpack(final String header, final ZMQ.Socket socket,
	                            final ProgressCallback log)
	{
		if (log != null) log.info("received header: "+header);
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
		Img<? extends NativeType<?>> img = createImg(dims, backendStr, createVoxelType(typeStr));

		if (img == null)
			throw new RuntimeException("Unsupported image backend type, sorry.");

		//if we got here, we assume that we have everything prepared to receive
		//the image, we therefore signal it to the sender
		socket.send("ready");
		if (log != null) log.info("receiving the image...");

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

		//send confirmation handshake after data has arrived
		socket.send("done");

		return imgP;
	}


	// -------- support for the transmission of the image metadata --------
	/// this function sends the header AND WAITS FOR RESPONSE
	private static
	void packAndSendHeader(final String hdr, final ZMQ.Socket socket, final int timeOut)
	{
		//send _complete_ message with just the header
		socket.send(hdr.getBytes(), 0);
		//NB: if message is not complete (i.e. SNDMORE is flagged),
		//system/ZeroMQ will not be ready to listen for confirmation message

		//wait for response, else complain for timeout-ing
		ArrayReceiver.waitForFirstMessage(socket, timeOut);
		//NB: if we got here (after the waitFor..()), some message is ready to be read out

		final String confirmation = socket.recvStr();
		if (! confirmation.startsWith("ready"))
			throw new RuntimeException("Protocol error, expected initial confirmation from the receiver.");
	}


	///meta data Message Separator
	private static
	final String mdMsgSep = new String("__QWE__");

	private static <T>
	void packAndSendPlusData(final ImgPlus<T> imgP, final ZMQ.Socket socket)
	{
		//TODO: use JSON because metadata are of various types (including Strings)

		String msg = new String("metadata");
		msg += mdMsgSep+"imagename"+mdMsgSep+imgP.getName();
		msg += mdMsgSep+"endmetadata";
		socket.send(msg, ZMQ.SNDMORE);
	}

	private static <T>
	void receiveAndUnpackPlusData(final ImgPlus<T> imgP, final ZMQ.Socket socket)
	{
		//TODO: use JSON because metadata are of various types (including Strings)

		//read the single message
		ArrayReceiver.waitForFirstMessage(socket);
		final String data = socket.recvStr();

		if (! data.startsWith("metadata"))
			throw new RuntimeException("Protocol error, expected metadata part from the receiver.");

		//split the input data to individual terms
		String[] terms = data.split(mdMsgSep);

		//should do more thorough tests....
		if (terms.length != 4)
			throw new RuntimeException("Protocol error, received likely corrupted metadata part.");

		//set filename
		imgP.setName(terms[2]);
	}


	// -------- support for the transmission of the payload/voxel data --------
	private static <T extends NativeType<T>>
	void packAndSendArrayImg(final ArrayImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket)
	{
		if (img.size() == 0)
			throw new RuntimeException("Refusing to send an empty image...");

		final Object data = img.update(null).getCurrentStorageArray();
		ArraySender.sendArray(data, socket, false);
	}

	private static <T extends NativeType<T>>
	void receiveAndUnpackArrayImg(final ArrayImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket)
	{
		if (img.size() == 0)
			throw new RuntimeException("Refusing to receive an empty image...");

		final Object data = img.update(null).getCurrentStorageArray();
		ArrayReceiver.receiveArray(data, socket);
	}

	private static <T extends NativeType<T>>
	void packAndSendPlanarImg(final PlanarImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket)
	{
		if (img.size() == 0)
			throw new RuntimeException("Refusing to send an empty image...");

		for (int slice = 0; slice < img.numSlices()-1; ++slice)
		{
			final Object data = img.getPlane(slice).getCurrentStorageArray();
			ArraySender.sendArray(data, socket, true);
		}
		{
			final Object data = img.getPlane(img.numSlices()-1).getCurrentStorageArray();
			ArraySender.sendArray(data, socket, false);
		}
	}

	private static <T extends NativeType<T>>
	void receiveAndUnpackPlanarImg(final PlanarImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket)
	{
		if (img.size() == 0)
			throw new RuntimeException("Refusing to receive an empty image...");

		for (int slice = 0; slice < img.numSlices()-1; ++slice)
		{
			final Object data = img.getPlane(slice).getCurrentStorageArray();
			ArrayReceiver.receiveArray(data, socket);
		}
		{
			final Object data = img.getPlane(img.numSlices()-1).getCurrentStorageArray();
			ArrayReceiver.receiveArray(data, socket);
		}
	}


	// -------- the types war --------
	/*
	 * Keeps unwrapping the input image \e img
	 * until it gets to the underlying pure imglib2.Img.
	 */
	@SuppressWarnings("unchecked")
	private static <Q>
	Img<Q> getUnderlyingImg(final Img<Q> img)
	{
		if (img instanceof Dataset)
			return (Img<Q>) getUnderlyingImg( ((Dataset)img).getImgPlus() );
		else if (img instanceof WrappedImg)
			return getUnderlyingImg( ((WrappedImg<Q>)img).getImg() );
		else
			return img;
	}

	@SuppressWarnings("rawtypes") // use raw type because of insufficient support of reflexive types in java
	private static
	NativeType createVoxelType(String typeStr)
	{
		for(Class<? extends NativeType> aClass : SUPPORTED_VOXEL_CLASSES)
			if(typeStr.startsWith(aClass.getSimpleName()))
				try {
					return aClass.newInstance();
				} catch (InstantiationException | IllegalAccessException e) {
					throw new RuntimeException(e);
				}
		throw new IllegalArgumentException("Unsupported voxel type, sorry.");
	}

	private static <T extends NativeType<T>>
	Img<T> createImg(int[] dims, String backendStr, T type)
	{
		if (backendStr.startsWith("ArrayImg"))
			return new ArrayImgFactory<T>().create(dims, type);
		if (backendStr.startsWith("PlanarImg"))
			return new PlanarImgFactory<T>().create(dims, type);
		if (backendStr.startsWith("CellImg"))
			return new CellImgFactory<T>().create(dims, type);
		throw new RuntimeException("Unsupported image backend type, sorry.");
	}
}
