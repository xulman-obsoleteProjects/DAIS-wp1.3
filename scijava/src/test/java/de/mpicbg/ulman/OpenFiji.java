/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman;

import net.imagej.ImageJ;

public class OpenFiji
{
	public static void main(final String... args)
	{
		final ImageJ ij = new net.imagej.ImageJ();
		ij.ui().showUI();
		//ij.command().run(machineGTViaMarkers.class, true);

/*
		try {
			//start up the worker class
			final machineGTViaMarkers_Worker Worker
				= new machineGTViaMarkers_Worker(ij.op(),ij.log());

			//do the job
			Worker.work(args);
		}
		catch (ImgIOException e) {
			ij.log().error("machineGTViaMarkers error: "+e);
		}

		//and quit
		ij.appEvent().quit();
*/
	}
}
