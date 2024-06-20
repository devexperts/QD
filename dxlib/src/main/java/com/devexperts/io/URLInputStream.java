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
package com.devexperts.io;

import com.devexperts.auth.AuthToken;
import com.devexperts.util.SystemProperties;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

/**
 * Convenient class that opens specified URL for reading.
 * It supports all URL formats supported by Java and also understands file names using both local and absolute paths.
 * It properly configures outgoing connections and releases all resources when closed.
 *
 * <p>To open HTTP resources use standard HTTP URL syntax:
 * <ul>
 * <li> http://www.host.com/path/file.txt
 * <li> http://www.host.com:8080/images/picture.jpg
 * <li> https://www.host.com/mydata/secret.zip
 * <li> https://user:password@www.host.com/mydata/secret.zip
 * </ul>
 *
 * <p>To open FTP resources use standard FTP URL syntax:
 * <ul>
 * <li> ftp://ftp.host.com/path/file.zip                - anonymous user, local path, default mode
 * <li> ftp://ftp.host.com/%2Fabsolute-path/file.zip    - anonymous user, absolute path ('%2F' is quoted root '/'), default mode
 * <li> ftp://ftp.host.com/file.zip;type=i              - anonymous user, local path, binary mode
 * <li> ftp://ftp.host.com/file.txt;type=a              - anonymous user, local path, ASCII mode
 * <li> ftp://ftp.host.com/path/                        - anonymous user, local path, UNIX-style file list
 * <li> ftp://ftp.host.com/path;type=d                  - anonymous user, local path, plain file list (includes 'path/' prefix)
 * <li> ftp://ftp.host.com/path/.;type=d                - anonymous user, local path, plain file list (without prefix)
 * <li> ftp://user:password@ftp.host.com/file.zip       - specified user, local path, default mode
 * </ul>
 *
 * <p>To open file use either local file name or standard file URL syntax:
 * <ul>
 * <li> data.txt
 * <li> path/data.txt
 * <li> /absolute-path/data.txt
 * <li> file:data.txt
 * <li> file:path/data.txt
 * <li> file:/absolute-path/data.txt
 * <li> file:/C:/absolute-path/data.txt
 * </ul>
 */
public class URLInputStream extends FilterInputStream {

    // ===================== private static constants =====================

    private static final int CONNECT_TIMEOUT = SystemProperties.getIntProperty(
        URLInputStream.class, "connectTimeout", 30000);
    private static final int READ_TIMEOUT = SystemProperties.getIntProperty(
        URLInputStream.class, "readTimeout", 60000);

    // =====================  public static methods =====================

    /**
     * Resolves specified URL in the context of the current user directory.
     *
     * @param url the URL
     * @return resolved URL
     * @throws MalformedURLException if URL cannot be parsed
     */
    public static URL resolveURL(String url) throws MalformedURLException {
        if (url.length() > 2 && url.charAt(1) == ':' && File.separatorChar == '\\')
            url = "/" + url; // special case for full file path with drive letter on windows
        return new URL(new File(".").toURL(), url);
    }

    /**
     * Opens {@link URLConnection} for specified URL.
     * This method {@link #resolveURL(String) resolves} specified URL first, for a proper support of file name.
     *
     * <p>Use {@link #checkConnectionResponseCode(URLConnection) checkConnectionResponseCode} after establishing
     * connection to ensure that it was Ok.
     *
     * <p>This is a shortcut for
     * <code>{@link #openConnection(URL, String, String) openConnection}({@link #resolveURL(String) resolveURL}(url),
     * <b>null</b>, <b>null</b>)</code>.
     *
     * @param url the URL
     * @return opened URLConnection
     * @throws IOException if an I/O error occurs
     */
    public static URLConnection openConnection(String url) throws IOException {
        return openConnection(resolveURL(url), null);
    }

