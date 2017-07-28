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
package com.dxfeed.ipf;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.*;

import com.devexperts.io.URLInputStream;
import com.devexperts.io.UncloseableInputStream;
import com.dxfeed.ipf.impl.InstrumentProfileParser;
import com.dxfeed.ipf.live.InstrumentProfileConnection;

/**
 * Reads instrument profiles from the stream using Simple File Format.
 * Please see <b>Instrument Profile Format</b> documentation for complete description.
 * This reader automatically uses data formats as specified in the stream.
 *
 * <p>Use {@link InstrumentProfileConnection} if support for streaming updates of instrument profiles is needed.
 */
public class InstrumentProfileReader {
    private static final String LIVE_PROP_KEY = "X-Live";
    private static final String LIVE_PROP_REQUEST_NO = "no";

    private long lastModified;

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
     * Reads and returns instrument profiles from specified file.
     * This method recognizes popular data compression formats "zip" and "gzip" by analysing file name.
     * If file name ends with ".zip" then all compressed files will be read independently one by one
     * in their order of appearing and total concatenated list of instrument profiles will be returned.
     * If file name ends with ".gz" then compressed content will be read and returned.
     * In other cases file will be considered uncompressed and will be read as is.
     *
     * <p>Authentication information can be supplied to this method as part of URL user info
     * like {@code "http://user:password@host:port/path/file.ipf"}.
     *
     * <p>This is a shortcut for
     * <code>{@link #readFromFile(String, String, String) readFromFile}(address, <b>null</b>, <b>null</b>)</code>.
     *
     * <p>This operation updates {@link #getLastModified() lastModified}.
     *
     * @param address URL of file to read from
     * @return list of instrument profiles
     *
     * @throws InstrumentProfileFormatException if input stream does not conform to the Simple File Format
     * @throws IOException  If an I/O error occurs
     */
    public List<InstrumentProfile> readFromFile(String address) throws IOException {
        return readFromFile(address, null, null);
    }

    /**
     * Reads and returns instrument profiles from specified address with a specified basic user and password credentials.
     * This method recognizes popular data compression formats "zip" and "gzip" by analysing file name.
     * If file name ends with ".zip" then all compressed files will be read independently one by one
     * in their order of appearing and total concatenated list of instrument profiles will be returned.
     * If file name ends with ".gz" then compressed content will be read and returned.
     * In other cases file will be considered uncompressed and will be read as is.
     *
     * <p>Specified user and password take precedence over authentication information that is supplied to this method
     * as part of URL user info like {@code "http://user:password@host:port/path/file.ipf"}.
     *
     * <p>This operation updates {@link #getLastModified() lastModified}.
     *
     * @param address URL of file to read from.
     * @param user the user name (may be null).
     * @param password the password (may be null).
     * @return list of instrument profiles.
     *
     * @throws InstrumentProfileFormatException if input stream does not conform to the Simple File Format.
     * @throws IOException  If an I/O error occurs.
     */
    public List<InstrumentProfile> readFromFile(String address, String user, String password) throws IOException {
        String url = resolveSourceURL(address);
        URLConnection connection = URLInputStream.openConnection(URLInputStream.resolveURL(url), user, password);
        connection.setRequestProperty(LIVE_PROP_KEY, LIVE_PROP_REQUEST_NO);
        try (InputStream in = connection.getInputStream()) {
            URLInputStream.checkConnectionResponseCode(connection);
            lastModified = connection.getLastModified();
            return read(in, url);
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
     * Reads and returns instrument profiles from specified stream using specified name to select data compression format.
     * This method recognizes popular data compression formats "zip" and "gzip" by analysing file name.
     * If file name ends with ".zip" then all compressed files will be read independently one by one
     * in their order of appearing and total concatenated list of instrument profiles will be returned.
     * If file name ends with ".gz" then compressed content will be read and returned.
     * In other cases file will be considered uncompressed and will be read as is.
     *
     * @throws InstrumentProfileFormatException if input stream does not conform to the Simple File Format
     * @throws IOException  If an I/O error occurs
     */
    public List<InstrumentProfile> read(InputStream in, String name) throws IOException {
        // NOTE: decompression streams (zip and gzip) require explicit call to "close()" method to release native Inflater resources.
        // However we shall not close underlying stream here to allow proper nesting of data streams.
        if (name.toLowerCase().endsWith(".zip")) {
            try (ZipInputStream zip = new ZipInputStream(new UncloseableInputStream(in))) {
                List<InstrumentProfile> profiles = null;
                for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                    if (!entry.isDirectory()) {
                        List<InstrumentProfile> p = read(zip, entry.getName());
                        if (!p.isEmpty()) {
                            if (profiles == null)
                                profiles = p;
                            else
                                profiles.addAll(p);
                        }
                    }
                return profiles == null ? new ArrayList<>() : profiles;
            }
        }
        if (name.toLowerCase().endsWith(".gz")) {
            try (GZIPInputStream gzip = new GZIPInputStream(new UncloseableInputStream(in))) {
                return read(gzip);
            }
        }
        return read(in);
    }

    /**
     * Reads and returns instrument profiles from specified stream.
     *
     * @throws InstrumentProfileFormatException if input stream does not conform to the Simple File Format
     * @throws IOException  If an I/O error occurs
     */
    public List<InstrumentProfile> read(InputStream in) throws IOException {
        List<InstrumentProfile> profiles = new ArrayList<>();
        InstrumentProfileParser parser = new InstrumentProfileParser(in) {
            @Override
            protected String intern(String value) {
                return InstrumentProfileReader.this.intern(value);
            }
        };
        InstrumentProfile ip;
        while ((ip = parser.next()) != null)
            profiles.add(ip);
        return profiles;
    }

    /**
     * To be overridden in subclasses to allow {@link String#intern() intern} strings using pools
     * (like {@link com.devexperts.util.StringCache StringCache}) to reduce memory footprint. Default implementation does nothing
     * (returns value itself).
     *
     * @param value string value to intern
     * @return canonical representation of the given string value
     */
    protected String intern(String value) {
        return value;
    }

}
