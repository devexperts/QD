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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class ExecutableEnvironment implements CodeGenEnvironment {
    private final Config config;

    ExecutableEnvironment(Config config) {
        this.config = config;
    }

    @Override
    public boolean hasSourceFile(ClassName className) {
        return Files.exists(config.getImplSourceRootPath().resolve(className.toSourcePath()));
    }

    @Override
    public BufferedReader openSourceFile(ClassName className) throws IOException {
        return Files.newBufferedReader(config.getImplSourceRootPath().resolve(className.toSourcePath()));
    }

    @Override
    public void writeSourceFile(ClassName className, List<String> sourceLines) throws IOException {
        Path outputFile = config.getImplSourceRootPath().resolve(className.toSourcePath());
        List<String> currentSourceLines = null;
        if (!Files.exists(outputFile)) {
            if (config.isVerifyOnly())
                throw new IOException("Generated file " + outputFile + " does not exist");
        } else {
            currentSourceLines = Files.readAllLines(outputFile);
        }
        if (currentSourceLines == null || !currentSourceLines.equals(sourceLines)) {
            if (config.isVerifyOnly())
                throw new IOException("Generated file " + outputFile + " is not up-to-date");
            System.out.println("Writing    " + outputFile);
            Files.write(outputFile, sourceLines);
        } else {
            System.out.println("Checked ok " + outputFile);
        }
    }

    @Override
    public void writeTextResourceFile(Path resourcePath, List<String> lines) throws IOException {
        throw new UnsupportedOperationException("Writing resource files not supported");
    }
}
