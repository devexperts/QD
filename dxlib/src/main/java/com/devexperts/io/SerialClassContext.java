/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.io;

import java.io.*;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.devexperts.logging.Logging;

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

    private static final WeakHashMap<ClassLoader, SerialClassContext> defaultSerialContextMap = new WeakHashMap<>();

    private static final String ARRAY_PREFIX = "[L";

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
            whiteClasses == null ? null : StringPrefixSet.valueOf(whiteClasses),
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
        if (loader == null)
            loader = ClassUtil.resolveContextClassLoader(null);
        SerialClassContext defaultContext = defaultSerialContextMap.get(loader);
        if (defaultContext == null) {
            StringPrefixSet whiteList = readPrefixSet(loader, DEFAULT_WHITE_LIST_NAME);
            StringPrefixSet blackList = readPrefixSet(loader, DEFAULT_BLACK_LIST_NAME);
            if (blackList == null)
                blackList = StringPrefixSet.NOTHING_SET;
            defaultContext = new SerialClassContext(loader, whiteList, blackList);
            defaultSerialContextMap.put(loader, defaultContext);
        }
        return defaultContext;
    }

    private static StringPrefixSet readPrefixSet(ClassLoader cl, String prefixSetName) {
        if (prefixSetName == null)
            return null;
        List<URL> urls = new ArrayList<>();
        if (cl == null)
            cl = Thread.currentThread().getContextClassLoader();
        try {
            urls.addAll(Collections.list(cl.getResources(prefixSetName)));
        } catch (IOException e) {
            return null;
        }
        if (urls.isEmpty())
            return null;
        StringPrefixSet set = null;
        for (URL url : urls) {
            try (BufferedReader r =
				     new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                String name;
                while ((name = r.readLine()) != null) {
                    if (set == null)
                        set = StringPrefixSet.valueOf(name);
                    else
                        set = set.add(StringPrefixSet.valueOf(name));
                }
            } catch (IOException e) {
                log.error("Cannot read " + url, e);
            }
        }
        return set;
    }

    @Nullable
    private StringPrefixSet whitelist;
    @Nonnull
    private StringPrefixSet blacklist;
    private final WeakReference<ClassLoader> weakLoader;

    // Note: whitelist can be nul, blacklist cannot be
    private SerialClassContext(ClassLoader loader, @Nullable StringPrefixSet whitelist, @Nonnull StringPrefixSet blacklist) {
        weakLoader = new WeakReference<>(loader);
        this.whitelist = whitelist;
        this.blacklist = blacklist;
    }

    public List<String> getWhitelist() {
        return whitelist == null ? new ArrayList<String>() : whitelist.getList();
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
    public synchronized boolean accept(String className) {
        if (className == null)
            throw new NullPointerException("className is null");
        if (className.startsWith(ARRAY_PREFIX))
            return true;
        if (blacklist.accept(className) || (whitelist != null && !whitelist.accept(className)))
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
    public synchronized void check(String className) throws ClassNotFoundException {
        if (className == null)
            throw new NullPointerException("className is null");
        if (className.startsWith(ARRAY_PREFIX))
            return;
        String message = null;
        if (blacklist.accept(className))
            message = "Class " + className + " is in the blacklist.";
        else if (whitelist != null && !whitelist.accept(className))
            message = "Class " + className + " is not in the whitelist.";

        if (message != null)
            throw new ClassNotFoundException(message);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SerialClassContext other = (SerialClassContext) o;
        if (whitelist != null ? !whitelist.equals(other.whitelist) : other.whitelist != null)
            return false;
        if (!blacklist.equals(other.blacklist))
            return false;
        return weakLoader.get().equals(other.weakLoader.get());
    }

    @Override
    public int hashCode() {
        int result = whitelist != null ? whitelist.hashCode() : 0;
        result = 31 * result + blacklist.hashCode();
        return 31 * result + weakLoader.hashCode();
    }
}
