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
package com.dxfeed.ipf.filter;

import com.devexperts.io.URLInputStream;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.kit.FilterSyntaxException;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.qd.util.SymbolSet;
import com.devexperts.util.LogUtil;
import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimePeriod;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileReader;
import com.dxfeed.schedule.Schedule;
import com.dxfeed.schedule.Session;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * Subscription filter that filters only based on symbol. It accepts all symbols,
 * listed in specific IPF file with their suffixes.
 */
public class IPFSymbolFilter extends QDFilter {
    private static final boolean TRACE_LOG = IPFSymbolFilter.class.desiredAssertionStatus();

    private final Logging log = new Logging(IPFSymbolFilter.class.getName()) {
        @Override
        protected String decorateLogMessage(String msg) {
            return "[" + LogUtil.hideCredentials(address) + "] " + super.decorateLogMessage(msg);
        }
    };

    static final String FILTER_NAME_PREFIX = "ipf";
    static final String UPDATE_PROPERTY = "update";
    static final String SCHEDULE_PROPERTY = "schedule";

    private static final int STATE_NEW = 0;
    private static final int STATE_READING = 1;
    private static final int STATE_ACTIVE = 2;

    // --- static factory ---

    public static IPFSymbolFilter create(DataScheme scheme, String spec) {
        List<String> props = new ArrayList<>();
        String prefix = QDConfig.parseProperties(spec, props);
        if (!prefix.equals(FILTER_NAME_PREFIX))
            throw new IllegalArgumentException("ipf filter specification must start with " + FILTER_NAME_PREFIX);
        String address = QDConfig.unescape(props.get(0)); // allow for escaped special characters in address
        Config config = new Config();
        QDConfig.setProperties(config, props.subList(1, props.size()));
        config.validate();
        IPFSymbolFilter filter = new IPFSymbolFilter(scheme, address, config, null);
        if (filter.isDynamic())
            filter = IPFRegistry.registerShared(filter);
        try {
            filter.readOrWaitActive();
        } catch (IOException e) {
            throw new FilterSyntaxException("Failed to create ipf filter \"" + filter + "\": " + e.getMessage(), e);
        }
        return filter;
    }

    // --- instance variables ---

    private final int wildcard;
    private final String address;
    private final Config config;

    private SymbolSet set = SymbolSet.createInstance();

    private final Set<String> tradingHours = new HashSet<>();
    private final List<Schedule> tradingSchedules = new ArrayList<>();

    private int state = STATE_NEW;
    private IOException error;
    private long lastModified;
    private long lastLoaded;
    private long lastChecked;

    private Future<?> updateTask;

    // --- instance code ---

    IPFSymbolFilter(DataScheme scheme, String address, Config config, QDFilter source) {
        super(scheme, source);
        this.wildcard = scheme.getCodec().getWildcardCipher();
        this.address = address;
        this.config = config;
    }

    private synchronized boolean checkActive() {
        return state == STATE_ACTIVE;
    }

    private synchronized boolean makeReading() {
        if (state == STATE_NEW) {
            state = STATE_READING;
            return true;
        }
        return false;
    }

    private synchronized void makeActive(IOException error) {
        state = STATE_ACTIVE;
        this.error = error;
        notifyAll();
    }

    private synchronized void waitActive() throws IOException {
        while (state != STATE_ACTIVE)
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        if (error != null)
            throw error;
    }

    private void readOrWaitActive() throws IOException {
        if (makeReading()) {
            try {
                read();
            } catch (IOException e) {
                error = e;
            }
            makeActive(error);
        }
        waitActive();
    }

    private void read() throws IOException {
        // determine skip schedule mode
        boolean readTradingHours = false;
        if (config.schedule != null) {
            if (config.schedule.isEmpty()) {
                readTradingHours = true; // will collect all trading hours
            } else
                tradingHours.add(config.schedule); // use a specified schedule
        }
        // start downloading
        log.info("Downloading IPF");
        SymbolCodec symbolCodec = getScheme().getCodec();
        InstrumentProfileReader reader = new InstrumentProfileReader();

        List<InstrumentProfile> list =  reader.readFromFile(address, config.user, config.password);
        for (InstrumentProfile profile : list) {
            int cipher = symbolCodec.encode(profile.getSymbol());
            set.add(cipher, profile.getSymbol());
            if (readTradingHours && profile.getTradingHours().length() > 0)
                tradingHours.add(profile.getTradingHours());
        }
        set = set.unmodifiable(); // finalize
        lastModified = reader.getLastModified();
        lastLoaded = System.currentTimeMillis();
        lastChecked = System.currentTimeMillis();
        log.info("Downloaded " + set.size() + " symbols" +
            (lastModified == 0 ? "" : " (last modified on " + TimeFormat.DEFAULT.format(lastModified) + ")"));
        if (readTradingHours)
            log.info("Found " + tradingHours.size() + " distinct trading hours descriptions to avoid updates");
        // parse trading hours strings into a list of schedules
        for (String s : tradingHours)
            tradingSchedules.add(Schedule.getInstance(s));
    }

    @Override
    protected void dynamicTrackingStart() {
        updateTask = IPFUpdater.track(this);
    }

    @Override
    protected void dynamicTrackingStop() {
        updateTask.cancel(false);
    }

