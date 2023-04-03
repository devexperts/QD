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
package com.devexperts.rmi.message;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class RMIExceptionMarshallerTest {

    private static final StackTraceElement[] EMPTY_STACK = new StackTraceElement[0];
    private Method removeStackTracesMethod;

    @Before
    public void setUp() throws NoSuchMethodException {
        removeStackTracesMethod = RMIExceptionMarshaller.class.getDeclaredMethod("removeStackTraces", Throwable.class);
        removeStackTracesMethod.setAccessible(true);
    }

    @Test
    public void testSimpleException() {
        Throwable t = new ArrayIndexOutOfBoundsException("message");
        removeStackTraces(t);
        checkStackTraces(t);
        assertEquals("message", t.getMessage());
    }

    @Test
    public void testUnmodifiableException() {
        Exception cause = new Exception("cause");
        Throwable t = new UnmodifiableThrowable("message", cause);
        removeStackTraces(t);
        checkStackTraces(t);
        assertEquals("message", t.getMessage());
        assertSame("cause", cause, t.getCause());
        assertEquals("cause", t.getCause().getMessage());
    }

    private static class UnmodifiableThrowable extends Throwable {
        public UnmodifiableThrowable(String message, Exception cause) {
            super(message, cause, false, false);
        }
    }

    @Test
    public void testExceptionWithCause() {
        Throwable t = new ArrayIndexOutOfBoundsException("message");
        Exception cause = new Exception("cause");
        t.initCause(cause);
        removeStackTraces(t);
        checkStackTraces(t);
        assertEquals("message", t.getMessage());
        assertSame("cause", cause, t.getCause());
        assertEquals("cause", t.getCause().getMessage());
    }

    @Test
    public void testExceptionWithDeepCauses() {
        Exception cause = new Exception("root-cause");
        for (int i = 10; i > 0; i--) {
            cause = new Exception("cause" + i, cause);
        }
        Throwable t = new Exception("message", cause);
        assertNotNull(t.getCause().getCause());
        removeStackTraces(t);
        checkStackTraces(t);
        assertEquals("message", t.getMessage());
        assertSame("cause", cause, t.getCause());
        assertEquals("cause1", t.getCause().getMessage());
    }

    @Test
    public void testExceptionWithSuppressed() {
        Throwable t = new ArrayIndexOutOfBoundsException("message");
        Exception cause = new Exception("cause");
        t.initCause(cause);
        RuntimeException suppressed = new RuntimeException("suppressed");
        t.addSuppressed(suppressed);
        removeStackTraces(t);
        checkStackTraces(t);
        assertEquals("message", t.getMessage());
        assertSame("cause", cause, t.getCause());
        assertEquals("cause", t.getCause().getMessage());
        assertSame("suppressed", suppressed, t.getSuppressed()[0]);
    }

    // invoke internal RMIExceptionMarshaller.removeStackTraces method
    private void removeStackTraces(Throwable exception) {
        try {
            removeStackTracesMethod.invoke(null, exception);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkStackTraces(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            assertArrayEquals("Expected empty stack trace", EMPTY_STACK, current.getStackTrace());
            for (Throwable t : current.getSuppressed()) {
                checkStackTraces(t);
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
    }
}
