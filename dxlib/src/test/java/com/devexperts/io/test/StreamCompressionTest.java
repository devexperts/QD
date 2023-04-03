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
package com.devexperts.io.test;

import com.devexperts.io.ByteArrayOutput;
import com.devexperts.io.StreamCompression;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StreamCompressionTest {

    @Test
    public void testString() {
        assertEquals(StreamCompression.NONE, StreamCompression.valueOf("NONE"));
        assertEquals(StreamCompression.NONE, StreamCompression.valueOf("None"));
        assertEquals(StreamCompression.NONE, StreamCompression.valueOf("none"));
        assertEquals(StreamCompression.GZIP, StreamCompression.valueOf("GZIP"));
        assertEquals(StreamCompression.GZIP, StreamCompression.valueOf("GZip"));
        assertEquals(StreamCompression.GZIP, StreamCompression.valueOf("gzip"));
        assertEquals(StreamCompression.ZIP, StreamCompression.valueOf("ZIP"));
        assertEquals(StreamCompression.ZIP, StreamCompression.valueOf("Zip"));
        assertEquals(StreamCompression.ZIP, StreamCompression.valueOf("zip"));

        assertEquals("none", StreamCompression.NONE.toString());
        assertEquals("gzip", StreamCompression.GZIP.toString());
        assertEquals("zip", StreamCompression.ZIP.toString());
        assertEquals("zip[level=5]", StreamCompression.valueOf("zip[level=5]").toString());
        assertEquals("gzip[level=8]", StreamCompression.valueOf("gzip[level=8]").toString());
        assertEquals("gzip[level=1]", StreamCompression.valueOf("gzip[level=1]").toString());
    }

    @Test
    public void testMimeType() {
        assertNull(StreamCompression.NONE.getMimeType());
        assertEquals("application/gzip", StreamCompression.GZIP.getMimeType());
        assertEquals("application/zip", StreamCompression.ZIP.getMimeType());

        assertEquals(StreamCompression.NONE, StreamCompression.detectCompressionByMimeType("text/plain"));
        assertEquals(StreamCompression.NONE, StreamCompression.detectCompressionByMimeType("application/octet-stream"));
        assertEquals(StreamCompression.GZIP, StreamCompression.detectCompressionByMimeType("application/x-gzip"));
        assertEquals(StreamCompression.GZIP, StreamCompression.detectCompressionByMimeType("application/gzip"));
        assertEquals(StreamCompression.ZIP, StreamCompression.detectCompressionByMimeType("application/zip"));
    }

    @Test
    public void testExtension() {
        assertEquals("", StreamCompression.NONE.getExtension());
        assertEquals(".gz", StreamCompression.GZIP.getExtension());
        assertEquals(".zip", StreamCompression.ZIP.getExtension());

        assertEquals(StreamCompression.NONE, StreamCompression.detectCompressionByExtension("filename"));
        assertEquals(StreamCompression.NONE, StreamCompression.detectCompressionByExtension("file.txt"));
        assertEquals(StreamCompression.NONE, StreamCompression.detectCompressionByExtension("file.bin"));
        assertEquals(StreamCompression.GZIP, StreamCompression.detectCompressionByExtension("file.gz"));
        assertEquals(StreamCompression.ZIP, StreamCompression.detectCompressionByExtension("file.zip"));
    }

    @Test
    public void testNoneCompression() throws IOException {
        checkCompression(StreamCompression.NONE, "This is a test");
        checkCompression(StreamCompression.NONE, "");
    }

    @Test
    public void testGZipCompression() throws IOException {
        checkCompression(StreamCompression.GZIP, "This is a test");
        checkCompression(StreamCompression.GZIP, "");
    }

    @Test
    public void testZipCompression() throws IOException {
        checkCompression(StreamCompression.ZIP, "This is a test");
        checkCompression(StreamCompression.ZIP, "");
    }

    private void checkCompression(StreamCompression compression, String testString) throws IOException {
        byte[] testBytes = testString.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutput out = new ByteArrayOutput();
        OutputStream cOut = compression.compress(out, "test");
        cOut.write(testBytes);
        cOut.close();

        // imitate slow stream
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray()) {
            @Override
            public synchronized int read(byte[] b, int off, int len) {
                return super.read(b, off, len > 0 ? 1 : len);
            }
        };
        assertEquals(compression, StreamCompression.detectCompressionByHeader(in));

        InputStream cIn = compression.decompress(in);
        byte[] readBytes = new byte[testBytes.length * 2];
        int readNoBytes = 0;
        do {
            int r = cIn.read(readBytes, readNoBytes, readBytes.length - readNoBytes);
            if (r <= 0)
                break;
            readNoBytes += r;
        } while (readNoBytes < readBytes.length);
        assertEquals(testBytes.length, readNoBytes);
        assertEquals(testString, new String(readBytes, 0, readNoBytes, StandardCharsets.UTF_8));
        assertEquals(-1, cIn.read());
        cIn.close();
    }
}
