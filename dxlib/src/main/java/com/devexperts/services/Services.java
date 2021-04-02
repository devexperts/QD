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

import com.devexperts.io.URLInputStream;
import com.devexperts.logging.Logging;
import com.devexperts.util.SystemProperties;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Utility class that contains service-loading functionality.
 */
public class Services {
    public static final String META_INF_SERVICES = "META-INF/services/";
    private static final String DISABLE_SUFFIX = ".disable";

    @Deprecated
    protected Services() {}

    private static Logging log() {
        return Logging.getLogging(Services.class);
    }

    private static boolean startupsStarted;

    /**
     * Loads and starts all classes that provide {@link StartupService} service.
     * This happens once per JVM, regardless of the number of calls to this method.
     * @deprecated No replacement.
     */
    public static synchronized void startup() {
        if (!startupsStarted) {
            startupsStarted = true;
            for (StartupService startup : createServices(StartupService.class, null))
                try {
                    startup.start();
                } catch (Throwable t) {
                    log().error("Cannot start startup", t);
                }
        }
    }

    /**
     * Creates service that implements given class. The implementation class name to instantiate
     * is determined with the following lookup procedure:
     * <ol>
     * <li>If {@code implClassName != null} then it is used.
     * <li>If {@code loader == null} and system property with name {@code serviceClass.getName()}
     *     is defined then it is used.
     * <li>If {@code "META-INF/services/" + serviceClass.getName()} resource is defined within
     *     the specified {@code loader} then its first line is used.
     * </ol>
     *
     * If {@code loader == null}, then the resulting implementation class name from steps 1 or 2 can have
     * value of the form {@code <jar-file-or-url>!<class-name>}, where both parts are optional and jar-file
     * is distinguished from class name by ".jar" suffix. Otherwise, if {@code loader != null},
     * implementation class name can only refer to the actual name of the class and the specified loader
     * is used.
     *
     * <p> If loader is not otherwise defined as parameter or in implementation class name, then the
     * first non-null class loader is taken from the below list:
     * <ol>
     * <li> {@code serviceClass.getClassLoader()}
     * <li> {@code Thread.currentThread().getContextClassLoader()}
     * <li> The class loader of this {@code Services} class is used as the last option.
     * </ol>
     *
     * @param serviceClass Service class.
     * @param loader Default class loader (may be null). If it is null, then
     *        the lookup procedure is used.
     * @param implClassName Implementation class name (may be null). If it is null, then
     *        the lookup procedure is used.
     * @return Service instance or null if service cannot be found or created.
     */
    @SuppressWarnings("unchecked")
    public static <T> T createService(Class<T> serviceClass, ClassLoader loader, String implClassName) {
        String serviceName = serviceClass.getName();
        String className = implClassName;
        if (className == null && loader == null)
            className = SystemProperties.getProperty(serviceName, null);
        if (className != null && loader == null) {
            int i = className.indexOf('!');
            if (i < 0 && className.toLowerCase(Locale.US).endsWith(".jar")) {
                // assume jar file specification when name ends with ".jar"
                className += "!";
                i = className.length() - 1;
            }
            if (i >= 0) {
                String jarName = className.substring(0, i).trim();
                className = className.substring(i + 1).trim();
                if (jarName.length() > 0) {
                    try {
                        loader = new OverrideURLClassLoader(new URL[] { URLInputStream.resolveURL(jarName) });
                    } catch (MalformedURLException e) {
                        throw new IllegalArgumentException("Service property " + serviceName + " has malformed jar file name " + jarName);
                    }
                }
            }
            if (className.isEmpty())
                className = null;
        }
        if (className != null)
            return createOrGetInstance(serviceClass, loader, loadServiceClass(serviceClass, loader, className));
        Service serviceAnnotation = serviceClass.getAnnotation(Service.class);
        if (serviceAnnotation != null && serviceAnnotation.combineMethod().length() > 0) {
            // create from list
            List<T> list = createInOrder(serviceClass, loader);
            return list.isEmpty() ? null : list.size() == 1 ? list.get(0) :
                (T) invokeStaticMethod(serviceClass, loader, serviceAnnotation.combineMethod(), list);
        } else {
            // create just first one
            List<Class<? extends T>> list = loadServiceClasses(serviceClass, loader);
            return list.isEmpty() ? null : createOrGetInstance(serviceClass, loader, list.get(0));
        }
    }

