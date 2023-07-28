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
package com.dxfeed.ipf.live;

import com.devexperts.io.StreamCompression;
import com.devexperts.io.URLInputStream;
import com.devexperts.logging.Logging;
import com.devexperts.util.LogUtil;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimePeriod;
import com.devexperts.util.TimeUtil;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileReader;
import com.dxfeed.ipf.impl.InstrumentProfileParser;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Connects to an instrument profile URL and reads instrument profiles with support of
 * streaming live updates.
 * Please see <b>Instrument Profile Format</b> documentation for complete description.
 *
 * <p>The key different between this class and {@link InstrumentProfileReader} is that the later just reads
 * a snapshot of a set of instrument profiles, while this classes allows to track live updates, e.g.
 * addition and removal of instruments.
 *
 * <p>To use this class you need an address of the data source from you data provider. The name of the IPF file can
 * also serve as an address for debugging purposes.
 *
 * <p>The recommended usage of this class to receive a live stream of instrument profile updates is:
 *
 * <pre><tt>
 *     String address = "&lt;host&gt;:&lt;port&gt;";
 *     {@link InstrumentProfileCollector InstrumentProfileCollector} collector = <b>new</b> {@link InstrumentProfileCollector InstrumentProfileCollector}();
 *     {@link InstrumentProfileConnection InstrumentProfileConnection} connection = InstrumentProfileConnection.{@link #createConnection(String, InstrumentProfileCollector) createConnection}(address, collector);
 *     connection.{@link InstrumentProfileConnection#start() start}();
 *     collector.{@link InstrumentProfileCollector#addUpdateListener(InstrumentProfileUpdateListener) addUpdateListener}(new {@link InstrumentProfileUpdateListener InstrumentProfileUpdateListener}() {
 *         <b>public void</b> {@link InstrumentProfileUpdateListener#instrumentProfilesUpdated instrumentProfilesUpdated}(Iterator&lt;{@link InstrumentProfile InstrumentProfile}&gt; instruments) {
 *             <b>while</b> (instruments.hasNext()) {
 *                 {@link InstrumentProfile InstrumentProfile} instrument = instruments.next();
 *                 // do something with instrument here.
 *             }
 *         }
 *     });
 * </tt></pre>
 *
 * <p>If long-running processing of instrument profile is needed, then it is better to use
 * {@link InstrumentProfileUpdateListener#instrumentProfilesUpdated instrumentProfilesUpdated} notification
 * to schedule processing task in a separate thread.
 *
 * <p><b>This class is thread-safe</b>.
 */
public class InstrumentProfileConnection {
    private static final Logging log = Logging.getLogging(InstrumentProfileConnection.class);

    // ===================== private static constants =====================

    private static final String IF_MODIFIED_SINCE = "If-Modified-Since";
    private static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";

    private static final String LIVE_PROP_KEY = "X-Live";
    private static final String LIVE_PROP_REQUEST_YES = "yes";
    private static final String LIVE_PROP_RESPONSE = "provided";

    private static final Pattern UPDATE_PATTERN = Pattern.compile("(.*)\\[update=([^\\]]+)\\]");

    private static final int UPDATE_BATCH_SIZE = SystemProperties.getIntProperty(
        InstrumentProfileConnection.class, "updateBatchSize", 1000, 1, Integer.MAX_VALUE / 2);
    private static final long UPDATE_PERIOD = TimePeriod.valueOf(SystemProperties.getProperty(
        InstrumentProfileConnection.class, "updatePeriod", "1m")).getTime();

    // =====================  public static factory methods =====================

    /**
     * Creates instrument profile connection with a specified address and collector.
     * Address may be just "&lt;host&gt;:&lt;port&gt;" of server, URL, or a file path.
     * The "[update=&lt;period&gt;]" clause can be optionally added at the end of the address to
     * specify an {@link #getUpdatePeriod() update period} via an address string.
     * Default update period is 1 minute.
     *
     * <p>Connection needs to be {@link #start() started} to begin an actual operation.
     *
     * @param address address.
     * @param collector instrument profile collector to push updates into.
     * @return new instrument profile connection.
     */
    public static InstrumentProfileConnection createConnection(String address, InstrumentProfileCollector collector) {
        return new InstrumentProfileConnection(address, collector);
    }

    // =====================  public inner classes =====================

    /**
     * Instrument profile connection state.
     */
    public enum State {
        /**
         * Instrument profile connection is not started yet.
         * {@link InstrumentProfileConnection#start() start} was not invoked yet.
         */
        NOT_CONNECTED,

        /**
         * Connection is being established.
         */
        CONNECTING,

        /**
         * Connection was established.
         */
        CONNECTED,

        /**
         * Initial instrument profiles snapshot was fully read (this state is set only once).
         */
        COMPLETED,

        /**
         * Instrument profile connection was {@link InstrumentProfileConnection#close() closed}.
         */
        CLOSED
    }

    // =====================  private instance fields =====================

    private final String address;
    private final InstrumentProfileCollector collector;
    private final List<PropertyChangeListener> stateChangeListeners = new CopyOnWriteArrayList<>();
    private volatile State state = State.NOT_CONNECTED;
    private volatile long lastModified;
    private volatile long updatePeriod;
    private volatile boolean completed;
    private Thread handlerThread; // != null when state in (CONNECTING, CONNECTED, COMPLETE)

    // =====================  private constructor =====================

    private InstrumentProfileConnection(String address, InstrumentProfileCollector collector) {
        long updatePeriod = UPDATE_PERIOD;
        Matcher update = UPDATE_PATTERN.matcher(address);
        if (update.matches()) {
            address = update.group(1);
            updatePeriod = TimePeriod.valueOf(update.group(2)).getTime();
        }
        this.address = address;
        this.collector = collector;
        setUpdatePeriod(updatePeriod);
    }

    // =====================  public instance methods =====================

    /**
     * Returns the address of this instrument profile connection.
     * It does not include additional options specified as part of the address.
     * @return the address of this instrument profile connection.
     */
    public String getAddress() {
        return address;
    }

    /**
     * Returns update period in milliseconds.
     * It is period of an update check when the instrument profiles source does not support live updates
     * and/or when connection is dropped.
     * Default update period is 1 minute, unless overriden in an
     * {@link #createConnection(String, InstrumentProfileCollector) address string}.
     *
     * @return update period in milliseconds.
     */
    public long getUpdatePeriod() {
        return updatePeriod;
    }

    /**
     * Changes update period in milliseconds.
     *
     * @param updatePeriod update period in milliseconds.
     * @see #getUpdatePeriod()
     */
    public void setUpdatePeriod(long updatePeriod) {
        if (updatePeriod < 0)
            throw new IllegalArgumentException();
        this.updatePeriod = updatePeriod;
    }

    /**
     * Returns state of this instrument profile connections.
     * @return state of this instrument profile connections.
     */
    public State getState() {
        return state;
    }

    /**
     * Returns last modification time (in milliseconds) of instrument profiles or zero if it is unknown.
     * Note, that while the time is represented in milliseconds, the actual granularity of time here is a second.
     * @return last modification time (in milliseconds) of instrument profiles or zero if it is unknown.
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Starts this instrument profile connection. This connection's state immediately changes to
     * {@link State#CONNECTING CONNECTING} and the actual connection establishment proceeds in the background.
     */
    public synchronized void start() {
        if (state != State.NOT_CONNECTED)
            throw new IllegalStateException("Invalid state " + state);
        String name = toString();
        log.info("[" + name + "] Starting instrument profiles connection");
        handlerThread = new Thread(new Handler(), name);
        handlerThread.start();
        setStateAndFireChangeEvent(State.CONNECTING);
    }

    /**
     * Closes this instrument profile connection. This connection's state immediately changes to
     * {@link State#CLOSED CLOSED} and the background update procedures are terminated.
     */
    public synchronized void close() {
        if (state == State.CLOSED)
            return;
        if (state != State.NOT_CONNECTED) {
            handlerThread.interrupt();
            handlerThread = null;
        }
        setStateAndFireChangeEvent(State.CLOSED);
    }

    /**
     * Adds listener that is notified about changes in {@link #getState() state} property.
     * Installed listener can be removed with
     * {@link #removeStateChangeListener(PropertyChangeListener) removeStateChangeListener} method.
     *
     * @param listener the listener to add.
     */
    public void addStateChangeListener(PropertyChangeListener listener) {
        stateChangeListeners.add(listener);
    }

    /**
     * Removes listener that is notified about changes in {@link #getState() state} property.
     * It removes the listener that was previously installed with
     * {@link #addStateChangeListener(PropertyChangeListener) addStateChangeListener} method.
     *
     * @param listener the listener to remove.
     */
    public void removeStateChangeListener(PropertyChangeListener listener) {
        stateChangeListeners.remove(listener);
    }

    /**
     * Synchronously waits for full first snapshot read with the specified timeout.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the {@code timeout} argument
     * @return {@code true} if {@link State#COMPLETED COMPLETED} state was reached and {@code false}
     *         if the waiting time elapsed before snapshot was fully read.
     */
    public boolean waitUntilCompleted(long timeout, TimeUnit unit) {
        final CountDownLatch latch = new CountDownLatch(1);
        PropertyChangeListener listener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                if (getState() == State.COMPLETED || getState() == State.CLOSED)
                    latch.countDown();
            }
        };
        addStateChangeListener(listener);

        try {
            if (completed)
                return true;
            if (getState() == State.CLOSED)
                return false;

            latch.await(timeout, unit);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            removeStateChangeListener(listener);
        }
        return completed;
    }

    /**
     * Returns a string representation of the object.
     *
     * @return a string representation of the object.
     */
    @Override
    public String toString() {
        // Note: it is also used as a thread name.
        return "IPC:" + LogUtil.hideCredentials(address);
    }

    // =====================  private instance methods =====================

    private synchronized void makeConnected() {
        if (state == State.CONNECTING)
            setStateAndFireChangeEvent(State.CONNECTED);
    }

    private synchronized void makeComplete() {
        if (state == State.CONNECTED) {
            completed = true;
            setStateAndFireChangeEvent(State.COMPLETED);
        }
    }

    private void setStateAndFireChangeEvent(State newState) {
        State oldState = state;
        state = newState;
        if (stateChangeListeners.isEmpty())
            return;
        PropertyChangeEvent event = new PropertyChangeEvent(this, "state", oldState, newState);
        for (PropertyChangeListener listener : stateChangeListeners) {
            try {
                listener.propertyChange(event);
            } catch (Throwable t) {
                log.error("Exception in InstrumentProfileConnection state change listener", t);
            }
        }
    }

    private class Handler implements Runnable {
        private final List<InstrumentProfile> instrumentProfiles = new ArrayList<>();
        private final String url = InstrumentProfileReader.resolveSourceURL(InstrumentProfileConnection.this.address);
        private final Set<Object> oldGenerations = new HashSet<>(); // drop them on receiving complete response
        private Object newGeneration;
        private boolean supportsLive;

        @Override
        public void run() {
            long retryPeriod = 1_000L;
            while (state != InstrumentProfileConnection.State.CLOSED) {
                try {
                    checkAndDownload();

                    // wait before retrying
                    Thread.sleep(updatePeriod);
                } catch (InterruptedException ignored) {
                    return; // closed
                } catch (Throwable t) {
                    log.error("Exception while reading instrument profiles", t);
                    try {
                        Thread.sleep(retryPeriod);
                        retryPeriod = Math.min(retryPeriod * 2, updatePeriod);
                    } catch (InterruptedException ignored) {
                        return; // closed
                    }
                }
            }
        }

        private void checkAndDownload() throws Exception {
            URLConnection connection = URLInputStream.openConnection(url);
            connection.setRequestProperty(LIVE_PROP_KEY, LIVE_PROP_REQUEST_YES);
            if (lastModified != 0 && !supportsLive && connection instanceof HttpURLConnection) {
                // Use If-Modified-Since
                SimpleDateFormat dateFormat = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
                dateFormat.setTimeZone(TimeUtil.getTimeZoneGmt());
                connection.setRequestProperty(IF_MODIFIED_SINCE, dateFormat.format(new Date(lastModified)));
                if (((HttpURLConnection) connection).getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED)
                    return; // not modified
            }
            try (InputStream in = connection.getInputStream()) {
                URLInputStream.checkConnectionResponseCode(connection);
                long time = connection.getLastModified();
                if (time != 0 && time == lastModified)
                    return; // nothing changed
                log.info("Downloading instrument profiles");
                supportsLive = LIVE_PROP_RESPONSE.equals(connection.getHeaderField(LIVE_PROP_KEY));
                if (supportsLive)
                    log.info("Live updates streaming connection has been open");
                makeConnected();
                try (InputStream decompressedIn = StreamCompression.detectCompressionByHeaderAndDecompress(in)) {
                    int count = process(decompressedIn);
                    // Update timestamp only after first successful processing
                    lastModified = time;
                    log.info("Downloaded " + count + " instrument profiles" +
                        (lastModified == 0 ? "" : " (last modified on " + TimeFormat.DEFAULT.format(lastModified) + ")"));
                } finally {
                    // move this generation to old (if anything was received), so that we can drop those
                    // instruments when we receive new update later on
                    if (newGeneration != null) {
                        oldGenerations.add(newGeneration);
                        newGeneration = null;
                    }
                }
            }
        }

        private int process(InputStream in) throws IOException {
            int count = 0;
            InstrumentProfileParser parser = new InstrumentProfileParser(in)
                .whenFlush(this::flush)
                .whenComplete(this::complete);

            InstrumentProfile ip;
            while ((ip = parser.next()) != null) {
                count++;
                instrumentProfiles.add(ip);
                if (instrumentProfiles.size() >= UPDATE_BATCH_SIZE)
                    flush();
            }
            flush();
            // EOF of live connection is _NOT_ a signal that snapshot was complete (it sends an explicit complete)
            // for non-live data sources, though, EOF is a completion signal
            if (!supportsLive)
                complete();
            return count;
        }

        void flush() {
            if (instrumentProfiles.isEmpty())
                return;
            if (newGeneration == null)
                newGeneration = new Object(); // allocate fresh generation marker for everything received in here
            collector.updateInstrumentProfiles(instrumentProfiles, newGeneration);
            instrumentProfiles.clear();
        }

        void complete() {
            flush();
            // drop old generations
            collector.removeGenerations(oldGenerations);
            oldGenerations.clear();
            makeComplete();
        }
    }
}
