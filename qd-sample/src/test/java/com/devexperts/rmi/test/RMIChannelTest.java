/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.rmi.test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import com.devexperts.logging.Logging;
import com.devexperts.rmi.*;
import com.devexperts.rmi.impl.RMIEndpointImpl;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.task.*;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import org.junit.*;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(TraceRunner.class)
public class RMIChannelTest {
	private static final Logging log = Logging.getLogging(RMIChannelTest.class);

	private static final int REQUEST_RUNNING_TIMEOUT = 10_000;

	private RMIEndpointImpl server;
	private RMIEndpointImpl client;
	private RMIEndpointImpl privateEndpoint;
	private RMIEndpointImpl remoteEndpoint;

	private List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
	private final TestThreadPool executor = new TestThreadPool(10, "RMIChannelTest", exceptions);
	private static Thread activeServerThread;

	@Before
	public void setUp() {
		ThreadCleanCheck.before();
		server = (RMIEndpointImpl) RMIEndpoint.newBuilder()
			.withName("Server")
			.withSide(RMIEndpoint.Side.SERVER)
			.build();
		client = (RMIEndpointImpl) RMIEndpoint.newBuilder()
			.withName("client")
			.withSide(RMIEndpoint.Side.CLIENT)
			.build();
		privateEndpoint = (RMIEndpointImpl) RMIEndpoint.newBuilder()
			.withName("privateClient")
			.withSide(RMIEndpoint.Side.CLIENT)
			.build();
		remoteEndpoint = (RMIEndpointImpl) RMIEndpoint.newBuilder()
			.withName("remoteServer")
			.withSide(RMIEndpoint.Side.SERVER)
			.build();
		server.getServer().setDefaultExecutor(executor);
		client.getClient().setDefaultExecutor(executor);
		privateEndpoint.getClient().setDefaultExecutor(executor);
		remoteEndpoint.getServer().setDefaultExecutor(executor);
		client.getClient().setRequestRunningTimeout(REQUEST_RUNNING_TIMEOUT);
		privateEndpoint.getClient().setRequestRunningTimeout(REQUEST_RUNNING_TIMEOUT);
	}

	@After
	public void tearDown() {
		privateEndpoint.close();
		remoteEndpoint.close();
		client.close();
		server.close();
		executor.shutdown();
		ThreadCleanCheck.after();
		assertTrue(exceptions.isEmpty());
	}

	private void connectDefault(int... ports) {
		NTU.connect(server, ":" + NTU.port(ports[0]));
		NTU.connect(client, NTU.LOCAL_HOST + ":" + NTU.port(ports[0]));
		if (ports.length == 2) {
			NTU.connect(remoteEndpoint, ":" + NTU.port(ports[1]));
			NTU.connect(privateEndpoint, NTU.LOCAL_HOST + ":" + NTU.port(ports[1]));
		}
	}

	// --------------------------------------------------

	private static final String MULTIPLICATIONS_SERVICE_NAME = "ManyMultiplications";
	public static final String CLIENT_CHANNEL = "ClientChannel";
	private static final String CHANNEL_HANDLER = "Progress";

	private static final RMIOperation<Void> INTERMEDIATE_RESULT_OPERATION = RMIOperation.valueOf(CLIENT_CHANNEL, Void.class, "intermediateResult", Long.class);
	public static final RMIOperation<Void> MULTIPLICATION_PROGRESS_OPERATION = RMIOperation.valueOf(CHANNEL_HANDLER, Void.class, "progress", int.class);

