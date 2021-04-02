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
package com.devexperts.services;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;
import java.util.Enumeration;

/**
 * {@code URLClassLoader} that always gives preferences to its "services" resources over the ones of
 * the parent class loader. This class loader should be used if services need to be loaded from external
 * user-specified jars.
 */
public class OverrideURLClassLoader extends URLClassLoader {
    public OverrideURLClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    public OverrideURLClassLoader(URL[] urls) {
        super(urls);
    }

    public OverrideURLClassLoader(URL[] urls, ClassLoader parent, URLStreamHandlerFactory factory) {
        super(urls, parent, factory);
    }

    private boolean isServiceResource(String name) {
        return name.startsWith(Services.META_INF_SERVICES);
    }

    public URL getResource(String name) {
        if (!isServiceResource(name))
            return super.getResource(name);
        URL url = findResource(name);
        if (url == null) {
            ClassLoader parent = getParent();
            url = parent == null ? getSystemResource(name) : parent.getResource(name);
        }
        return url;
    }

    public Enumeration<URL> getResources(String name) throws IOException {
        if (!isServiceResource(name))
            return super.getResources(name);
        @SuppressWarnings("unchecked")
        Enumeration<URL>[] tmp = (Enumeration<URL>[]) new Enumeration[2];
        tmp[0] = findResources(name);
        ClassLoader parent = getParent();
        tmp[1] = parent == null ? getSystemResources(name) : parent.getResources(name);
        return new SequenceEnumeration<URL>(tmp);
    }
}
