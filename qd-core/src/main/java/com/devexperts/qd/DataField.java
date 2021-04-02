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

/**
 * Common super-interface for {@link DataIntField} and {@link DataObjField}. Those two interfaces are
 * the only actual interfaces that are used for all data fields in QD --
 * any data field instance implements exactly one of them.
 */
public interface DataField {
    /**
     * Returns parent {@link DataRecord} of this field.
     */
    public DataRecord getRecord();

    /**
     * Returns index of this field in its parent {@link DataRecord}.
     */
    public int getIndex();

    /**
     * Returns name of this field.
     * The name must be unique within the whole {@link DataScheme}.
     * It is required that this full field name consists of its parent record name
     * followed by dot ('.') followed by {@link #getLocalName local name} of this filed
     * like these: "Quote.Bid.Price", "Quote.Bid.Exchange", "Trade.Last.Price",
     * "TimeAndSale.Price", etc.
     */
    public String getName();

    /**
     * Returns local name of this field -- the name of this field that is unique within the record
     * and identifies this field in QTP binary protocol.
     * The naming convention for record names is to use CapitalizedNames
     * with occasional dots ('.') to separate conceptual pieces and to make local names system-wide unique
     * in certain application like these: "Bid.Price", "Bid.Exchange", "Last.Price", "Price", etc.
     * Note, that uniqueness in the data scheme of local names is not required by QD itself, but full
     * {@link #getName() names} must be unique in each data scheme.
     *
     * @see #getName()
     * @see #getPropertyName()
     */
    public String getLocalName();

    /**
     * Returns property name of this field -- the name of this field that is unique within the record
     * and identifies this field in QTP text formats.
     * The naming convention for record names is to use CapitalizedNames
     * without separators, like "BidPrice", "BidExchangeCode", "Price", etc.
     * The property name in a combination with "get" or "is" prefix serves as a method name in
     * object-oriented APIs for QD data like {@link RecordMapping}.
     *
     * <p>The property name serves as an alternative identification in QTP binary protocol, too.
     * Local names and property names should not conflict in the record.
     *
     * <p>By default, property names are constructed from {@link #getLocalName() local names} by
     * dropping dots ('.') inside them using
     * {@link AbstractDataField#getDefaultPropertyName(String) AbstractDataField.getDefaultPropertyName}
     * method, but {@link RecordMapping} can provide custom property names with
     * {@link RecordMapping#getNonDefaultPropertyName(String) getNonDefaultPropertyName} method.
     *
     * @see #getLocalName()
     */
    public String getPropertyName();

    /**
     * Returns serial type of this field. It describes the serialized form of this field in QTP protocol with
     * enough detail to skip the serialized value of this field if necessary.
     */
    public SerialFieldType getSerialType();

    public String getString(RecordCursor cursor);

    public void setString(RecordCursor cursor, String value);

    public void write(BufferedOutput out, RecordCursor cursor) throws IOException;

    public void read(BufferedInput in, RecordCursor cursor) throws IOException;
}
