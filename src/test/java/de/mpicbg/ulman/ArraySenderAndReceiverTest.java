package de.mpicbg.ulman;

import org.junit.Test;
import org.zeromq.ZMQ;

import java.util.Arrays;
import java.util.function.BiConsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by arzt on 13.07.17.
 */
public class ArraySenderAndReceiverTest {

	@Test
	public void testFewBytes() {
		testSendAndReceiveArray(new byte[]{1,2,3,4});
	}

	@Test
	public void testFewShorts() {
		testSendAndReceiveArray(new short[]{1000,2000,3000,4000});
	}

	@Test
	public void testFewFloats() {
		testSendAndReceiveArray(new float[]{0.1f,0.2f,0.3f,0.4f});
	}

	@Test
	public void testFewDoubles() {
		testSendAndReceiveArray(new double[]{0.1,0.2,0.3,0.4});
	}

	@Test
	public void testManyBytes() {
		testSendAndReceiveArray(manyBytes());
	}

	@Test
	public void testManyShorts() {
		testSendAndReceiveArray(manyShorts());
	}

	@Test
	public void testManyFloats() {
		testSendAndReceiveArray(manyFloats());
	}

	@Test
	public void testManyDoubles() {
		testSendAndReceiveArray(manyDoubles());
	}

	private void testSendAndReceiveArray(Object array) {
		setupSocketsAndRun((pull, push) -> {
			String header = "Header";

			push.send(header, ZMQ.SNDMORE);
			ArraySender.sendArray(array, push, false);

			String headerReceived = pull.recvStr();

			Object arrayReceived = ArrayReceiver.createArrayOfSameSizeAndType(array);
			ArrayReceiver.receiveArray(arrayReceived, pull);

			assertEquals(header, headerReceived);
			assertTrue(Arrays.deepEquals(new Object[]{array}, new Object[]{arrayReceived}));
		});
	}

	private void setupSocketsAndRun(BiConsumer<ZMQ.Socket, ZMQ.Socket> run) {
		ZMQ.Context context = ZMQ.context(1);
		ZMQ.Socket pull = null;
		ZMQ.Socket push = null;
		try {
			pull = context.socket(ZMQ.PULL);
			int port = pull.bindToRandomPort("tcp://localhost");
			push = context.socket(ZMQ.PUSH);
			push.connect("tcp://localhost:" + port);
			run.accept(pull, push);
		}
		finally {
			if(pull != null) pull.close();
			if(push != null) push.close();
			context.term();
		}
	}

	private byte[] manyBytes() {
		byte[] values = new byte[3333];
		for (int i = 0; i < values.length; i++)
			values[i] = (byte) (i * 3);
		return values;
	}

	private short[] manyShorts() {
		short[] values = new short[3333];
		for (int i = 0; i < values.length; i++)
			values[i] = (short) (i * 3);
		return values;
	}

	private float[] manyFloats() {
		float[] values = new float[3333];
		for (int i = 0; i < values.length; i++)
			values[i] = i * 0.1f;
		return values;
	}

	private double[] manyDoubles() {
		double[] values = new double[3333];
		for (int i = 0; i < values.length; i++)
			values[i] = i * 0.1;
		return values;
	}
}
