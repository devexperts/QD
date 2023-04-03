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

import com.devexperts.io.CSVFormatException;
import com.devexperts.io.CSVReader;
import com.devexperts.io.CSVWriter;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit test for {@link CSVReader} and {@link CSVWriter} classes.
 */
public class CSVTest {

    @Test
    public void testReader() {
        try {
            CSVReader r = new CSVReader(new StringReader("aaa,\"bbb\"\n,ccc,\n\nddd\r\"\"\r\nfff"));
            assertTrue(r.getLineNumber() == 1 && r.getRecordNumber() == 1);
            assertEquals(r.readField(), "aaa");
            assertEquals(r.readField(), "bbb");
            assertNull(r.readField());
            assertNull(r.readField());
            assertTrue(r.getLineNumber() == 1 && r.getRecordNumber() == 1);
            assertTrue(Arrays.equals(r.readRecord(), new String[0]));
            assertTrue(r.getLineNumber() == 2 && r.getRecordNumber() == 2);
            assertTrue(Arrays.equals(r.readRecord(), new String[] {"", "ccc", ""}));
            assertTrue(r.getLineNumber() == 3 && r.getRecordNumber() == 3);
            assertEquals(r.readField(), "");
            assertNull(r.readField());
            assertNull(r.readField());
            assertTrue(r.getLineNumber() == 3 && r.getRecordNumber() == 3);
            assertTrue(Arrays.equals(r.readRecord(), new String[0]));
            assertTrue(r.getLineNumber() == 4 && r.getRecordNumber() == 4);
            assertTrue(Arrays.equals(r.readRecord(), new String[] {"ddd"}));
            assertTrue(r.getLineNumber() == 5 && r.getRecordNumber() == 5);
            assertTrue(Arrays.equals(r.readRecord(), new String[] {""}));
            assertTrue(r.getLineNumber() == 6 && r.getRecordNumber() == 6);
            assertEquals(r.readField(), "fff");
            assertNull(r.readField());
            assertTrue(r.getLineNumber() == 6 && r.getRecordNumber() == 6);
            assertTrue(Arrays.equals(r.readRecord(), new String[0]));
            assertTrue(r.getLineNumber() == 6 && r.getRecordNumber() == 7);
            assertTrue(Arrays.equals(r.readRecord(), null));
            assertTrue(r.getLineNumber() == 6 && r.getRecordNumber() == 7);
            r.close();
        } catch (IOException e) {
            fail();
        }
        try {
            CSVReader r = new CSVReader(new StringReader("aaa,bbb\"ccc\",ddd"));
            assertEquals(r.readField(), "aaa");
            r.readField();
            fail();
        } catch (CSVFormatException e) {
            // ignore
        } catch (IOException ee) {
            fail();
        }
        try {
            CSVReader r = new CSVReader(new StringReader("aaa,\"bbb\"ccc,ddd"));
            assertEquals(r.readField(), "aaa");
            r.readField();
            fail();
        } catch (CSVFormatException e) {
            // ignore
        } catch (IOException ee) {
            fail();
        }
    }

    @Test
    public void testWriter() {
        try {
            StringWriter sw = new StringWriter();
            CSVWriter w = new CSVWriter(sw);
            assertTrue(w.getLineNumber() == 1 && w.getRecordNumber() == 1);
            w.writeField("aaa");
            w.writeField("bbb");
            assertTrue(w.getLineNumber() == 1 && w.getRecordNumber() == 1);
            w.writeRecord(null);
            assertTrue(w.getLineNumber() == 1 && w.getRecordNumber() == 2);
            w.writeRecord(new String[] {"", "ccc", null});
            assertTrue(w.getLineNumber() == 2 && w.getRecordNumber() == 3);
            w.writeField(null);
            w.writeRecord(new String[0]);
            assertTrue(w.getLineNumber() == 3 && w.getRecordNumber() == 4);
            w.writeRecord(new String[] {"ddd"});
            assertTrue(w.getLineNumber() == 4 && w.getRecordNumber() == 5);
            w.writeField("fff");
            assertTrue(w.getLineNumber() == 5 && w.getRecordNumber() == 5);
            w.writeRecord(null);
            assertTrue(w.getLineNumber() == 5 && w.getRecordNumber() == 6);
            w.close();
            assertEquals(sw.toString(), "aaa,bbb\r\n,ccc,\r\n\r\nddd\r\nfff");
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void testPipe() {
        testPipe("", 1);
        testPipe("\r\n", 2);
        testPipe(",", 1);
        testPipe(",,,", 1);
        testPipe(",,\r\n", 2);
        testPipe("aaa,bbb\r\n,ccc,\r\n\r\nddd\r\nfff", 5);
    }

    private void testPipe(String s, int count) {
        try {
            CSVReader r = new CSVReader(new StringReader(s));
            List<String[]> records = r.readAll();
            r.close();
            assertEquals(records.size(), count);
            StringWriter sw = new StringWriter();
            CSVWriter w = new CSVWriter(sw);
            w.writeAll(records);
            w.close();
            assertEquals(sw.toString(), s);
        } catch (IOException e) {
            fail();
        }
    }
}
