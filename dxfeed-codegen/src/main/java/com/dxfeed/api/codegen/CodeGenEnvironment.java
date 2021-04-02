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
import java.nio.file.Path;
import java.util.List;

interface CodeGenEnvironment {
    boolean hasSourceFile(ClassName className);

    BufferedReader openSourceFile(ClassName className) throws IOException;

    void writeSourceFile(ClassName className, List<String> sourceLines) throws IOException;

    void writeTextResourceFile(Path resourcePath, List<String> lines) throws IOException;
}
