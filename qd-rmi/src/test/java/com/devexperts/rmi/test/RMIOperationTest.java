/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.test;

import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIServiceInterface;
import com.dxfeed.promise.Promise;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class RMIOperationTest {

    interface Bar {
        float foo(int n, String str, List<Integer> lists, Thread... threads) throws IOException;
    }

    @Test
    public void testOperationSignatures() throws NoSuchMethodException {
        Method method;
        @SuppressWarnings("rawtypes")
        Class[] paramTypes = {int.class, String.class, List.class, Thread[].class};
        method = Bar.class.getMethod("foo", paramTypes);
        String serviceName = "com.devexperts.rmi.test.RMIOperationTest$Bar";
        List<RMIOperation<?>> ops = new ArrayList<>();
        ops.add(RMIOperation.valueOf(Bar.class, method));
        ops.add(RMIOperation.valueOf(serviceName, method));
        ops.add(RMIOperation.valueOf(serviceName, float.class, "foo", paramTypes));
        String signature = serviceName + "#foo(int,java.lang.String,java.util.List,[Ljava.lang.Thread;):float";

        for (RMIOperation<?> op : ops) {
            assertEquals(op.getSignature(), signature);
        }
    }

    interface Foo {
        Promise<Integer> bar(int a, int b);
    }

    @Test
    public void testOperationWithPromise() throws NoSuchMethodException {
        Method method = Foo.class.getMethod("bar", int.class, int.class);
        RMIOperation<?> op = RMIOperation.valueOf(Foo.class.getName(), method);
        String signature =  Foo.class.getName() + "#bar(int,int):java.lang.Integer";
        assertEquals(op.getSignature(), signature);
    }

    interface Hack {
        Promise<Collection<String>> hack(Set<Integer> set);
    }

    @Test
    public void testRawTypes() throws NoSuchMethodException {
        Method method = Hack.class.getMethod("hack", Set.class);
        RMIOperation<?> op = RMIOperation.valueOf(Hack.class.getName(), method);
        String signature =  Hack.class.getName() + "#hack(java.util.Set):java.util.Collection";
        assertEquals(op.getSignature(), signature);
    }

    @RMIServiceInterface(name = "WithAnnotationXXX")
    interface WithAnnotation {
        int baz();
    }

    @Test
    public void testValueOfClassWithAnnotatedService() throws NoSuchMethodException {
        Method method = WithAnnotation.class.getMethod("baz");
        RMIOperation<Void> actual = RMIOperation.valueOf(WithAnnotation.class, method);
        RMIOperation<Void> expected = RMIOperation.valueOf("WithAnnotationXXX", method);
        assertEquals(expected, actual);

        RMIOperation<Integer> actual2 = RMIOperation.valueOf(WithAnnotation.class, Integer.class, "baz");
        RMIOperation<Integer> expected2 = RMIOperation.valueOf("WithAnnotationXXX", Integer.class, "baz");
        assertEquals(expected2, actual2);
    }
}
