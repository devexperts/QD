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
package com.devexperts.qd.qtp;

import com.devexperts.util.SystemProperties;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

/**
 * Optional features of QTP protocol.
 */
public enum ProtocolOption {
    /**
     * History snapshot feature.
     */
    HISTORY_SNAPSHOT("hs");

    // ======================= instance =======================

    private final String tag;

    ProtocolOption(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }

    // ======================= static =======================

    private static final ProtocolOption[] VALUES = values();
    private static final int N_OPTIONS = VALUES.length;

    /**
     * Empty set of protocol options.
     */
    public static final Set EMPTY_SET = new Set(0, 0);

    /**
     * All supported protocol options.
     */
    public static final Set SUPPORTED_SET;

    static {
        String s = SystemProperties.getProperty(ProtocolOption.class, "supportedSet", null);
        SUPPORTED_SET = s != null ? parseOptSetImpl(s, null) :
            new Set(N_OPTIONS, (1 << N_OPTIONS) - 1); // all available options are supported by default
    }

    /**
     * Parses string set of options.
     * The result is an empty set when s is null, empty, or cannot be parsed.
     *
     * @param s string set of options.
     * @return set of options.
     */
    public static Set parseProtocolOptions(String s) {
        return parseOptSetImpl(s, SUPPORTED_SET);
    }

    private static Set parseOptSetImpl(String s, Set supportedSet) {
        if (s == null || s.isEmpty())
            return EMPTY_SET;
        int size = 0;
        int bits = 0;
        for (StringTokenizer st = new StringTokenizer(s, ","); st.hasMoreTokens();) {
            String t = st.nextToken();
            for (ProtocolOption value : VALUES) {
                if (t.equals(value.tag)) {
                    int mask = 1 << value.ordinal();
                    if ((bits & mask) == 0) {
                        size++;
                        bits |= mask;
                    }
                    break;
                }
            }
        }
        if (supportedSet != null && bits == supportedSet.bits)
            return supportedSet;
        return new Set(size, bits);
    }

    /**
     * Set of QTP protocol options.
     */
    public static class Set extends AbstractSet<ProtocolOption> {
        private final int size;
        private final int bits;

        Set(int size, int bits) {
            this.size = size;
            this.bits = bits;
        }

        @Override
        public Iterator<ProtocolOption> iterator() {
            return new Iterator<ProtocolOption>() {
                int i;

                { initNext(); }

                private void initNext() {
                    while (i < N_OPTIONS && ((bits & (1 << i)) == 0))
                        i++;
                }

                @Override
                public boolean hasNext() {
                    return i < N_OPTIONS;
                }

                @Override
                public ProtocolOption next() {
                    if (i >= N_OPTIONS)
                        throw new NoSuchElementException();
                    ProtocolOption result = VALUES[i];
                    initNext();
                    return result;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof ProtocolOption))
                return false;
            return (bits & (1 << ((ProtocolOption) o).ordinal())) != 0;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < N_OPTIONS; i++)
                if ((bits & (1 << i)) != 0) {
                    if (sb.length() > 0)
                        sb.append(",");
                    sb.append(VALUES[i].tag);
                }
            return sb.toString();
        }
    }
}
