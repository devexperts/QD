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
package com.devexperts.qd.impl.hash;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;

import java.util.HashSet;

/**
 * The <code>RecordValue</code> represents a value of individual record
 * with a set of agents subscribed to this particular record.
 */
final class RecordValue extends RecordKey {

    private final int[] int_fields;
    private final Object[] obj_fields;
    private boolean available;

    private int max_agents_size;
    private HashSet<HashAgent> agents_set;
    private HashAgent[] agents; // Current snapshot of agents_set.

    RecordValue(DataRecord record, int cipher, String symbol) {
        super(record, cipher, symbol);
        this.int_fields = record.getIntFieldCount() > 0 ? new int[record.getIntFieldCount()] : null;
        this.obj_fields = record.getObjFieldCount() > 0 ? new Object[record.getObjFieldCount()] : null;
        this.agents_set = new HashSet<>();
    }

    private void checkRehash() {
        int size = agents_set.size();
        if (size < (max_agents_size >> 1) && max_agents_size > 8) {
            max_agents_size = size;
            HashSet<HashAgent> set = new HashSet<>();
            if (size > 0)
                set.addAll(agents_set);
            agents_set = set;
        }
    }

    @Override
    final void set(DataRecord record, int cipher, String symbol) {
        throw new IllegalStateException("Identity of RecordValue may not be changed.");
    }

    final boolean isAvailable() {
        return available;
    }

    final void setAvailable(boolean available) {
        this.available = available;
    }

    final int getInt(int index) {
        return int_fields[index];
    }

    final void setInt(int index, int value) {
        int_fields[index] = value;
    }

    final Object getObj(int index) {
        return obj_fields[index];
    }

    final void setObj(int index, Object value) {
        obj_fields[index] = value;
    }

    final RecordCursor getData(RecordCursor.Owner owner, RecordMode mode) {
        owner.setReadOnly(true);
        owner.setRecord(getRecord(), mode);
        owner.setSymbol(getCipher(), getSymbol());
        owner.setArrays(int_fields, obj_fields);
        owner.setOffsets(0, 0);
        return owner.cursor();
    }

    final int getAgentCount() {
        return agents_set.size();
    }

    final boolean addAgent(HashAgent agent) {
        if (agents_set.add(agent)) {
            agents = null;
            return true;
        }
        return false;
    }

    final boolean removeAgent(HashAgent agent) {
        max_agents_size = Math.max(max_agents_size, agents_set.size());
        if (agents_set.remove(agent)) {
            agents = null;
            checkRehash();
            return true;
        }
        return false;
    }

    final HashAgent[] getAgents() {
        if (agents == null)
            agents = agents_set.toArray(new HashAgent[agents_set.size()]);
        return agents;
    }

    boolean containsAgent(HashAgent agent) {
        return agents_set.contains(agent);
    }
}
