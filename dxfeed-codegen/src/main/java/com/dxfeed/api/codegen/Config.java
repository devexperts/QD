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
package com.dxfeed.api.codegen;

import java.nio.file.Path;
import java.nio.file.Paths;

class Config {
    static final String DXFEED_IMPL = "dxfeed-impl/src/main/java";

    private final Path implModulePath;
    private final boolean verifyOnly;

    Config(String rootDir, String outputPath, boolean verifyOnly) {
        this.verifyOnly = verifyOnly;
        this.implModulePath = createModulePath(rootDir, outputPath);
    }

    private Path createModulePath(String rootDir, String moduleName) {
        return Paths.get(rootDir, moduleName);
    }

    Path getImplSourceRootPath() {
        return implModulePath;
    }

    boolean isVerifyOnly() {
        return verifyOnly;
    }
}
