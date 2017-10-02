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

import org.scijava.ItemIO;
import java.net.InetAddress;
import java.net.UnknownHostException;

import java.io.IOException;

import de.mpicbg.ulman.imgtransfer.ImgPacker;
import de.mpicbg.ulman.imgtransfer.ProgressCallback;

@Plugin(type = Command.class, menuPath = "DAIS>Local Network Image Receiver")
public class LocalNetReceiver implements Command
{
	@Parameter
	private LogService log;

	@Parameter
	private StatusService status;

	@Parameter(type = ItemIO.OUTPUT)
	private ImgPlus<?> imgP;

	@Parameter(visibility = ItemVisibility.MESSAGE, initializer="getHostURL")
	private String hostURLmsg = "";

	private String hostURL = "";
	void getHostURL() throws UnknownHostException
	{
		hostURL = InetAddress.getLocalHost().getHostAddress();
		hostURLmsg = "Please, tell your sending partner to use this for the address: ";
		hostURLmsg += hostURL;
	}

	@Parameter(label = "port to listen at:",
			description = "The port number should be higher than"
			+" 1024 such as 54545. It is important not to use any spaces.")
	private int portNo = 54545;

	@Parameter(label = "listening timeout in seconds:",
			description = "The maximum time in seconds during which Fiji waits"
			+" for incomming connection. If nothing comes after this period of time,"
			+" the listening is stopped until this command is started again.",
			min="1")
	private int timeoutTime = 60;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String firewallMsg = "Make sure the firewall is not blocking incomming connections to Fiji.";

	public class FijiLogger implements ProgressCallback
	{
		FijiLogger(final LogService _log, final StatusService _bar)
		{ log = _log; bar = _bar; }

		final LogService log;
		final StatusService bar;

		@Override
		public void info(String msg)
		{ log.info(msg); }

		@Override
		public void setProgress(float howFar)
		{ bar.showProgress((int)(100 * howFar), 100); }
	}

	@Override
	public void run()
	{
		final FijiLogger flog = new FijiLogger(log, status);
		try {
			imgP = ImgPacker.receiveImage(portNo, timeoutTime, flog);
		}
		catch (IOException e) {
			log.error(e.getMessage());
		}
	}
}
