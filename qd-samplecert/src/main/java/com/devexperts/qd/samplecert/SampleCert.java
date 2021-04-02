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
package com.devexperts.qd.samplecert;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;

public class SampleCert {
    private static final String PASSWORD = "qdsample";

    public static final String KEY_STORE_FILE = getResourceFile("qdkeystore");
    public static final String TRUST_STORE_FILE = getResourceFile("qdtruststore");

    public static final String KEY_STORE_CONFIG = "keyStore=" + KEY_STORE_FILE + ",keyStorePassword=" + PASSWORD;
    public static final String TRUST_STORE_CONFIG = "trustStore=" + TRUST_STORE_FILE + ",trustStorePassword=" + PASSWORD;

    private SampleCert() {} // avoid creation

    public static void init() {
        // do nothing -- static section below will init on first use
    }


    static {
        System.setProperty("javax.net.ssl.keyStore", KEY_STORE_FILE);
        System.setProperty("javax.net.ssl.keyStorePassword", PASSWORD);
        System.setProperty("javax.net.ssl.trustStore", TRUST_STORE_FILE);
        System.setProperty("javax.net.ssl.trustStorePassword", PASSWORD);
    }

    private static String getResourceFile(String resource) {
        URL url = SampleCert.class.getResource("/samplecert/" + resource);
        File file = null;
        try {
            file = new File(url.toURI());
        } catch (IllegalArgumentException e) {
            // not a good file -- go on
        } catch (URISyntaxException e) {
            // not a good file -- go on
        }
        if (file == null || !file.exists()) {
            // not a file (like in archive) -- unpack to temporary file
            InputStream in = null;
            OutputStream out = null;
            try {
                file = File.createTempFile(resource, ".tmp");
                file.deleteOnExit();
                in = url.openStream();
                out = new FileOutputStream(file);
                byte[] buf = new byte[16536];
                int len;
                while ((len = in.read(buf)) > 0)
                    out.write(buf, 0, len);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                if (in != null)
                    try {
                        in.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                if (out != null)
                    try {
                        out.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
            }
        }
        // return relative to current directory
        return new File(".").getAbsoluteFile().toURI().relativize(file.getAbsoluteFile().toURI()).getPath();
    }
}
