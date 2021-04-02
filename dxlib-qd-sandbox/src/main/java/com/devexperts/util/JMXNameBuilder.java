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
package com.devexperts.util;

import java.util.HashSet;
import java.util.Set;

public class JMXNameBuilder {
    private final StringBuffer sb = new StringBuffer(80);
    private final Set<String> keys = new HashSet<String>();

    public JMXNameBuilder() {}

    public JMXNameBuilder(String domain) {
        sb.append(domain);
        sb.append(':');
    }

    /**
     *
     * @throws IllegalArgumentException if keyProperties is improperly formatted.
     */
    public void appendKeyProperties(String keyProperties) {
        if (keyProperties == null || keyProperties.isEmpty())
            return;
        // correctly tokenize key_properties taking into account quoted values
        boolean in_key = true;
        boolean first_value_char = false;
        boolean quoted_value = false;
        String key = "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0, n = keyProperties.length(); i <= n; i++) {
            char c = i < n ? keyProperties.charAt(i) : '\0';
            if (in_key && c == '=') {
                // key done
                key = sb.toString();
                sb.setLength(0);
                in_key = false;
                first_value_char = true;
            } else if (quoted_value && c == '"') {
                // quoted value done
                quoted_value = false;
            } else if (!in_key && !quoted_value && (c == ',' || i == n)) {
                // value done
                String value = sb.toString();
                sb.setLength(0);
                in_key = true;
                first_value_char = false;
                append(key, value);
            } else if (first_value_char && c == '"') {
                quoted_value = true;
                first_value_char = false;
            } else if (quoted_value && c == '\\') {
                if (++i >= n)
                    throw new IllegalArgumentException("Invalid quoted value -- backslash at the end of string");
                c = keyProperties.charAt(i);
                switch (c) {
                case 'n':
                    sb.append('\n');
                    break;
                case '\\': case '"': case '*': case '?':
                    sb.append(c);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid character after backslash: " + c);
                }
            } else {
                sb.append(c);
                first_value_char = false;
            }
        }
        if (!in_key || sb.length() > 0)
            throw new IllegalArgumentException("Unexpected end of key properties string: " + keyProperties);
    }

    public void append(String key, String rawValue) {
        if (keys.contains(key))
            return; // already defined
        if (!keys.isEmpty())
            sb.append(',');
        keys.add(key);
        sb.append(key);
        sb.append('=');
        sb.append(quoteKeyPropertyValue(rawValue));
    }

    public boolean isEmpty() {
        return sb.length() == 0;
    }

    public String toString() {
        return sb.toString();
    }

    /**
     * Quotes key property value so that it can be safely used in JMX.
     */
    public static String quoteKeyPropertyValue(String rawValue) {
        boolean legal = true;
        for (int i = 0, n = rawValue.length(); i < n; i++)
            switch (rawValue.charAt(i)) {
            case ',': case '=': case ':': case '"': case '*': case '?':
                legal = false;
            }
        if (legal)
            return rawValue;
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0, n = rawValue.length(); i < n; i++) {
            char c = rawValue.charAt(i);
            switch (c) {
            case '\n':
                sb.append("\\n");
                break;
            case '\\': case '"': case '*': case '?':
                sb.append('\\');
                // falls through
            default:
                sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * Validates key properties string for correctness of format.
     * @throws IllegalArgumentException if key properties have invalid format.
     */
    public static void validateKeyProperties(String keyProperties) {
        if (keyProperties != null)
            new JMXNameBuilder().appendKeyProperties(keyProperties); // parses and validates
    }
}