    public static <T> Iterable<T> createServices(final Class<T> serviceClass, final ClassLoader loader) {
        return createInOrder(serviceClass, loader);
    }

    private static Object invokeStaticMethod(Class<?> serviceClass, ClassLoader loader, String methodName,
        Object argument) {
        int i = methodName.lastIndexOf('.');
        if (i >= 0) {
            String className = methodName.substring(0, i);
            methodName = methodName.substring(i + 1);
            serviceClass = loadServiceClass(serviceClass, loader, className);
            if (serviceClass == null)
                return null;
        }
        Class<?> argClass = argument.getClass();
        Method method = null;
        for (Method m : serviceClass.getMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(methodName)) {
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 1 && pts[0].isAssignableFrom(argClass)) {
                    // candidate found
                    if (method != null) {
                        log().error("Ambiguous method " + methodName + " on " + serviceClass.getName() + " for parameter class " + argClass.getName());
                        return null;
                    } else
                        method = m;
                }
            }
        }
        if (method == null) {
            log().error("Cannot find suitable method " + methodName + " on " + serviceClass.getName() + " for parameter class " + argClass.getName());
            return null;
        }
        try {
            return method.invoke(null, argument);
        } catch (Exception e) {
            log().error("Exception while invoking method " + methodName + " on " + serviceClass.getName() + " for parameter class " + argClass.getName(), e);
            return null;
        }
    }

    private static <T> List<T> createInOrder(Class<T> serviceClass, ClassLoader loader) {
        List<T> instances = new ArrayList<>();
        for (Class<?> clazz : loadServiceClasses(serviceClass, loader)) {
            T instance = createOrGetInstance(serviceClass, loader, clazz);
            if (instance != null)
                instances.add(instance);
        }
        return instances;
    }

    private static <T> ClassLoader resolveLoader(ClassLoader loader, Class<T> serviceClass) {
        if (loader == null)
            loader = serviceClass.getClassLoader();
        if (loader == null)
            loader = Thread.currentThread().getContextClassLoader();
        return loader;
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<? extends T> loadServiceClass(Class<T> serviceClass, ClassLoader loader, String className) {
        try {
            return (Class<T>) Class.forName(className.trim(), false, resolveLoader(loader, serviceClass));
        } catch (ClassNotFoundException e) {
            log().error("Cannot find " + className, e);
            return null;
        }
    }

    @SuppressWarnings({"unchecked"})
    private static <T> T createOrGetInstance(Class<T> serviceClass, ClassLoader loader, Class<?> clazz) {
        if (clazz == null)
            return null;
        try {
            Object instance = null;
            try {
                Method getInstance = clazz.getDeclaredMethod("getInstance");
                int m = getInstance.getModifiers();
                if (Modifier.isStatic(m) && Modifier.isPublic(m))
                    instance = getInstance.invoke(null);
            } catch (NoSuchMethodException e) {
                // ignore -- leave null instance
            }
            if (instance == null)
                instance = clazz.newInstance();
            instance = adaptInstanceIfNeeded(serviceClass, loader, instance);
            instance = upgradeInstanceIfNeeded(serviceClass, loader, instance);
            return (T) instance;
        } catch (Exception e) {
            log().error("Cannot create " + clazz.getName(), e);
        }
        return null;
    }

    private static Object adaptInstanceIfNeeded(Class<?> serviceClass, ClassLoader loader, Object instance) throws Exception {
        if (serviceClass.isInstance(instance))
            return instance;
        SupersedesService supersedesAnnotation = serviceClass.getAnnotation(SupersedesService.class);
        if (supersedesAnnotation != null) {
            instance = adaptInstanceIfNeeded(supersedesAnnotation.value(), loader, instance);
            if (serviceClass.isInstance(instance))
                return instance;
            if (supersedesAnnotation.adapterMethod().length() > 0) {
                instance = invokeStaticMethod(serviceClass, loader, supersedesAnnotation.adapterMethod(), instance);
                if (serviceClass.isInstance(instance))
                    return instance;
            }
        }
        throw new IllegalArgumentException(instance.getClass().getName() + " is not an instance of " + serviceClass);
    }

    private static Object upgradeInstanceIfNeeded(Class<?> serviceClass, ClassLoader loader, Object instance) {
        Service serviceAnnotation = serviceClass.getAnnotation(Service.class);
        if (serviceAnnotation != null && serviceAnnotation.upgradeMethod().length() > 0)
            instance = invokeStaticMethod(serviceClass, loader, serviceAnnotation.upgradeMethod(), instance);
        return instance;
    }

    public static <T> List<Class<? extends T>> loadServiceClasses(Class<T> serviceClass, ClassLoader loader) {
        SortedMap<Integer, List<Class<? extends T>>> ordered = new TreeMap<>();
        for (String className : getServiceClassNames(serviceClass, loader)) {
            Class<? extends T> clazz = loadServiceClass(serviceClass, loader, className);
            if (clazz == null)
                continue;
            int order = 0;
            ServiceProvider providerAnnotation = clazz.getAnnotation(ServiceProvider.class);
            if (providerAnnotation != null)
                order = providerAnnotation.order();
            List<Class<? extends T>> list = ordered.get(order);
            if (list == null)
                ordered.put(order, list = new ArrayList<>());
            list.add(clazz);
        }
        List<Class<? extends T>> classes = new ArrayList<>();
        for (List<Class<? extends T>> list : ordered.values())
            classes.addAll(list);
        return classes;
    }

    private static List<String> getServiceClassNames(Class<?> serviceClass, ClassLoader loader) {
        Set<String> names = new LinkedHashSet<>();
        for (URL url : getServiceConfigURLs(serviceClass, loader)) {
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)))
            {
                String name;
                while ((name = r.readLine()) != null) {
                    int ci = name.indexOf('#');
                    if (ci >= 0)
                        name = name.substring(0, ci);
                    name = name.trim();
                    if (name.isEmpty())
                        continue;
                    if (SystemProperties.getProperty(name + DISABLE_SUFFIX, null) != null)
                        continue; // this service was disabled
                    names.add(name);
                }
            } catch (IOException e) {
                log().error("Cannot read " + url, e);
            }
        }
        return new ArrayList<>(names);
    }

    private static List<URL> getServiceConfigURLs(Class<?> serviceClass, ClassLoader loader) {
        String serviceConfig = META_INF_SERVICES + serviceClass.getName();
        List<URL> urls = new ArrayList<>();
        try {
            loader = resolveLoader(loader, serviceClass);
            urls.addAll(Collections.list(loader.getResources(serviceConfig)));
        } catch (IOException e) {
            log().error("Cannot read " + serviceConfig);
            urls = Collections.emptyList();
        }
        if (urls.isEmpty()) {
            // empty list returned... try to load at least one directly with getResource as a work-around for
            // class loaders that do not correctly implement getResources method.
            URL resource = loader.getResource(serviceConfig);
            if (resource != null)
                urls.add(resource);
        }
        SupersedesService supersedes = serviceClass.getAnnotation(SupersedesService.class);
        if (supersedes != null && supersedes.value() != null)
            urls.addAll(getServiceConfigURLs(supersedes.value(), loader));
        return urls;
    }
}
