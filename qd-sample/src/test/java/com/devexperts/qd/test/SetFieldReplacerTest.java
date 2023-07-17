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
package com.devexperts.qd.test;

import com.devexperts.test.TraceRunnerWithParametersFactory;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.EventType;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandlePeriod;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.candle.CandleType;
import com.dxfeed.event.market.AnalyticOrder;
import com.dxfeed.event.market.OtcMarketsOrder;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.Profile;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Side;
import com.dxfeed.event.market.SpreadOrder;
import com.dxfeed.event.market.TimeAndSale;
import com.dxfeed.event.market.Trade;
import com.dxfeed.event.market.TradeETH;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@SuppressWarnings("unchecked")
@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TraceRunnerWithParametersFactory.class)
public class SetFieldReplacerTest {
    //FIXME Switch to temporary folder when [QD-1436] is fixed
    private static final Path DIRECTORY_WRITE_TO = Paths.get("./target");

    private final boolean replace;
    private final FieldPairs fields;
    private final String fileFormat;

    public SetFieldReplacerTest(boolean replace, FieldPairs fields, String fileFormat) {
        this.replace = replace;
        this.fields = fields;
        this.fileFormat = fileFormat;
    }

    @Parameterized.Parameters(name = "replace={0}, fields={1}, fileFormat={2}")
    public static Iterable<Object[]> parameters() {
        ArrayList<Object[]> parameters = new ArrayList<>();
        boolean[] replaceParameters = {true, false};
        String[] fileFormatParameters = {"text", "binary"};
        for (boolean replace : replaceParameters) {
            for (FieldPairs field : FieldPairs.values()) {
                for (String fileFormat : fileFormatParameters) {
                    parameters.add(new Object[] {replace, field, fileFormat});
                }
            }
        }
        return parameters;
    }

    private <T extends EventType<?>> void testEvent(T initialEvent, T modifiedEvent) {
        try (TempFile tmp = new TempFile()) {
            // 1. Create endpoint with PUBLISHER role and connect to tape file
            DXEndpoint endpoint = DXEndpoint.newBuilder()
                    .withRole(DXEndpoint.Role.PUBLISHER)
                    .withProperty(DXEndpoint.DXFEED_WILDCARD_ENABLE_PROPERTY, "true")
                    .build();
            endpoint.connect("tape:" + tmp.getFile() + "[format=" + fileFormat + "]");

            // 2. Publish event and close endpoint
            endpoint.getPublisher().publishEvents(Collections.singletonList(initialEvent));
            endpoint.awaitProcessed();
            endpoint.closeAndAwaitTermination();

            // 3. Read published events
            endpoint = DXEndpoint.newBuilder()
                .withRole(DXEndpoint.Role.STREAM_FEED)
                .build();
            DXFeedSubscription<T> sub = endpoint.getFeed().createSubscription((Class<T>) initialEvent.getClass());
            List<T> events = new ArrayList<>();
            sub.addEventListener(events::addAll);
            sub.addSymbols(initialEvent.getEventSymbol());
            String fieldReplacerConfig = "";
            if (replace) {
                fieldReplacerConfig = "[fieldReplacer=";
                for (String record : fields.records.split(",")) {
                    fieldReplacerConfig += "(set:" + record + ":" + fields.config + ")";
                }
                fieldReplacerConfig += "]";
            }
            endpoint.connect("file:" + tmp.getFile() + fieldReplacerConfig);
            endpoint.awaitNotConnected();
            endpoint.closeAndAwaitTermination();

            // 4. Check that events are changed as required
            for (T readEvent : events) {
                assertTrue(initialEvent + " --> " + readEvent, eventsAreEqual(modifiedEvent, readEvent));
            }
        } catch (IOException e) {
            fail("I/O error: " + e.getMessage());
        } catch (Exception e) {
            fail("Error: " + e.getMessage());
        }
    }

    @Test
    public void testTimeAndSale() throws Exception {
        TimeAndSale initialEvent = new TimeAndSale("IBM");
        initialEvent.setTime(123_000);
        initialEvent.setPrice(1.0);
        initialEvent.setSize(100);
        initialEvent.setAskPrice(1.1);
        initialEvent.setBidPrice(0.9);
        TimeAndSale modifiedEvent = cloneEvent(initialEvent);

        if (replace) {
            switch (fields) {
                case BID_ASK:
                    modifiedEvent.setBidPrice(modifiedEvent.getAskPrice());
                    break;
                case PRICE_SIZE:
                    modifiedEvent.setPrice(modifiedEvent.getSizeAsDouble());
                    break;
                case HIGH_LOW:
                case HALT_START_END_TIME:
                    break;
                default:
                    throw new IllegalArgumentException("Unknown fields: " + fields.config);
            }
        }
        testEvent(initialEvent, modifiedEvent);
    }

