/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.sample.api;

import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimeUtil;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.Scope;
import com.dxfeed.event.market.Side;
import com.dxfeed.event.market.TimeAndSale;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileReader;
import com.dxfeed.schedule.Schedule;
import com.dxfeed.schedule.Session;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Utility class that generates periodic slices of order book and timeandsales.
 */
public class Slicer implements PropertyChangeListener {

    public static final int PERIODS_PER_BATCH = 100;
    public static final int MIN_BATCH_MILLIS = 120000;

    /**
     * Individual slice with book snapshot and all sales occurred within the slice.
     */
    public static class Slice {
        public final String symbol;   // symbol of this slice
        public final long startTime;  // time when slice starts
        public final long endTime;    // time when slice ends, endTime = startTime + period
        public boolean isValid;       // book is crossed or something wrong with the slice

        public Slice prev;            // will set to null when book is initialized from prev
        public Map<Long, Order> book; // book with all levels at the end of the slice

        public final List<TimeAndSale> sales = new ArrayList<>();  // sales occurred between startTime and endTime

        public long lastBookUpdate;  // max time of book update or 0 if none occurred
        public long lastSaleUpdate;  // max time of sale update or 0 if none occurred

        public Slice(String symbol, long startTime, long period) {
            this.symbol = symbol;
            this.startTime = startTime;
            this.endTime = startTime + period;
            this.isValid = true;
        }

        public Slice(Slice prev) {
            this(prev.symbol, prev.endTime, prev.endTime - prev.startTime);
            this.prev = prev;
        }

        @Override
        public String toString() { // debug
            return symbol + "{" + TimeFormat.DEFAULT.format(startTime) + " to " + TimeFormat.DEFAULT.format(endTime) + ", isValid = " + isValid + "}";
        }

        public Map<Long, Order> getBook() {
            if (book == null)
                copyPrevBook();
            return book;
        }