	private static RMIService<?> multiplications = new RMIService<Double>(MULTIPLICATIONS_SERVICE_NAME) {
		static final double HUNDRED = 100d;
		Random rnd = new Random();

		@Override
		public void openChannel(RMITask<Double> task) {
			task.setCancelListener(task1 -> {
				task1.cancel(RMIExceptionType.CANCELLED_DURING_EXECUTION);
				log.info("Server side: Cancelled task & UNPARK");
				isContinue = true;
				LockSupport.unpark(activeServerThread);
			});
			task.getChannel().addChannelHandler(new RMIService<Void>(CHANNEL_HANDLER) {
				@Override
				public void processTask(RMITask<Void> task) {
					log.info("Server side channel: message = " + task.getRequestMessage());
				}
			});
		}

		@Override
		public void processTask(RMITask<Double> task) {
			activeServerThread = Thread.currentThread();
			double temp = 1;
			double startMult = (double) task.getRequestMessage().getParameters().getObject()[0];
			double n = (int) task.getRequestMessage().getParameters().getObject()[1];
			int percentStat = (int) task.getRequestMessage().getParameters().getObject()[2];
			double countStat = n * (double) percentStat / HUNDRED;
			int percent = 0;
			RMIRequest<Void> request = null;
			for (int i = 0; i <= n; i += countStat) {
				if (task.isCompleted())
					return;
				// load emulation
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					log.info("Interrupted", e);
					return;
				}
				request = task.getChannel().createRequest(new RMIRequestMessage<>(
					RMIRequestType.ONE_WAY, MULTIPLICATION_PROGRESS_OPERATION, percent));
				percent += percentStat;
				temp *= Math.sqrt(Math.abs(rnd.nextDouble() * Math.sin(rnd.nextDouble())));
				isContinue = false;
				request.send();
				log.info("Server side: PARK");
				while (!isContinue && !task.isCompleted()) {
					log.info("Server side: ProgressTask pause.");
					LockSupport.park();
				}
				log.info("Server side: ProgressTask continue.");
			}
			assert request != null;
			double finalTemp = temp;
			request.setListener(req -> task.complete(finalTemp * startMult));
		}
	};

	@Test
	public void testProgressCalculations() throws InterruptedException {
		connectDefault(1);
		server.getServer().export(multiplications);
		final CountDownLatch processChannelLatch = new CountDownLatch(20);
		final CountDownLatch processResultLatch = new CountDownLatch(1);
		final int percentStat = 5;
		final AtomicInteger complete = new AtomicInteger(0);
		RMIRequest<Double> request = client.getClient().createRequest(null,
			RMIOperation.valueOf(MULTIPLICATIONS_SERVICE_NAME, double.class, "MULT", double.class, int.class, int.class), 0d, 1000000, percentStat);
		request.getChannel().addChannelHandler(new RMIService<Void>(CHANNEL_HANDLER) {
			@Override
			public void processTask(RMITask<Void> task) {
				RMIRequestMessage<Void> message = task.getRequestMessage();
				int percent = (int) message.getParameters().getObject()[0];
				processChannelLatch.countDown();
				log.info("percent = " + percent + " " + complete.get());
				assertEquals(percent, complete.getAndAdd(percentStat));
				log.info("UNPARK client");
				isContinue = true;
				LockSupport.unpark(activeServerThread);
				task.complete(null);
			}

		});
		request.setListener(request1 -> {
			try {
				if (!processChannelLatch.await(10, TimeUnit.SECONDS))
					fail();
			} catch (InterruptedException e) {
				fail();
			}
			log.info("COMPLETED! processChannelLatch=" + processChannelLatch.getCount());
			processResultLatch.countDown();
			log.info("processResultLatch = " + processResultLatch);
		});
		request.send();
		assertTrue(processChannelLatch.await(30, TimeUnit.SECONDS));
		if (!processResultLatch.await(30, TimeUnit.SECONDS))
			fail(new AssertionError(processResultLatch.getCount()).getMessage());
		assertEquals(request.getNonBlocking(), (Double)0d);
		log.info("processResultLatch = " + processResultLatch);
		log.info("processChannelLatch = " + processChannelLatch);
	}

	// --------------------------------------------------

	@Test
	public void testProgressCancel() throws InterruptedException {
		connectDefault(3);
		server.getServer().export(multiplications);
		final CountDownLatch processResultLatch = new CountDownLatch(1);
		final CountDownLatch processChannelLatch = new CountDownLatch(1);
		final int percentStat = 1;
		final RMIRequest<Double> request = client.getClient().createRequest(null,
			RMIOperation.valueOf(MULTIPLICATIONS_SERVICE_NAME, double.class, "MULT", double.class, int.class, int.class), 0d, 1000000, percentStat);
		final AtomicInteger process = new AtomicInteger(0);
		request.getChannel().addChannelHandler(new RMIService<Object>("*") {
			@Override
			public void processTask(RMITask<Object> task) {
				RMIRequestMessage<?> message = task.getRequestMessage();
				assertEquals(MULTIPLICATION_PROGRESS_OPERATION, task.getOperation());
				int percent = (int) message.getParameters().getObject()[0];
				log.info("=== " + percent + " task = " + task);
				if (percent == 20) {
					request.cancelOrAbort();
					processChannelLatch.countDown();
				}
				process.set(percent);
				log.info("UNPARK server");
				isContinue = true;
				LockSupport.unpark(activeServerThread);
				task.complete(null);
			}
		});
		request.setListener(new RMIRequestListener() {
			@Override
			public void requestCompleted(RMIRequest<?> request) {
				log.info("request completed");
				processResultLatch.countDown();
				if (request.getState() != RMIRequestState.FAILED)
					fail();
			}

			@Override
			public String toString() {
				return " I LISTENER";
			}
		});
		request.send();
		assertTrue(processChannelLatch.await(30, TimeUnit.SECONDS));
		assertTrue(processResultLatch.await(30, TimeUnit.SECONDS));
		log.info("process = " + process);
		assertTrue(process.get() < 50);
	}

	// --------------------------------------------------

	@Test
	public void testProgressDisconnect() throws InterruptedException {
		connectDefault(5);
		server.getServer().export(multiplications);
		final CountDownLatch processResult = new CountDownLatch(1);
		final CountDownLatch processChannelLatch = new CountDownLatch(1);
		final int percentStat = 10;
		final RMIRequest<Double> request = client.getClient().createRequest(null,
			RMIOperation.valueOf(MULTIPLICATIONS_SERVICE_NAME, double.class, "MULT", double.class, int.class, int.class), 0d, 10000000, percentStat);
		final AtomicInteger process = new AtomicInteger(0);
		request.getChannel().addChannelHandler(new RMIService<Object>("*") {
			@Override
			public void processTask(RMITask<Object> task) {
				log.info("Client side channel: 1 step (percent = " + task.getRequestMessage().getParameters().getObject()[0] + ")");
				assertEquals(task.getOperation(), MULTIPLICATION_PROGRESS_OPERATION);
				RMIRequestMessage<?> message = task.getRequestMessage();
				int percent = (int) message.getParameters().getObject()[0];
				process.set(percent);
				if (percent == 50) {
					log.info("Client side channel: Client disconnect");
					client.disconnect();
					processChannelLatch.countDown();
				}
				log.info("Client side channel: 2 step");
				task.complete(null);
				log.info("Client side channel: UNPARK");
				isContinue = true;
				LockSupport.unpark(activeServerThread);
			}
		});
		request.setListener(request1 -> {
			log.info("request complete");
			processResult.countDown();
		});
		request.send();
		assertTrue(processResult.await(30, TimeUnit.SECONDS));
		log.info("process = " + process);
		assertTrue(processChannelLatch.await(30, TimeUnit.SECONDS));
		log.info("process = " + process);
		assertEquals(50, process.get());
	}

	// --------------------------------------------------

	private static final String POWER_SERVICE_NAME = "calculationPower";
	private static final String SERVER_CHANNEL = "ServerChannel";
	private static final RMIOperation<Void> SERVER_UPDATE_OPERATION = RMIOperation.valueOf(SERVER_CHANNEL, Void.class, "update", long.class);
	private static final RMIOperation<Void> SERVER_STOP_OPERATION = RMIOperation.valueOf(SERVER_CHANNEL, Void.class, "stop", long.class, boolean.class);

	private final RMIService<Long> powerService = new RMIService<Long>(POWER_SERVICE_NAME) {
		volatile long factor;
		volatile long total = 1;
		volatile boolean stop = false;
		volatile long count = 1;

		@Override
		public void openChannel(RMITask<Long> task) {
			task.setCancelListener(task1 -> {
				task1.cancel(RMIExceptionType.CANCELLED_DURING_EXECUTION);
				log.info("Server side: Cancelled task & UNPARK");
				isContinue = true;
				LockSupport.unpark(activeServerThread);
			});
			task.getChannel().addChannelHandler(new RMIService<Object>(SERVER_CHANNEL) {
				@Override
				public void processTask(RMITask<Object> task) {
					log.info("ServerChanelMessage = " + task.getRequestMessage());
					boolean updateOp = task.getOperation().equals(SERVER_UPDATE_OPERATION);
					boolean stopOp = task.getOperation().equals(SERVER_STOP_OPERATION);
					assertTrue(updateOp || stopOp);
					RMIRequestMessage<?> message = task.getRequestMessage();
					if (stopOp) {
						stop = true;
						log.info("UNPARK server stop");
						isContinue = true;
						LockSupport.unpark(activeServerThread);
						return;
					}
					factor = (long) message.getParameters().getObject()[0];
					total = 1;
					count = 0;
					log.info("UNPARK server");
					isContinue = true;
					LockSupport.unpark(activeServerThread);
					task.complete(null);
				}
			});
		}

		@Override
		public void processTask(RMITask<Long> task) {
			activeServerThread = Thread.currentThread();
			factor = (long) task.getRequestMessage().getParameters().getObject()[0];
			log.info("Start process");
			RMIRequest<Void> request;
			while (!stop) {
				total *= factor;
				log.info("Count = " + count);
				if (count % 3 == 0) {
					request = task.getChannel().createRequest(new RMIRequestMessage<>(RMIRequestType.ONE_WAY,
						INTERMEDIATE_RESULT_OPERATION, total));
					isContinue = false;
					request.send();
					while (!isContinue && !task.isCompleted()) {
						log.info("ProgressTask pause.");
						LockSupport.park();
					}
					log.info("ProgressTask continue.");
				}
				count++;
			}
			task.complete(total);
		}
	};


	// --------------------------------------------------

	@Test
	public void testIntermediateResultChannel() throws InterruptedException, RMIException {
		connectDefault(7);
		server.getServer().export(powerService);
		intermediateResultChannel();
	}

	// --------------------------------------------------

	private static volatile boolean isContinue = false;

	@Test
	public void testChannelForward() throws RMIException, InterruptedException {
		connectDefault(9, 11);
		remoteEndpoint.getServer().export(powerService);
		server.getServer().export(privateEndpoint.getClient().getService("*"));
		intermediateResultChannel();
	}

	private void intermediateResultChannel() throws InterruptedException, RMIException {
		final CountDownLatch firstProcessChannelLatch = new CountDownLatch(3);
		final CountDownLatch secondProcessChannelLatch = new CountDownLatch(3);
		final CountDownLatch processResultLatch = new CountDownLatch(1);
		RMIRequest<Long> request = client.getClient().createRequest(null,
			RMIOperation.valueOf(POWER_SERVICE_NAME, long.class, "calc", long.class), 2L);
		final AtomicLong step = new AtomicLong(8);
		final AtomicLong expectedResult = new AtomicLong(8);
		RMIChannel channel = request.getChannel();
		channel.addChannelHandler(new RMIService<Object>("*") {
			@Override
			public void processTask(RMITask<Object> task) {
				assertEquals(task.getOperation(), INTERMEDIATE_RESULT_OPERATION);
				RMIRequestMessage<?> message = task.getRequestMessage();
				log.info("ClientChanelMessage = " + message);
				long result = (long) message.getParameters().getObject()[0];
				if (expectedResult.get() == result) {
					expectedResult.set(result * step.get());
					log.info("channel: new expected result = " + expectedResult.get());
					if (result % 2 == 0) {
						firstProcessChannelLatch.countDown();
						if (firstProcessChannelLatch.getCount() != 0) {
							log.info("UNPARK first");
							isContinue = true;
							LockSupport.unpark(activeServerThread);
						}
					} else {
						secondProcessChannelLatch.countDown();
						if (secondProcessChannelLatch.getCount() != 0) {
							log.info("UNPARK second");
							isContinue = true;
							LockSupport.unpark(activeServerThread);
						}
					}
				}
				task.complete(null);
			}

		});
		request.setListener(request1 -> {
			log.info("request response = " + request1);
			if (firstProcessChannelLatch.getCount() != 0 && secondProcessChannelLatch.getCount() != 0)
				fail();
			processResultLatch.countDown();
		});
		request.send();
		log.info("------ first step ------");
		firstProcessChannelLatch.await(10, TimeUnit.SECONDS);

		log.info("------ second step ------");
		expectedResult.set(27);
		log.info("second step: expected result = " + expectedResult.get());
		channel.createRequest(new RMIRequestMessage<>(RMIRequestType.ONE_WAY, SERVER_UPDATE_OPERATION, 3L)).send();
		step.set(27);
		assertTrue(secondProcessChannelLatch.await(10, TimeUnit.SECONDS));
		channel.createRequest(new RMIRequestMessage<>(RMIRequestType.ONE_WAY, SERVER_STOP_OPERATION, 3L, true)).send();
		assertTrue(processResultLatch.await(10, TimeUnit.SECONDS));
		long result = request.getBlocking();
		assertEquals((long) Math.pow(3, 9), result);
		System.out.println("THE END!");
	}

	// --------------------------------------------------

	@Test
	public void testChannelOpenMethod() throws RMIException, InterruptedException {
		connectDefault(13);
		server.getServer().export(new ChannelServiceImpl(42), ChannelService.class);

		RMIRequest<Void> request = client.getClient().getPort(null).createRequest(
			RMIOperation.valueOf(ChannelService.class, void.class, "startChannel"));
		RMIChannel channel = request.getChannel();
		request.send();
		RMIRequest<?> channelRequest = channel.createRequest(
			RMIOperation.valueOf(ChannelService.class, int.class, "getValue"));
		channelRequest.send();
		Integer a = (Integer) channelRequest.getBlocking();
		assertEquals(42, (int) a);
		assertEquals(request.getNonBlocking(), null);
		channelRequest = channel.createRequest(
			RMIOperation.valueOf(ChannelService.class, void.class, "finishChannel"));
		channelRequest.send();
		assertEquals(channelRequest.getBlocking(), null);
		assertEquals(request.getBlocking(), null);
	}

	// --------------------------------------------------

	@Test
	public void testChannelSendBeforeRequest() throws RMIException, InterruptedException {
		connectDefault(15);
		server.getServer().export(new ChannelServiceImpl(42), ChannelService.class);

		RMIRequest<Void> request = client.getClient().getPort(null).createRequest(
			RMIOperation.valueOf(ChannelService.class, void.class, "startChannel"));
		RMIChannel channel = request.getChannel();
		RMIRequest<?> channelRequest = channel.createRequest(
			RMIOperation.valueOf(ChannelService.class, int.class, "getValue"));
		channelRequest.send();
		assertEquals(channelRequest.getNonBlocking(), null);
		request.send();
		Integer a = (Integer) channelRequest.getBlocking();
		assertEquals(42, (int) a);
		assertEquals(request.getNonBlocking(), null);
		channelRequest = channel.createRequest(
			RMIOperation.valueOf(ChannelService.class, void.class, "finishChannel"));
		channelRequest.send();
		assertEquals(channelRequest.getBlocking(), null);
		assertEquals(request.getBlocking(), null);
	}

	@SuppressWarnings("unused")
	interface ChannelService {
		void startChannel();
		int getValue();
		void finishChannel();
	}

	private static class ChannelServiceImpl implements ChannelService, RMIChannelSupport<Object> {

		RMITask<?> task;
		final int value;

		ChannelServiceImpl(int value) {
			this.value = value;
		}

		@Override
		public void startChannel() {
			task = RMITask.current();
			task.setCancelListener(task1 -> {
				log.info("TASK CANCEL");
				task1.cancel();
			});
			task.suspend((RMITaskCancelListener) task1 -> {
				log.info("SUSPEND CANCEL");
				task1.cancel();
			});
		}

		@Override
		public int getValue() {
			return value;
		}

		@Override
		public void finishChannel() {
			RMITask.current().complete(null);
			task.complete(null);
		}

		@Override
		public void openChannel(RMITask<Object> task) {
			task.getChannel().addChannelHandler(this, ChannelService.class);
		}
	}

	// --------------------------------------------------

	@Test
	public void testOpenChannelError() {
		connectDefault(17);
		server.getServer().export(new OpenChannelError());

		RMIRequest<Integer> request = client.getClient().getPort(null).createRequest(
			RMIOperation.valueOf(OpenChannelError.NAME, int.class, "method"));
		RMIChannel channel = request.getChannel();
		RMIRequest<Integer> channelRequest = channel.createRequest(
			RMIOperation.valueOf(OpenChannelError.NAME, int.class, "channelMethod"));
		channelRequest.send();
		assertEquals(channelRequest.getNonBlocking(), null);
		request.send();
		try {
			request.getBlocking();
			fail();
		} catch (RMIException e) {
			if (e.getType() != RMIExceptionType.EXECUTION_ERROR)
				fail();
			assertEquals("open channel error", e.detail.getMessage());
		}
		try {
			channelRequest.getBlocking();
			fail();
		} catch (RMIException e) {
			if (e.getType() != RMIExceptionType.CHANNEL_CLOSED)
				fail(e.getType().getMessage());
		}
	}

	private static class OpenChannelError extends RMIService<Integer> {

		static final String NAME = "openChannelError";

		OpenChannelError() {
			super(NAME);
		}

		@Override
		public void processTask(RMITask<Integer> task) {
			task.complete(5);
		}

		@Override
		public void openChannel(RMITask<Integer> task) {
			throw new RuntimeException("open channel error");
		}
	}

	// ---------------------------------------------------------

	private static final int REQUEST_COUNT = 6;
	private static class ChannelOneWayServiceImpl implements ChannelService, RMIChannelSupport<Object> {

		@Override
		public void startChannel() {
			isStart = true;
		}

		@Override
		public int getValue() {
			return 0;
		}

		@Override
		public void finishChannel() {}

		@Override
		public void openChannel(RMITask<Object> task) {
			isOpen = true;
		}
	}

	private static volatile boolean isOpen;
	private static volatile boolean isStart;

	@Test
	public void testOpenChannelForOneWayRequest() throws InterruptedException {
		connectDefault(20);
		isOpen = false;
		isStart = true;
		server.getServer().export(new ChannelOneWayServiceImpl(), ChannelService.class);
		CountDownLatch reqComplete = new CountDownLatch(REQUEST_COUNT);
		RMIRequest<Void> request = null;
		for (int i = 0; i < REQUEST_COUNT; i++) {
			request = client.getClient().getPort(null).createRequest(new RMIRequestMessage<>(
				RMIRequestType.ONE_WAY, RMIOperation.valueOf(ChannelService.class, void.class, "startChannel")));
			request.setListener(req -> reqComplete.countDown());
			request.send();
			System.out.println(request.getChannel());
		}
		assertTrue(reqComplete.await(10, TimeUnit.SECONDS));
		assertEquals(RMIChannelState.CLOSED, request.getChannel().getState());
		assertTrue(isStart);
		assertFalse(isOpen);
	}
}
