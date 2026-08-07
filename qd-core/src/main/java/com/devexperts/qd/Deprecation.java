/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd;

import com.devexperts.annotation.Internal;
import com.devexperts.logging.Logging;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class to notify users of QD of its deprecated features (properties, classes, etc).
 * Deprecation message is shown only once to not overflow logs.
 */
@Internal
public class Deprecation extends AtomicReference<String> {

    private static final Logging log = Logging.getLogging(Deprecation.class);

    private static final ConcurrentMap<String, Deprecation> DEPRECATIONS = new ConcurrentHashMap<>();

    // If not null then source is added to the warning message
    private final String skipPrefix;

    // Convenient methods for common deprecation messages

    public static Deprecation of(String message) {
        return getOrCreate(message, null);
    }

    public static Deprecation ofUse(String message) {
        return of("WARNING: DEPRECATED use of " + message);
    }

    public static Deprecation ofProperty(String property, String newProperty) {
        return of("WARNING: DEPRECATED use of \"" + property + "\" property, use \"" + newProperty + "\" instead.");
    }

    public static Deprecation ofClass(Class<?> clazz) {
        return getOrCreate("WARNING: DEPRECATED use of class " + clazz.getName() + ".", clazz.getName());
    }

    public static Deprecation ofImpl(Class<?> clazz, String message) {
        return getOrCreate("WARNING: DEPRECATED use of custom " + clazz.getSimpleName() +
            " implementation class %s. Do not implement " + clazz.getSimpleName() + " interface. " + message,
            "com.devexperts.qd.");
    }

    private static Deprecation getOrCreate(String message, String skipPrefix) {
        Objects.requireNonNull(message, "message");
        return DEPRECATIONS.computeIfAbsent(message, (key) -> new Deprecation(message, skipPrefix));
    }

    private Deprecation(String message, String skipPrefix) {
        super(message);
        this.skipPrefix = skipPrefix;
    }

    public void warn() {
        String message = getAndSet(null);
        if (message != null)
            log.warn(message + sourceMessage());
    }

    public void warnClass(Class<?> clazz) {
        String message = getAndSet(null);
        if (message != null)
            log.warn(String.format(message, clazz.getName()) + sourceMessage());
    }

    // Utility methods

    private String sourceMessage() {
        return (skipPrefix != null) ? (" Found at: " + getSource(skipPrefix)) : "";
    }

    private static String getSource(String skipPrefix) {
        StackTraceElement[] trace = new Exception().getStackTrace();
        for (StackTraceElement ste : trace) {
            if (ste.getClassName().equals("com.devexperts.qd.Deprecation") ||
                ste.getClassName().startsWith("java.lang.reflect.Method") ||
                ste.getClassName().startsWith("sun.reflect.") ||
                ste.getClassName().startsWith(skipPrefix))
            {
                continue;
            }
            return ste.getClassName() + "." + ste.getMethodName();
        }
        return "<unknown>";
    }
}
