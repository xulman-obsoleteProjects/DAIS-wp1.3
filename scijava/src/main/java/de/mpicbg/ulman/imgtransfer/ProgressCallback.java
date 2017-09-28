/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman.imgtransfer;

/**
 * An interface via which the ImgPacker is able to feed its caller
 * with updates about the image transmission progress.
 */
public interface ProgressCallback
{
	/**
	 * Reports any message to the caller of about progress of the worker.
	 * It is assumed that the parameter is submitted further to some
	 * loggin widget.
	 */
	void info(final String msg);

	/**
	 * Report an indication back to the caller of how far the worker is with its work.
	 * The parameter \e howFar takes real values between 0.0 and 1.0,
	 * where 0.0 indicated "not yet even started" and 1.0 indicates
	 * "all work is done". It is assumed that the parameter is submitted
	 * further to some progress bar widgets.
	 */
	void setProgress(final float howFar);
}
