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

import java.io.InputStream;

/**
 * Delegating InputStream that does not close underlying stream.
 * @deprecated use {@link com.devexperts.io.UncloseableInputStream} instead
 */
@Deprecated
public class UncloseableInputStream extends com.devexperts.io.UncloseableInputStream {
    public UncloseableInputStream(InputStream in) {
        super(in);
    }
}
