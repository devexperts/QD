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

import java.io.IOException;

public class ImplVerifyMain {
    public static void main(String[] args) throws IOException {
        String rootDir = args.length > 0 ? args[0] : "";
        new ImplCodeGen(rootDir, true).run();
    }
}