    /**
     * Opens {@link URLConnection} for specified URL with specified credentials.
     * Credentials are used only if any of user or password is non-empty.
     * Specified credentials take precedence over authentication information that is supplied to this method
     * as part of URL user info like {@code "http://user:password@host:port/path/file"}.
     *
     * <p>Use {@link #checkConnectionResponseCode(URLConnection) checkConnectionResponseCode} after establishing
     * connection to ensure that it was Ok.
     *
     * @param url the URL
     * @param user the username, may be null
     * @param password the password, may be null
     * @return opened URLConnection
     * @throws IOException if an I/O error occurs
     */
    public static URLConnection openConnection(URL url, String user, String password) throws IOException {
        return openConnection(url, AuthToken.createBasicTokenOrNull(user, password));
    }

    /**
     * Opens {@link URLConnection} for specified URL with specified credentials.
     * Specified non-null token takes precedence over authentication information that is supplied to this method
     * as part of URL user info like {@code "http://user:password@host:port/path/file"}.
     *
     * <p>Use {@link #checkConnectionResponseCode(URLConnection) checkConnectionResponseCode} after establishing
     * connection to ensure that it was Ok.
     *
     * @param url the URL
     * @param token the token, may be null
     * @return opened URLConnection
     * @throws IOException if an I/O error occurs
     */
    public static URLConnection openConnection(URL url, AuthToken token) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setAllowUserInteraction(false);
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);

        AuthToken requestToken = token;
        if (token == null && url.getUserInfo() != null && !url.getUserInfo().isEmpty())
            requestToken = AuthToken.createBasicToken(url.getUserInfo());
        if (requestToken != null)
            connection.setRequestProperty("Authorization", requestToken.getHttpAuthorization());
        return connection;
    }

    /**
     * Checks connection response code and throws {@link IOException} if it is not Ok.
     *
     * @param connection URLConnection
     * @throws IOException if an I/O error occurs or if connection response code is not Ok
     */
    public static void checkConnectionResponseCode(URLConnection connection) throws IOException {
        if (connection instanceof HttpURLConnection &&
            ((HttpURLConnection) connection).getResponseCode() != HttpURLConnection.HTTP_OK)
        {
            throw new IOException("Unexpected response: " + connection.getHeaderField(0));
        }
    }

    /**
     * Reads content for specified URL and returns it as a byte array.
     * This method {@link #resolveURL(String) resolves} specified URL first, for a proper support of file name.
     *
     * <p>This is a shortcut for
     * <code>{@link #readBytes(URL, String, String) readBytes}({@link #resolveURL(String) resolveURL}(url),
     * <b>null</b>, <b>null</b>)</code>.
     *
     * @param url the URL
     * @return the byte array with read content
     * @throws IOException if an I/O error occurs
     * @deprecated use {@link #readBytes(String) readBytes} instead
     */
    @Deprecated
    public static byte[] readURL(String url) throws IOException {
        return readBytes(resolveURL(url), null, null);
    }

    /**
     * Reads content for specified URL and returns it as a byte array.
     * This method {@link #resolveURL(String) resolves} specified URL first, for a proper support of file name.
     *
     * <p>This is a shortcut for
     * <code>{@link #readBytes(URL, String, String) readBytes}({@link #resolveURL(String) resolveURL}(url),
     * <b>null</b>, <b>null</b>)</code>.
     *
     * @param url the URL
     * @return the byte array with read content
     * @throws IOException if an I/O error occurs
     */
    public static byte[] readBytes(String url) throws IOException {
        return readBytes(resolveURL(url), null, null);
    }

    /**
     * Reads content for specified URL with specified credentials and returns it as a byte array.
     * Credentials are used only if any of user or password is non-empty.
     * Specified credentials take precedence over authentication information that is supplied to this method
     * as part of URL user info like {@code "http://user:password@host:port/path/file"}.
     *
     * @param url the URL
     * @param user the username, may be null
     * @param password the password, may be null
     * @return the byte array with read content
     * @throws IOException if an I/O error occurs
     */
    public static byte[] readBytes(URL url, String user, String password) throws IOException {
        return readBytes(url, AuthToken.createBasicTokenOrNull(user, password));
    }

    /**
     * Reads content for specified URL with specified credentials and returns it as a byte array.
     * Specified non-null token takes precedence over authentication information that is supplied to this method
     * as part of URL user info like {@code "http://user:password@host:port/path/file"}.
     *
     * @param url the URL
     * @param token the token, may be null
     * @return the byte array with read content
     * @throws IOException if an I/O error occurs
     */
    public static byte[] readBytes(URL url, AuthToken token) throws IOException {
        try (URLInputStream in = new URLInputStream(url, token, 0)) {
            return in.readAllBytes();
        }
    }

    /**
     * Returns last modification time for specified URL. This method never returns 0.
     * <p>
     * This is a shortcut for
     * <code>{@link #getLastModified(URL, String, String) getLastModified}({@link #resolveURL(String) resolveURL}(url),
     * <b>null</b>, <b>null</b>)</code>.
     *
     * @param url the URL
     * @return last modification time
     * @throws IOException if an I/O error occurs or if last modification time is not known
     */
    public static long getLastModified(String url) throws IOException {
        return getLastModified(resolveURL(url), null, null);
    }

    /**
     * Returns last modification time for specified URL with specified credentials. This method never returns 0.
     * Credentials are used only if any of user or password is non-empty.
     * Specified credentials take precedence over authentication information that is supplied to this method
     * as part of URL user info like {@code "http://user:password@host:port/path/file"}.
     *
     * @param url the URL
     * @param user the username, may be null
     * @param password the password, may be null
     * @return last modification time
     * @throws IOException if an I/O error occurs or if last modification time is not known
     */
    public static long getLastModified(URL url, String user, String password) throws IOException {
        return getLastModified(url, AuthToken.createBasicTokenOrNull(user, password));
    }

    /**
     * Returns last modification time for specified URL with specified credentials. This method never returns 0.
     * Specified non-null token takes precedence over authentication information that is supplied to this method
     * as part of URL user info like {@code "http://user:password@host:port/path/file"}.
     *
     * @param url the URL
     * @param token the token, may be null
     * @return last modification time
     * @throws IOException if an I/O error occurs or if last modification time is not known
     */
    public static long getLastModified(URL url, AuthToken token) throws IOException {
        URLConnection connection = openConnection(url, token);
        try {
            if (connection instanceof HttpURLConnection)
                ((HttpURLConnection) connection).setRequestMethod("HEAD");
            long lastModified = connection.getLastModified();
            checkConnectionResponseCode(connection);
            if (lastModified == 0)
                throw new IOException("Last modified time is not known");
            return lastModified;
        } finally {
            // NOTE: "getLastModified" and "getResponseCode" implicitly perform "connect", need to close input stream
            connection.getInputStream().close();
        }
    }

    // =====================  instance fields =====================

    protected final URLConnection connection;

    // =====================  constructor and instance methods =====================

    /**
     * Creates a new {@code URLInputStream} instance for specified URL.
     * <p>
     * This is a shortcut for
     * <code>{@link #URLInputStream(URL, String, String) URLInputStream}({@link #resolveURL(String) resolveURL}(url),
     * <b>null</b>, <b>null</b>)</code>.
     *
     * @param url the URL
     * @throws IOException if an I/O error occurs
     */
    public URLInputStream(String url) throws IOException {
        this(resolveURL(url), null, 0);
    }

    /**
     * Creates a new {@code URLInputStream} instance for specified URL with specified credentials.
     * Credentials are used only if any of user or password is non-empty.
     * Specified credentials take precedence over authentication information that is supplied to this method
     * as part of URL user info like {@code "http://user:password@host:port/path/file"}.
     *
     * @param url the URL
     * @param user the username, may be null
     * @param password the password, may be null
     * @throws IOException if an I/O error occurs
     */
    public URLInputStream(URL url, String user, String password) throws IOException {
        this(url, AuthToken.createBasicTokenOrNull(user, password), 0);
    }

    /**
     * Creates a new {@code URLInputStream} instance for specified URL with specified credentials
     * and specified {@code If-Modified-Since} request parameter.
     * Credentials are used only if any of user or password is non-empty.
     * Specified credentials take precedence over authentication information that is supplied to this method
     * as part of URL user info like {@code "http://user:password@host:port/path/file"}.
     * The {@code If-Modified-Since} time is used only when it is not 0.
     *
     * @param url the URL
     * @param user the username, may be null
     * @param password the password, may be null
     * @param ifModifiedSince the If-Modified-Since time, may be 0
     * @throws IOException if an I/O error occurs
     */
    public URLInputStream(URL url, String user, String password, long ifModifiedSince) throws IOException {
        this(url, AuthToken.createBasicTokenOrNull(user, password), ifModifiedSince);
    }

    /**
     * Creates a new {@code URLInputStream} instance for specified URL with specified credentials
     * and specified {@code If-Modified-Since} request parameter.
     * Specified non-null token takes precedence over authentication information that is supplied to this method
     * as part of URL user info like {@code "http://user:password@host:port/path/file"}.
     * The {@code If-Modified-Since} time is used only when it is not 0.
     *
     * @param url the URL
     * @param token the token, may be null
     * @param ifModifiedSince the If-Modified-Since time, may be 0
     * @throws IOException if an I/O error occurs
     */
    public URLInputStream(URL url, AuthToken token, long ifModifiedSince) throws IOException {
        super(null);
        connection = openConnection(url, token);
        connection.setIfModifiedSince(ifModifiedSince);
        in = connection.getInputStream();
        if (connection instanceof HttpURLConnection) {
            int response = ((HttpURLConnection) connection).getResponseCode();
            boolean skipped = ifModifiedSince != 0 && response == HttpURLConnection.HTTP_NOT_MODIFIED;
            // Close on any error response except NOT_MODIFIED
            if (response != HttpURLConnection.HTTP_OK && !skipped) {
                close();
                throw new IOException("Unexpected response: " + connection.getHeaderField(0));
            }
        }
    }

    /**
     * Returns {@link URLConnection} for this {@code URLInputStream}.
     *
     * @return URLConnection for this URLInputStream
     */
    public URLConnection getConnection() {
        return connection;
    }

    /**
     * Returns last modification time from this {@code URLInputStream}.
     * Returns 0 when last modification time is not known.
     *
     * @return last modification time from this URLInputStream or 0 if unknown
     */
    public long getLastModified() {
        return connection.getLastModified();
    }

    /**
     * Determines whether content of this {@code URLInputStream} was modified according to {@code If-Modified-Since}
     * request parameter specified at creation.
     *
     * @return {@code true} if content was modified according to {@code If-Modified-Since} parameter,
     *      or parameter was not specified or was 0
     * @throws IOException if an I/O error occurs
     */
    public boolean isModifiedSince() throws IOException {
        if (connection instanceof HttpURLConnection &&
            ((HttpURLConnection) connection).getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED)
        {
            return false;
        }
        if (connection.getIfModifiedSince() != 0 && connection.getLastModified() == connection.getIfModifiedSince())
            return false;
        return true;
    }

    /**
     * Reads remaining content from this {@code URLInputStream} and returns it as a byte array.
     *
     * @return the byte array with read content
     * @throws IOException if an I/O error occurs
     */
    public byte[] readAllBytes() throws IOException {
        ByteArrayOutput out = new ByteArrayOutput(connection.getContentLength() + 1000);
        for (int n; (n = in.read(out.getBuffer(), out.getPosition(), out.getLimit() - out.getPosition())) >= 0; ) {
            out.setPosition(out.getPosition() + n);
            out.ensureCapacity(out.getPosition() + 1000);
        }
        return out.toByteArray();
    }

    @Override
    public void close() throws IOException {
        if (in != null)
            in.close();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        close();
    }
}
