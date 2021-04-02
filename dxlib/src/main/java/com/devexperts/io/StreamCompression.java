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
package com.devexperts.io;

import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.SystemProperties;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Defines byte stream compression format.
 *
 * <p>Supported compression formats are:
 * <table border=1 summary="">
 * <tr><th>Name</th><th>{@link #getMimeType() Mime type}</th><th>{@link #getExtension() Extension}</th><th>File magic bytes</th><th>{@link #hasSyncFlush() Sync flush}</th></tr>
 * <tr><td>{@link #NONE}</td><td>{@code null}</td><td></td><td></td><td>Yes</td></tr>
 * <tr><td>{@link #GZIP}</td><td>application/gzip</td><td>.gz</td><td>0x1f8b</td><td>Yes</td></tr>
 * <tr><td>{@link #ZIP}</td><td>application/zip</td><td>.zip</td><td>0x504b0304</td><td>Yes</td></tr>
 * </table>
 *
 * <p>{@link #NONE} compression format serves as a null object and does not do anything.
 */
public final class StreamCompression {

    // ===================== private static constants =====================

    private static final int GZIP_BUFFER_SIZE =
        SystemProperties.getIntProperty(StreamCompression.class, "gzipBufferSize", 4096, 1, 1 << 30);
    private static final int ZIP_BUFFER_SIZE =
        SystemProperties.getIntProperty(StreamCompression.class, "zipBufferSize", 4096, 1, 1 << 30);

    private static final int DEFAULT_LEVEL = -1;
    private static final String LEVEL_PREFIX = "[";
    private static final String LEVEL_NAME = "level=";
    private static final String LEVEL_SUFFIX = "]";

    private static final int KIND_NONE = 0;
    private static final int KIND_GZIP = 1;
    private static final int KIND_ZIP = 2;

    // =====================  public static constants =====================

    /**
     * No compression.
     */
    public static final StreamCompression NONE = new StreamCompression(KIND_NONE, "none", null, "", DEFAULT_LEVEL);

    /**
     * Gzip compression format.
     */
    public static final StreamCompression GZIP = new StreamCompression(KIND_GZIP, "gzip", "application/gzip", ".gz", DEFAULT_LEVEL);

    /**
     * Zip compression format.
     */
    public static final StreamCompression ZIP = new StreamCompression(KIND_ZIP, "zip", "application/zip", ".zip", DEFAULT_LEVEL);

    // =====================  public static factory methods =====================

    /**
     * Parses stream compression format description from string. The string can contain (ignoring case):
     * "none" for {@link #NONE},
     * "gzip" for {@link #GZIP}, or
     * "zip" for {@link #ZIP}.
     * The last two can be optionally followed by "[level=X]", where X is a number from 0 to 9 (inclusive) specifying
     * desired compression level which takes effect during compression.
     *
     * <p>This method can reverse the result of{@link #toString()} method.
     *
     * @return stream compression format.
     * @throws NullPointerException   if {@code value} is {@code null}.
     * @throws InvalidFormatException if {@code value} is not a supported compression type string.
     */
    public static StreamCompression valueOf(String value) {
        String s = value;
        int level = DEFAULT_LEVEL;
        int i = s.indexOf(LEVEL_PREFIX);
        if (i >= 0 && s.endsWith(LEVEL_SUFFIX)) {
            int j = i + LEVEL_PREFIX.length() + LEVEL_NAME.length();
            if (j <= s.length() && s.substring(i + LEVEL_PREFIX.length(), j).equalsIgnoreCase(LEVEL_NAME)) {
                try {
                    level = Integer.parseInt(s.substring(j, s.length() - LEVEL_SUFFIX.length()));
                } catch (NumberFormatException e) {
                    throw new InvalidFormatException("Invalid compression '" + value + "'", e);
                }
                if (level < 0 || level > 9)
                    throw new InvalidFormatException("Invalid compression '" + value + "'");
                s = s.substring(0, i);
            }
        }
        if (s.equalsIgnoreCase(NONE.name) && level == DEFAULT_LEVEL)
            return NONE;
        StreamCompression base;
        if (s.equalsIgnoreCase(GZIP.name))
            base = GZIP;
        else if (s.equalsIgnoreCase(ZIP.name))
            base = ZIP;
        else
            throw new InvalidFormatException("Invalid compression '" + value + "'");
        if (level == DEFAULT_LEVEL)
            return base;
        return new StreamCompression(base.kind, base.name, base.mimeType, base.extension, level);
    }

    /**
     * Detects compression format by the mime type.
     *
     * @param mimeType the mime type.
     * @return detected compression format or {@link #NONE} is the mime type is not recognized.
     * @throws NullPointerException if {@code mimeType} is {@code null}.
     */
    public static StreamCompression detectCompressionByMimeType(String mimeType) {
        if (mimeType.equalsIgnoreCase(GZIP.mimeType) ||
            mimeType.equalsIgnoreCase("application/gzip") ||
            mimeType.equalsIgnoreCase("application/x-gzip"))
        {
            return GZIP;
        }
        if (mimeType.equalsIgnoreCase(ZIP.mimeType))
            return ZIP;
        return NONE;
    }

    /**
     * Detects compression format by the extension at the end of the file name.
     *
     * @param fileName the file name.
     * @return detected compression format or {@link #NONE} is the file name extension is not recognized.
     * @throws NullPointerException if {@code fileName} is {@code null}.
     */
    public static StreamCompression detectCompressionByExtension(String fileName) {
        fileName = fileName.toLowerCase(Locale.ROOT);
        if (fileName.endsWith(GZIP.extension))
            return GZIP;
        if (fileName.endsWith(ZIP.extension))
            return ZIP;
        return NONE;
    }

    /**
     * Detects compression format by the magic number in the file header. This method
     * {@link InputStream#mark(int) marks} the stream, read first 4 bytes, and
     * {@link InputStream#reset() resets} the stream to an original state.
     *
     * @param in the input stream.
     * @return detected compression format or {@link #NONE} is the header is not recognized.
     * @throws IOException              if an I/O error occurs.
     * @throws IllegalArgumentException if {@code in} does not {@link InputStream#markSupported() support mark}.
     */
    public static StreamCompression detectCompressionByHeader(InputStream in) throws IOException {
        if (!in.markSupported())
            throw new IllegalArgumentException("mark is not supported");
        int n = 4;
        byte[] buffer = new byte[n];
        in.mark(n);
        int pos = 0;
        do {
            int r = in.read(buffer, pos, n - pos);
            if (r <= 0)
                break;
            pos += r;
        } while (pos < n);
        in.reset();
        if (pos >= 2 && buffer[0] == (byte) 0x1f && buffer[1] == (byte) 0x8b)
            return GZIP;
        if (pos >= 4 && buffer[0] == 'P' && buffer[1] == 'K' && buffer[2] == 0x03 && buffer[3] == 0x04)
            return ZIP;
        return NONE;
    }

    /**
     * Detects compression format by the magic number in the file header and decompresses
     * the given input stream. This method wraps the input stream in {@link BufferedInputStream} if
     * the original stream does not {@link InputStream#markSupported() support mark} before using
     * {@link #detectCompressionByHeader(InputStream) detectCompressionByHeader} method.
     *
     * @param in the input stream.
     * @return the decompressed stream.
     * @throws IOException if an I/O error occurs.
     */
    public static InputStream detectCompressionByHeaderAndDecompress(InputStream in) throws IOException {
        if (!in.markSupported())
            in = new BufferedInputStream(in);
        return detectCompressionByHeader(in).decompress(in);
    }

    // =====================  private instance fields =====================

    private final int kind;
    private final String name;
    private final String mimeType;
    private final String extension;
    private final int level;

    // =====================  private constructor =====================

    private StreamCompression(int kind, String name, String mimeType, String extension, int level) {
        this.kind = kind;
        this.name = name;
        this.mimeType = mimeType;
        this.extension = extension;
        this.level = level;
    }

    // =====================  public instance methods =====================

    /**
     * Return mime type of this compression format. The result is
     * {@code null} for {@link #NONE},
     * "application/gzip" for {@link #GZIP}, and
     * "application/zip" for {@link #ZIP}.
     *
     * @return mime type extension string.
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Return file name extension of this compression format. The result is
     * "" (empty string) for {@link #NONE},
     * ".gz" for {@link #GZIP}, and
     * ".zip" for {@link #ZIP}.
     *
     * @return file name extension string.
     */
    public String getExtension() {
        return extension;
    }

    /**
     * Strips extension of this compression format from the end of the file if it matches.
     *
     * @param fileName the file name.
     * @return the file name without extension of this compression format or an original file name otherwise.
     * @throws NullPointerException if {@code fileName} is {@code null}.
     */
    public String stripExtension(String fileName) {
        if (fileName.endsWith(extension))
            return fileName.substring(0, fileName.length() - extension.length());
        return fileName;
    }

    /**
     * Returns true if this compression format can be used for live data streams.
     * @return true if this compression format can be used for live data streams.
     */
    public boolean hasSyncFlush() {
        return true;
    }

    /**
     * Decompresses the given input stream with this compression format.
     *
     * @param in the input stream.
     * @return the decompressed stream or an original stream if this compression format is {@link #NONE}.
     * @throws IOException if an I/O error occurs.
     */
    public InputStream decompress(InputStream in) throws IOException {
        switch (kind) {
            case KIND_NONE:
                return in;
            case KIND_GZIP:
                return new GZIPInput(in, GZIP_BUFFER_SIZE);
            case KIND_ZIP:
                return new ZipInput(in, ZIP_BUFFER_SIZE);
            default:
                throw new AssertionError();
        }
    }

    /**
     * Compresses the given output stream with this compression format.
     *
     * @param out the output stream.
     * @param name the name of the file that is being compressed. It is used by {@link #ZIP} compression format.
     * @return the compressed stream or an original stream if this compression format is {@link #NONE}.
     * @throws IOException if an I/O error occurs.
     */
    public OutputStream compress(OutputStream out, String name) throws IOException {
        switch (kind) {
            case KIND_NONE:
                return out;
            case KIND_GZIP:
                return new GZIPOutput(out, GZIP_BUFFER_SIZE, level);
            case KIND_ZIP:
                return new ZipOutput(out, ZIP_BUFFER_SIZE, level, name);
            default:
                throw new AssertionError();
        }
    }

    /**
     * Returns a string representation of the stream compression type.
     * It is represented in lower-case.
     *
     * @return a string representation of the stream compression type.
     */
    @Override
    public String toString() {
        if (level == DEFAULT_LEVEL)
            return name;
        return name + LEVEL_PREFIX + LEVEL_NAME + level + LEVEL_SUFFIX;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     *
     * @param o the reference object with which to compare.
     * @return {@code true} if this object is the same as the obj
     *         argument; {@code false} otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof StreamCompression))
            return false;
        StreamCompression that = (StreamCompression) o;
        return kind == that.kind && level == that.level;
    }

    /**
     * Returns a hash code value for the object.
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode() {
        return 31 * kind + level;
    }

    // ===================== private helper classes =====================

    private static class GZIPOutput extends GZIPOutputStream {
        GZIPOutput(OutputStream out, int size, int level) throws IOException {
            super(out, size, true);
            def.setLevel(level);
        }
    }

    private static class GZIPInput extends GZIPInputStream {
        GZIPInput(InputStream in, int size) throws IOException {
            super(in, size);
        }

        @Override
        public int available() throws IOException {
            // always return 0 because there is no reliable way to determine if read is going to block
            return 0;
        }
    }

    private static class ZipOutput extends ZipOutputStream {
        ZipOutput(OutputStream out, int size, int level, String name) throws IOException {
            super(out);
            buf = new byte[size];
            setLevel(level);
            putNextEntry(new ZipEntry(name));
        }

        @Override
        public void flush() throws IOException {
            if (!def.finished()) {
                int len;
                while ((len = def.deflate(buf, 0, buf.length, Deflater.SYNC_FLUSH)) > 0) {
                    out.write(buf, 0, len);
                    if (len < buf.length)
                        break;
                }
            }
            out.flush();
        }
    }

    private static class ZipInput extends ZipInputStream {
        ZipInput(InputStream in, int size) throws IOException {
            super(in);
            buf = new byte[size];
            // skip directory entries until the first file is encountered
            ZipEntry entry;
            do {
                entry = getNextEntry();
                if (entry == null)
                    throw new IOException("No file entries in zip");
            } while (entry.isDirectory());
        }

        @Override
        public int available() throws IOException {
            // always return 0 because there is no reliable way to determine if read is going to block
            return 0;
        }
    }
}
