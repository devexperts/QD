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
 * The <code>DataObjField</code> defines identity and access API for <i>Obj-fields</i>.
 */
public interface DataObjField extends DataField {
    /**
     * Returns string representation of specified field value.
     * This method is used for debugging purposes.
     */
    public String toString(Object value);

    /**
     * Parses string representation of specified field value.
     * This method is used for debugging purposes.
     * @throws IllegalArgumentException if string cannot be parsed.
     */
    public Object parseString(String value);

    /**
     * Compares two specified field values for equality.
     * This method is used for implementation of ticker contract.
     */
    public boolean equals(Object value1, Object value2);

    /**
     * Writes specified field value into specified data output.
     *
     * @throws IOException as specified data output does.
     * @deprecated Use {@link #writeObj(BufferedOutput, Object)} which is faster.
     */
    public void writeObj(DataOutput out, Object value) throws IOException;

    /**
     * Writes specified field value into specified buffered output.
     *
     * @throws IOException as specified data output does.
     */
    public void writeObj(BufferedOutput out, Object value) throws IOException;

    /**
     * Reads field value from specified data input and returns it to the caller.
     *
     * @throws IOException as specified data input does.
     * @deprecated Use {@link #readObj(BufferedInput)} which is faster.
     */
    public Object readObj(DataInput in) throws IOException;

    /**
     * Reads field value from specified data input and returns it to the caller.
     *
     * @throws IOException as specified data input does.
     */
    public Object readObj(BufferedInput in) throws IOException;
}
