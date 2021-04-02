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
import com.devexperts.qd.kit.AbstractDataField;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMapping;

import java.io.IOException;
import java.util.Map;

/**
 * The <code>DataRecord</code> defines identity and content of generic data record.
 * <p>
 * The <code>DataRecord</code> contains a set of data fields in a form of a two
 * indexed lists of Int-fields and Obj-fields respectively. For determination,
 * whenever serial access to the data fields of a record is performed (iteration,
 * visiting, serialization), all Int-fields go first in their list order, then
 * all Obj-fields go in their list order.
 */
public interface DataRecord {

    /**
     * Returns parent {@link DataScheme} of this field.
     */
    public DataScheme getScheme();

    /**
     * Returns mapping for this data record or {@code null} if there is no such mapping for this record.
     * If data record has multiple mappings then the first processed mapping will be returned.
     * The result of invoking {@link RecordMapping#getRecord() getRecord()} method on
     * the resulting record mapping (when it is non null) is equal to this data record.
     */
    public RecordMapping getMapping();

    /**
     * Returns specified mapping for this data record or {@code null} if there is no such mapping for this record.
     * The result of invoking {@link RecordMapping#getRecord() getRecord()} method on
     * the resulting record mapping (when it is non null) is equal to this data record.
     */
    public <T extends RecordMapping> T getMapping(Class<T> mappingClass);

    /**
     * Returns all mappings for this data record.
     * The result of invoking {@link RecordMapping#getRecord() getRecord()} method on
     * any of the resulting record mappings is equal to this data record.
     */
    public Map<Class<? extends RecordMapping>, RecordMapping> getMappings();

    /**
     * Returns identifier of this record.
     * The identifier coincides with record index in its parent {@link DataScheme}.
     * It is also used for identification of data record in serialized form.
     */
    public int getId();

    /**
     * Returns name of this record.
     * The name must be unique within whole {@link DataScheme}.
     * The naming convention for record names is to use CapitalizedNames
     * without separators and to prefer one-word names,
     * like "Quote", "Trade", "TimeAndSales", etc.
     */
    public String getName();

    /**
     * Determines if this record contains <i>time</i> coordinate.
     */
    public boolean hasTime();

    /**
     * Returns a number of Int-fields in this record.
     */
    public int getIntFieldCount();

    /**
     * Returns Int-field by its index within this record.
     *
     * @throws IndexOutOfBoundsException if the index is out of range
     *                                   (index &lt; 0 || index &gt;= getIntFieldCount()).
     */
    public DataIntField getIntField(int index);

    /**
     * Returns a number of Obj-fields in this record.
     */
    public int getObjFieldCount();

    /**
     * Returns Obj-field by its index within this record.
     *
     * @throws IndexOutOfBoundsException if the index if out of range
     *                                   (index &lt; 0 || index &gt;= getObjFieldCount()).
     */
    public DataObjField getObjField(int index);

    /**
     * Finds field by its full {@link #getName() name},
     * or by its {@link DataField#getLocalName() local name},
     * or by its {@link DataField#getPropertyName() property name},
     * or by its default property name that is created from its local name
     * using {@link AbstractDataField#getDefaultPropertyName(String) AbstractDataField.getDefaultPropertyName}
     * method.
     * @return data field with the corresponding full name, local or property name or {@code null} if not found.
     */
    public DataField findFieldByName(String name);

    /**
     * Updates this record's field values in <code>to</code> cursor by values <code>from</code> cursor.
     * This method is used for implementation of ticker contract.
     * @return <code>true</code> if any values were updated and listeners shall be notified on data change.
     */
    public boolean update(RecordCursor from, RecordCursor to);

    /**
     * Writes this record's field values in a binary form into a specified buffered output.
     * The bytes written should be the same as if each field is individually
     * written using {@link DataIntField#writeInt(BufferedOutput, int)} and {@link DataObjField#writeObj(BufferedOutput, Object)}.
     * @deprecated Use {@link com.devexperts.qd.qtp.BinaryQTPComposer} class
     */
    public void writeFields(BufferedOutput out, RecordCursor cursor) throws IOException;

    /**
     * Reads this record's field values in a binary from the specified buffered input.
     * The data read should be the same as if each field is individually
     * read using {@link DataIntField#readInt(BufferedInput)} and {@link DataObjField#readObj(BufferedInput)}.
     * @deprecated Use {@link com.devexperts.qd.qtp.BinaryQTPParser} class
     */
    public void readFields(BufferedInput in, RecordCursor cursor) throws IOException;
}
