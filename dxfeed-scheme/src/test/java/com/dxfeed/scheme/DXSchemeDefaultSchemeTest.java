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
package com.dxfeed.scheme;

import com.devexperts.qd.DataField;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDLog;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.util.SystemProperties;
import com.dxfeed.scheme.impl.DXSchemeFactory;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DXSchemeDefaultSchemeTest {
    private static final DXSchemeFactory loader = new DXSchemeFactory();

    @Test
    public void testDefaultSchemeEquality() {
        assertNull(SystemProperties.getProperty("scheme", null));
        DataScheme external = loader.createDataScheme("ext:dxfeed");
        DataScheme internal = QDFactory.getDefaultScheme();

        QDLog.log.info("Internal digest: " + internal.getDigest());
        QDLog.log.info("External digest: " + external.getDigest());

        boolean result = true;

        // Compare scheme
        for (int ridx = 0; ridx < external.getRecordCount(); ridx++) {
            DataRecord er = external.getRecord(ridx);
            DataRecord ir = internal.findRecordByName(er.getName());
            if (ir == null) {
                QDLog.log.error("Cannot find record \"" + er.getName() + "\" in internal DXFeedScheme");
                result = false;
                continue;
            }
            if (er.getId() != ir.getId()) {
                QDLog.log.error(
                    "Record \"" + er.getName() + "\" index mismatch: ext.getId()=" + er.getId() +
                        ", int.getId()=" + ir.getId());
                result = false;
            }
            if (er.hasTime() != ir.hasTime()) {
                QDLog.log.error(
                    "Record \"" + er.getName() + "\" time mismatch: ext.hasTime()=" + er.hasTime() +
                        ", int.hasTime()=" + ir.hasTime());
                result = false;
            }
            // Skip logical shortcut
            result = compareRecord(er, "ext", ir, "int") && result;
            result = compareRecord(ir, "int", er, "ext") && result;
        }

        // Try to find records unique to internal scheme
        for (int ridx = 0; ridx < internal.getRecordCount(); ridx++) {
            DataRecord ir = internal.getRecord(ridx);
            DataRecord er = external.findRecordByName(ir.getName());
            if (er == null) {
                QDLog.log.error("Cannot find record \"" + ir.getName() + "\" in external dxfeed scheme");
                result = false;
            }
        }

        assertTrue("All check passed", result);
    }

    private boolean compareRecord(DataRecord r1, String name1, DataRecord r2, String name2) {
        boolean result = true;
        // Compare fields 0 and 1 if we have time
        if (r1.hasTime() && r2.hasTime()) {
            result = compareTimeFields(r1, name1, name2, 0, r1.getIntField(0), r2.getIntField(0));
            result = compareTimeFields(r1, name1, name2, 1, r1.getIntField(1), r2.getIntField(1)) && result;
        }
        for (int idx = 0; idx < r1.getIntFieldCount(); idx++) {
            DataField f1 = r1.getIntField(idx);
            DataField f2n, f2ln, f2pn;
            // Skip void fields
            if (f1.getSerialType() == SerialFieldType.VOID) {
                continue;
            }
            // Check, that all 3 names could be used
            if ((f2n = r2.findFieldByName(f1.getName())) == null) {
                QDLog.log.error("Record \"" + r1.getName() + "\" field with Name \"" + f1.getName() + "\"" +
                    " from " + name1 + " scheme could not be found in " + name2 + " scheme");
                result = false;
            }
            if ((f2ln = r2.findFieldByName(f1.getLocalName())) == null) {
                QDLog.log.error("Record \"" + r1.getName() + "\" field with Name \"" + f1.getName() + "\"" +
                    " from " + name1 + " scheme could not be found by local name " +
                    "\"" + f1.getLocalName() + "\" in " + name2 + " scheme");
                result = false;
            }
            if ((f2pn = r2.findFieldByName(f1.getPropertyName())) == null) {
                QDLog.log.error("Record \"" + r1.getName() + "\" field with Name \"" + f1.getName() + "\"" +
                    " from " + name1 + " scheme could not be found by property name " +
                    "\"" + f1.getPropertyName() + "\" in " + name2 + " scheme");
                result = false;
            }
            if (f2n == null && f2ln == null && f2pn == null) {
                QDLog.log.error("Record \"" + r1.getName() + "\" field with Name \"" + f1.getName() + "\"" +
                    " from " + name1 + " scheme could not be found by ANY name in " + name2 + " scheme");
                result = false;
                continue;
            }
            if (f2n != f2ln || f2n != f2pn) {
                QDLog.log.error("Record \"" + r1.getName() + "\" field with Name \"" + f1.getName() + "\"" +
                    " from " + name1 + " scheme resolves to different fields by different names in " + name2 + " scheme");
                result = false;
                continue;
            }

            if (f1.getIndex() != f2n.getIndex()) {
                QDLog.log.error(
                    "Record \"" + r1.getName() + "\" field with name \"" + f1.getName() + "\"" +
                    " index mismatch: " + name1 + ".getIndex()=" + f1.getIndex() +
                        ", " + name2 + ".getIndex()=" + f2n.getIndex());
                result = false;
            }

            // Ok, we have equal fields, compare types
            if (!compareSerialTypes(f1.getSerialType(), f2n.getSerialType())) {
                QDLog.log.error("Record \"" + r1.getName() + "\" field with Name \"" + f1.getName() + "\"" +
                    " from " + name1 + " scheme has type " + f1.getSerialType() + " and" +
                    " type " + f2n.getSerialType() + " in " + name2 + " scheme");
                result = false;
            }
        }

        for (int idx = 0; idx < r1.getObjFieldCount(); idx++) {
            DataField f1 = r1.getObjField(idx);
            DataField f2n, f2ln, f2pn;
            // Check, that all 3 names could be used
            if ((f2n = r1.findFieldByName(f1.getName())) == null) {
                QDLog.log.error("Record \"" + r1.getName() + "\" field with Name \"" + f1.getName() + "\"" +
                    " from " + name1 + " scheme could not be found in " + name2 + " scheme");
                result = false;
            }
            if ((f2ln = r1.findFieldByName(f1.getLocalName())) == null) {
                QDLog.log.error("Record \"" + r1.getName() + "\" field with Name \"" + f1.getName() + "\"" +
                    " from " + name1 + " scheme could not be found by local name " +
                    "\"" + f1.getLocalName() + "\" in " + name2 + " scheme");
                result = false;
            }
            if ((f2pn = r1.findFieldByName(f1.getPropertyName())) == null) {
                QDLog.log.error("Record \"" + r1.getName() + "\" field with Name \"" + f1.getName() + "\" " +
                    "from " + name1 + " scheme could not be found by property name " +
                    "\"" + f1.getPropertyName() + "\" in " + name2 + " scheme");
                result = false;
            }
            if (f2n == null && f2ln == null && f2pn == null) {
                QDLog.log.error("Record \"" + r1.getName() + "\" field with Name \"" + f1.getName() + "\"" +
                    " from " + name1 + " scheme could not be found by ANY name in " + name2 + " scheme");
                result = false;
                continue;
            }
            if (f2n != f2ln || f2n != f2pn) {
                QDLog.log.error("Record \"" + r1.getName() + "\" field with Name \"" + f1.getName() + "\"" +
                    " from " + name1 + " scheme resolves to different fields by different names in " + name2 + " scheme");
                result = false;
                continue;
            }
            // Ok, we have equal fields, compare types
            if (!compareSerialTypes(f1.getSerialType(), f2n.getSerialType())) {
                QDLog.log.error("Record \"" + r1.getName() + "\" field with Name \"" + f1.getName() + "\"" +
                    " from " + name1 + " scheme has type " + f1.getSerialType() + " and" +
                    " type " + f2n.getSerialType() + " in " + name2 + " scheme");
                result = false;
            }
        }
        return result;
    }

    private boolean compareTimeFields(DataRecord r1, String name1, String name2, int idx, DataIntField f1,
        DataIntField f2)
    {
        boolean result = true;
        if (!compareSerialTypes(f1.getSerialType(), f2.getSerialType())) {
            QDLog.log.error(
                "Record \"" + r1.getName() + "\" Time field #" + idx + "\" from " + name1 + " scheme" +
                    " has type " + f1.getSerialType() + " and type " + f2.getSerialType() + " in " +
                    name2 + " scheme");
            result = false;
        }
        // Names are irrelevent in case of VOID
        if (f1.getSerialType() != SerialFieldType.VOID || f2.getSerialType() != SerialFieldType.VOID) {
            if (!f1.getName().equals(f2.getName())) {
                QDLog.log.error(
                    "Record \"" + r1.getName() + "\" Time field #" + idx + "\" from " + name1 + " scheme" +
                        " has name " + f1.getName() + " and type " + f2.getName() + " in " + name2 + " scheme");
                result = false;
            }
            if (!f1.getLocalName().equals(f2.getLocalName())) {
                QDLog.log.error("Record \"" + r1.getName() + "\" Time field #" + idx + "\" from " + name1
                    + " scheme has local name " + f1.getLocalName() + " and type " + f2.getLocalName() + " in "
                    + name2 + " scheme");
                result = false;
            }
            if (!f1.getPropertyName().equals(f2.getPropertyName())) {
                QDLog.log.error("Record \"" + r1.getName() + "\" Time field #" + idx + "\" from " + name1
                    + " scheme has property name " + f1.getPropertyName() + " and type " + f2.getPropertyName() +
                    " in " + name2 + " scheme");
                result = false;
            }
        }
        return result;
    }

    private boolean compareSerialTypes(SerialFieldType t1, SerialFieldType t2) {
        return t1.hasSameRepresentationAs(t2) && t1.hasSameSerialTypeAs(t2);
    }
}
