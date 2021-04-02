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
package com.devexperts.io;

import com.devexperts.logging.Logging;
import com.devexperts.util.LogUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Defines context of deserialization operation. Class contains whitelist, blacklist and classLoader.
 * Whitelist and blacklist can be set in different ways:
 * <ul>
 * <li>Specified in code using {@link #createSerialClassContext(ClassLoader, Collection, Collection) createSerialClassContext}</li>
 * <li>Read from class path resources {@link #DEFAULT_BLACK_LIST_NAME} and {@link #DEFAULT_BLACK_LIST_NAME}
 *     using {@link #getDefaultSerialContext(ClassLoader) getDefaultSerialContext}</li>
 * </ul>
 * Note, this class retains {@link WeakReference weak reference} to the class loader.
 */
public class SerialClassContext {
    /**
     * Path to the resource with classes for whitelist.
     */
    public static final String DEFAULT_WHITE_LIST_NAME = "/META-INF/SerialClassWhitelist.txt";

    /**
     * Path for resource with classes for blacklist.
     */
    public static final String DEFAULT_BLACK_LIST_NAME = "/META-INF/SerialClassBlacklist.txt";

    private static final Map<ClassLoader, SerialClassContext> defaultSerialContextMap =
        Collections.synchronizedMap(new WeakHashMap<>());

    private static final String ARRAY_PREFIX = "[";

    private static final Logging log = Logging.getLogging(SerialClassContext.class);

    /**
     * Creates serial class context with a specified class loader, whitelist, and blacklist.
     * Note, the resulting class retains {@link WeakReference weak reference} to the class loader.
     *
     * @param whiteClasses classes for whitelist.
     * @param blackClasses classes for blacklist.
     * @param loader Class loader, when null uses {@link Thread#getContextClassLoader() Thread.currentThread().getContextClassLoader()}.
     * @return serial class context.
     * @see #getDefaultSerialContext(ClassLoader)
     */
    public static SerialClassContext createSerialClassContext(ClassLoader loader, Collection<String> whiteClasses, Collection<String> blackClasses) {
        return new SerialClassContext(ClassUtil.resolveContextClassLoader(loader),
            whiteClasses == null ? StringPrefixSet.ANYTHING_SET : StringPrefixSet.valueOf(whiteClasses),
            blackClasses == null ? StringPrefixSet.NOTHING_SET : StringPrefixSet.valueOf(blackClasses));
    }

    /**
     * Returns default serial class context for the specified class loader.
     * This method reads whitelist and blacklist from resources using {@link ClassLoader#getResources(String)}.
     * Note, the resulting class retains {@link WeakReference weak reference} to the class loader.
     *
     * @param loader Class loader, when null uses {@link Thread#getContextClassLoader() Thread.currentThread().getContextClassLoader()}.
     * @return serial class context.
     */
    public static SerialClassContext getDefaultSerialContext(ClassLoader loader) {
        return defaultSerialContextMap.computeIfAbsent(
            ClassUtil.resolveContextClassLoader(loader),
            SerialClassContext::readSerialClassContext);
    }

    private static SerialClassContext readSerialClassContext(ClassLoader cl) {
        StringPrefixSet whitelist = readPrefixSet(cl, DEFAULT_WHITE_LIST_NAME, StringPrefixSet.ANYTHING_SET);
        StringPrefixSet blacklist = readPrefixSet(cl, DEFAULT_BLACK_LIST_NAME, StringPrefixSet.NOTHING_SET);
        return new SerialClassContext(cl, whitelist, blacklist);
    }

    private static StringPrefixSet readPrefixSet(ClassLoader cl, String prefixSetName, StringPrefixSet def) {
        List<URL> urls;
        try {
            urls = Collections.list(cl.getResources(prefixSetName));
        } catch (IOException e) {
            return def;
        }
        if (urls.isEmpty())
            return def;
        List<String> names = new ArrayList<>();
        for (URL url : urls) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = r.readLine()) != null;) {
                    // support multiple names on a single line for compatibility
                    names.addAll(Arrays.asList(line.split(StringPrefixSet.DEFAULT_NAMES_SEPARATOR)));
                }
            } catch (IOException e) {
                log.error("Cannot read " + LogUtil.hideCredentials(url), e);
            }
        }
        return names.isEmpty() ? def : StringPrefixSet.valueOf(names);
    }

    private final StringPrefixSet whitelist;
    private final StringPrefixSet blacklist;
    private final WeakReference<ClassLoader> weakLoader;

    // Note: whitelist can be nul, blacklist cannot be
    private SerialClassContext(ClassLoader loader, StringPrefixSet whitelist, StringPrefixSet blacklist) {
        weakLoader = new WeakReference<>(loader);
        this.whitelist = whitelist;
        this.blacklist = blacklist;
    }

    public List<String> getWhitelist() {
        return whitelist.getList();
    }

    public List<String> getBlacklist() {
        return blacklist.getList();
    }

    /**
     * Returns class loader.
     * Note, this method retains {@link WeakReference weak reference} to the class loader.
     * This method may return null if all strong references to the class loader are lost and it was garbage collected.
     *
     * @return class loader.
     */
    public ClassLoader getClassLoader() {
        return weakLoader.get();
    }

    /**
     * Returns {@code true}, if class name contained in whitelist and not contained in blacklist.
     *
     * @param className class name.
     * @return {@code true}, if class name contained in whitelist and not contained in blacklist.
     * @throws NullPointerException if className is null.
     */
    public boolean accept(String className) {
        Objects.requireNonNull(className, "className");
        if (className.startsWith(ARRAY_PREFIX))
            return true;
        if (blacklist.accept(className))
            return false;
        if (!whitelist.accept(className))
            return false;
        return true;
    }


    /**
     * Throws an {@link ClassNotFoundException} if the class not contained in whitelist or contained in blacklist.
     *
     * @param className class name.
     * @throws ClassNotFoundException if the class not contained in whitelist or contained in blacklist.
     * @throws NullPointerException   if className is null.
     */
    public void check(String className) throws ClassNotFoundException {
        Objects.requireNonNull(className, "className");
        if (className.startsWith(ARRAY_PREFIX))
            return;
        if (blacklist.accept(className))
            throw new ClassNotFoundException("Class " + className + " is in the blacklist.");
        if (!whitelist.accept(className))
            throw new ClassNotFoundException("Class " + className + " is not in the whitelist.");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SerialClassContext other = (SerialClassContext) o;
        if (!whitelist.equals(other.whitelist))
            return false;
        if (!blacklist.equals(other.blacklist))
            return false;
        return Objects.equals(weakLoader.get(), other.weakLoader.get());
    }

    @Override
    public int hashCode() {
        return whitelist.hashCode() + blacklist.hashCode();
    }
}
