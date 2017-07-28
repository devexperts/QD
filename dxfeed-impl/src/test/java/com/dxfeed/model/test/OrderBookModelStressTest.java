/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.model.test;

import java.util.*;
import java.util.concurrent.*;

import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.*;
import com.dxfeed.event.market.*;
import com.dxfeed.model.ObservableListModelListener;
import com.dxfeed.model.market.OrderBookModel;
import com.dxfeed.model.market.OrderBookModelFilter;
import junit.framework.TestCase;

/**
 * This test is designed to detect "hangs" and NPEs in OrderBookModel.
 */
public class OrderBookModelStressTest extends TestCase {
    public static final String[] SYMBOLS = {"GOOG", "AAPL", "IBM"};
    public static final int N_SECONDS = 3;
    public static final int MAX_PUB_EVENTS = 10;

    private DXEndpoint endpoint;
    private ExecutorService executor;
    private PublisherThread publisherThread;
    private volatile Thread processingThread;
    private volatile long lastChangeTime;
    private final ArrayBlockingQueue<Throwable> exception = new ArrayBlockingQueue<>(1);

    private final Thread.UncaughtExceptionHandler uncaughtExceptionHandler = (t, e) -> exception.offer(e);

    @Override
    protected void setUp() throws Exception {
        ThreadCleanCheck.before();
        executor = Executors.newFixedThreadPool(1, r -> {
            processingThread = new Thread(r);
            processingThread.setUncaughtExceptionHandler(uncaughtExceptionHandler);
            return processingThread;
        });
        endpoint = DXEndpoint.newBuilder().withRole(DXEndpoint.Role.LOCAL_HUB).build()
            .executor(executor);
    }

    @Override
    protected void tearDown() throws Exception {
        endpoint.close();
        if (publisherThread != null)
            publisherThread.interrupt();
        if (executor != null)
            executor.shutdown();
        ThreadCleanCheck.after();
    }

    public void testOrderBookUnderStress() throws InterruptedException {
        // connect order books to feed
        DXFeed feed = endpoint.getFeed();
        for (String symbol : SYMBOLS) {
            for (OrderBookModelFilter filter : EnumSet.allOf(OrderBookModelFilter.class)) {
                final OrderBookModel model = new OrderBookModel();
                model.setSymbol(symbol);
                model.setFilter(filter);

                ObservableListModelListener<Order> listener = change -> lastChangeTime = System.currentTimeMillis();
                model.getBuyOrders().addListener(listener);
                model.getSellOrders().addListener(listener);
                model.attach(feed);
            }
        }
        // ready to start
        long startTime = System.currentTimeMillis();
        lastChangeTime = startTime;
        // create publisher
        publisherThread = new PublisherThread(endpoint.getPublisher());
        publisherThread.setPriority(3); // lower priority, so that processing catches up
        publisherThread.setUncaughtExceptionHandler(uncaughtExceptionHandler);
        publisherThread.start();
        // Wait for N_SECONDS, must always have recent changes (in past second)
        for (int i = 0; i < N_SECONDS; i++) {
            Throwable t = exception.poll(1, TimeUnit.SECONDS);
            if (t != null) {
                t.printStackTrace();
                fail("Fails: " + t);
            }
            long now = System.currentTimeMillis();
            if (lastChangeTime < now - 1000) {
                // hangs
                System.out.println("Hangs at stack trace:");
                StackTraceElement[] stackTrace = processingThread.getStackTrace();
                for (StackTraceElement element : stackTrace) {
                    System.out.println("\tat " + element);
                }
                fail("Hangs");
            }
        }
    }

    static class PublisherThread extends Thread {
        private DXPublisher publisher;

        PublisherThread(DXPublisher publisher) {
            this.publisher = publisher;
        }

        @Override
        public void run() {
            Random rnd = new Random(1);
            List<MarketEvent> events = new ArrayList<>();
            while (!Thread.interrupted()) {
                int n = rnd.nextInt(MAX_PUB_EVENTS) + 1;
                for (int i = 0; i < n; i++)
                    events.add(randomEvent(rnd));
                publisher.publishEvents(events);
                events.clear();
            }
        }

        private MarketEvent randomEvent(Random rnd) {
            String symbol = SYMBOLS[rnd.nextInt(SYMBOLS.length)];
            switch (rnd.nextInt(3)) {
            case 0:
                Quote quote = new Quote(symbol);
                quote.setBidPrice(randomPrice(rnd));
                quote.setBidSize(randomSize(rnd));
                quote.setBidExchangeCode(randomExchange(rnd));
                quote.setAskPrice(randomPrice(rnd));
                quote.setAskSize(randomSize(rnd));
                quote.setAskExchangeCode(randomExchange(rnd));
                return quote;
            case 1:
                Quote regQuote = new Quote(symbol + "&" + randomExchange(rnd));
                regQuote.setBidPrice(randomPrice(rnd));
                regQuote.setBidSize(randomSize(rnd));
                regQuote.setAskPrice(randomPrice(rnd));
                regQuote.setAskSize(randomSize(rnd));
                return regQuote;
            case 2:
                Order order = new Order(symbol);
                order.setOrderSide(rnd.nextBoolean() ? Side.BUY : Side.SELL);
                order.setIndex(rnd.nextInt(100));
                order.setPrice(randomPrice(rnd));
                order.setSize(randomSize(rnd));
                order.setExchangeCode(randomExchange(rnd));
                return order;
            default:
                throw new AssertionError();
            }
        }

        private char randomExchangeOrComposite(Random rnd) {
            return rnd.nextInt(20) == 0 ? (char) 0 : randomExchange(rnd);
        }

        private char randomExchange(Random rnd) {
            return (char) (rnd.nextInt(26) + 'A');
        }

        private long randomSize(Random rnd) {
            return rnd.nextInt(10);
        }

        private double randomPrice(Random rnd) {
            return (rnd.nextInt(1000) + 10) / 10.0;
        }
    }
}
