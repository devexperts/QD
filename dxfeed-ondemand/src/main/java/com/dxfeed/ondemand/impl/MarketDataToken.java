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

import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.ByteArrayOutput;
import com.devexperts.logging.Logging;
import com.devexperts.util.Base64;
import com.devexperts.util.TimeFormat;

import java.io.IOException;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Map;

/**
 * Token that specifies and grants access to market data service.
 * The token is signed using a shared secret between the market data provider and the sponsor firm.
 */
public class MarketDataToken implements Serializable {
    private static final long serialVersionUID = 0;

    private static final String TOKEN_PASSWORD_PREFIX = "$.";
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final int DIGEST_SIZE = 32;

    private static final Logging log = Logging.getLogging(MarketDataToken.class);

    /** Configuration parameter for service state, true or false. */
    public static final String SERVICE_ACTIVE = "service_active";
    /** Configuration parameter for a service message to be shown for end-users. */
    public static final String SERVICE_MESSAGE = "service_message";
    /** Configuration parameter for minimum serviceable time in a "yyyy-MM-dd HH:mm:ss z" format or as plain long milliseconds. */
    public static final String SERVICE_MIN_TIME = "service_minTime";
    /** Configuration parameter for maximum serviceable time in a "yyyy-MM-dd HH:mm:ss z" format or as plain long milliseconds. */
    public static final String SERVICE_MAX_TIME = "service_maxTime";
    /** Configuration parameter for a comma-separated list of individual addresses
     * (each represented by a URL or a "host:port" pair) to be used for market data requests. */
    public static final String SERVICE_ADDRESS = "service_address";

    /** Configuration parameter for a contract name. */
    public static final String TOKEN_CONTRACT = "token_contract";
    /** Configuration parameter for a token timeout (how long it remains valid), in milliseconds. */
    public static final String TOKEN_TIMEOUT = "token_timeout";
    /** Configuration parameter for a shared secret key. */
    public static final String TOKEN_SECRET = "token_secret";

    private final boolean serviceActive;
    private final String serviceMessage;
    private final long serviceMinTime;
    private final long serviceMaxTime;
    private final String serviceAddress;

    private final String tokenContract;
    private final String tokenUser;
    private final long tokenExpiration;
    private final byte[] tokenDigest;

    /**
     * Creates new token for specified configuration and end-user.
     */
    public MarketDataToken(Map<String,String> configuration, String user) {
        serviceActive = getString(configuration, SERVICE_ACTIVE, "").equalsIgnoreCase("true");
        serviceMessage = getString(configuration, SERVICE_MESSAGE, "");
        serviceMinTime = getDate(configuration, SERVICE_MIN_TIME, 0);
        serviceMaxTime = getDate(configuration, SERVICE_MAX_TIME, 0);
        serviceAddress = getString(configuration, SERVICE_ADDRESS, "");

        tokenContract = getString(configuration, TOKEN_CONTRACT, "");
        tokenUser = user == null ? "" : user;
        tokenExpiration = System.currentTimeMillis() + getLong(configuration, TOKEN_TIMEOUT, 600000);
        tokenDigest = computeDigest(getString(configuration, TOKEN_SECRET, ""));
    }

    /**
     * Creates new token for specified parameters.
     */
    public MarketDataToken(String contract, String user, long expiration, byte[] digest) {
        this(contract, user, expiration, digest, "");
    }

    /**
     * Creates new token for specified parameters.
     */
    private MarketDataToken(String contract, String user, long expiration, byte[] digest, String address) {
        serviceActive = true;
        serviceMessage = "";
        serviceMinTime = 0;
        serviceMaxTime = 0;
        serviceAddress = address;

        tokenContract = contract == null ? "" : contract;
        tokenUser = user == null ? "" : user;
        tokenExpiration = expiration;
        tokenDigest = digest == null ? new byte[0] : digest;
    }

    /**
     * Creates new token for specified parameters.
     */
    private MarketDataToken(String user, String message) {
        serviceActive = false;
        serviceMessage = message;
        serviceMinTime = 0;
        serviceMaxTime = 0;
        serviceAddress = "";

        tokenContract = "";
        tokenUser = user;
        tokenExpiration = 0;
        tokenDigest = new byte[0];
    }

    /**
     * Returns <code>true</code> if the service is active, <code>false</code> otherwise.
     */
    public boolean isServiceActive() {
        return serviceActive;
    }

    /**
     * Returns service message to be shown to end-users.
     * Can be used for service maintenance messages, etc.
     */
    public String getServiceMessage() {
        return serviceMessage;
    }

