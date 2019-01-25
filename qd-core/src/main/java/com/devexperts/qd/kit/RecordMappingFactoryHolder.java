/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.kit;

import java.util.*;

import com.devexperts.qd.QDLog;
import com.devexperts.qd.ng.RecordMapping;
import com.devexperts.qd.ng.RecordMappingFactory;
import com.devexperts.services.Services;

class RecordMappingFactoryHolder {
    private static final List<RecordMappingFactory> RECORD_MAPPING_FACTORIES = new ArrayList<RecordMappingFactory>();

    static {
        for (RecordMappingFactory factory : Services.createServices(RecordMappingFactory.class, null))
             RECORD_MAPPING_FACTORIES.add(factory);
    }

    static LinkedHashMap<Class<? extends RecordMapping>, RecordMapping> createMapping(DefaultRecord record) {
        LinkedHashMap<Class<? extends RecordMapping>, RecordMapping> mappings = new LinkedHashMap<>();
        for (RecordMappingFactory factory : RECORD_MAPPING_FACTORIES) {
            RecordMapping mapping = createMapping(factory, record);
            if (mapping != null)
                mappings.put(mapping.getClass(), mapping);
        }
        return mappings;
    }

    private static RecordMapping createMapping(RecordMappingFactory factory, DefaultRecord record) {
        RecordMapping mapping = null;
        try {
            mapping = factory.createMapping(record);
        } catch (IllegalArgumentException e) {
            QDLog.log.warn("Invalid record " + record.getName() + ": " + e.getMessage());
        }
        if (mapping != null) {
            if (mapping.getRecord() != record)
                throw new IllegalArgumentException("Created mapping " + mapping + " uses record " + mapping.getRecord() + " instead of record " + record);
            return mapping;
        }
        return null;
    }
}
