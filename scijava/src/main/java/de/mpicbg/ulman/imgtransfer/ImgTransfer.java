/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman.imgtransfer;

import net.imagej.ImgPlus;
import net.imglib2.type.NativeType;

import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import java.io.IOException;

/**
 * This class provides convenience, front-end functions for ImgPlus transfer.
 *
 * There are actually two main sorts of the functions. The first sort
 * consists solely of static functions, which thus cannot remember any state
 * of the transfer after they end. These functions, therefore, allow to
 * send/receive only one image.
 *
 * The second sort of functions are not static functions. Caller must create
 * an object/instance of this class in order to use them. In this way,
 * multiple images can be sent/received one by one, always single image with
 * single function call. This necessitates that the looping over images to be
 * transferred has to happen on the caller's side, but it is not a difficult
 * piece of code and brings additional flexibility for the caller to decide
 * when he is ready for the transfer (e.g., allowing for streamlining of the
 * processing of the transferred images).
 *
 * In order to transfer multiple images, just call the transfer function
 * repeatedly (every time with different image, of course). The first call
 * would open the connection and transfer the first image, consequent calls
 * would just transfer their images. REMEMBER, however, to call the HangUp()
 * after last image was transferred! Receiving party should call
 * isThereNextImage() to determine if it should receive one more image or
 * HangUp() now. Note that the image transferring protocol knows, after every
 * image is transferred, whether there shall be next transfer.
 *
 * When sending images, you can optionally advice/send hint to the receiving
 * party about the number of images you plan to send. This can be achieved by
 * providing the constructor with the particular positive number. When receiving
 * images, you can optionally read the hint with getExpectedNumberOfImages()
 * anytime after the first image has arrived.
 *
 * Their might come, if requested, a third sort that would be collecting
 * convenience functions to send/receive an array of images.
 */
public class ImgTransfer
{
// ------------------ static, single-image handling functions ------------------

	/**
	 * Sends/pushes an image over network to someone who is receiving it.
	 *
	 * Logging/reporting IS supported here whenever \e log != null.
	 */
	@SuppressWarnings({"unchecked","rawtypes"})
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
			ImgPacker.packAndSend((ImgPlus) imgP, writerSocket, timeOut, log);

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

	/**
	 * Sends/pushes an image over network to someone who is receiving it.
	 *
	 * No logging/reporting is supported here.
	 */
	public static <T extends NativeType<T>>
	void sendImage(final ImgPlus<T> imgP, final String addr,
	               final int timeOut)
	throws IOException
	{ sendImage(imgP, addr, timeOut, null); }


	/**
	 * Receives an image over network from someone who is sending/pushing it.
	 *
	 * Logging/reporting IS supported here whenever \e log != null.
	 */
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
			byte[] incomingData = waitForIncomingData(listenerSocket, "receiver", timeOut, log);

			//process incoming data if there is some...
			if (incomingData != null) {
				imgP = ImgPacker.receiveAndUnpack(new String(incomingData), listenerSocket, log);
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

	/**
	 * Receives an image over network from someone who is sending/pushing it.
	 *
	 * No logging/reporting is supported here.
	 */
	public static <T extends NativeType<T>>
	ImgPlus<?> receiveImage(final int portNo,
	                        final int timeOut)
	throws IOException
	{ return receiveImage(portNo, timeOut, null); }


	/**
	 * Serves an image over network to someone who is receiving/pulling it,
	 * it acts in fact as the sendImage() but connection is initiated from
	 * the receiver (the other peer)
	 *
	 * Logging/reporting IS supported here whenever \e log != null.
	 */
	@SuppressWarnings({"unchecked","rawtypes"})
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
			byte[] incomingData = waitForIncomingData(listenerSocket, "server", timeOut, log);

			//process incoming data if there is some...
			if (incomingData != null && new String(incomingData).startsWith("can get"))
			{
				ImgPacker.packAndSend((ImgPlus) imgP, listenerSocket, timeOut, log);
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

	/**
	 * Serves an image over network to someone who is receiving/pulling it,
	 * it acts in fact as the sendImage() but connection is initiated from
	 * the receiver (the other peer)
	 *
	 * No logging/reporting is supported here.
	 */
	public static <T extends NativeType<T>>
	void serveImage(final ImgPlus<T> imgP, final int portNo,
	                final int timeOut)
	throws IOException
	{ serveImage(imgP, portNo, timeOut, null); }


	/**
	 * Receives/pulls an image over network from someone who is serving it,
	 * it acts in fact as the receiveImage() but connection is initiated from
	 * this function (the receiver).
	 *
	 * Logging/reporting IS supported here whenever \e log != null.
	 */
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
			byte[] incomingData = waitForIncomingData(writerSocket, "receiver", timeOut, log);

			//process incoming data if there is some...
			if (incomingData != null)
			{
				imgP = ImgPacker.receiveAndUnpack(new String(incomingData), writerSocket, log);
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

	/**
	 * Receives/pulls an image over network from someone who is serving it,
	 * it acts in fact as the receiveImage() but connection is initiated from
	 * this function (the receiver).
	 *
	 * No logging/reporting is supported here.
	 */
	public static <T extends NativeType<T>>
	ImgPlus<?> requestImage(final String addr,
	                        final int timeOut)
	throws IOException
	{ return requestImage(addr, timeOut, null); }


// ------------------ non-static, multiple-images handling functions ------------------


// ------------------ helper functions ------------------

	/**
	 * This is an internal helper function to poll socket for incoming data,
	 * it reports progress of the polling too.
	 *
	 * Returns null if no data has arrived during the \e timeOut interval,
	 * otherwise returns the data itself.
	 */
	private static byte[] waitForIncomingData(final ZMQ.Socket socket,
		final String waiter, final int timeOut, final ProgressCallback log)
	throws InterruptedException
	{
		//"an entry point" for the input data
		byte[] incomingData = null;

		//"busy wait" up to the given period of time
		int timeAlreadyWaited = 0;
		while (timeAlreadyWaited < timeOut && incomingData == null)
		{
			if (timeAlreadyWaited % 10 == 0 && timeAlreadyWaited > 0)
				if (log != null) log.info(waiter+" waiting already " + timeAlreadyWaited + " seconds");

			//check if there is some data from a sender
			incomingData = socket.recv(ZMQ.NOBLOCK);

			//if nothing found, wait a while before another checking attempt
			if (incomingData == null) Thread.sleep(1000);

			++timeAlreadyWaited;
		}

		return incomingData;
	}
}
