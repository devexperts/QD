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
package com.devexperts.qd.spi;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SpecificSubscriptionFilter;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.SubscriptionFilterFactory;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.kit.FilterSyntaxException;
import com.devexperts.services.Service;
import com.devexperts.services.SupersedesService;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for {@link QDFilter} instances.
 * This abstract class supersedes {@link SubscriptionFilterFactory} interface.
 */
@Service(combineMethod = "combineFactories")
@SupersedesService(value = SubscriptionFilterFactory.class, adapterMethod = "fromFilterFactory")
public abstract class QDFilterFactory implements SubscriptionFilterFactory, DataSchemeService {
    private volatile DataScheme scheme; // double-checked initialization

    protected QDFilterFactory() {}

    protected QDFilterFactory(DataScheme scheme) {
        this.scheme = scheme;
    }

    /**
     * Returns data scheme of this filter factory.
     */
    public final DataScheme getScheme() {
        return scheme;
    }

    /**
     * Sets data scheme for this filter factory. This method can be invoked only once in an object lifetime.
     * @throws NullPointerException when scheme is null.
     * @throws IllegalStateException when scheme is already set.
     */
    @Override
    public void setScheme(DataScheme scheme) {
        if (scheme == null)
            throw new NullPointerException();
        if (this.scheme == scheme)
            return;
        setSchemeSync(scheme);
    }

    private synchronized void setSchemeSync(DataScheme scheme) {
        if (this.scheme != null && this.scheme != scheme)
            throw new IllegalStateException("Different scheme is already set");
        this.scheme = scheme;
    }

    /**
     * Creates custom project-specific filter based on the specification string.
     * Returns null if the given {@code spec} is not supported by this factory.
     */
    public abstract QDFilter createFilter(String spec);

    /**
     * Creates custom project-specific filter based on the specification string in the specified context.
     * Returns null if the given {@code spec} is not supported by this factory.
     * This implementation calls {@link #createFilter(String)} and ignores context.
     */
    public QDFilter createFilter(String spec, QDFilterContext context) {
        return createFilter(spec);
    }

    /**
     * @deprecated Use {@link #createFilter(String)}.
     */
    @Override
    public final SubscriptionFilter createFilter(String spec, SubscriptionFilter chainedFilter) {
        QDFilter filter = createFilter(spec);
        if (filter == null)
            throw new FilterSyntaxException("Unrecognized filter spec: " + spec);
        return CompositeFilters.makeAnd(filter, chainedFilter);
    }

    /**
     * Returns a map from name to description for all supported filters.
     */
    public Map<String, String> describeFilters() {
        return describeImpl(this);
    }

    public static QDFilterFactory fromFilterFactory(SubscriptionFilterFactory filterFactory) {
        return filterFactory instanceof QDFilterFactory ? (QDFilterFactory) filterFactory :
            new Adapter(filterFactory);
    }

    public static QDFilterFactory combineFactories(List<QDFilterFactory> factories) {
        return new Combined(factories);
    }

    private static Map<String,String> describeImpl(SubscriptionFilterFactory filterFactory) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Field f : filterFactory.getClass().getDeclaredFields())
            if (Modifier.isStatic(f.getModifiers()) && Modifier.isPublic(f.getModifiers()) && f.getType().equals(String.class)) {
                SpecificSubscriptionFilter filterAnnotation = f.getAnnotation(SpecificSubscriptionFilter.class);
                if (filterAnnotation != null)
                    try {
                        result.put((String) f.get(null), filterAnnotation.value());
                    } catch (IllegalAccessException e) {
                        // ignored -- should not happen as we query only public fields
                    }
            }
        return result;
    }

    private static class Combined extends QDFilterFactory {
        private final List<QDFilterFactory> factories;

        Combined(List<QDFilterFactory> factories) {
            this.factories = factories;
        }

        @Override
        public void setScheme(DataScheme scheme) {
            super.setScheme(scheme);
            for (QDFilterFactory factory : factories)
                factory.setScheme(scheme);
        }

        @Override
        public QDFilter createFilter(String spec) {
            for (QDFilterFactory factory : factories) {
                QDFilter filter = factory.createFilter(spec);
                if (filter != null)
                    return filter;
            }
            return null;
        }

        @Override
        public QDFilter createFilter(String spec, QDFilterContext context) {
            for (QDFilterFactory factory : factories) {
                QDFilter filter = factory.createFilter(spec, context);
                if (filter != null)
                    return filter;
            }
            return null;
        }

        @Override
        public Map<String, String> describeFilters() {
            Map<String, String> result = new LinkedHashMap<>();
            for (QDFilterFactory factory : factories)
                for (Map.Entry<String, String> entry : factory.describeFilters().entrySet())
                    if (!result.containsKey(entry.getKey()))
                        result.put(entry.getKey(), entry.getValue());
            return result;
        }
    }

    private static class Adapter extends QDFilterFactory {
        private final SubscriptionFilterFactory filterFactory;

        Adapter(SubscriptionFilterFactory filterFactory) {
            this.filterFactory = filterFactory;
        }

        @Override
        public QDFilter createFilter(String spec) {
            SubscriptionFilter filter = filterFactory.createFilter(spec, null);
            return filter == null ? null : QDFilter.fromFilter(filter, getScheme());
        }

        @Override
        public Map<String, String> describeFilters() {
            return describeImpl(filterFactory);
        }
    }
}
