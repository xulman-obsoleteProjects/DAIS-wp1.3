/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2017, Vladimír Ulman
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.mpicbg.ulman;

import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import de.mpicbg.ulman.imgtransfer.ImgTransfer;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;

public class TestPacker
{
	public static void main(final String... args)
	{
		final ImageJ ij = new net.imagej.ImageJ();
		//ij.ui().showUI();

		try {
			//read the input image
/*
			final SCIFIOConfig openingRegime = new SCIFIOConfig();
			openingRegime.imgOpenerSetImgModes(ImgMode.ARRAY);
			ImgOpener imgOpener = new ImgOpener();
			@SuppressWarnings("rawtypes")
			ImgPlus imgPlus = imgOpener.openImgs("/Users/ulman/DATA/combinedGT__009.tif",openingRegime).get(0);

/*
			Dataset ds = ij.dataset().open("/Users/ulman/DATA/combinedGT__009.tif");
			@SuppressWarnings("rawtypes")
			ImgPlus imgPlus = ds.getImgPlus();
*/

			Img<UnsignedShortType> img =
					new ArrayImgFactory(new UnsignedShortType()).create(new int[] {50, 50});
			Cursor<UnsignedShortType> c = img.cursor();
			while (c.hasNext()) c.next().set(97);
			RandomAccess<UnsignedShortType> r = img.randomAccess();
			int[] pos = new int[2];
			for (int i=0;i < 25; ++i)
			{
				pos[0]=i;
				pos[1]=i;
				r.setPosition(pos);
				r.get().set(200);

				pos[0]=49-i;
				r.setPosition(pos);
				r.get().set(150);
			}
			for (int i=25;i < 50; ++i)
			{
				pos[0]=i;
				pos[1]=i;
				r.setPosition(pos);
				r.get().set(205);

				pos[0]=49-i;
				r.setPosition(pos);
				r.get().set(155);
			}
			ImgPlus<UnsignedShortType> imgPlus = new ImgPlus<>(img);

/*
			ImgSaver is = new ImgSaver();
			is.saveImg("/Users/ulman/DATA/removeMe.tif", imgPlus);
*/

			//init the writing socket (but not bind it)
			ZMQ.Context zmqContext = ZMQ.context(1);
			ZMQ.Socket writerSocket = zmqContext.socket(SocketType.PUSH);
			writerSocket.connect("tcp://localhost:54545");

			//start up the packer class
			//ImgTransfer.packAndSend(imgPlus, writerSocket, 60, null);

			//TODO: send request to send image and wait for "ready" coming back

			//clean up
			writerSocket.close();
			zmqContext.term();
		}
		catch (Exception e) {
			//ij.log().error(e.getMessage());
			System.out.println(e.getMessage());
		}

		//and quit
		//ij.appEvent().quit();
	}
}