        private void copyPrevBook() {
            Slice head = this;
            while (head.prev != null)
                head = head.prev;
            // get book to copy
            Map<Long, Order> book = head.book;
            if (book == null)
                head.book = book = new HashMap<>(); // no book -- create empty
            // now copy book forward up to slice
            head = this;
            while (head.prev != null) {
                head.book = new HashMap<>(book);
                Slice prev = head.prev;
                head.prev = null; // signal that book copy is done
                head = prev;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        new Slicer().start(args);
    }

    // need executor with 3 threads (state changes for two endpoins, process sales, process orders)
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

    // Parameters derived from command line arguments. Effectively final.

    protected ArrayList<String> symbols;    // symbols for slicing
    protected long period;                  // slice period (duration)
    protected long startTime;               // first slice start time (0 in real-time mode)
    protected long endTime;                 // last slice start time (Long.MAX_VALUE in real-time mode)
    protected long oldThreshold;            // threshold for old slices to finish them even if some events lag
    protected long waitThreshold;           // threshold for old slices to blocking wait for them (Long.MAX_VALUE is real-time mode)
    protected long sessionStartTime;        // ignore everything before this time

    // Slicer state updated during event processing.

    private DXEndpoint orderEndpoint;
    private DXEndpoint saleEndpoint;

    // both sale & order processing must terminate
    private final CountDownLatch completionLatch = new CountDownLatch(2);

    private final Map<String, List<Slice>> allSlices = new HashMap<>();
    private long oldestActiveSliceTime;
    private long lastOrderTime;
    private long lastSaleTime;
    private long doneSlices;
    private int orderCount = 0;
    private int saleCount = 0;

    private boolean isRTOrTapeMode;
    private boolean zeroFillFromStartTimeToFirstTick = false;
    private boolean calcAvgBookPrices = false;
    private boolean useCompositeOrderOrQuote = false;

    private long[] avgBookSizes = new long[5];
    private String scheduleDef;
    private Schedule schedule = null;

    private BufferedWriter outputWriter;

    /**
     * Initializes and starts processing using command line parameters.
     */
    protected void start(String[] args) throws InterruptedException, IOException {

        // check command line
        if (args.length == 0 || args.length > 2) {
            showHelp();
            System.exit(-1);
        }
        // get mode of operation
        String mode = args[0].toLowerCase();
        if (!(mode.equals("realtime") || mode.equals("tape") || mode.equals("history"))) {
            showHelp();
            System.exit(-1);
        }
        // load config file
        String configFile = "slicer.cfg";
        if (args.length == 2)
            configFile = args[1];

        Properties properties = loadConfiguration(configFile);
        if (properties.getProperty("avgBookSizes", "").length() > 0) {
            calcAvgBookPrices = true;
            String[] bookSizes = properties.getProperty("avgBookSizes", "").split(",");
            if (bookSizes.length != 5) {
                System.out.println("Invalid value for avgBookSizes: must be a list of 5 comma-separated values");
                System.exit(-1);
            }
            for (int i = 0; i < 5; i++)
                avgBookSizes[i] = Long.parseLong(bookSizes[i]);
        }
        scheduleDef = properties.getProperty("schedule", "");
        if (scheduleDef.length() > 0)
            schedule = Schedule.getInstance(scheduleDef);
        getSymbols(properties.getProperty("symbols", ""));
        period = Integer.parseInt(properties.getProperty("period", "")) * 1000L;
        initOutputFile(properties.getProperty("outputFile", "slicer.cfg"));
        useCompositeOrderOrQuote = Boolean.parseBoolean(properties.getProperty("useCompositeOrderOrQuote", "false"));

        switch (mode) {
        case "realtime":
            startTime = 0;
            endTime = Long.MAX_VALUE;
            oldThreshold = Math.max(MIN_BATCH_MILLIS, period * 5);
            waitThreshold = Long.MAX_VALUE;
            isRTOrTapeMode = true;
            sessionStartTime = getSessionStartTime(System.currentTimeMillis());
            DXEndpoint endpoint = DXEndpoint.create(DXEndpoint.Role.FEED).executor(EXECUTOR);
            startProcessing(endpoint.getFeed(), endpoint.getFeed());
            endpoint.connect(properties.getProperty("feedAddress", ""));
            awaitCompletionAndShowProgress();
            break;
        case "tape":
            startTime = TimeFormat.DEFAULT.parse(properties.getProperty("startTime", "")).getTime() / period * period;
            endTime = TimeFormat.DEFAULT.parse(properties.getProperty("endTime", "")).getTime() / period * period;
            oldThreshold = Math.max(MIN_BATCH_MILLIS, period * PERIODS_PER_BATCH);
            waitThreshold = oldThreshold / 2;
            isRTOrTapeMode = true;
            zeroFillFromStartTimeToFirstTick = Boolean.valueOf(properties.getProperty("zeroFillFromStartTimeToFirstTick", "false"));
            String tapeAddress = properties.getProperty("tapeAddress", "");
            readFiles(tapeAddress, tapeAddress);
            finish();
            break;
        case "history":
            startTime = TimeFormat.DEFAULT.parse(properties.getProperty("startTime", "")).getTime() / period * period;
            endTime = TimeFormat.DEFAULT.parse(properties.getProperty("endTime", "")).getTime() / period * period;
            oldThreshold = Math.max(MIN_BATCH_MILLIS, period * PERIODS_PER_BATCH);
            waitThreshold = oldThreshold / 2;
            isRTOrTapeMode = false;
            zeroFillFromStartTimeToFirstTick = Boolean.valueOf(properties.getProperty("zeroFillFromStartTimeToFirstTick", "false"));
            String orderAddress = properties.getProperty("historyOrderAddress", "");
            String saleAddress = properties.getProperty("historySaleAddress", "");
            readFiles(orderAddress, saleAddress);
            finish();
            break;
        default:
            showHelp();
            break;
        }
        outputWriter.close();
        System.exit(0);
    }

    private Properties loadConfiguration(String configFile) {
        Properties properties = new Properties();
        File file = new File(configFile);
        if (!file.exists()) {
            showHelp();
        }

        if (file.exists()) {
            System.out.println("Loading configuration from " + file.getAbsoluteFile());
            try {
                properties.load(new FileInputStream(file));
                properties.list(System.out);
                System.out.println("");
            } catch (IOException e) {
                System.out.println("Failed to load configuration from " + file.getAbsoluteFile());
                System.exit(-1);
            }
        }
        return properties;
    }

    private void showHelp() {
        System.out.println("Usage: Slicer [realtime|tape|history] [config-file]");
        System.out.println("The following properties should be defined in the configuration file (defaults to slicer.cfg):");
        System.out.println("1) For real-time feed slicing: feed-address, symbols, period, [avgBookSizes], [outputFile], [schedule]");
        System.out.println("2) For tape slicing: tape-address, symbols, period, startTime, endTime, [size1, size2, size3, size4, size5], [outputFile], [schedule]");
        System.out.println("3) For history slicing:  order-address, sale-address, symbols, period, startTime, endTime, [size1, size2, size3, size4, size5], [outputFile], [schedule]");
        System.out.println("  feedAddress                       - dxFeed address for RT feed or file name");
        System.out.println("  tapeAddress                       - dxFeed tape file name. ~-notation is supported to list through local files with dates in the name");
        System.out.println("  historyOrderAddress,");
        System.out.println("  historySaleAddress                - dxFeed history file names for Order and TimeAndSale/TradeHistory events. ~-notation is supported to list through local files with dates in the name");
        System.out.println("  symbols                           - comma-separated list of symbols or IPF file name");
        System.out.println("  period                            - slice duration in seconds");
        System.out.println("  startTime                         - time of first slice, yyyyMMdd-HHmmssZ");
        System.out.println("  endTime                           - time of last slice, yyyyMMdd-HHmmssZ");
        System.out.println("  [avgBookSizes]                    - 5 comma-separated sizes to calculate average book prices upon; if omitted book average prices will not be calculated");
        System.out.println("  [outputFile]                      - output CSV file name; defaults to slicer.csv");
        System.out.println("  [schedule]                        - optional schedule to mark slices invalid during trading session pause");
        System.out.println("  [useCompositeOrderOrQuote]        - use composite orders or quote events instead of aggregate scope orders");
    }

    private void initOutputFile(String fileName) throws IOException {
        outputWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), StandardCharsets.UTF_8));
        if (calcAvgBookPrices)
            outputWriteLn("SYMBOL, TIME, BEST_BID, BEST_BID_SIZE, BEST_OFFER, BEST_OFFER_SIZE, AVG_BID_PRICE_1, AVG_OFFER_PRICE_1, AVG_BID_PRICE_2, AVG_OFFER_PRICE_2, AVG_BID_PRICE_3, AVG_OFFER_PRICE_3, AVG_BID_PRICE_4, AVG_OFFER_PRICE_4, AVG_BID_PRICE_5, AVG_OFFER_PRICE_5, VWAP, CUMULATIVE_VOLUME, IS_VALID_SLICE");
        else
            outputWriteLn("SYMBOL, TIME, BEST_BID, BEST_BID_SIZE, BEST_OFFER, BEST_OFFER_SIZE, VWAP, CUMULATIVE_VOLUME, IS_VALID_SLICE");
    }

    private void outputWriteLn(String s) {
        try {
            outputWriter.write(s);
            outputWriter.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void outputFlush() {
        try {
            outputWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void getSymbols(String arg) throws IOException {
        if (arg.toLowerCase().contains(".ipf")) {
            System.out.print("Loading profiles from " + arg + "...");
            symbols = new ArrayList<>();
            for (InstrumentProfile profile : new InstrumentProfileReader().readFromFile(arg))
                symbols.add(profile.getSymbol());
            System.out.println(symbols.size() + " profiles loaded");
        } else
            symbols = new ArrayList<>(Arrays.asList(arg.split(",")));
    }

    private void finish() {
        checkOld();
        report("Finished");
    }

    protected void readFiles(String orderAddress, String saleAddress) throws InterruptedException {
        orderEndpoint = DXEndpoint.create(DXEndpoint.Role.STREAM_FEED).executor(EXECUTOR);
        saleEndpoint = DXEndpoint.create(DXEndpoint.Role.STREAM_FEED).executor(EXECUTOR);
        orderEndpoint.addStateChangeListener(this);
        saleEndpoint.addStateChangeListener(this);
        startProcessing(orderEndpoint.getFeed(), saleEndpoint.getFeed());
        sessionStartTime = getSessionStartTime(startTime);
        String props =
            "[start=" + TimeFormat.DEFAULT.format(sessionStartTime) + "]" +
            "[stop=" + TimeFormat.DEFAULT.format(endTime) + "]" +
            "[speed=max]";
        orderEndpoint.connect(orderAddress + props + "[name=order]");
        saleEndpoint.connect(saleAddress + props + "[name=sale]");
        awaitCompletionAndShowProgress();
    }

    private long getSessionStartTime(long startTime) {
        if (schedule == null)
            return startTime - TimeUtil.DAY; // crude approximation
        Session startSession = schedule.getSessionByTime(startTime);
        return startSession.isTrading() ? startSession.getStartTime() : startTime;
    }

    private void awaitCompletionAndShowProgress() throws InterruptedException {
        while (completionLatch.getCount() > 0) {
            report("Progress");
            completionLatch.await(10000, TimeUnit.MILLISECONDS);
        }
    }

    private static String fmt(long time) {
        return time == Long.MAX_VALUE ? "done" : TimeFormat.DEFAULT.format(time);
    }

    private void startProcessing(DXFeed orderFeed, DXFeed saleFeed) {
        System.out.println("Starting: " + symbols.size() + " symbols, " + allSlices.size() + " sliced, " + doneSlices + " slices" +
            ", startTime " + TimeFormat.DEFAULT.format(startTime) +
            ", endTime " + TimeFormat.DEFAULT.format(endTime));
        startProcessing(orderFeed, Order.class);
        startProcessing(saleFeed, TimeAndSale.class);
    }

    private void report(String what) {

        long avgSize = 0;
        long maxSize = 0;
        long firstSliceDate = 0;
        long lastSliceDate = 0;

        for (String symbol : symbols) {
            List<Slice> sl = allSlices.get(symbol);
            if (sl != null) {
                avgSize += sl.size();
                if (sl.size() > maxSize) {
                    maxSize = sl.size();
                    firstSliceDate = sl.get(0).startTime;
                    lastSliceDate = sl.get(sl.size() - 1).startTime;
                }
            }
        }
        avgSize /= symbols.size();

        System.out.println(fmt(System.currentTimeMillis()) + ": " + what + ": " + symbols.size() +
            " symbols, " + allSlices.size() + " symbols sliced, " + doneSlices + " done slices" +
            ", " + avgSize + " avg. slices per symbol, " + maxSize + " max slices per symbol (" +
            fmt(firstSliceDate) + " - " + fmt(lastSliceDate) + ")" +
            ", lastOrderTime " + fmt(lastOrderTime) +
            ", lastSaleTime " + fmt(lastSaleTime) +
            ", oldestActiveSliceTime " + fmt(oldestActiveSliceTime) +
            ", orderCount " + orderCount +
            ", saleCount " + saleCount
        );
    }

    private void startProcessing(DXFeed feed, final Class<?> eventType) {
        DXFeedSubscription<Object> subscription = feed.createSubscription(eventType);
        subscription.addEventListener(events -> {
            try {
                process(eventType, events);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        subscription.setSymbols(symbols);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        try {
            checkCompletion(evt, orderEndpoint, true);
            checkCompletion(evt, saleEndpoint, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void checkCompletion(PropertyChangeEvent evt, DXEndpoint endpoint, boolean order) throws InterruptedException {
        if (evt.getSource() == endpoint && endpoint.getState() == DXEndpoint.State.NOT_CONNECTED) {
            endpoint.closeAndAwaitTermination();
            System.out.println("Completed processing " + (order ? "orders" : "sales"));
            // we are now sure that that all order or sales were processed
            synchronized (this) {
                if (order)
                    lastOrderTime = Long.MAX_VALUE;
                else
                    lastSaleTime = Long.MAX_VALUE;
                notifyAll();
            }
            completionLatch.countDown();
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized void process(Class<?> eventType, List<?> events) throws InterruptedException {
        if (eventType == Order.class)
            processOrders((List<Order>) events);
        else if (eventType == TimeAndSale.class)
            processSales((List<TimeAndSale>) events);
        else
            throw new IllegalArgumentException();
    }

    private void processOrders(List<Order> orders) throws InterruptedException {
        for (Order order : orders) {
            if ((useCompositeOrderOrQuote && order.getScope() != Scope.COMPOSITE) || (!useCompositeOrderOrQuote && order.getScope() != Scope.AGGREGATE))
                continue;

            long time = order.getTime();

            // zero time may happen for level deletion messages (deletions are always applied to current book)
            // last order time should not go backwards
            time = Math.max(lastOrderTime, time);
            // skip everything before this trading session
            if (time < sessionStartTime)
                continue;

            orderCount++;
            Slice slice = prepareSlice(order.getEventSymbol(), time);
            if (slice == null)
                continue;
            // update book
            if (useCompositeOrderOrQuote) {
                slice.getBook().put((long) order.getOrderSide().getCode(), order);
            } else {
                if (order.hasSize())
                    slice.getBook().put(order.getIndex(), order);
                else
                    slice.getBook().remove(order.getIndex());
            }

            slice.lastBookUpdate = time;
            lastOrderTime = time;
            waitOther(1);
            checkOld();
        }
    }

    private void processSales(List<TimeAndSale> sales) throws InterruptedException {
        for (TimeAndSale sale : sales) {
            // do not process outlier ticks with special conditions
            if (isRTOrTapeMode && !sale.isValidTick())
                continue;

            long time = sale.getTime();
            // skip everything before this trading session
            if (time < sessionStartTime)
                continue;

            // ignore events too far back in time
            if (time < lastSaleTime - oldThreshold) {
                System.out.println("!!! T&S too far back in time: " + sale);
                continue;
            }

            saleCount++;
            Slice slice = prepareSlice(sale.getEventSymbol(), time);
            if (slice == null)
                continue;
            slice.sales.add(sale);

            // last sale time should not go backwards
            time = Math.max(time, lastSaleTime);
            slice.lastSaleUpdate = time;
            lastSaleTime = time;
            waitOther(-1);
            checkOld();
        }
    }

    private Slice prepareSlice(String symbol, long time) {
        if (startTime == 0 && time != 0)
            startTime = (time - oldThreshold) / period * period;
        if (oldestActiveSliceTime == 0 && startTime != 0)
            oldestActiveSliceTime = startTime - oldThreshold;
        // init per symbol slice list
        List<Slice> sl = allSlices.get(symbol);
        if (sl == null) {
            if (oldestActiveSliceTime == 0)
                return null;
            allSlices.put(symbol, sl = new ArrayList<>());
            if (zeroFillFromStartTimeToFirstTick)
                sl.add(new Slice(symbol, startTime / period * period, period));
            else
                sl.add(new Slice(symbol, Math.max(oldestActiveSliceTime, time) / period * period, period));
        }

        // add missing slices
        Slice lastSlice = sl.get(sl.size() - 1);
        while (lastSlice.endTime <= time)
            sl.add(lastSlice = new Slice(lastSlice));

        // find proper slice from the history
        for (int i = sl.size(); --i >= 0;) {
            Slice s = sl.get(i);
            if (s.startTime <= time && time < s.endTime)
                return s;
        }

        // add missing slices to the beginning of slice list
        while (time < sl.get(0).startTime) {
            Slice next = sl.get(0);
            final Slice slice = new Slice(symbol, next.startTime - period, period);
            next.prev = slice;
            sl.add(0, slice);
        }

        return sl.get(0);
    }

    private void waitOther(int direction) throws InterruptedException {
        if (waitThreshold == Long.MAX_VALUE)
            return;
        while ((lastOrderTime - lastSaleTime) * direction > waitThreshold)
            wait(10000);
        if ((lastSaleTime - lastOrderTime) * direction > waitThreshold)
            notifyAll();
    }

    private void checkOld() {
        long time = waitThreshold == Long.MAX_VALUE ? Math.max(lastOrderTime, lastSaleTime) : Math.min(lastOrderTime, lastSaleTime);
        if (time < oldestActiveSliceTime + oldThreshold)
            return;
        // note, that we never slice past endTime
        oldestActiveSliceTime = Math.min(endTime, (time - oldThreshold + period) / period * period);
        String[] symbols = allSlices.keySet().toArray(new String[allSlices.size()]);
        Arrays.sort(symbols);
        while (true) {
            boolean hasMore = false;
            List<Slice> done = new ArrayList<>(allSlices.size());
            for (String symbol : symbols) {
                List<Slice> sl = allSlices.get(symbol);
                if (sl.get(0).startTime < oldestActiveSliceTime) {
                    Slice s = sl.remove(0);
                    if (startTime <= s.startTime && s.startTime <= endTime)
                        done.add(s);
                    if (sl.isEmpty())
                        sl.add(new Slice(s));
                    if (sl.get(0).startTime < oldestActiveSliceTime)
                        hasMore = true;
                }
            }
            slicesDone(done);
            doneSlices += done.size();
            if (!hasMore)
                break;
        }
    }

    /**
     * Invoked whenever some slices are done - i.e. have finished processing.
     * Add your processing code here.
     */
    protected void slicesDone(List<Slice> slices) {
        for (Slice slice : slices) {
            // Initialize output string
            StringBuilder output = new StringBuilder(slice.symbol + ", " + TimeFormat.DEFAULT.format(slice.startTime) + ", ");

            // Check if within trading session
            if (schedule != null) {
                Session session = schedule.getSessionByTime(slice.startTime);
                if (!session.isTrading())
                    slice.isValid = false;
            }

            // Construct sorted book out of slice.book:
            ArrayList<Order> sortedBook = new ArrayList<>(slice.getBook().values());
            Collections.sort(sortedBook, (o1, o2) -> Double.compare(o1.getPrice(), o2.getPrice()));

            //boolean printBook = false;

            // book size 0
            StringBuilder bbo = new StringBuilder();
            StringBuilder bookAvgPrices = new StringBuilder();

            boolean oneSidedBook = true;
            for (int i = 0; i < sortedBook.size() - 1; i++) {
                Order o1 = sortedBook.get(i);
                Order o2 = sortedBook.get(i + 1);
                // Check book
                if (o1.getPrice() >= o2.getPrice()) {
                    slice.isValid = false;
                }
                // check invalid bbo / book cross
                if (o1.getOrderSide() == Side.SELL && o2.getOrderSide() == Side.BUY) {
                    slice.isValid = false;
                    //printBook = true;
                } else
                // this is bbo
                if (o1.getOrderSide() == Side.BUY && o2.getOrderSide() == Side.SELL) {
                    bbo.append(o1.getPrice());
                    bbo.append(", ");
                    bbo.append(o1.getSizeAsDouble());
                    bbo.append(", ");
                    bbo.append(o2.getPrice());
                    bbo.append(", ");
                    bbo.append(o2.getSizeAsDouble());
                    bbo.append(", ");

                    oneSidedBook = false;

                    if (calcAvgBookPrices) {
                        for (int j = 0; j < 5; j++) {
                            bookAvgPrices.append(getAvgPriceForSize(avgBookSizes[j], Side.BUY, sortedBook, i));
                            bookAvgPrices.append(getAvgPriceForSize(avgBookSizes[j], Side.SELL, sortedBook, i));
                        }
                    }
                }
            }

            if (oneSidedBook && sortedBook.size() > 0) {
                Order o = sortedBook.get(0);
                if (o.getOrderSide() == Side.BUY) {
                    bbo.append(o.getPrice());
                    bbo.append(", ");
                    bbo.append(o.getSizeAsDouble());
                    bbo.append(", 0, 0, ");

                    if (calcAvgBookPrices) {
                        for (int j = 0; j < 5; j++) {
                            bookAvgPrices.append("0, ");
                            bookAvgPrices.append(getAvgPriceForSize(avgBookSizes[j], o.getOrderSide(), sortedBook, 0));
                        }
                    }
                }
                else {
                    bbo.append("0, 0, ");
                    bbo.append(o.getPrice());
                    bbo.append(", ");
                    bbo.append(o.getSizeAsDouble());
                    bbo.append(", ");

                    if (calcAvgBookPrices) {
                        for (int j = 0; j < 5; j++) {
                            bookAvgPrices.append(getAvgPriceForSize(avgBookSizes[j], o.getOrderSide(), sortedBook, 0));
                            bookAvgPrices.append("0, ");
                        }
                    }
                }
            }

            if (bbo.length() == 0)
                bbo.append("0, 0, 0, 0, ");

            if (calcAvgBookPrices && bookAvgPrices.length() == 0)
                bookAvgPrices.append("0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ");

            output.append(bbo);
            output.append(bookAvgPrices);

            // calculate vwap and cumulative size by time-and-sales
            double cumulativeSize = 0;
            double VWAP = 0;

            for (TimeAndSale sale : slice.sales) {
                cumulativeSize += sale.getSizeAsDouble();
                VWAP += sale.getPrice() * sale.getSizeAsDouble();
            }
            if (cumulativeSize > 0)
                VWAP /= cumulativeSize;

            // finalize output
            output.append(VWAP);
            output.append(", ");
            output.append(cumulativeSize);
            output.append(", ");
            output.append(slice.isValid);

            // print output
            outputWriteLn(output.toString());

            // debug book printing

            //if (printBook) {
            //    for (int i = 0; i < sortedBook.size(); i++)
            //        System.out.println("  " + sortedBook.get(i));
            //    System.out.println("***");
            //}

        }
        outputFlush();
    }

    private String getAvgPriceForSize(long size, Side side, ArrayList<Order> sortedBook, int bboIndex) {
        int direction = 1;
        if (side == Side.BUY)
            direction = -1;
        if (side == Side.SELL)
            bboIndex++;
        double cumulativeSize = 0;
        double avgPrice = 0;

        for (int i = bboIndex; i >= 0 && i < sortedBook.size() && cumulativeSize < size; i += direction) {
            Order o = sortedBook.get(i);
            double orderSize = o.getSizeAsDouble();
            if (cumulativeSize + orderSize > size) {
                orderSize = size - cumulativeSize;
                cumulativeSize = size;
            } else
                cumulativeSize += orderSize;
            avgPrice += o.getPrice() * orderSize;
        }

        if (cumulativeSize > 0)
            avgPrice /= cumulativeSize;

        String result = "0, ";
        if (avgPrice > 0)
            result = avgPrice + ", ";

        return result;
    }
}
