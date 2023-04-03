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
package com.devexperts.logging.test;

import com.devexperts.logging.LogFormatter;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertNotNull;

public class ConfigLogFormatterTest extends StandardLogFormatterTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    protected void initLogFormatter() throws Exception {
        final URL fileUrl = ConfigLogFormatterTest.class.getResource("/test.logformatter.properties");
        assertNotNull(fileUrl);

        File logConfig = new File(tempFolder.getRoot(), "log.properties");
        Files.copy(Paths.get(fileUrl.toURI()), logConfig.toPath());
        System.setProperty(LogFormatter.CONFIG_FILE_PROPERTY, logConfig.toURI().toString());
    }
}
