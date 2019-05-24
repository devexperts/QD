/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.io.test;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.devexperts.io.*;
import com.devexperts.logging.Logging;
import junit.framework.TestCase;

public class DeserializationErrorHandlingTest extends TestCase {

    private static final String DUMP_ERRORS_PROPERTY = "com.devexperts.io.ObjectDeserializer.dumpErrors";
    private static final String LOG_FILE_NAME = "deserialization_test.log";

    private static final String CLASS_LOADER_STR = "I'm a class loader";
    private static final ClassLoader CLASS_LOADER = new ClassLoader() {
        @Override
        public String toString() {
            return CLASS_LOADER_STR;
        }
    };

    private static final byte[] SMALL_BYTE_ARRAY = "Some random bytes here".getBytes();
    private static final byte[] LARGE_BYTE_ARRAY = new byte[1500];

    static {
        for (int i = 0; i < LARGE_BYTE_ARRAY.length; i++)
            LARGE_BYTE_ARRAY[i] = SMALL_BYTE_ARRAY[i % SMALL_BYTE_ARRAY.length];
    }

    private static final Class<?>[] TYPES = {Void.class, Integer.class, int[].class, Object.class};
    private static final String ERR_FILE_PATTERN = "(deserialization-\\d{8}+-\\d{6}+\\.\\d{3}+\\.dump)";

    private String dumpErrorsValue;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        dumpErrorsValue = System.setProperty(DUMP_ERRORS_PROPERTY, "true");
        removeFiles();
        Logging.configureLogFile(LOG_FILE_NAME);
    }

    @Override
    public void tearDown() throws Exception {
        Logging.configureLogFile(System.getProperty("log.file"));
        if (dumpErrorsValue == null)
            System.clearProperty(DUMP_ERRORS_PROPERTY);
        else
            System.setProperty(DUMP_ERRORS_PROPERTY, dumpErrorsValue);
        removeFiles();
        super.tearDown();
    }

    private void removeFiles() throws IOException {
        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.matches(ERR_FILE_PATTERN);
            }
        };
        for (File file : new File("").getAbsoluteFile().listFiles(filter))
            file.delete();
        new File(LOG_FILE_NAME).delete();
    }

    public void testOneObjectFewBytes() throws IOException {
        doTest(false, false);
    }

    public void testOneObjectManyBytes() throws IOException {
        doTest(false, true);
    }

    public void testSeveralObjectsFewBytes() throws IOException {
        doTest(true, false);
    }

    public void testSeveralObjectsManyBytes() throws IOException {
        doTest(true, true);
    }

    private void doTest(boolean severalObjects, boolean manyBytes) throws IOException {
        byte[] bytes = manyBytes ? LARGE_BYTE_ARRAY : SMALL_BYTE_ARRAY;
        try {
            if (severalObjects)
                Marshalled.forBytes(bytes, Marshaller.forClasses(TYPES)).getObject(CLASS_LOADER);
            else
                Marshalled.forBytes(bytes).getObject(CLASS_LOADER);
        } catch (MarshallingException me) {
            checkLog(severalObjects, manyBytes, me.getCause());
            return;
        }
        fail();
    }

    private void checkLog(boolean severalObjects, boolean manyBytes, IOException thrown) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(findLogFile()));
        try {
            assertTrue(reader.readLine().endsWith("An error has occurred while deserializing object:"));
            if (severalObjects)
                assertEquals("  Object types: [class java.lang.Void, class java.lang.Integer, class [I, class java.lang.Object]", reader.readLine());
            assertEquals("  Classloader: I'm a class loader", reader.readLine());
            if (manyBytes) {
                String bytesLine = reader.readLine();
                Matcher matcher = Pattern.compile("  Bytes were dumped into " + ERR_FILE_PATTERN).matcher(bytesLine);
                assertTrue(matcher.matches());
                checkBytesDump(matcher.group(1));
            } else
                assertEquals("  Bytes: [53 6F 6D 65 20 72 61 6E 64 6F 6D 20 62 79 74 65 73 20 68 65 72 65]", reader.readLine());

            StringWriter sw = new StringWriter();
            thrown.printStackTrace(new PrintWriter(sw));
            BufferedReader stringReader = new BufferedReader(new StringReader(sw.toString()));
            while (true) {
                String line1 = stringReader.readLine();
                String line2 = reader.readLine();
                if (line1 == null) {
                    assertNull(line2);
                    break;
                }
                assertEquals(line1, line2);
            }
            assertNull(reader.readLine());
        } finally {
            reader.close();
        }
    }

    private File findLogFile() {
        // j.u.l name
        File f = new File(LOG_FILE_NAME + ".0");
        if (f.exists())
            return f;
        // log4j name otherwise
        return new File(LOG_FILE_NAME);
    }

    private void checkBytesDump(String fileName) throws IOException {
        FileInputStream inputStream = new FileInputStream(fileName);
        try {
            int n = LARGE_BYTE_ARRAY.length;
            byte[] bytes = new byte[n];
            assertEquals(n, inputStream.read(bytes));
            for (int i = 0; i < n; i++)
                assertEquals(LARGE_BYTE_ARRAY[i], bytes[i]);
        } finally {
            inputStream.close();
        }
    }
}
