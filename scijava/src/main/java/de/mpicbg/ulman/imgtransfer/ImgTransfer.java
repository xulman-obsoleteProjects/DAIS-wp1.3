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

public class ImgTransfer
{
	/// sends/pushes an image over network to someone who is receiving it
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

	/// receives an image over network from someone who is sending/pushing it
	public static <T extends NativeType<T>>
	ImgPlus<?> receiveImage(final int portNo,
	                        final int timeOut)
	throws IOException
	{ return receiveImage(portNo, timeOut, null); }


	/// just like sendImage() but connection is initiated from the receiver
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

	/// just like receiveImage() but initiate the connection
	public static <T extends NativeType<T>>
	ImgPlus<?> requestImage(final String addr,
	                        final int timeOut)
	throws IOException
	{ return requestImage(addr, timeOut, null); }
}
