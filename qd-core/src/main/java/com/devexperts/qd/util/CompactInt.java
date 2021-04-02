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
 * The <code>CompactInt</code> utility class provides algorithms for compact
 * serialization of integer numbers. It uses encoding scheme with variable-length
 * two's complement big-endian format capable to encode 64-bits signed numbers.
 * <p>
 * The following table defines used serial format (the first byte is given in bits
 * with 'x' representing payload bit; the remaining bytes are given in bit count):
 * <pre>
 * 0xxxxxxx     - for -64 &lt;= N &lt; 64
 * 10xxxxxx  8x - for -8192 &lt;= N &lt; 8192
 * 110xxxxx 16x - for -1048576 &lt;= N &lt; 1048576
 * 1110xxxx 24x - for -134217728 &lt;= N &lt; 134217728
 * 11110xxx 32x - for -17179869184 &lt;= N &lt; 17179869184 (includes whole range of signed int)
 * 111110xx 40x - for -2199023255552 &lt;= N &lt; 2199023255552
 * 1111110x 48x - for -281474976710656 &lt;= N &lt; 281474976710656
 * 11111110 56x - for -36028797018963968 &lt;= N &lt; 36028797018963968
 * 11111111 64x - for -9223372036854775808 &lt;= N &lt; 9223372036854775808 (the range of signed long)
 * </pre>
 * <p>
 * <b>Note:</b> this class is deprecated and is replaced by {@link IOUtil} class.
 * See {@link IOUtil} class, section <b>CompactInt</b>, and individual methods for documentation.
 *
 * @deprecated Use {@link IOUtil} class instead.
 */
public class CompactInt {
    /**
     * Writes specified integer to the specified data output in a compact form.
     *
     * @deprecated Use {@link IOUtil#writeCompactInt} method instead.
     */
    public static void writeInt(DataOutput out, int n) throws IOException {
        IOUtil.writeCompactInt(out, n);
    }

    /**
     * Reads integer from specified data input in a compact form.
     * If encoded number does not fit into <code>int</code> data type,
     * then loss of precision occurs as it is type casted into
     * <code>int</code>; the number is read entirely in this case.
     *
     * @deprecated Use {@link IOUtil#readCompactInt} method instead.
     */
    public static int readInt(DataInput in) throws IOException {
        return IOUtil.readCompactInt(in);
    }

    /**
     * Writes specified long to the specified data output in a compact form.
     *
     * @deprecated Use {@link IOUtil#writeCompactLong} method instead.
     */
    public static void writeLong(DataOutput out, long l) throws IOException {
        IOUtil.writeCompactLong(out, l);
    }

    /**
     * Reads long from specified data input in a compact form.
     *
     * @deprecated Use {@link IOUtil#readCompactLong} method instead.
     */
    public static long readLong(DataInput in) throws IOException {
        return IOUtil.readCompactLong(in);
    }

    /**
     * Prevents unintentional instantiation.
     */
    private CompactInt() {}
}