    @Test
    public void testTrade() throws Exception {
        Trade initialEvent = new Trade("IBM");
        initialEvent.setTime(123_000);
        initialEvent.setPrice(1.0);
        initialEvent.setSize(100);
        Trade modifiedEvent = cloneEvent(initialEvent);
        if (replace) {
            switch (fields) {
                case PRICE_SIZE:
                    modifiedEvent.setPrice(modifiedEvent.getSizeAsDouble());
                    break;
                case BID_ASK:
                case HIGH_LOW:
                case HALT_START_END_TIME:
                    break;
                default:
                    throw new IllegalArgumentException("Unknown fields: " + fields.config);
            }
        }
        testEvent(initialEvent, modifiedEvent);
    }

    @Test
    public void testTradeETH() throws Exception {
        TradeETH initialEvent = new TradeETH("IBM");
        initialEvent.setTime(123_000);
        initialEvent.setPrice(1.0);
        initialEvent.setSize(100);
        TradeETH modifiedEvent = cloneEvent(initialEvent);
        if (replace) {
            switch (fields) {
                case PRICE_SIZE:
                    modifiedEvent.setPrice(modifiedEvent.getSizeAsDouble());
                    break;
                case BID_ASK:
                case HIGH_LOW:
                case HALT_START_END_TIME:
                    break;
                default:
                    throw new IllegalArgumentException("Unknown fields: " + fields.config);
            }
        }
        testEvent(initialEvent, modifiedEvent);
    }

    @Test
    public void testOrder() throws Exception {
        Order initialEvent = new Order("IBM");
        initialEvent.setTime(123_000);
        initialEvent.setPrice(1.0);
        initialEvent.setSize(100);
        initialEvent.setOrderSide(Side.BUY);
        Order modifiedEvent = cloneEvent(initialEvent);
        if (replace) {
            switch (fields) {
                case PRICE_SIZE:
                    modifiedEvent.setPrice(modifiedEvent.getSizeAsDouble());
                    break;
                case BID_ASK:
                case HIGH_LOW:
                case HALT_START_END_TIME:
                    break;
                default:
                    throw new IllegalArgumentException("Unknown fields: " + fields.config);
            }
        }
        testEvent(initialEvent, modifiedEvent);
    }

    @Test
    public void testAnalyticOrder() throws Exception {
        AnalyticOrder initialEvent = new AnalyticOrder("IBM");
        initialEvent.setTime(123_000);
        initialEvent.setPrice(1.0);
        initialEvent.setSize(100);
        initialEvent.setOrderSide(Side.BUY);
        AnalyticOrder modifiedEvent = cloneEvent(initialEvent);
        if (replace) {
            switch (fields) {
                case PRICE_SIZE:
                    modifiedEvent.setPrice(modifiedEvent.getSizeAsDouble());
                    break;
                case BID_ASK:
                case HIGH_LOW:
                case HALT_START_END_TIME:
                    break;
                default:
                    throw new IllegalArgumentException("Unknown fields: " + fields.config);
            }
        }
        testEvent(initialEvent, modifiedEvent);
    }

    @Test
    public void testOtcMarketsOrder() throws Exception {
        OtcMarketsOrder initialEvent = new OtcMarketsOrder("IBM");
        initialEvent.setTime(123_000);
        initialEvent.setPrice(1.0);
        initialEvent.setSize(100);
        initialEvent.setOrderSide(Side.BUY);
        OtcMarketsOrder modifiedEvent = cloneEvent(initialEvent);
        if (replace) {
            switch (fields) {
                case PRICE_SIZE:
                    modifiedEvent.setPrice(modifiedEvent.getSizeAsDouble());
                    break;
                case BID_ASK:
                case HIGH_LOW:
                case HALT_START_END_TIME:
                    break;
                default:
                    throw new IllegalArgumentException("Unknown fields: " + fields.config);
            }
        }
        testEvent(initialEvent, modifiedEvent);
    }

