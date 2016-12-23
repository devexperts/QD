/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.event.option;

import java.util.EnumSet;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.dxfeed.api.impl.EventDelegate;
import com.dxfeed.api.impl.EventDelegateFlags;
import com.dxfeed.event.option.impl.GreeksMapping;

public final class GreeksDelegate extends EventDelegate<Greeks> {
// BEGIN: CODE AUTOMATICALLY GENERATED: DO NOT MODIFY. IT IS REGENERATED BY com.dxfeed.api.codegen.ImplCodeGen
	private final GreeksMapping m;

	public GreeksDelegate(DataRecord record, QDContract contract, EnumSet<EventDelegateFlags> flags) {
		super(record, contract, flags);
		m = record.getMapping(GreeksMapping.class);
	}

	@Override
	public GreeksMapping getMapping() {
		return m;
	}

	@Override
	public Greeks createEvent() {
		return new Greeks();
	}

	@Override
	public Greeks getEvent(Greeks event, RecordCursor cursor) {
		super.getEvent(event, cursor);
		event.setEventFlags(cursor.getEventFlags());
		event.setIndex((((long)m.getTimeSeconds(cursor)) << 32) | (m.getSequence(cursor) & 0xFFFFFFFFL));
		event.setPrice(m.getPrice(cursor));
		event.setVolatility(m.getVolatility(cursor));
		event.setDelta(m.getDelta(cursor));
		event.setGamma(m.getGamma(cursor));
		event.setTheta(m.getTheta(cursor));
		event.setRho(m.getRho(cursor));
		event.setVega(m.getVega(cursor));
		return event;
	}

	@Override
	public RecordCursor putEvent(Greeks event, RecordBuffer buf) {
		RecordCursor cursor = super.putEvent(event, buf);
		cursor.setEventFlags(event.getEventFlags());
		m.setTimeSeconds(cursor, (int)(event.getIndex() >>> 32));
		m.setSequence(cursor, (int)event.getIndex());
		m.setPrice(cursor, event.getPrice());
		m.setVolatility(cursor, event.getVolatility());
		m.setDelta(cursor, event.getDelta());
		m.setGamma(cursor, event.getGamma());
		m.setTheta(cursor, event.getTheta());
		m.setRho(cursor, event.getRho());
		m.setVega(cursor, event.getVega());
		return cursor;
	}
// END: CODE AUTOMATICALLY GENERATED
}
