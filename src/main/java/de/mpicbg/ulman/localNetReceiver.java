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
import org.scijava.ui.UIService;
import net.imagej.ops.OpService;
import net.imagej.ImageJ;

import org.zeromq.ZMQ;

@Plugin(type = Command.class, menuPath = "DAIS>Local Network Image Receiver")
public class localNetReceiver implements Command
{
	@Parameter
	private LogService log;

	@Parameter
	private StatusService statusService;

	//@Override
	public void initiate()
	{
		System.out.println("receiver initiate");
	}

	@Override
	public void run()
	{
		System.out.println("receiver run");
	}
}
