/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman;

import org.scijava.app.StatusService;
import org.scijava.log.LogService;

import de.mpicbg.ulman.imgstreamer.ProgressCallback;

public class FijiLogger implements ProgressCallback
{
	//singleton accross all loggers of this kind
	final static long initTime = System.currentTimeMillis();

	FijiLogger(final LogService _log, final StatusService _bar)
	{ log = _log; bar = _bar; }

	final LogService log;
	final StatusService bar;

	@Override
	public void info(String msg)
	{ System.out.println((System.currentTimeMillis()-initTime)+" : "+msg); }

	@Override
	public void setProgress(float howFar)
	{ bar.showProgress((int)(100 * howFar), 100); }
}
