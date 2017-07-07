/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.app.StatusService;
import org.scijava.log.LogService;

import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import de.mpicbg.ulman.ImgPacker;

@Plugin(type = Command.class, menuPath = "DAIS>Local Network Image Receiver")
public class localNetReceiver implements Command
{
	@Parameter
	private LogService log;

	@Parameter
	private StatusService statusService;

	@Override
	public void run()
	{
		log.info("receiver started");

		//init the communication side
		ZMQ.Context zmqContext = ZMQ.context(1);
		ZMQ.Socket listenerSocket = null;
		try {
			//port to listen for incomming data
			listenerSocket = zmqContext.socket(ZMQ.PULL);
			listenerSocket.bind("tcp://*:5555");
		}
		catch (ZMQException e) {
			//log.error(e);
			log.info("receiver crashed");

			//clean up...
			listenerSocket.close();
			zmqContext.term();

			//indiciation of failure
			listenerSocket = null;
		}

		//stop plugin execution here if we cannot continue
		if (listenerSocket == null) return;
		log.info("receiver waiting");

		//"an entry point" for the input data
		byte[] incomingData = null;

		//"busy wait" up to the given period of time
		int timeAlreadyWaited=0;
		while (timeAlreadyWaited < 20 && incomingData == null)
		{
			log.info("receiver read attempt no. "+timeAlreadyWaited);

			//check if there is some data from a sender
			incomingData = listenerSocket.recv(ZMQ.NOBLOCK);

			//if nothing found, wait a while before another checking attempt
			try {
				if (incomingData == null) Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			++timeAlreadyWaited;
		}

		//process incoming data if there is some...
		if (incomingData != null)
		{
			final ImgPacker<?> ip = new ImgPacker<>();
			try {
				//this guy returns the ImgPlus that we desire...:w
				ip.receiveAndUnpack(new String(incomingData), listenerSocket);
			} catch (Exception e) {
				System.out.println("Error: "+e.getMessage());
				e.printStackTrace();
			}
		}

		//clean up...
		listenerSocket.close();
		zmqContext.term();
		log.info("receiver closed");
	}
}
