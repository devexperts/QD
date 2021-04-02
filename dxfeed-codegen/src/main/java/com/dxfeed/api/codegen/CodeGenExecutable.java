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
package com.dxfeed.api.codegen;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Abstraction over methods and constructors
 */
interface CodeGenExecutable {
    String getName();

    boolean isOverriding();

    List<CodeGenType> getParameters();

    CodeGenType getReturnType();

    String generateCall(String instance, String... values);

    boolean isInstanceMethod();

    <A extends Annotation> A getAnnotation(Class<A> annotationClass);

    Object getUnderlyingExecutable(); // for logging purposes
}
