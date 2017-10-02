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

import java.io.IOException;

import de.mpicbg.ulman.imgtransfer.ImgPacker;
import de.mpicbg.ulman.imgtransfer.ProgressCallback;

@Plugin(type = Command.class, menuPath = "DAIS>Request Image over Network")
public class RequestImage implements Command
{
	@Parameter
	private LogService log;

	@Parameter
	private StatusService status;

	@Parameter(type = ItemIO.OUTPUT)
	private ImgPlus<?> imgP;

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private String hostURL = "Please, ask your serving partner to tell you his address.";

	@Parameter(label = "address:port of the receiving party:",
			description = "The address can be anything as example.net or IP address"
			+" as 10.0.0.2 delimited with ':' followed by a port number higher than"
			+" 1024 such as 54545. It is important not to use any spaces.",
			columns=15)
	private String remoteURL = "replace_me:54545";

	@Parameter(label = "receiving timeout in seconds:",
			description = "The maximum time in seconds during which Fiji waits"
			+" for establishing connection. If connection is not made after this period of time,"
			+" no further attempts are made until this command is started again.",
			min="1")
	private int timeoutTime = 60;

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

	@SuppressWarnings("unchecked")
	@Override
	public void run()
	{
		final FijiLogger flog = new FijiLogger(log, status);
		try {
			imgP = ImgPacker.requestImage("tcp://"+remoteURL,
				timeoutTime, flog);
		}
		catch (IOException e) {
			log.error(e.getMessage());
		}
	}
}
