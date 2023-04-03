/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.scheme;

import com.dxfeed.scheme.impl.DXSchemeFactory;
import com.dxfeed.scheme.model.SchemeModel;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class DXSchemeLoaderTypesTests {
    private static final DXSchemeFactory loader = new DXSchemeFactory();

    @Test
    public void testTypesOkBasic() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader().fromSpecification("resource:types/02-types-ok.xml").load();
        assertNotNull(file);

        assertEquals(2, file.getTypes().size());

        assertNotNull(file.getTypes().get("one"));
        assertEquals("compact_int", file.getTypes().get("one").getBase());
        assertEquals("compact_int", file.getTypes().get("one").getResolvedType());

        assertNotNull(file.getTypes().get("two"));
        assertEquals("one", file.getTypes().get("two").getBase());
        assertEquals("compact_int", file.getTypes().get("two").getResolvedType());
    }

    @Test
    public void testTypesOkOverride() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification("resource:types/09-types-ok-override-a.xml,resource:types/09-types-ok-override-b.xml")
            .load();
        assertNotNull(file);

        assertEquals(3, file.getTypes().size());

        assertNotNull(file.getTypes().get("one"));
        assertEquals("compact_int", file.getTypes().get("one").getBase());
        assertEquals("compact_int", file.getTypes().get("one").getResolvedType());

        assertNotNull(file.getTypes().get("two"));
        assertEquals("three", file.getTypes().get("two").getBase());
        assertEquals("char", file.getTypes().get("two").getResolvedType());

        assertNotNull(file.getTypes().get("three"));
        assertEquals("char", file.getTypes().get("three").getBase());
        assertEquals("char", file.getTypes().get("three").getResolvedType());
    }

    @Test(expected = SchemeException.class)
    public void testTypesErrorXMLNoName() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification("resource:types/05-types-err-noname.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testTypesErrorXMLNoBase() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification("resource:types/06-types-err-nobase.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testTypesErrorXMLDoubleDoc() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification("resource:types/07-types-err-twodoc.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testTypesErrorXMLInvalidName() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification("resource:types/08-types-err-badname.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testTypesErrorStructureDuplicateName() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification("resource:types/03-types-err-dup.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testTypesErrorStructureUnknownBase() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification("resource:types/04-types-err-unknown.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testTypesErrorStructureSelfRecurse() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification("resource:types/10-types-err-recurse.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testTypesErrorStructureLoop() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification("resource:types/11-types-err-loop.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testTypesErrorOverrideByNew() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader()
            .fromSpecification(
                "resource:types/12-types-err-override-new-a.xml,resource:types/12-types-err-override-new-b.xml")
            .load());
    }

    @Test(expected = SchemeException.class)
    public void testTypesErrorLeftWithUpdate() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification("resource:types/13-types-err-upd.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testTypesErrorOverrideNothingByUpdate() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader()
            .fromSpecification(
                "resource:types/14-types-err-override-upd-a.xml,resource:types/14-types-err-override-upd-b.xml")
            .load());
    }
}
