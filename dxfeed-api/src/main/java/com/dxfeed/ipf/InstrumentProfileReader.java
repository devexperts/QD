/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.ipf;

import com.devexperts.auth.AuthToken;
import com.devexperts.io.StreamCompression;
import com.devexperts.io.URLInputStream;
import com.devexperts.io.UncloseableInputStream;
import com.devexperts.logging.Logging;
import com.devexperts.util.LogUtil;
import com.devexperts.util.SystemProperties;
import com.dxfeed.ipf.impl.InstrumentProfileParser;
import com.dxfeed.ipf.live.InstrumentProfileConnection;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads instrument profiles from the stream using Instrument Profile Format (IPF).
 * Please see <b>Instrument Profile Format</b> documentation for complete description.
 * This reader automatically uses data formats as specified in the stream.
 *
 * <p>This reader is intended for "one time only" usage: create new instances for new IPF reads.
 * <p>Use {@link InstrumentProfileConnection} if support for streaming updates of instrument profiles is needed.
 *
 * <p>For backward compatibility reader can be configured with system property "-Dcom.dxfeed.ipf.complete" to control
 * the strategy for missing "##COMPLETE" tag when reading IPF, possible values are:
 * <ul>
 *     <li>{@code warn} - show warning in the log (default)</li>
 *     <li>{@code error} - throw exception (future default)</li>
 *     <li>{@code ignore} - do nothing (for backward compatibility)</li>
 * </ul>
 */
public class InstrumentProfileReader {

    private static final Logging log = Logging.getLogging(InstrumentProfileReader.class);

    private static final String LIVE_PROP_KEY = "X-Live";
    private static final String LIVE_PROP_REQUEST_NO = "no";

    private long lastModified;
    protected boolean wasComplete;

    /**
     * Creates instrument profile reader.
     */
    public InstrumentProfileReader() {}

    /**
     * Returns last modification time (in milliseconds) from last {@link #readFromFile} operation
     * or zero if it is unknown.
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Returns {@code true} if IPF was fully read on last {@link #readFromFile} operation.
     */
    public boolean wasComplete() {
        return wasComplete;
    }

    /**
     * Reads and returns instrument profiles from specified file.
     * This method recognizes data compression formats "zip" and "gzip" automatically.
     * In case of <em>zip</em> the first file entry will be read and parsed as a plain data stream.
     * In case of <em>gzip</em> compressed content will be read and processed.
     * In other cases data considered uncompressed and will be parsed as is.
     *
     * <p>Authentication information can be supplied to this method as part of URL user info
     * like {@code "http://user:password@host:port/path/file.ipf"}.
     *
     * <p>This is a shortcut for
     * <code>{@link #readFromFile(String, String, String) readFromFile}(address, <b>null</b>, <b>null</b>)</code>.
     *
     * <p>This operation updates {@link #getLastModified() lastModified} and {@link #wasComplete() wasComplete}.
     *
     * @param address URL of file to read from
     * @return list of instrument profiles
     * @throws InstrumentProfileFormatException if input stream does not conform to the Instrument Profile Format
     * @throws IOException If an I/O error occurs
     */
    public List<InstrumentProfile> readFromFile(String address) throws IOException {
        return readFromFile(address, null, null);
    }

    /**
     * Reads and returns instrument profiles from specified address with a specified basic user and password
     * credentials.
     * This method recognizes data compression formats "zip" and "gzip" automatically.
     * In case of <em>zip</em> the first file entry will be read and parsed as a plain data stream.
     * In case of <em>gzip</em> compressed content will be read and processed.
     * In other cases data considered uncompressed and will be parsed as is.
     *
     * <p>Specified user and password take precedence over authentication information that is supplied to this method
     * as part of URL user info like {@code "http://user:password@host:port/path/file.ipf"}.
     *
     * <p>This operation updates {@link #getLastModified() lastModified} and {@link #wasComplete() wasComplete}.
     *
     * @param address URL of file to read from
     * @param user the username, may be null
     * @param password the password, may be null
     * @return list of instrument profiles
     * @throws InstrumentProfileFormatException if input stream does not conform to the Instrument Profile Format
     * @throws IOException If an I/O error occurs
     */
    public List<InstrumentProfile> readFromFile(String address, String user, String password) throws IOException {
        return readFromFile(address, AuthToken.createBasicTokenOrNull(user, password));
    }

    /**
     * Reads and returns instrument profiles from specified address with a specified token credentials.
     * This method recognizes data compression formats "zip" and "gzip" automatically.
     * In case of <em>zip</em> the first file entry will be read and parsed as a plain data stream.
     * In case of <em>gzip</em> compressed content will be read and processed.
     * In other cases data considered uncompressed and will be parsed as is.
     *
     * <p>Specified token take precedence over authentication information that is supplied to this method
     * as part of URL user info like {@code "http://user:password@host:port/path/file.ipf"}.
     *
     * <p>This operation updates {@link #getLastModified() lastModified} and {@link #wasComplete() wasComplete}.
     *
     * @param address URL of file to read from
     * @param token the token, may be null
     * @return list of instrument profiles
     * @throws InstrumentProfileFormatException if input stream does not conform to the Instrument Profile Format
     * @throws IOException If an I/O error occurs
     */
    public List<InstrumentProfile> readFromFile(String address, AuthToken token) throws IOException {
        String url = resolveSourceURL(address);
        URLConnection connection = URLInputStream.openConnection(URLInputStream.resolveURL(url), token);
        connection.setRequestProperty(LIVE_PROP_KEY, LIVE_PROP_REQUEST_NO);
        try (InputStream in = connection.getInputStream()) {
            URLInputStream.checkConnectionResponseCode(connection);
            lastModified = connection.getLastModified();
            return read(in, address);
        }
    }

