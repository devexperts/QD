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

import com.dxfeed.scheme.model.SchemeModel;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class DXSchemeLoaderEnumsTests {
    @Test
    public void testEnumsOkBasic() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification("resource:enums/01-enums-ok-basic.xml")
            .load();
        assertNotNull(file);

        assertEquals(2, file.getEnums().size());

        assertNotNull(file.getEnums().get("one"));
        assertEquals("Enum one", file.getEnums().get("one").getDoc());
        assertEquals(3, file.getEnums().get("one").getValuesByName().size());
        assertEquals(0, file.getEnums().get("one").getValuesByName().get("A").getOrd());
        assertEquals("Value a", file.getEnums().get("one").getValuesByName().get("A").getDoc());
        assertEquals(1, file.getEnums().get("one").getValuesByName().get("B").getOrd());
        assertNull(file.getEnums().get("one").getValuesByName().get("B").getDoc());
        assertEquals(2, file.getEnums().get("one").getValuesByName().get("C").getOrd());
        assertNull(file.getEnums().get("one").getValuesByName().get("C").getDoc());

        assertNotNull(file.getEnums().get("two"));
        assertNull(file.getEnums().get("two").getDoc());
        assertEquals(3, file.getEnums().get("two").getValuesByName().size());
        assertEquals(0, file.getEnums().get("two").getValuesByName().get("x").getOrd());
        assertNull(file.getEnums().get("two").getValuesByName().get("x").getDoc());
        assertEquals(1, file.getEnums().get("two").getValuesByName().get("y").getOrd());
        assertNull(file.getEnums().get("two").getValuesByName().get("y").getDoc());
        assertEquals(2, file.getEnums().get("two").getValuesByName().get("z").getOrd());
        assertEquals("Value z", file.getEnums().get("two").getValuesByName().get("z").getDoc());
    }

    @Test(expected = SchemeException.class)
    public void testEnumsErrNoOrd() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader()
            .fromSpecification("resource:enums/02-enums-err-noord.xml")
            .load()
        );
    }

    @Test(expected = SchemeException.class)
    public void testEnumsErrBadOrd() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader()
            .fromSpecification("resource:enums/03-enums-err-badord.xml")
            .load()
        );
    }

    @Test
    public void testEnumsOkOverride() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification("resource:enums/12-enums-ok-override-a.xml,resource:enums/12-enums-ok-override-b.xml")
            .load();
        assertNotNull(file);

        assertNotNull(file.getEnums().get("one"));
        assertEquals(3, file.getEnums().get("one").getValuesByName().size());
        assertEquals(0, file.getEnums().get("one").getValuesByName().get("A").getOrd());
        assertEquals(1, file.getEnums().get("one").getValuesByName().get("B").getOrd());
        assertEquals(2, file.getEnums().get("one").getValuesByName().get("C").getOrd());

        assertNotNull(file.getEnums().get("two"));
        assertEquals(4, file.getEnums().get("two").getValuesByName().size());
        assertEquals(0, file.getEnums().get("two").getValuesByName().get("x").getOrd());
        assertEquals("Doc override", file.getEnums().get("two").getValuesByName().get("x").getDoc());
        assertEquals(1, file.getEnums().get("two").getValuesByName().get("y").getOrd());
        assertEquals("Doc add", file.getEnums().get("two").getValuesByName().get("y").getDoc());
        assertEquals(2, file.getEnums().get("two").getValuesByName().get("z").getOrd());
        assertEquals(3, file.getEnums().get("two").getValuesByName().get("q").getOrd());

        assertNotNull(file.getEnums().get("three"));
        assertEquals(2, file.getEnums().get("three").getValuesByName().size());
        assertEquals(0, file.getEnums().get("three").getValuesByName().get("foo").getOrd());
        assertEquals(1, file.getEnums().get("three").getValuesByName().get("bar").getOrd());
    }

    @Test(expected = SchemeException.class)
    public void testEnumsErrorXMLEnumWithoutName() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification("resource:enums/05-enums-err-noname.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testEnumsErrorXMLEnumDoubleDoc() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification("resource:enums/06-enums-err-twodoc.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testEnumsErrorXMLValueWithoutName() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification("resource:enums/08-enums-err-vnoname.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testEnumsErrorXMLValueDoubleDoc() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification("resource:enums/09-enums-err-vtwodoc.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testEnumsErrorStructureDuplicateEnum() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification("resource:enums/04-enums-err-dup.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testEnumsErrorStructureDuplicateValue() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification("resource:enums/07-enums-err-vdup.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testEnumsErrorStructureDuplicateValueImplicitOrd() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification("resource:enums/10-enums-err-vorddup-a.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testEnumsErrorStructureDuplicateValueExplicitOrd() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification("resource:enums/11-enums-err-vorddup-b.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testEnumsErrOverrideName() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification(
            "resource:enums/13-enums-err-overriden-a.xml,resource:enums/13-enums-err-overriden-b.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testEnumsErrOverrideOrd() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification(
            "resource:enums/14-enums-err-overrideo-a.xml,resource:enums/14-enums-err-overrideo-b.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testEnumsErrOverrideWithNewEnum() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification(
            "resource:enums/15-enums-err-override-newe-a.xml,resource:enums/15-enums-err-override-newe-b.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testEnumsErrOverrideWithNewVal() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification(
            "resource:enums/16-enums-err-override-newv-a.xml,resource:enums/16-enums-err-override-newv-b.xml").load());
    }

    @Test(expected = SchemeException.class)
    public void testEnumsErrOverrideWithUpdateVal() throws IOException, SchemeException {
        assertNull(SchemeModel.newLoader().fromSpecification(
            "resource:enums/17-enums-err-override-updv-a.xml,resource:enums/17-enums-err-override-updv-b.xml").load());
    }
}

