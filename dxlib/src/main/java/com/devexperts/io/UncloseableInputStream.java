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
package com.devexperts.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Delegating InputStream that does not close underlying stream.
 */
public class UncloseableInputStream extends FilterInputStream {
    public UncloseableInputStream(InputStream in) {
        super(in);
    }

    @Override
    public void close() throws IOException {}
}