    @Test
    public void testSpreadOrder() throws Exception {
        SpreadOrder initialEvent = new SpreadOrder("IBM");
        initialEvent.setTime(123_000);
        initialEvent.setPrice(1.0);
        initialEvent.setSize(100);
        initialEvent.setOrderSide(Side.BUY);
        SpreadOrder modifiedEvent = cloneEvent(initialEvent);
        if (replace) {
            switch (fields) {
                case PRICE_SIZE:
                    modifiedEvent.setPrice(modifiedEvent.getSizeAsDouble());
                    break;
                case BID_ASK:
                case HIGH_LOW:
                case HALT_START_END_TIME:
                    break;
                default:
                    throw new IllegalArgumentException("Unknown fields: " + fields.config);
            }
        }
        testEvent(initialEvent, modifiedEvent);
    }

    @Test
    public void testCandle() throws Exception {
        // Bare symbol doesn't turn around via tape well.
        Candle initialEvent = new Candle(CandleSymbol.valueOf("IBM", CandlePeriod.valueOf(1.0, CandleType.WEEK)));
        initialEvent.setTime(123_000);
        initialEvent.setOpen(10.0);
        initialEvent.setHigh(12.0);
        initialEvent.setLow(8.0);
        initialEvent.setClose(9.0);
        Candle modifiedEvent = cloneEvent(initialEvent);
        if (replace) {
            switch (fields) {
                case HIGH_LOW:
                    modifiedEvent.setHigh(modifiedEvent.getLow());
                    break;
                case PRICE_SIZE:
                case BID_ASK:
                case HALT_START_END_TIME:
                    break;
                default:
                    throw new IllegalArgumentException("Unknown fields: " + fields.config);
            }
        }
        testEvent(initialEvent, modifiedEvent);
    }

    @Test
    public void testQuote() throws Exception {
        Quote initialEvent = new Quote("IBM");
        initialEvent.setBidTime(123_000);
        initialEvent.setBidPrice(10.0);
        initialEvent.setAskTime(234_000);
        initialEvent.setAskPrice(11.0);
        Quote modifiedEvent = cloneEvent(initialEvent);
        if (replace) {
            switch (fields) {
                case BID_ASK:
                    modifiedEvent.setBidPrice(modifiedEvent.getAskPrice());
                    break;
                case PRICE_SIZE:
                case HIGH_LOW:
                case HALT_START_END_TIME:
                    break;
                default:
                    throw new IllegalArgumentException("Unknown fields: " + fields.config);
            }
        }
        testEvent(initialEvent, modifiedEvent);
    }

    @Test
    public void testProfile() throws Exception {
        Profile initialEvent = new Profile("IBM");
        initialEvent.setHaltStartTime(123_000);
        initialEvent.setHaltEndTime(234_000);
        Profile modifiedEvent = cloneEvent(initialEvent);
        if (replace) {
            switch (fields) {
                case HALT_START_END_TIME:
                    modifiedEvent.setHaltStartTime(modifiedEvent.getHaltEndTime());
                    break;
                case BID_ASK:
                case PRICE_SIZE:
                case HIGH_LOW:
                    break;
                default:
                    throw new IllegalArgumentException("Unknown fields: " + fields.config);
            }
        }
        testEvent(initialEvent, modifiedEvent);
    }

    private static <T extends EventType<?>> boolean eventsAreEqual(T initial, T modified) {
        // Events don't have #equals() overridden.
        return initial.toString().equals(modified.toString());
    }

    private enum FieldPairs {
        BID_ASK("BidPrice:AskPrice", "Quote,TimeAndSale"),
        PRICE_SIZE("Price:Size", "TimeAndSale,Trade,TradeETH,Order,AnalyticOrder,OtcMarketsOrder,SpreadOrder"),
        HIGH_LOW("High:Low", "Candle,Trade.*"),
        HALT_START_END_TIME("HaltStartTime:HaltEndTime", "Profile");

        final String config;
        final String records;

        FieldPairs(String config, String records) {
            this.config = config;
            this.records = records;
        }
    }

    private static <T extends EventType<?>> T cloneEvent(T in) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(in);
            }
            try (ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                ObjectInputStream ois = new ObjectInputStream(bais))
            {
                return (T)ois.readObject();
            } catch (ClassNotFoundException e) {
                return null;
            }
        } catch (IOException e) {
        }
        return null;
    }

    private static class TempFile implements AutoCloseable {
        private final File file;

        TempFile() throws IOException {
            file = java.nio.file.Files.createTempFile(DIRECTORY_WRITE_TO, "sfrt", ".qds.tmp").toFile();
            file.deleteOnExit();
        }

        String getFile() {
            return file.getPath();
        }

        @Override
        public void close() throws Exception {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
