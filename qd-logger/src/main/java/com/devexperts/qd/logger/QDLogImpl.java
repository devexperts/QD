/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.logger;

import java.io.PrintStream;

/**
 * @deprecated Use {@link com.devexperts.logging.Logging}
 */
public class QDLogImpl extends com.devexperts.qd.impl.QDLogImpl {
    public QDLogImpl(PrintStream out, PrintStream err) {
        super(out, err);
    }

    public QDLogImpl(PrintStream out) {
        super(out);
    }
}
