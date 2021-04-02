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

import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedOutput;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * The <code>DataIntField</code> defines identity and access API for <i>Int-fields</i>.
 */
public interface DataIntField extends DataField {
    /**
     * Returns string representation of specified field value.
     * This method is used for debugging purposes.
     */
    public String toString(int value);

    /**
     * Parses string representation of specified field value.
     * This method is used for debugging purposes.
     * @throws IllegalArgumentException if string cannot be parsed.
     */
    public int parseString(String value);

    /**
     * Converts raw QD int-value to meaningful double value, or to <code>Double.NaN</code> if inapplicable.
     */
    public double toDouble(int value);

    /**
     * Converts meaningful double value to raw QD int-value, or to <code>0</code> if inapplicable.
     */
    public int toInt(double value);

    /**
     * Compares two specified field values for equality.
     * This method is used for implementation of ticker contract.
     */
    public boolean equals(int value1, int value2);

    /**
     * Writes specified field value into specified data output.
     *
     * @throws IOException as specified data output does.
     * @deprecated Use {@link #writeInt(BufferedOutput, int)} which is faster.
     */
    public void writeInt(DataOutput out, int value) throws IOException;

    /**
     * Writes specified field value into specified buffered output.
     *
     * @throws IOException as specified data output does.
     */
    public void writeInt(BufferedOutput out, int value) throws IOException;

    /**
     * Reads field value from specified data input and returns it to the caller.
     *
     * @throws IOException as specified data input does.
     * @deprecated Use {@link #readInt(BufferedInput)} which is faster.
     */
    public int readInt(DataInput in) throws IOException;

    /**
     * Reads field value from specified buffered input and returns it to the caller.
     *
     * @throws IOException as specified data input does.
     */
    public int readInt(BufferedInput in) throws IOException;
}
