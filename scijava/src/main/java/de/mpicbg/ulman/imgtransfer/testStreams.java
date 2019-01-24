package de.mpicbg.ulman.imgtransfer;

import java.io.*;

import net.imagej.ImgPlus;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;

public class testStreams
{
	public static void main(String... args)
	{
		testImgPlus();
		testQueingOfObjectsInAStream();
	}


	static
	void testImgPlus()
	{
		class myLogger implements ProgressCallback
		{
			@Override
			public void info(String msg) {
				System.out.println(msg);
			}

			@Override
			public void setProgress(float howFar) {
				System.out.println("progress: "+howFar);
			}
		}

		try {
			ImgPlus<UnsignedShortType> imgP = new ImgPlus<>( testStreams.createFakeImg() );

			final ObjectOutputStream os = new ObjectOutputStream( new FileOutputStream("/tmp/out.dat") );
			ImgStreamer.packAndSend(imgP, os, new myLogger());
			os.close();

			final ObjectInputStream is = new ObjectInputStream( new FileInputStream("/tmp/out.dat") );
			ImgPlus<?> imgPP = ImgStreamer.receiveAndUnpack(is, new myLogger());
			is.close();

			System.out.println("got this image: "+imgPP.getImg().toString());
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	static
	void testQueingOfObjectsInAStream()
	{
		try {
			final ObjectOutputStream os = new ObjectOutputStream( new FileOutputStream("/tmp/out.dat") );
			os.writeUTF("ahoj");
			os.writeUTF("clovece");
			os.close();

			final ObjectInputStream is = new ObjectInputStream( new FileInputStream("/tmp/out.dat") );
			System.out.println(is.readUTF());
			System.out.println(is.readUTF());
			is.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	static
	Img<UnsignedShortType> createFakeImg()
	{
		Img<UnsignedShortType> img
				= new ArrayImgFactory<UnsignedShortType>().create(new long[] {200,100,1}, new UnsignedShortType());

		Cursor<UnsignedShortType> imgC = img.cursor();
		int counter = 0;
		while (imgC.hasNext())
			imgC.next().set(counter++);

		return img;
	}
}