    public void update() {
        if (TRACE_LOG)
            log.trace("update()");
        if (!checkActive())
            return; // is not loaded yet, try later
        if (getUpdatedFilter() != this)
            return; // already updated -- don't bother
        // check tradingSchedules
        for (Schedule schedule : tradingSchedules) {
            Session session = schedule.getSessionByTime(System.currentTimeMillis());
            if (session.isTrading()) {
                if (log.debugEnabled())
                    log.debug("Skip update check because of trading session " + session + " in schedule " + schedule);
                return; // avoid trading hours
            }
        }
        // actually perform update check
        updateCheck();
    }

    public void forceUpdate() {
        log.info("Forcing update via JMX");
        if (checkActive())
            downloadUpdate();
    }

    private void updateCheck() {
        if (TRACE_LOG)
            log.trace("updateCheck() @" + Integer.toHexString(System.identityHashCode(this)));
        if (lastModified == 0)
            log.warn("Last modified time is not known, will download file to compare contents");
        else {
            if (TRACE_LOG)
                log.trace("Checking last modified time @" + Integer.toHexString(System.identityHashCode(this)));
            long lastModified = 0;
            try {
                String url = InstrumentProfileReader.resolveSourceURL(address);
                lastModified = URLInputStream.getLastModified(URLInputStream.resolveURL(url), config.user, config.password);
            } catch (IOException e) {
                log.warn("Failed to get last modified time: " + e.getMessage() + ", will download file to compare contents");
            }
            if (lastModified == this.lastModified) {
                lastChecked = System.currentTimeMillis();
                if (TRACE_LOG)
                    log.trace("No change in time @" + Integer.toHexString(System.identityHashCode(this)));
                return; // no change -- do not update
            }
        }
        downloadUpdate();
    }

    private void downloadUpdate() {
        // download into an updated instance & compare
        IPFSymbolFilter other = new IPFSymbolFilter(getScheme(), address, config, this);
        try {
            other.readOrWaitActive();
        } catch (IOException e) {
            log.error("Failed to update IPF filter: " + e.getMessage(), e);
            return; // will try again
        }
        if (hasSameContents(other)) {
            log.info("Symbols in IPF has not changed");
            // update last modified time and keep this instance
            lastModified = other.lastModified;
            lastChecked = System.currentTimeMillis();
            return;
        }
        // send change notification and drop this instance
        fireFilterUpdated(IPFRegistry.registerUpdate(other));
    }

    private boolean hasSameContents(IPFSymbolFilter other) {
        return set.equals(other.set) && tradingHours.equals(other.tradingHours);
    }

    public long getUpdateMillis() {
        TimePeriod update = config.update;
        return update == null ? 0 : update.getTime();
    }

    public int getNumberOfSymbols() {
        return set.size();
    }

    public long getLastModified() {
        return lastModified;
    }

    public long getLastLoaded() {
        return lastLoaded;
    }

    public long getLastChecked() {
        return lastChecked;
    }

    @Override
    public boolean isDynamic() {
        TimePeriod update = config.update;
        return update != null && update.getTime() > 0;
    }

    @Override
    public final Kind getKind() {
        return Kind.SYMBOL_SET_WITH_ATTRIBUTES;
    }

    @Override
    public boolean isFast() {
        return true;
    }

    @Override
    public final SymbolSet getSymbolSet() {
        return set;
    }

    @Override
    public final boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
        // NOte: IPFSymbolFilter is "symbol-only filter" (see QDFilter.Kind.isSymbolOnly), so
        // it must work even when contract and record are null (it cannot be used here)
        if (cipher == wildcard)
            return true;
        if (set.contains(cipher, symbol))
            return true;
        if (symbol == null || symbol.length() < 2 || symbol.charAt(symbol.length() - 1) != '}')
            return false;
        int i = symbol.indexOf('{');
        if (i < 0)
            return false;
        String prefix = symbol.substring(0, i);
        return set.contains(getScheme().getCodec().encode(prefix), prefix);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        return set.equals(((IPFSymbolFilter) o).set);
    }

    @Override
    public int hashCode() {
        return set.hashCode();
    }

    @Override
    public String getDefaultName() {
        return FILTER_NAME_PREFIX + "[" + QDConfig.escape(address) + config.suffixString() + "]";
    }

    public static class Config {
        TimePeriod update;
        String schedule;
        String user;
        String password;

        void validate() throws FilterSyntaxException {
            if (schedule != null && (update == null || update.getTime() == 0))
                throw new FilterSyntaxException("\"" + SCHEDULE_PROPERTY + "\" property can only be used with \"" + UPDATE_PROPERTY + "\" property");
        }

        public TimePeriod getUpdate() {
            return update;
        }

        public void setUpdate(TimePeriod update) {
            if (update != null && update.getTime() < 0)
                throw new FilterSyntaxException("\"" + UPDATE_PROPERTY + "\" property for ipf filter cannot be negative");
            this.update = update;
        }

        public String getSchedule() {
            return schedule;
        }

        public void setSchedule(String schedule) {
            this.schedule = schedule;
        }

        /*
         * There are no getter methods for user and password <b>by design</b>,
         * so that they do not show in a string representation.
         */

        public void setUser(String user) {
            this.user = user;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        String suffixString() {
            StringBuilder sb = new StringBuilder();
            for (String kv : QDConfig.getProperties(this)) {
                sb.append(",");
                sb.append(kv);
            }
            return sb.toString();
        }
    }
}
