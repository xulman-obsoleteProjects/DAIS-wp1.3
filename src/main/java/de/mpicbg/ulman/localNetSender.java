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

import net.imagej.Dataset;
import net.imagej.ImgPlus;

import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import de.mpicbg.ulman.ImgPacker;

@Plugin(type = Command.class, menuPath = "DAIS>Local Network Image Sender")
public class localNetSender implements Command
{
	@Parameter
	private LogService log;

	@Parameter
	private StatusService statusService;

	@Parameter
	private Dataset dataset;

	@Override
	public void run()
	{
		log.info("sender started");

		//init the communication side
		ZMQ.Context zmqContext = ZMQ.context(1);
		ZMQ.Socket writerSocket = null;
		try {
			//peer to send data out
			writerSocket = zmqContext.socket(ZMQ.PUSH);
			writerSocket.connect("tcp://localhost:5555");
		}
		catch (ZMQException e) {
			log.error(e);

			//clean up...
			writerSocket.close();
			zmqContext.term();

			//indiciation of failure
			writerSocket = null;
		}

		//stop plugin execution here if we cannot continue
		if (writerSocket == null) return;
		log.info("sender connected");

		//send the image
		final ImgPacker<?> ip = new ImgPacker<>();
		try {
			//this sends the ImgPlus...
			ip.packAndSend((ImgPlus)dataset.getImgPlus(), writerSocket);
		} catch (Exception e) {
			System.out.println("Error: "+e.getMessage());
			e.printStackTrace();
		}

		//clean up...
		writerSocket.close();
		zmqContext.term();
		log.info("sender finished");
	}
}
