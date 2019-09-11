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
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.log.LogService;

import net.imagej.ImgPlus;
import net.imagej.display.ImageDisplayService;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.IOException;

import de.mpicbg.ulman.imgtransfer.ImgTransfer;

@Plugin(type = Command.class, menuPath = "File>Import>Receive Multiple Images")
public class ReceiveImages implements Command
{
	@Parameter
	private LogService log;

	@Parameter
	private StatusService status;

	@Parameter
	private ImageDisplayService ui;

	// ----------- receiving -----------
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false, initializer="getHostURL")
	private String hostURLmsg = "";

	private String hostURL = "";
	void getHostURL() throws UnknownHostException
	{
		hostURL = InetAddress.getLocalHost().getHostAddress();
		hostURLmsg = "Option A: Tell your sending partner this address: ";
		hostURLmsg += hostURL + ":";
		hostURLmsg += new Integer(portNo).toString();
	}

	@Parameter(label = "port to listen at:", callback="getHostURL", min="1025", max="65535",
			description = "The port number should be higher than 1024 such as 54545.")
	private int portNo = 54545;

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private String firewallMsg = "Make sure the firewall is not blocking incoming connections to Fiji.";

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private String sepMsgA = "------------------------------------------------------";

	// ----------- requesting -----------
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private String remoteURLmsg = "Option B: Ask your serving partner to tell you his address.";

	@Parameter(label = "address:port of the serving party:",
			description = "The address can be anything as example.net or IP address"
			+" as 10.0.0.2 delimited with ':' followed by a port number higher than"
			+" 1024 such as 54545. It is important not to use any spaces.",
			columns=15)
	private String remoteURL = "replace_me:54545";

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private String sepMsgB = "------------------------------------------------------";

	// ----------- common -----------
	@Parameter(label = "Choose the same sending option as your partner:", choices = {"A", "B"})
	private char transferMode = 'A';

	@Parameter(label = "Connection timeout in seconds:",
			description = "The maximum time in seconds during which Fiji waits"
			+" for establishing connection. If connection is not made after this period of time,"
			+" no further attempts are made until this command is started again.",
			min="1")
	private int timeoutTime = 60;

	// ----------- executive part -----------
	@Override
	public void run()
	{
		final FijiLogger flog = new FijiLogger(log, status);

		//number of received images, total expected no. of images
		int cnt = 0, cntE = 0;

		try {
			if (transferMode == 'A')
			{
				final ImgTransfer Receiver = new ImgTransfer(portNo, timeoutTime, flog);

				while (Receiver.isThereNextImage())
				{
					//get next image and display it
					final ImgPlus<?> i = Receiver.receiveImage();
					ui.getDisplayService().createDisplay(i);

					//show the special banner after the very first transfer
					if (cnt == 0)
					{
						cntE = Receiver.getExpectedNumberOfImages();
						log.info("ReceiveImages plugin: going to receive "+cntE+" images");
					}

					//have we actually got something?
					if (i != null)
					{
						++cnt;
						status.showProgress(cnt,cntE);
						log.info("ReceiveImages plugin: received "+cnt+"/"+cntE+": "+i.getName());
					}
				}
			}
			else
			{
				final ImgTransfer Receiver = new ImgTransfer("tcp://"+remoteURL, timeoutTime, flog);

				while (Receiver.isThereNextImage())
				{
					//get next image and display it
					final ImgPlus<?> i = Receiver.requestImage();
					ui.getDisplayService().createDisplay(i);

					//show the special banner after the very first transfer
					if (cnt == 0)
					{
						cntE = Receiver.getExpectedNumberOfImages();
						log.info("ReceiveImages plugin: going to receive "+cntE+" images");
					}

					//have we actually got something?
					if (i != null)
					{
						++cnt;
						status.showProgress(cnt,cntE);
						log.info("ReceiveImages plugin: received "+cnt+"/"+cntE+": "+i.getName());
					}
				}
			}
		}
		catch (IOException e) {
			log.error(e.getMessage());
		}
	}
}
