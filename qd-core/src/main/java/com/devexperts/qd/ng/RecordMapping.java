/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.ng;

import java.util.HashMap;
import java.util.Map;

import com.devexperts.qd.DataField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.kit.AbstractDataField;

/**
 * Base class for record mappings.
 * Record mapping maps record field indices onto specific getters and setters.
 * Actual implementation of this class store the indices of the corresponding data record fields and
 * provide a set of methods like
 * {@code T getXXX(RecordCursor cursor)} and
 * {@code void setXXX(RecordCursor cursor, T value)} for them.
 */
public abstract class RecordMapping {
	private final DataRecord record;
	private final Map<String, String> nonDefaultPropertyName = new HashMap<String, String>();

	/**
	 * Creates record mapping for a specified record.
	 */
	protected RecordMapping(DataRecord record) {
		this.record = record;
	}

	/**
	 * Defines non-default property name in descendant classes constructors.
	 */
	protected void putNonDefaultPropertyName(String localName, String propertyName) {
		nonDefaultPropertyName.put(localName, propertyName);
	}

	/**
	 * Returns field {@link DataField#getPropertyName() property name} that corresponding to
	 * its {@link DataField#getLocalName() local name} in case when it is not
	 * {@link AbstractDataField#getDefaultPropertyName(String) default}.
	 * @return non-default local name or {@code null} if default.
	 */
	public String getNonDefaultPropertyName(String localName) {
		return nonDefaultPropertyName.get(localName);
	}

	public Object getEventSymbolByQDSymbol(String qdSymbol) {
		return qdSymbol;
	}

	public String getQDSymbolByEventSymbol(Object symbol) {
		return symbol.toString();
	}

	/**
	 * Returns data record that this record mapping works for.
	 * The result of invoking {@link DataRecord#getMapping(Class) getMapping(mappingClass)} method on
	 * the resulting data record is equal to this mapping.
	 */
	public final DataRecord getRecord() {
		return record;
	}

	protected final int getInt(RecordCursor cursor, int int_field_index) {
		return cursor.getIntMappedImpl(record, int_field_index);
	}

	protected final Object getObj(RecordCursor cursor, int obj_field_index) {
		return cursor.getObjMappedImpl(record, obj_field_index);
	}

	protected final void setInt(RecordCursor cursor, int int_field_index, int value) {
		cursor.setIntMappedImpl(record, int_field_index, value);
	}

	protected final void setObj(RecordCursor cursor, int obj_field_index, Object value) {
		cursor.setObjMappedImpl(record, obj_field_index, value);
	}
}
