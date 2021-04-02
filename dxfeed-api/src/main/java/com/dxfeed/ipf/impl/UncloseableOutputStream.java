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
package com.dxfeed.ipf.impl;

import java.io.OutputStream;

/**
 * Delegating OutputStream that does not close underlying stream.
 * @deprecated use {@link com.devexperts.io.UncloseableOutputStream} instead
 */
@Deprecated
public class UncloseableOutputStream extends com.devexperts.io.UncloseableOutputStream {
    public UncloseableOutputStream(OutputStream out) {
        super(out);
    }
}
