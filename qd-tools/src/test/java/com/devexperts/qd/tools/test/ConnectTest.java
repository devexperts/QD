/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools.test;

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.kit.CompactCharField;
import com.devexperts.qd.kit.DecimalField;
import com.devexperts.qd.kit.PlainIntField;
import com.devexperts.qd.kit.RecordOnlyFilter;
import com.devexperts.qd.kit.StringField;
import com.devexperts.qd.kit.SymbolSetFilter;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.qtp.file.FileFormat;
import com.devexperts.qd.qtp.file.FileWriterImpl;
import com.devexperts.qd.qtp.file.FileWriterParams;
import com.devexperts.qd.qtp.file.TimestampsType;
import com.devexperts.qd.qtp.socket.ServerSocketTestHelper;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.tools.AbstractTool;
import com.devexperts.qd.tools.Tools;
import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.promise.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NavigableSet;
import java.util.Random;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class ConnectTest {
    private static final DataScheme SCHEME = QDFactory.getDefaultScheme();
    private static final String CONNECT = "connect";
    private static final long CONNECTOR_INIT_TIMEOUT = 3_000; // max wait time for connector init (millis)

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
    // Poll period for operations checking effect of a parallel process execution (millis)
    private static final int POLL_PERIOD = 10;

    // to fail from another thread
    private AtomicReference<String> failMessage;

    // unique ID of the test invocation
    private String testID;

    private String expectedFile;
    private String outputFile;

    // test parameters
    private final String recordsSpec;
    private final String symbolsSpec;

    @Parameters
    public static Collection<Object[]> params() {
        return Arrays.asList(new Object[][] {
            // { recordsSpec, symbolsSpec }
            {"Trade.1min", "AAPL"},
            {"Trade.5min", "AAPL,IBM"},
            {"*", "AAPL,IBM,GOOG,SPX"},
            {"Trade.30min", "GOOG,AAPL"},
            {"Trade*", "IBM"},
            {"Quote", "AAPL,SPX"}
        });
    }

    public ConnectTest(String recordsSpec, String symbolsSpec) {
        this.recordsSpec = recordsSpec;
        this.symbolsSpec = symbolsSpec;
    }

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
        testID = UUID.randomUUID().toString();
        failMessage = new AtomicReference<>();
        expectedFile = testID + "-connect-test-expected.temp";
        outputFile = testID + "-connect-test-output.temp";
        deleteFiles();
    }

    @After
    public void tearDown() {
        deleteFiles();
        ThreadCleanCheck.after();
    }

    @Test
    public void testSubscriptionByRecordsAndSymbols() {
        try {
            RecordBuffer buffer = generate(NUMBER_OF_EVENTS);
            writeExpected(buffer, recordsSpec, symbolsSpec);
            buffer.rewind();

            Promise<Integer> dataPort = ServerSocketTestHelper.createPortPromise(testID + "-data-cn");
            QDEndpoint dataEndpoint = createDataSourceEndpoint("data-source-endpoint", testID + "-data-cn",
                Collections.singletonList(QDContract.STREAM));
            RecordBuffer subscription = createSubscription(recordsSpec, symbolsSpec);
            CountDownLatch subscribed = new CountDownLatch(1);
            CountDownLatch send = new CountDownLatch(1);
            distributeOnSubscriptionArrived(buffer, subscription, dataEndpoint, subscribed, send);

            doConnect(new String[] {
                    "localhost:" + awaitPort(dataPort),
                    recordsSpec,
                    symbolsSpec,
                    "-q", // do not spam to stdout
                    "-n", "connect-via-records-and-symbols",
                    "-c", "stream", // no conflations
                    "--tape", outputFile + "[format=text,time=none]"
            }, subscribed, send);

            dataEndpoint.close();

            assertNull(failMessage.get(), failMessage.get());
            assertEquals(getContent(Paths.get(expectedFile)), getContent(Paths.get(outputFile)));
        } catch (InterruptedException ignored) {
        } catch (IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testSubscriptionMirroring() {
        try {
            RecordBuffer buffer = generate(NUMBER_OF_EVENTS);
            writeExpected(buffer, recordsSpec, symbolsSpec);
            buffer.rewind();

            RecordBuffer subscription = createSubscription(recordsSpec, symbolsSpec);
            Promise<Integer> subscriptionPort = ServerSocketTestHelper.createPortPromise(testID + "-sub-cn" );
            QDEndpoint subscriptionSource = createSubscriptionSourceEndpoint("subscription-source", testID + "-sub-cn",
                Collections.singletonList(QDContract.STREAM), subscription);
            Promise<Integer> dataPort = ServerSocketTestHelper.createPortPromise(testID + "-data-cn");
            QDEndpoint dataSource = createDataSourceEndpoint("data-source", testID + "-data-cn",
                Collections.singletonList(QDContract.STREAM));

            CountDownLatch subscribed = new CountDownLatch(1);
            CountDownLatch send = new CountDownLatch(1);
            distributeOnSubscriptionArrived(buffer, subscription, dataSource, subscribed, send);

            doConnect(new String[] {
                    "localhost:" + awaitPort(dataPort),
                    "localhost:" + awaitPort(subscriptionPort),
                    "-t", outputFile + "[format=text,time=none]",
                    "-q",
                    "-n", "connect-via-subscription-mirroring",
                    "-c", "stream"
            }, subscribed, send);

            dataSource.close();
            subscriptionSource.close();
            assertNull(failMessage.get(), failMessage.get());
            assertEquals(getContent(Paths.get(expectedFile)), getContent(Paths.get(outputFile)));
        } catch (InterruptedException ignored) {
        } catch (IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    private void distributeOnSubscriptionArrived(RecordBuffer buffer, RecordBuffer subscription, QDEndpoint dataSource,
        CountDownLatch subscribed, CountDownLatch send)
    {
        QDDistributor dataDistributor = dataSource.getStream().distributorBuilder().build();
        RecordProvider provider = dataDistributor.getAddedRecordProvider();
        RecordBuffer resultSubscription = new RecordBuffer(RecordMode.SUBSCRIPTION);
        NavigableSet<Item> allItems = parseSubscription(subscription);
        provider.setRecordListener(p -> {
            p.retrieve(resultSubscription);
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
        throws InterruptedException, IOException
    {
        AbstractTool connect = Tools.getTool(CONNECT);
        connect.parse(args);
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
        long timeToSubscribe, long timeToWrite) throws InterruptedException, IOException
    {
        // wait for subscription
        subscribed.await(timeToSubscribe, TimeUnit.MILLISECONDS);
        if (subscribed.getCount() == 1) {
            fail("not subscribed in " + timeToSubscribe + "ms.");
        }
        // wait for data
        send.await();
        Path outputPath = Paths.get(outputFile);
        Path expectedPath = Paths.get(expectedFile);
        // Expected output size. Output file may be a bit shorter than expected on finish until dumping tool is closed.
        // If outputFile is larger than expected then test is failed anyway.
        long waitSize = Files.exists(expectedPath) ? Math.max(Files.size(expectedPath) - 10, 0) : -1;
        long startTime = System.currentTimeMillis();
        long currentTime = startTime;
        do {  // give a chance to receive some data even when output is not expected
            Thread.sleep(Math.min(startTime + timeToWrite - currentTime, POLL_PERIOD));
            currentTime = System.currentTimeMillis();
        } while (fileSize(outputPath) < waitSize && currentTime < startTime + timeToWrite);
    }

    /**
     * Get a size of file or -1 if file does not exists
     * @param file - a file to be checked
     * @return size of file in bytes or -1 if file was not found
     * @throws IOException
     */
    private long fileSize(Path file) throws IOException {
        return Files.exists(file) ? Files.size(file) : -1;
    }

    private int awaitPort(Promise<Integer> portPromise) {
        return portPromise.await(CONNECTOR_INIT_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    private QDEndpoint createDataSourceEndpoint(String name, String connectorName,
        Collection<? extends QDCollector.Factory> contracts)
    {
        QDEndpoint dataSource = QDEndpoint.newBuilder()
                .withName(name)
                .withScheme(SCHEME)
                .withCollectors(contracts)
                .build();
        dataSource.addConnectors(MessageConnectors.createMessageConnectors(
                new AgentAdapter.Factory(dataSource, null), ":0[name=" + connectorName + "]", QDStats.VOID));
        dataSource.startConnectors();
        return dataSource;
    }

    private QDEndpoint createSubscriptionSourceEndpoint(String name, String connectorName,
        Collection<QDContract> contracts, RecordSource subscription)
    {
        QDEndpoint subscriptionSource = QDEndpoint.newBuilder()
                .withName(name)
                .withScheme(SCHEME)
                .withCollectors(contracts)
                .build();
        subscriptionSource.addConnectors(MessageConnectors.createMessageConnectors(
            new DistributorAdapter.Factory(subscriptionSource, null), ":0[name=" + connectorName + "]", QDStats.VOID));
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
        try (FileWriterImpl expected = new FileWriterImpl(expectedFile, SCHEME, params)) {
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
            Files.deleteIfExists(Paths.get(expectedFile));
            Files.deleteIfExists(Paths.get(outputFile));
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

            Item item = (Item) o;

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
