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
package com.devexperts.logging.test;

import com.devexperts.logging.LogFormatter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class ConfigLogFormatterTest extends StandardLogFormatterTest {
    protected void initLogFormatter() {
        final URL file_url = ConfigLogFormatterTest.class.getResource("/test.logformatter.properties");
        final File config_file = new File(file_url.getFile());
        final File temp_file;
        FileInputStream is = null;
        FileOutputStream os = null;
        try {
            temp_file = File.createTempFile("test.logformatter", ".configuration");
            temp_file.deleteOnExit();
            is = new FileInputStream(config_file);
            os = new FileOutputStream(temp_file);
            byte[] buf = new byte[1024];
            int count;
            while ((count = is.read(buf)) != -1) {
                os.write(buf, 0, count);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (is != null)
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            if (os != null)
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }

        try {
            System.getProperties().setProperty(LogFormatter.CONFIG_FILE_PROPERTY, temp_file.toURL().toString());
        } catch (MalformedURLException e) {
            fail(e.toString());
        }
    }
}
