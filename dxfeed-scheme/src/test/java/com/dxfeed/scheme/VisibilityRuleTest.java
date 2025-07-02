/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.scheme;

import com.dxfeed.scheme.model.SchemeModel;
import com.dxfeed.scheme.model.SchemeRecord;
import com.dxfeed.scheme.model.VisibilityRule;
import org.junit.Before;
import org.junit.Test;

import static com.dxfeed.scheme.model.NamedEntity.Mode;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VisibilityRuleTest {
    private static final String NON_EXISTENT_RECORD = "NonExistentRecord";
    private static final String QUOTE_RECORD = "Quote";
    private static final String TIME_FIELD = "Time";
    private SchemeModel schemeModel;

    @Before
    public void setup() throws SchemeException {
        schemeModel = SchemeModel.newBuilder()
            .withName("<test-scheme>")
            .withDefaultTypes()
            .build();

        SchemeRecord quote = createSchemeRecord(QUOTE_RECORD);
        addField(quote, TIME_FIELD);
        schemeModel.addRecord(quote);
    }

    @Test
    public void testMatchWithoutUseEventName() {
        VisibilityRule rule = createVisibilityRule(QUOTE_RECORD, false, TIME_FIELD);
        assertTrue(rule.match(QUOTE_RECORD, getField(QUOTE_RECORD, TIME_FIELD)));
        assertFalse(rule.match(NON_EXISTENT_RECORD, getField(QUOTE_RECORD, TIME_FIELD)));
    }

    @Test
    public void testMatchWithUseEventName() {
        VisibilityRule rule = createVisibilityRule(QUOTE_RECORD, true, TIME_FIELD);
        assertTrue(rule.match(QUOTE_RECORD, getField(QUOTE_RECORD, TIME_FIELD)));
        assertTrue(rule.match(NON_EXISTENT_RECORD, getField(QUOTE_RECORD, TIME_FIELD)));
    }

    private SchemeRecord createSchemeRecord(String name) {
        return new SchemeRecord(name, Mode.NEW, true, "", "");
    }

    private void addField(SchemeRecord record, String fieldName) throws SchemeException {
        record.addField(fieldName, Mode.NEW, false, "", false, false, "");
    }

    private VisibilityRule createVisibilityRule(String record, boolean useEventName, String field) {
        return new VisibilityRule(record, useEventName, field, true, "");
    }

    private SchemeRecord.Field getField(String record, String fieldName) {
        return schemeModel.getRecords().get(record).getField(fieldName);
    }
}