    /**
     * Returns minimum serviceable time in milliseconds (for positive values).
     * Returns zero if undefined and negative value for relative time shift.
     */
    public long getServiceMinTime() {
        return serviceMinTime;
    }

    /**
     * Returns maximum serviceable time in milliseconds (for positive values).
     * Returns zero if undefined and negative value for relative time shift.
     */
    public long getServiceMaxTime() {
        return serviceMaxTime;
    }

    /**
     * Returns comma-separated list of "host:port" addresses to be used for market data requests.
     */
    public String getServiceAddress() {
        return serviceAddress;
    }

    /**
     * Returns contract name for which this token was issued.
     */
    public String getTokenContract() {
        return tokenContract;
    }

    /**
     * Returns username for which this token was issued.
     */
    public String getTokenUser() {
        return tokenUser;
    }

    /**
     * Returns token expiration.
     */
    public long getTokenExpiration() {
        return tokenExpiration;
    }

    /**
     * Returns token digest.
     */
    public byte[] getTokenDigest() {
        return tokenDigest;
    }

    public String toString() {
        return "<" + tokenUser + "/" + tokenContract + "> " + TimeFormat.DEFAULT.format(tokenExpiration);
    }

    // ========== Conversion to password and back ==========

    /**
     * Creates new token for specified user, password, and address.
     */
    public static MarketDataToken fromUserPassword(String user, String password, String address) {
        String contract = user;
        long expiration = Long.MAX_VALUE;
        byte[] digest = null;
        if (password.startsWith(TOKEN_PASSWORD_PREFIX)) {
            try {
                byte[] bytes = Base64.URLSAFE_UNPADDED.decode(password.substring(TOKEN_PASSWORD_PREFIX.length()));
                ByteArrayInput in = new ByteArrayInput(bytes);
                contract = in.readUTFString();
                expiration = in.readCompactLong();
                // now we should have digest there
                if (in.available() == DIGEST_SIZE) {
                    digest = new byte[DIGEST_SIZE];
                    in.readFully(digest);
                }
            } catch (Exception e) {
                // Self-signed token on any exception
            }
        }
        if (digest == null) // self-sign when failed to read
            digest = computeDigest(contract, user, expiration, password);
        return new MarketDataToken(contract, user, expiration, digest, address);
    }

    /**
     * Converts token information into a password string that can be used together with a user to
     * recreate token.
     */
    public String toTokenPassword() {
        ByteArrayOutput out = new ByteArrayOutput();
        try {
            out.writeUTFString(tokenContract);
            out.writeCompactLong(tokenExpiration);
            out.write(tokenDigest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return TOKEN_PASSWORD_PREFIX + Base64.URLSAFE_UNPADDED.encode(out.toByteArray());
    }

    // ========== Implementation Utility ==========

    /**
     * Computes token digest for specified parameters.
     */
    byte[] computeDigest(String secret) {
        return computeDigest(tokenContract, tokenUser, tokenExpiration, secret);
    }

    /**
     * Computes token digest for specified parameters.
     */
    private static byte[] computeDigest(String contract, String user, long expiration, String secret) {
        try {
            ByteArrayOutput bao = new ByteArrayOutput();
            bao.writeUTFString(contract);
            bao.writeUTFString(user);
            bao.writeCompactLong(expiration);
            bao.writeUTFString(secret);
            return MessageDigest.getInstance(DIGEST_ALGORITHM).digest(bao.toByteArray());
        } catch (IOException e) {
            log.error("Unexpected IOException", e);
            throw new IllegalStateException(e);
        } catch (NoSuchAlgorithmException e) {
            log.error("Unexpected NoSuchAlgorithmException", e);
            throw new IllegalStateException(e);
        }
    }

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");

    static String getString(Map<String,String> configuration, String key, String def) {
        Object value = configuration.get(key);
        return value == null ? def : value.toString();
    }

    private static synchronized long getDate(Map<String,String> configuration, String key, long def) {
        String value = getString(configuration, key, "").trim();
        if (value.length() == 0)
            return def;
        try {
            return DATE_FORMAT.parse(value).getTime();
        } catch (ParseException e) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
            }
            log.error("Error parsing date \"" + value + "\"", e);
            return def;
        }
    }

    private static long getLong(Map<String,String> configuration, String key, long def) {
        String value = getString(configuration, key, "").trim();
        if (value.length() == 0)
            return def;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.error("Error parsing long \"" + value + "\"", e);
            return def;
        }
    }
}
