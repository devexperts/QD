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
package com.devexperts.qd.dataextractor;

import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.LogUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import javax.naming.InitialContext;
import javax.naming.NamingException;

public class DataExtractorConfig {
    private static final Logging log = Logging.getLogging(DataExtractorConfig.class);

    public static final String DATA_PROPERTIES = "data.properties";
    public static final String CONFIG_CONTEXT = "java:comp/env/" + DATA_PROPERTIES;

    public static final String DATA_FILE = "data.file";
    public static final String DATA_READ_AS = "data.readAs";

    private String file;
    private MessageType readAs;

    public static final DataExtractorConfig INSTANCE = new DataExtractorConfig();

    private DataExtractorConfig() {
        Properties props = new Properties();
        try {
            // initialize default properties
            InputStream defaultConfigIn = getClass().getResourceAsStream("/" + DATA_PROPERTIES);
            try {
                props.load(defaultConfigIn);
            } finally {
                defaultConfigIn.close();
            }
            // override default properties from system properties
            // Properties.stringPropertyNames() is properly synchronized to avoid ConcurrentModificationException.
            for (String key : System.getProperties().stringPropertyNames())
                props.put(key, System.getProperty(key));
            // override props from config file
            String configFilePath = loadConfigFilePath();
            if (configFilePath == null)
                configFilePath = System.getProperty(DATA_PROPERTIES);
            if (configFilePath != null) {
                log.info("Loading config file from " + LogUtil.hideCredentials(configFilePath));
                InputStream in = new FileInputStream(configFilePath);
                try {
                    props.load(in);
                } finally {
                    in.close();
                }
            }
        } catch (IOException e) {
            log.error("Failed to load config", e);
            throw new RuntimeException(e);
        }
        // resolve values
        file = props.getProperty(DATA_FILE);
        readAs = MessageType.valueOf(props.getProperty(DATA_READ_AS).toUpperCase(Locale.US));
        // check
        if (file == null)
            throw new InvalidFormatException(DATA_FILE + " is not specified in " + DATA_PROPERTIES);
    }

    public String getFile() {
        return file;
    }

    public MessageType getReadAs() {
        return readAs;
    }

    @Override
    public String toString() {
        return "DataExtractorConfig{" +
            "file='" + file + '\'' +
            ", readAs=" + readAs +
            '}';
    }

    private static String loadConfigFilePath() {
        try {
            InitialContext ctx = new InitialContext();
            try {
                return (String) ctx.lookup(CONFIG_CONTEXT);
            } finally {
                ctx.close();
            }
        } catch (NamingException e) {
            // just ignore exception to avoid log pollution
            return null;
        }
    }
}

