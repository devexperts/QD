/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.tools.test;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import com.devexperts.qd.*;
import com.devexperts.qd.kit.*;
import com.devexperts.qd.ng.*;
import com.devexperts.qd.qtp.*;
import com.devexperts.qd.qtp.file.*;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.tools.AbstractTool;
import com.devexperts.tools.Tools;
import com.devexperts.services.Services;
import com.devexperts.test.ThreadCleanCheck;
import junit.framework.TestCase;

public class ConnectTest extends TestCase {
	private static final DataScheme SCHEME = QDFactory.getDefaultScheme();
	private static final String CONNECT = "connect";
	private static final String EXPECTED_FILE = "connect-test-expected.temp";
	private static final String OUTPUT_FILE = "connect-test-output.temp";
	private static final String SUBSCRIPTION_FILE = "connect-test-subscription.temp";

	private static final Random RNG = new Random();

	private static final DataRecord[] RECORDS = {
			SCHEME.findRecordByName("Quote"),
			SCHEME.findRecordByName("Trade.1hour"),
			SCHEME.findRecordByName("Trade.1min"),
			SCHEME.findRecordByName("Trade.30min")
	};
	private static final String[] SYMBOLS = {
			"AAPL",
			"IBM",
			"GOOG",
			"SPX"
	};
	private static final int NUMBER_OF_EVENTS = 500;

	// to fail from another thread
	private AtomicReference<String> failMessage;

	@Override
	protected void setUp() throws Exception {
		ThreadCleanCheck.before();
		failMessage = new AtomicReference<>();
		deleteFiles();
	}

	@Override
	public void tearDown() {
		deleteFiles();
		ThreadCleanCheck.after();
	}

	public void testSubscriptionByRecordsAndSymbols() {
		testSupport(this::testSubscriptionByRecordsAndSymbols);
	}

	public void testSubscriptionMirroring() {
		testSupport(this::testSubscriptionMirroring);
	}

	// common test cases for connect with different subscriptions
	private void testSupport(BiConsumer<String, String> testKind) {
		testKind.accept("Trade.1min", "AAPL");
		testKind.accept("Trade.5min", "AAPL,IBM");
		testKind.accept("*", "AAPL,IBM,GOOG,SPX");
		testKind.accept("Trade.30min", "GOOG,AAPL");
		testKind.accept("Trade*", "IBM");
		testKind.accept("Quote", "AAPL,SPX");
	}

