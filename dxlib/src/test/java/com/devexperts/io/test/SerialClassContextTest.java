/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.io.test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import com.devexperts.io.IOUtil;
import com.devexperts.io.SerialClassContext;
import junit.framework.TestCase;

public class SerialClassContextTest extends TestCase {

	public void testEmptySerialContext() {
		SerialClassContext context1 = SerialClassContext.createSerialClassContext(null, null, null);
		SerialClassContext context2 = SerialClassContext.createSerialClassContext(null, null, Collections.singleton(""));
		assertEquals(context1, context2);

		assertTrue(context1.accept(Class.class.getName()));
		assertTrue(context1.accept(IOUtil.class.getName()));
		assertTrue(context1.accept(ClassLoader.class.getName()));
	}

	public void testAddWhiteAndBlackClasses() {
		SerialClassContext context = SerialClassContext.createSerialClassContext(null, null, null);
		assertTrue(context.accept(List.class.getName()));
		assertTrue(context.accept(Executor.class.getName()));
		try {
			context.check(List.class.getName());
			context.check(Executor.class.getName());
		} catch (Throwable t) {
			fail();
		}

		List<String> whitelist = context.getWhitelist();
		whitelist.add(List.class.getName());
		context = SerialClassContext.createSerialClassContext(null, whitelist, context.getBlacklist());
		assertTrue(context.accept(List.class.getName()));
		assertFalse(context.accept(Executor.class.getName()));
		try {
			context.check(List.class.getName());
		} catch (Throwable t) {
			fail();
		}
		try {
			context.check(Executor.class.getName());
			fail();
		} catch (Throwable t) {
			assertTrue(t instanceof ClassNotFoundException);
			assertTrue(t.getMessage().contains("whitelist"));
		}

		whitelist = context.getWhitelist();
		whitelist.add("java.*");
		context = SerialClassContext.createSerialClassContext(null, whitelist, context.getBlacklist());
		assertTrue(context.accept(List.class.getName()));
		assertTrue(context.accept(Executor.class.getName()));
		try {
			context.check(List.class.getName());
			context.check(Executor.class.getName());
		} catch (Throwable t) {
			fail();
		}

		List<String> blacklist = context.getBlacklist();
		blacklist.add(List.class.getName());
		context = SerialClassContext.createSerialClassContext(null, context.getWhitelist(), blacklist);
		assertFalse(context.accept(List.class.getName()));
		assertTrue(context.accept(Executor.class.getName()));
		try {
			context.check(List.class.getName());
			fail();
		} catch (Throwable t) {
			assertTrue(t instanceof ClassNotFoundException);
			assertTrue(t.getMessage().contains("blacklist"));
		}
		try {
			context.check(Executor.class.getName());
		} catch (Throwable t) {
			fail();
		}

		blacklist = context.getBlacklist();
		blacklist.add("java.*");
		context = SerialClassContext.createSerialClassContext(null, context.getWhitelist(), blacklist);
		assertFalse(context.accept(List.class.getName()));
		assertFalse(context.accept(Executor.class.getName()));
		try {
			context.check(List.class.getName());
			fail();
		} catch (Throwable t) {
			assertTrue(t instanceof ClassNotFoundException);
			assertTrue(t.getMessage().contains("blacklist"));
		}
		try {
			context.check(Executor.class.getName());
			fail();
		} catch (Throwable t) {
			assertTrue(t instanceof ClassNotFoundException);
			assertTrue(t.getMessage().contains("blacklist"));
		}
	}

	public void testDifferentClassLoader() {
		ClassLoader loader1 = getClass().getClassLoader();
		ClassLoader loader2 = new URLClassLoader(new URL[]{});
		ClassLoader loader3 = new URLClassLoader(new URL[]{});
		assertFalse(SerialClassContext.getDefaultSerialContext(loader1).equals(SerialClassContext.getDefaultSerialContext(loader2)));
		assertFalse(SerialClassContext.getDefaultSerialContext(loader1).equals(SerialClassContext.getDefaultSerialContext(loader3)));
		assertFalse(SerialClassContext.getDefaultSerialContext(loader2).equals(SerialClassContext.getDefaultSerialContext(loader3)));

		assertTrue(SerialClassContext.getDefaultSerialContext(loader1).equals(SerialClassContext.getDefaultSerialContext(loader1)));
		assertTrue(SerialClassContext.getDefaultSerialContext(loader2).equals(SerialClassContext.getDefaultSerialContext(loader2)));
		assertTrue(SerialClassContext.getDefaultSerialContext(loader3).equals(SerialClassContext.getDefaultSerialContext(loader3)));
	}
}
