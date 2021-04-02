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
package com.devexperts.qd.util;

import com.devexperts.io.IOUtil;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * The <code>CompactChar</code> utility class provides algorithms for compact
 * serialization of characters and strings. It uses <tt>CESU-8</tt> format
 * (the format close to <tt>UTF-8</tt> but with special handling of surrogate
 * characters). This is generally the same format as used by standard Java I/O
 * streams, though Java uses modified <tt>CESU-8</tt> (Java represents character
 * with code 0 using 2-byte encoding, not 1-byte as required).
 *
 * When encoding character sequences, the <code>CompactChar</code> uses
 * <code>CompactInt</code> to encode character sequence length first,
 * then serializes characters themselves. The value -1 for length is used
 * as a marker to distinguish 'null' sequence from empty sequence.
 *
 * See <A href="http://www.unicode.org/unicode/reports/tr26/tr26-2.html">CESU-8</A>
 * for format basics.
 * <p>
 * <b>Note:</b> this class is deprecated and is replaced by {@link IOUtil} class.
 * See {@link IOUtil} class, section <b>UTF</b> APU, and individual methods for documentation.
 *
 * @deprecated Use {@link IOUtil} class instead.
 */
public class CompactChar {
    /**
     * Writes specified string to the specified data output in a compact form as
     * a sequence of characters.
     * Accepts <code>null</code> string as a valid value.
     *
     * @deprecated Use {@link IOUtil#writeCharArray(DataOutput, String)} method instead.
     */
    public static void writeString(DataOutput out, String str) throws IOException {
        IOUtil.writeCharArray(out, str);
    }

    /**
     * Reads string from specified data input in a compact form as a sequence of
     * characters.
     * Returns <code>null</code> if such value was written to the stream.
     *
     * @deprecated Use {@link IOUtil#readCharArrayString} method instead.
     */
    public static String readString(DataInput in) throws IOException {
        return IOUtil.readCharArrayString(in);
    }

    /**
     * Prevents unintentional instantiation.
     */
    private CompactChar() {}
}
