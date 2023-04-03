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
package com.devexperts.qd.test;

import com.devexperts.qd.QDFactory;
import com.devexperts.qd.impl.hash.HashFactory;
import com.devexperts.qd.impl.matrix.MatrixFactory;
import com.devexperts.qd.impl.stripe.StripedFactory;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
class QDTestBase {

    private static final int STRIPED_SIZE = 16;

    @Parameters(name = "{0}")
    public static String[] data() {
        return new String[] {
            "matrix",
            "hash",
            "striped",
        };
    }

    protected final String matrixType;
    protected final QDFactory qdf;

    public QDTestBase(String matrixType) {
        this.matrixType = matrixType;
        this.qdf = matrixType.equals("matrix") ? new MatrixFactory() :
            matrixType.equals("hash") ? new HashFactory() : new StripedFactory(STRIPED_SIZE);
    }

    public boolean isMatrix() {
        return matrixType.equals("matrix");
    }

    public boolean isHash() {
        return matrixType.equals("hash");
    }

    public boolean isStriped() {
        return matrixType.equals("striped");
    }
}
