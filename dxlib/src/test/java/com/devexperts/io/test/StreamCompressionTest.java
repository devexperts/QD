/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.io.test;

import java.io.*;
import java.nio.charset.StandardCharsets;

import com.devexperts.io.*;
import junit.framework.TestCase;

public class StreamCompressionTest extends TestCase {
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

    public void testMimeType() {
        assertEquals(null, StreamCompression.NONE.getMimeType());
        assertEquals("application/gzip", StreamCompression.GZIP.getMimeType());
        assertEquals("application/zip", StreamCompression.ZIP.getMimeType());

        assertEquals(StreamCompression.NONE, StreamCompression.detectCompressionByMimeType("text/plain"));
        assertEquals(StreamCompression.NONE, StreamCompression.detectCompressionByMimeType("application/octet-stream"));
        assertEquals(StreamCompression.GZIP, StreamCompression.detectCompressionByMimeType("application/x-gzip"));
        assertEquals(StreamCompression.GZIP, StreamCompression.detectCompressionByMimeType("application/gzip"));
        assertEquals(StreamCompression.ZIP, StreamCompression.detectCompressionByMimeType("application/zip"));
    }

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

    public void testNoneCompression() throws IOException {
        checkCompression(StreamCompression.NONE);
    }

    public void testGZipCompression() throws IOException {
        checkCompression(StreamCompression.GZIP);
    }

    public void testZipCompression() throws IOException {
        checkCompression(StreamCompression.GZIP);
    }

    private void checkCompression(StreamCompression compression) throws IOException {
        String testString = "This is a test";
        byte[] testBytes = testString.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutput out = new ByteArrayOutput();
        OutputStream cOut = compression.compress(out, "test");
        cOut.write(testBytes);
        cOut.close();

        ByteArrayInput in = new ByteArrayInput(out.toByteArray());
        assertEquals(compression, StreamCompression.detectCompressionByHeader(in));

        InputStream cIn = compression.decompress(in);
        byte[] readBytes = new byte[testBytes.length];
        int readNoBytes = cIn.read(readBytes);
        assertEquals(testBytes.length, readNoBytes);
        assertEquals(testString, new String(readBytes, StandardCharsets.UTF_8));
        assertEquals(-1, cIn.read());
        cIn.close();
    }
}
