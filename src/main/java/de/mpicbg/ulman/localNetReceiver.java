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

		//process incomming data if there is some...
		if (incomingData != null)
		{
			System.out.println("Received: " + new String(incomingData));

			//is there more messages comming?
			while (listenerSocket.hasReceiveMore())
			{
				incomingData = listenerSocket.recv(ZMQ.NOBLOCK);
				System.out.println("Received: " + new String(incomingData));
			}

			/*
			String update = String.format("%05d %d %d", zipcode, temperature, relhumidity);
			ZMQ.Socket publisher = context.socket(ZMQ.PUB);
			publisher.send(update, 0);

			import java.util.StringTokenizer;
			StringTokenizer sscanf = new StringTokenizer(inputString, " ");
			int zipcode = Integer.valueOf(sscanf.nextToken());
			*/

			/*
			// Send reply back to client
			String reply = "World";
			listenerSocket.send(reply.getBytes(), 0);
			*/
		}

		//clean up...
		listenerSocket.close();
		zmqContext.term();
		log.info("receiver closed");
	}
}
