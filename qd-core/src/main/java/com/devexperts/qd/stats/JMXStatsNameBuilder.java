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
package com.devexperts.qd.stats;

import com.devexperts.util.JMXNameBuilder;

class JMXStatsNameBuilder extends JMXNameBuilder {
    private String type;
    private StringBuilder id = new StringBuilder();
    private boolean firstId = true;

    JMXStatsNameBuilder(String domain) {
        super(domain);
    }

    public void insertId(int i) {
        // separate from previous id
        if (firstId)
            firstId = false;
        else
            id.insert(0, '.');
        if (i > 0) // don't insert zeroes
            id.insert(0, i);
        // append letter so that it goes alphabetically - 0,1,..,9,A10,...,A99,B100,...
        if (i >= 10) {
            int max = 100;
            char letter = 'A';
            while (i >= max) {
                max *= 10;
                letter++;
            }
            id.insert(0, letter);
        }
    }

    public void insertSumModeFlag() {
        id.insert(0, '#'); // add '#' for sums
    }

    public void doneId() {
        if (type != null) {
            // append type to id, but don't add separator after '#' at all       `
            int len = id.length();
            if (len > 0 && id.charAt(len - 1) != '#') {
                if (id.charAt(len - 1) == '.')
                    id.setCharAt(len - 1, '-');
                else
                    id.append('+');
            }
            if (len == 0)
                id.append('!'); // for root stat
            id.append(type);
        }
        append("id", id.toString());
    }

    public void appendType(String type) {
        if (this.type == null)
            this.type = type;
    }

    @Override
    public void append(String key, String rawValue) {
        if (key.equals("type")) // suppress type (integrate into id)
            appendType(rawValue);
        else
            super.append(key, rawValue);
    }
}
