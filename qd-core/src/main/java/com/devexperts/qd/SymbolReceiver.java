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
package com.devexperts.qd;

/**
 * The <code>SymbolReceiver</code> interface provides a way to retrieve encoded
 * cipher-symbol pair from certain methods. It exists solely because Java does not
 * allow to return multiple values from the method, and wrapping cipher-symbol pair
 * into a new object each time would generate prohibitive amount of garbage.
 */
public interface SymbolReceiver {

    /**
     * Remembers specified cipher and symbol.
     */
    public void receiveSymbol(int cipher, String symbol);
}
