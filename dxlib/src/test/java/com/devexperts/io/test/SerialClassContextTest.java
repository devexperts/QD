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
package com.devexperts.io.test;

import com.devexperts.io.IOUtil;
import com.devexperts.io.SerialClassContext;
import org.junit.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SerialClassContextTest {

    @Test
    public void testEmptySerialContext() {
        SerialClassContext context1 =
            SerialClassContext.createSerialClassContext(null, null, null);
        SerialClassContext context2 =
            SerialClassContext.createSerialClassContext(null, Collections.singleton("*"), null);
        SerialClassContext context3 =
            SerialClassContext.createSerialClassContext(null, null, Collections.singleton(""));
        SerialClassContext context4 =
            SerialClassContext.createSerialClassContext(null, Collections.singleton("*"), Collections.singleton(""));
        assertEquals(context1, context2);
        assertEquals(context1, context3);
        assertEquals(context1, context4);

        checkEmpty(context1);
        checkEmpty(context2);
        checkEmpty(context3);
        checkEmpty(context4);
    }

    private void checkEmpty(SerialClassContext context) {
        assertEquals(Collections.singletonList("*"), context.getWhitelist());
        assertTrue(context.getBlacklist().isEmpty());

        checkAccept(context, Class.class.getName());
        checkAccept(context, IOUtil.class.getName());
        checkAccept(context, ClassLoader.class.getName());
    }

    @Test
    public void testAddWhiteAndBlackClasses() {
        SerialClassContext context;
        String c1 = List.class.getName();
        String c2 = Executor.class.getName();

        context = get(null, null);
        checkAccept(context, c1);
        checkAccept(context, c2);

        context = get(c1, "");
        checkAccept(context, c1);
        checkReject(context, c2, "whitelist");

        context = get(c1 + ",java.*", "");
        checkAccept(context, c1);
        checkAccept(context, c2);

        context = get(c1 + ",java.*", c1);
        checkReject(context, c1, "blacklist");
        checkAccept(context, c2);

        context = get(c1 + ",java.*", c1 + ",java.*");
        checkReject(context, c1, "blacklist");
        checkReject(context, c2, "blacklist");
    }

    private SerialClassContext get(String whitelist, String blacklist) {
        List<String> whiteClasses = whitelist == null ? null : Arrays.asList(whitelist.split(","));
        List<String> blackClasses = blacklist == null ? null : Arrays.asList(blacklist.split(","));
        return SerialClassContext.createSerialClassContext(null, whiteClasses, blackClasses);
    }

    private void checkAccept(SerialClassContext context, String className) {
        assertTrue(context.accept(className));
        try {
            context.check(className);
        } catch (Throwable t) {
            fail();
        }
    }

    private void checkReject(SerialClassContext context, String className, String listName) {
        assertFalse(context.accept(className));
        try {
            context.check(className);
            fail();
        } catch (Throwable t) {
            assertTrue(t instanceof ClassNotFoundException);
            assertTrue(t.getMessage().contains(listName));
        }
    }

    @Test
    public void testDifferentClassLoader() {
        ClassLoader loader1 = getClass().getClassLoader();
        ClassLoader loader2 = new URLClassLoader(new URL[]{});
        ClassLoader loader3 = new URLClassLoader(new URL[]{});
        assertNotEquals(getDefault(loader1), getDefault(loader2));
        assertNotEquals(getDefault(loader1), getDefault(loader3));
        assertNotEquals(getDefault(loader2), getDefault(loader3));

        assertEquals(getDefault(loader1), getDefault(loader1));
        assertEquals(getDefault(loader2), getDefault(loader2));
        assertEquals(getDefault(loader3), getDefault(loader3));
    }

    private static SerialClassContext getDefault(ClassLoader cl) {
        return SerialClassContext.getDefaultSerialContext(cl);
    }
}