	private void testSubscriptionByRecordsAndSymbols(String recordsSpec, String symbolsSpec) {
		try {
			RecordBuffer buffer = generate(NUMBER_OF_EVENTS);
			writeExpected(buffer, recordsSpec, symbolsSpec);
			buffer.rewind();

			int port = 4477 + RNG.nextInt(1000);
			QDEndpoint dataEndpoint = createDataSourceEndpoint("data-source-endpoint", port,
					Collections.singletonList(QDContract.STREAM));
			RecordBuffer subscription = createSubscription(recordsSpec, symbolsSpec);
			CountDownLatch subscribed = new CountDownLatch(1);
			CountDownLatch send = new CountDownLatch(1);
			distributeOnSubscriptionArrived(buffer, subscription, dataEndpoint, subscribed, send);

			doConnect(new String[] {
					"localhost:" + port,
					recordsSpec,
					symbolsSpec,
					"-q", // do not spam to stdout
					"-n", "connect-via-records-and-symbols",
					"-c", "stream", // no conflations
					"--tape", OUTPUT_FILE + "[format=text,time=none]"
			}, subscribed, send);

			dataEndpoint.close();

			assertNull(failMessage.get(), failMessage.get());
			assertEquals(getContent(Paths.get(EXPECTED_FILE)), getContent(Paths.get(OUTPUT_FILE)));
		} catch (InterruptedException ignored) {
		} catch (IOException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	private void testSubscriptionMirroring(String recordsSpec, String symbolsSpec) {
		try {
			RecordBuffer buffer = generate(NUMBER_OF_EVENTS);
			writeExpected(buffer, recordsSpec, symbolsSpec);
			buffer.rewind();

			int subscriptionPort = 4747 + RNG.nextInt(1000);
			RecordBuffer subscription = createSubscription(recordsSpec, symbolsSpec);
			QDEndpoint subscriptionSource = createSubscriptionSourceEndpoint(subscriptionPort, "subscription-source",
					Collections.singletonList(QDContract.STREAM), subscription);
			int dataPort = 5747 + RNG.nextInt(1000);
			QDEndpoint dataSource = createDataSourceEndpoint("data-source", dataPort,
					Collections.singletonList(QDContract.STREAM));

			CountDownLatch subscribed = new CountDownLatch(1);
			CountDownLatch send = new CountDownLatch(1);
			distributeOnSubscriptionArrived(buffer, subscription, dataSource, subscribed, send);

			doConnect(new String[] {
					"localhost:" + dataPort,
					"localhost:" + subscriptionPort,
					"-t", OUTPUT_FILE + "[format=text,time=none]",
					"-q",
					"-n", "connect-via-subscription-mirroring",
					"-c", "stream"
			}, subscribed, send);

			dataSource.close();
			subscriptionSource.close();
			assertNull(failMessage.get(), failMessage.get());
			assertEquals(getContent(Paths.get(EXPECTED_FILE)), getContent(Paths.get(OUTPUT_FILE)));
		} catch (InterruptedException ignored) {
		} catch (IOException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	private void distributeOnSubscriptionArrived(RecordBuffer buffer, RecordBuffer subscription, QDEndpoint dataSource,
			CountDownLatch subscribed, CountDownLatch send) {
		QDDistributor dataDistributor = dataSource.getStream().distributorBuilder().build();
		RecordProvider provider = dataDistributor.getAddedRecordProvider();
		RecordBuffer resultSubscription = new RecordBuffer(RecordMode.SUBSCRIPTION);
		NavigableSet<Item> allItems = parseSubscription(subscription);
		provider.setRecordListener(p -> {
			p.retrieveData(resultSubscription);
			NavigableSet<Item> items = parseSubscription(resultSubscription);
			if (allItems.equals(items)) {
				if (subscribed.getCount() == 1) {
					subscribed.countDown();
					// start sending events
					dataDistributor.process(buffer);
					send.countDown();
				}
			} else if (subscribed.getCount() == 0) {
				// received some redundant items
				if (!allItems.equals(items)) {
					failMessage.compareAndSet(null, "different subscriptions");
				}
			}
		});
	}

	private void doConnect(String[] args, CountDownLatch subscribed, CountDownLatch send)
			throws InterruptedException, IOException {
		AbstractTool connect = Tools.getTool(CONNECT);
		connect.parse(args);
		Services.startup();
		Thread connectThread = new Thread(connect::execute, "connect-thread");
		connectThread.start();

		long timeToSubscribe = 2000;
		long timeToWrite = 3000;
		waitForSubscriptionAndData(subscribed, send, timeToSubscribe, timeToWrite);
		List<Closeable> toClose = connect.closeOnExit();
		if (toClose != null) {
			for (Closeable closeable : toClose) {
				closeable.close();
			}
		}

		connectThread.join();
	}

	private void waitForSubscriptionAndData(CountDownLatch subscribed, CountDownLatch send,
			long timeToSubscribe, long timeToWrite) throws InterruptedException, IOException {
		// wait for subscription
		subscribed.await(timeToSubscribe, TimeUnit.MILLISECONDS);
		if (subscribed.getCount() == 1) {
			fail("not subscribed in " + timeToSubscribe + "ms.");
		}
		// wait for data
		send.await();
		Path outputFile = Paths.get(OUTPUT_FILE);
		Path expectedPath = Paths.get(EXPECTED_FILE);
		long expectedSize = Files.exists(expectedPath) ? Files.size(expectedPath) : 0;
		long startTime = System.currentTimeMillis();
		long currentTime = startTime;
		while ((!Files.exists(outputFile) || Files.size(outputFile) != expectedSize)
				&& currentTime < startTime + timeToWrite) {
			Thread.sleep(startTime + timeToWrite - currentTime);
			currentTime = System.currentTimeMillis();
		}
	}

	private QDEndpoint createDataSourceEndpoint(String name, int port,
			Collection<? extends QDCollector.Factory> contracts) {
		QDEndpoint dataSource = QDEndpoint.newBuilder()
				.withName(name)
				.withScheme(SCHEME)
				.withCollectors(contracts)
				.build();
		dataSource.addConnectors(MessageConnectors.createMessageConnectors(
				new AgentAdapter.Factory(dataSource, null), ":" + port, QDStats.VOID));
		dataSource.startConnectors();
		return dataSource;
	}

	private QDEndpoint createSubscriptionSourceEndpoint(int port, String name, Collection<QDContract> contracts,
			RecordSource subscription) {
		QDEndpoint subscriptionSource = QDEndpoint.newBuilder()
				.withName(name)
				.withScheme(SCHEME)
				.withCollectors(contracts)
				.build();
		subscriptionSource.addConnectors(MessageConnectors.createMessageConnectors(
				new DistributorAdapter.Factory(subscriptionSource, null), ":" + port, QDStats.VOID));
		subscriptionSource.startConnectors();
		QDAgent subscriptionAgent = subscriptionSource.getStream().agentBuilder().build();
		subscriptionAgent.addSubscription(subscription);
		return subscriptionSource;
	}

	private void writeExpected(RecordBuffer buffer, String recordsSpec, String symbolsSpec) {
		FileWriterParams.Default params = new FileWriterParams.Default();
		params.setFormat(FileFormat.TEXT);
		params.setTime(TimestampsType.NONE);
		RecordOnlyFilter records = RecordOnlyFilter.valueOf(recordsSpec, SCHEME);
		SymbolSetFilter symbols = SymbolSetFilter.valueOf(symbolsSpec, SCHEME);
		RecordBuffer filtered = new RecordBuffer(buffer.getMode());
		for (RecordCursor cursor; (cursor = buffer.next()) != null; ) {
			if (records.acceptRecord(cursor.getRecord())
					&& symbols.getSymbolSet().contains(cursor.getCipher(), cursor.getSymbol())) {
				filtered.append(cursor);
			}
		}
		try (FileWriterImpl expected = new FileWriterImpl(EXPECTED_FILE, SCHEME, params)) {
			expected.open();
			expected.addSendMessageType(MessageType.STREAM_DATA); // like connect does
			while (expected.visitData(filtered, MessageType.STREAM_DATA)) {
			}
		}
	}

	private RecordBuffer createSubscription(String recordsSpec, String symbolsSpec) {
		RecordOnlyFilter records = RecordOnlyFilter.valueOf(recordsSpec, SCHEME);
		SymbolSetFilter symbols = SymbolSetFilter.valueOf(symbolsSpec, SCHEME);
		RecordBuffer buffer = new RecordBuffer(RecordMode.SUBSCRIPTION);
		for (int rid = 0; rid < SCHEME.getRecordCount(); ++rid) {
			DataRecord record = SCHEME.getRecord(rid);
			if (records.acceptRecord(record)) {
				symbols.getSymbolSet().examine((cypher, symbol) -> buffer.add(record, cypher, symbol));
			}
		}
		return buffer;
	}

	private NavigableSet<Item> parseSubscription(RecordBuffer subscription) {
		subscription.rewind();
		NavigableSet<Item> items = new TreeSet<>();
		for (RecordCursor cursor; (cursor = subscription.next()) != null; ) {
			DataRecord record = cursor.getRecord();
			int cipher = cursor.getCipher();
			String symbol = cursor.getSymbol();
			items.add(new Item(record, cipher, symbol));
		}
		return items;
	}

	private RecordBuffer generate(int qty) {
		RecordBuffer buffer = new RecordBuffer(RecordMode.DATA);
		SymbolCodec codec = SCHEME.getCodec();
		for (int i = 0; i < qty; ++i) {
			int recordId = RNG.nextInt(RECORDS.length);
			int symbolId = RNG.nextInt(SYMBOLS.length);
			RecordCursor cursor = buffer.add(RECORDS[recordId], codec.encode(SYMBOLS[symbolId]), SYMBOLS[symbolId]);
			fillRecord(cursor, RECORDS[recordId]);
		}
		return buffer;
	}

	private void fillRecord(RecordCursor cursor, DataRecord record) {
		for (int i = 0; i < record.getIntFieldCount(); ++i) {
			DataIntField field = record.getIntField(i);
			if (field instanceof PlainIntField) {
				int val = RNG.nextInt(15000);
				if (RNG.nextBoolean()) {
					val = -val;
				}
				cursor.setInt(i, val);
			} else if (field instanceof CompactCharField) {
				int val = RNG.nextInt('Z' - 'A' + 1) + 'A';
				cursor.setInt(i, val);
			} else if (field instanceof DecimalField) {
				int val = RNG.nextInt(100500);
				cursor.setInt(i, val);
			} else {
				cursor.setInt(i, 0);
			}
		}
		for (int i = 0; i < record.getObjFieldCount(); ++i) {
			DataObjField field = record.getObjField(i);
			if (field instanceof StringField) {
				cursor.setObj(i, "" + RNG.nextDouble());
			} else {
				cursor.setObj(i, null);
			}
		}
	}

	private String getContent(Path path) {
		if (!Files.exists(path)) return "";
		StringBuilder content = new StringBuilder();
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			String line;
			while ((line = reader.readLine()) != null) {
				content.append(line).append('\n');
			}
		} catch (IOException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		return content.toString();
	}

	private void deleteFiles() {
		try {
			Files.deleteIfExists(Paths.get(EXPECTED_FILE));
			Files.deleteIfExists(Paths.get(OUTPUT_FILE));
			Files.deleteIfExists(Paths.get(SUBSCRIPTION_FILE));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static class Item implements Comparable<Item> {
		final DataRecord record;
		final int cipher;
		final String symbol;

		private Item(DataRecord record, int cipher, String symbol) {
			this.record = record;
			this.cipher = cipher;
			this.symbol = symbol;
		}

		@Override
		public int compareTo(Item other) {
			int cmp = Integer.compare(this.record.getId(), other.record.getId());
			if (cmp == 0) {
				cmp = Integer.compare(this.cipher, other.cipher);
			}
			if (cmp == 0) {
				if (this.symbol != null || other.symbol != null) {
					cmp = this.symbol != null ? other.symbol != null ? this.symbol.compareTo(other.symbol) : -1 : 1;
				}
			}
			return cmp;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Item item = (Item)o;

			if (cipher != item.cipher) return false;
			if (record != null ? !record.equals(item.record) : item.record != null) return false;
			if (symbol != null ? !symbol.equals(item.symbol) : item.symbol != null) return false;

			return true;
		}

		@Override
		public int hashCode() {
			int result = record != null ? record.hashCode() : 0;
			result = 31 * result + cipher;
			result = 31 * result + (symbol != null ? symbol.hashCode() : 0);
			return result;
		}

		@Override
		public String toString() {
			return "(" + record + "," + cipher + "," + symbol + ")";
		}
	}
}