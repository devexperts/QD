/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.codegen;

import com.devexperts.util.SystemProperties;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/**
 * Verifies that the current source code matches the code generation by {@link ImplCodeGen}.
 */
public class ImplCodeGenVerifyTest {

    @Test
    public void testGeneratedFiles() throws IOException {
        Path rootDir = Paths.get(SystemProperties.getProperty("codegenRootDir", "")).normalize();
        Path srcDir = rootDir.resolve(Config.DXFEED_IMPL);
        assertTrue("Source files folder not found", Files.isDirectory(srcDir));
        new ImplCodeGen(rootDir.toString(), true).run();
    }
}
