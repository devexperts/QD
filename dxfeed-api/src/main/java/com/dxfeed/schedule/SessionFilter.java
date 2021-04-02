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

import java.io.Serializable;

/**
 * A filter for sessions used by various search methods.
 * This class provides predefined filters for certain Session attributes,
 * although users can create their own filters to suit their needs.
 * <p>
 * Please note that sessions can be either trading or non-trading, and this distinction can be
 * either based on rules (e.g. weekends) or dictated by special occasions (e.g. holidays).
 * Different filters treat this distinction differently - some accept only trading sessions,
 * some only non-trading, and some ignore type of session altogether.
 */
public class SessionFilter implements Serializable {
    private static final long serialVersionUID = 0;

    /** Accepts any session - useful for pure schedule navigation. */
    public static final SessionFilter ANY = new SessionFilter(null, null);
    /** Accepts trading sessions only - those with <code>({@link Session#isTrading()} == true)</code>. */
    public static final SessionFilter TRADING = new SessionFilter(null, true);
    /** Accepts non-trading sessions only - those with <code>({@link Session#isTrading()} == false)</code>. */
    public static final SessionFilter NON_TRADING = new SessionFilter(null, false);

    /** Accepts any session with type {@link SessionType#NO_TRADING}. */
    public static final SessionFilter NO_TRADING = new SessionFilter(SessionType.NO_TRADING, null);
    /** Accepts any session with type {@link SessionType#PRE_MARKET}. */
    public static final SessionFilter PRE_MARKET = new SessionFilter(SessionType.PRE_MARKET, null);
    /** Accepts any session with type {@link SessionType#REGULAR}. */
    public static final SessionFilter REGULAR = new SessionFilter(SessionType.REGULAR, null);
    /** Accepts any session with type {@link SessionType#AFTER_MARKET}. */
    public static final SessionFilter AFTER_MARKET = new SessionFilter(SessionType.AFTER_MARKET, null);

    /** Required type, <code>null</code> if not relevant. */
    protected final SessionType type;
    /** Required trading flag, <code>null</code> if not relevant. */
    protected final Boolean trading;

    /**
     * Creates filter with specified type and trading flag conditions.
     * <p>
     * Both parameters specify what value corresponding attributes should have.
     * If some parameter is <code>null</code> then corresponding attribute is ignored (any value is accepted).
     *
     * @param type required type, <code>null</code> if not relevant
     * @param trading required trading flag, <code>null</code> if not relevant
     */
    public SessionFilter(SessionType type, Boolean trading) {
        this.type = type;
        this.trading = trading;
    }

    /**
     * Tests whether or not the specified session is an acceptable result.
     *
     * @param session the session to be tested
     * @return <code>true</code> if specified session is accepted
     */
    public boolean accept(Session session) {
        return (type == null || type == session.getType()) &&
            (trading == null || trading == session.isTrading());
    }

    public int hashCode() {
        return (type == null ? 0 : type.hashCode()) +
            (trading == null ? 0 : trading.hashCode());
    }

    public boolean equals(Object object) {
        if (!(object instanceof SessionFilter))
            return false;
        SessionFilter filter = (SessionFilter) object;
        return type == filter.type && trading == filter.trading;
    }

    public String toString() {
        return "SessionFilter(" + type + ", " + trading + ")";
    }
}
