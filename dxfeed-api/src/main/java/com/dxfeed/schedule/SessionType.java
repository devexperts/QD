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
package com.dxfeed.schedule;

/**
 * Defines type of a session - what kind of trading activity is allowed (if any),
 * what rules are used, what impact on daily trading statistics it has, etc..
 * The {@link #NO_TRADING} session type is used for non-trading sessions.
 * <p>
 * Some exchanges support all session types defined here, others do not.
 * <p>
 * Some sessions may have zero duration - e.g. indices that post value once a day.
 * Such sessions can be of any appropriate type, trading or non-trading.
 */
public enum SessionType {
    /** Non-trading session type is used to mark periods of time during which trading is not allowed. */
    NO_TRADING(false),
    /** Pre-market session type marks extended trading session before regular trading hours. */
    PRE_MARKET(true),
    /** Regular session type marks regular trading hours session. */
    REGULAR(true),
    /** After-market session type marks extended trading session after regular trading hours. */
    AFTER_MARKET(true);

    private final boolean trading;

    SessionType(boolean trading) {
        this.trading = trading;
    }

    /**
     * Returns <code>true</code> if trading activity is allowed for this type of session.
     * <p>
     * Some sessions may have zero duration - e.g. indices that post value once a day.
     * Such sessions can be of any appropriate type, trading or non-trading.
     */
    public boolean isTrading() {
        return trading;
    }
}
