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
package com.devexperts.qd.tools;

import com.devexperts.qd.DataField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDFactory;
import com.devexperts.services.ServiceProvider;

@ToolSummary(
    info = "Dump scheme contents in a human-readable form.",
    argString = "[<records>]",
    arguments = {
        "<records> -- comma-separated list of record names"
    }
)
@ServiceProvider
public class SchemeDump extends AbstractTool {
    @Override
    protected void executeImpl(String[] args) {
        if (args.length > 1)
            wrongNumberOfArguments();
        for (DataRecord record : Tools.parseRecords(args.length == 0 ? "*" : args[0], QDFactory.getDefaultScheme()))
            printRecord(record);
    }

    public static void printRecord(DataRecord record) {
        System.out.println("Record #" + record.getId() + " : " + record.getName() +
            (record.hasTime() ? " (hasTime)" : ""));
        for (int j = 0; j < record.getIntFieldCount(); j++)
            printField("Int", j, record.getIntField(j));
        for (int j = 0; j < record.getObjFieldCount(); j++)
            printField("Obj", j, record.getObjField(j));
    }

    private static void printField(String kind, int j, DataField f) {
        System.out.println("\t" + kind + " field #" + j + " : " + f.getPropertyName() +
            " (" + f.getLocalName() + ")" +
            " : " + f.getSerialType() + " (" + f.getClass().getName() + ")");
    }

    public static void main(String[] args) {
        Tools.executeSingleTool(SchemeDump.class, args);
    }
}
