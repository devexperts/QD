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
package com.dxfeed.ondemand.impl;

import com.devexperts.io.URLInputStream;
import com.devexperts.logging.Logging;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Provides API to control access to market data.
 * <p>
 * Before requesting market data the end user first obtains a secure token with a limited lifetime
 * from its sponsor firm and then uses it when communicating with market data provider.
 * The token is signed using shared secret between market data provider and the sponsor firm.
 * <p>
 */
public class MarketDataAccess {
    /** Configuration parameter for banned users. */
    public static final String DENY_LIST = "deny_list";

    private static final Logging log = Logging.getLogging(MarketDataAccess.class);

    private static final MarketDataAccess instance = new MarketDataAccess();

    /**
     * Returns shared singleton instance of access controller.
     */
    public static MarketDataAccess getInstance() {
        return instance;
    }

    // ========== Instance API ==========

    private volatile Map configuration = Collections.emptyMap();
    private volatile Set<String> denyList = Collections.emptySet();
    private volatile Watcher watcher;

    /**
     * Creates new instance of access controller.
     */
    public MarketDataAccess() {
    }

    /**
     * Sets new configuration parameters to be used for creation and verification of URLs.
     */
    public void setConfiguration(Map configuration) {
        this.configuration = Collections.unmodifiableMap(new HashMap(configuration));
        this.denyList = Collections.unmodifiableSet(getDenyList(configuration));
    }

    private Set<String> getDenyList(Map configuration) {
        Set<String> result = new HashSet<>();
        String listString = MarketDataToken.getString(configuration, DENY_LIST, "");
        for (String user : listString.split(",")) {
            String user1 = user.trim();
            if (!user1.isEmpty())
                result.add(user1);
        }
        return result;
    }

    /**
     * Starts new thread that will periodically check and read/download configuration from specified URL.
     *
     * @param masterURL primary (master) location of configuration file
     * @param cacheFile file name to be used for configuration caching
     * @param period period how often original configuration is checked
     */
    public void startConfigurationWatcher(String masterURL, String cacheFile, long period) {
        watcher = new Watcher(masterURL, cacheFile, period);
    }

    /**
     * Stops thread that checks configuration.
     */
    public void stopConfigurationWatcher() {
        watcher = null;
    }

    /**
     * Creates new token for the specified username.
     */
    public MarketDataToken createToken(String user) {
        return new MarketDataToken(configuration, user);
    }

    public boolean checkAllowed(MarketDataToken token) {
        return !denyList.contains(token.getTokenUser() + "@" + token.getTokenContract());
    }

    /**
     * Verifies authenticity of specified URL. Does not check URL expiration condition.
     */
    public boolean verifyToken(MarketDataToken token) {
        String contractSecret = MarketDataToken.getString(configuration, MarketDataToken.TOKEN_SECRET + "_" + token.getTokenContract(), null);
        if (contractSecret != null)
            return Arrays.equals(token.getTokenDigest(), token.computeDigest(contractSecret));
        String contract = MarketDataToken.getString(configuration, MarketDataToken.TOKEN_CONTRACT, "");
        String secret = MarketDataToken.getString(configuration, MarketDataToken.TOKEN_SECRET, "");
        return token.getTokenContract().equals(contract) && Arrays.equals(token.getTokenDigest(), token.computeDigest(secret));
    }

    // ========== Implementation ==========

    private class Watcher implements Runnable {
        private final String url;
        private final String cache;
        private final long period;
        private byte[] data = new byte[0];

        public Watcher(String url, String cache, long period) {
            this.url = url;
            this.cache = cache;
            this.period = period;
            Thread t = new Thread(this, "Watcher-" + cache);
            t.setDaemon(true);
            t.start();
        }

        public void run() {
            try {
                data = URLInputStream.readURL(cache);
                updateConfiguration();
            } catch (FileNotFoundException e) {
                log.warn("File " + cache + " not found - treating as empty.");
            } catch (IOException e) {
                log.error("Failed to load file " + cache + " - treating as empty.", e);
            }
            while (MarketDataAccess.this.watcher == this)
                try {
                    byte[] newData = URLInputStream.readURL(url);
                    if (newData.length == 0)
                        log.error("Failed to download data for file " + cache);
                    else if (!Arrays.equals(newData, data)) {
                        log.info("Downloaded new data for file " + cache);
                        data = newData;
                        updateConfiguration();
                        FileOutputStream out = new FileOutputStream(cache);
                        out.write(data);
                        out.close();
                    }
                    Thread.sleep((long) (period * (0.8 + 0.4 * Math.random())));
                } catch (Throwable t) {
                    log.error("Error updating file " + cache, t);
                    try {
                        Thread.sleep((long) (10 * 1000 * (1 + Math.random())));
                    } catch (InterruptedException ignored) {}
                }
        }

        private void updateConfiguration() throws IOException {
            Properties p = new Properties();
            p.load(new ByteArrayInputStream(data));
            if (MarketDataAccess.this.watcher == this)
                MarketDataAccess.this.setConfiguration(p);
        }
    }
}