    /**
     * Converts a specified string address specification into an URL that will be read by
     * {@link #readFromFile} method using {@link URLInputStream}.
     */
    public static String resolveSourceURL(String address) {
        // Detect simple "host:port" source and convert it to full HTTP URL
        if (address.indexOf(':') > 0 && address.indexOf('/') < 0)
            try {
                int j = address.indexOf('?');
                String query = "";
                if (j >= 0) {
                    query = address.substring(j);
                    address = address.substring(0, j);
                }
                int port = Integer.parseInt(address.substring(address.indexOf(':') + 1));
                if (port > 0 && port < 65536)
                    address = "http://" + address + "/ipf/all.ipf.gz" + query;
            } catch (NumberFormatException e) {
                // source does not end with valid port number, so just use it as is
            }
        return address;
    }

    /**
     * Reads and returns instrument profiles from specified stream
     * This method recognizes data compression formats "zip" and "gzip" automatically.
     * In case of <em>zip</em> the first file entry will be read and parsed as a plain data stream.
     * In case of <em>gzip</em> compressed content will be read and processed.
     * In other cases data considered uncompressed and will be parsed as is.
     *
     * <p>This operation updates {@link #wasComplete() wasComplete} flag.
     *
     * @param in InputStream to read profiles from
     * @param address origin of the stream for debugging purposes
     * @throws InstrumentProfileFormatException if input stream does not conform to the Instrument Profile Format
     * @throws IOException If an I/O error occurs
     */
    public final List<InstrumentProfile> read(InputStream in, String address) throws IOException {
        // For backward compatibility with overriders of #read(InputStream) methods
        wasComplete = true;

        try (InputStream decompressed = StreamCompression.detectCompressionByHeaderAndDecompress(
            new UncloseableInputStream(in)))
        {
            List<InstrumentProfile> profiles = read(decompressed);
            if (!wasComplete)
                handleIncomplete(address);
            return profiles;
        } catch (IOException e) {
            wasComplete = false;
            throw e;
        }
    }

    /**
     * @throws InstrumentProfileFormatException if input stream does not conform to the Instrument Profile Format
     * @throws IOException If an I/O error occurs
     * @deprecated Use {@link #read(InputStream, String)}
     */
    @Deprecated
    public final List<InstrumentProfile> readCompressed(InputStream in) throws IOException {
        return read(in, "<unknown stream>");
    }

    /**
     * @throws InstrumentProfileFormatException if input stream does not conform to the Instrument Profile Format
     * @throws IOException If an I/O error occurs
     * @deprecated Use {@link #read(InputStream, String)}.
     *     This is an extension point only and will be made protected in future.
     */
    @Deprecated
    public List<InstrumentProfile> read(InputStream in) throws IOException {
        //NOTE: The method has been overridden to support non-standard data formats
        wasComplete = false;

        List<InstrumentProfile> profiles = new ArrayList<>();
        InstrumentProfileParser parser = new InstrumentProfileParser(in)
            .withIntern(this::intern)
            .whenComplete(() -> wasComplete = true);

        InstrumentProfile ip;
        while ((ip = parser.next()) != null) {
            profiles.add(ip);
        }
        return profiles;
    }

    /**
     * To be overridden in subclasses to allow {@link String#intern() intern} strings using pools
     * (like {@link com.devexperts.util.StringCache StringCache}) to reduce memory footprint. Default implementation
     * does nothing (returns value itself).
     *
     * @param value string value to intern
     * @return canonical representation of the given string value
     */
    protected String intern(String value) {
        return value;
    }

    private static final String COMPLETE_ERROR = "error";
    private static final String COMPLETE_WARN = "warn";
    private static final String COMPLETE_IGNORE = "ignore";
    private static final String DEFAULT_COMPLETE_STRATEGY = COMPLETE_WARN;

    private static final String COMPLETE_PROPERTY = "com.dxfeed.ipf.complete";
    private static final String COMPLETE_STRATEGY = initializeCompleteStrategy();

    // Made protected to be overridden in tests - do not use!
    protected void handleIncomplete(String address) throws InstrumentProfileFormatException {
        switch (COMPLETE_STRATEGY) {
            case COMPLETE_ERROR:
                throw new InstrumentProfileFormatException("##COMPLETE tag is missing in IPF " +
                    LogUtil.hideCredentials(address));
            case COMPLETE_WARN:
                log.warn("##COMPLETE tag is missing in IPF " + LogUtil.hideCredentials(address));
                break;
            case COMPLETE_IGNORE:
            default:
                // Do nothing
        }
    }

    private static String initializeCompleteStrategy() {
        String value = SystemProperties.getProperty(COMPLETE_PROPERTY, DEFAULT_COMPLETE_STRATEGY);
        switch (value) {
            case COMPLETE_ERROR:
            case COMPLETE_WARN:
            case COMPLETE_IGNORE:
                return value;
            default:
                log.warn("Unknown value for " + COMPLETE_PROPERTY + " property: " + value);
                return DEFAULT_COMPLETE_STRATEGY;
        }
    }
}
