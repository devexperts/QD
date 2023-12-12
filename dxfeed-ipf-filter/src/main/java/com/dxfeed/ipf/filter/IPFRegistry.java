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
package com.dxfeed.ipf.filter;

import com.devexperts.logging.Logging;
import com.devexperts.management.Management;
import com.devexperts.qd.DataScheme;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.IndexerFunction;
import com.devexperts.util.TimeFormat;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Registry for dynamic {@link IPFSymbolFilter} to make sure we have one instance of such filter for
 * any given {@link Key}.
 */
class IPFRegistry implements IPFRegistryMXBean {
    private IPFRegistry() {} // do not create

    private static final ReferenceQueue<IPFSymbolFilter> QUEUE = new ReferenceQueue<IPFSymbolFilter>();

    private static final IndexedSet<Key, FilterReference> REFS = IndexedSet.create((IndexerFunction<Key, FilterReference>) ref -> ref.key);

    private static final Logging log = Logging.getLogging(IPFRegistry.class);

    private static Management.Registration registration;

    public static synchronized IPFSymbolFilter registerShared(IPFSymbolFilter filter) {
        FilterReference ref = REFS.getByKey(new Key(filter));
        if (ref != null) {
            IPFSymbolFilter registered = ref.get();
            if (registered != null)
                return registered;
        }
        return registerUpdate(filter);
    }

    public static synchronized IPFSymbolFilter registerUpdate(IPFSymbolFilter filter) {
        REFS.add(new FilterReference(filter));
        cleanupQueue();
        if (registration == null)
            registration = Management.registerMBean(new IPFRegistry(), IPFRegistryMXBean.class,
                Management.getMBeanNameForClass(IPFRegistry.class));
        return filter;
    }

    private static void cleanupQueue() {
        Reference<? extends IPFSymbolFilter> ref;
        while ((ref = QUEUE.poll()) != null)
            REFS.remove(ref);
        if (REFS.isEmpty() && registration != null) {
            registration.unregister();
            registration = null;
        }
    }

    private static class FilterReference extends WeakReference<IPFSymbolFilter> {
        final Key key;

        FilterReference(IPFSymbolFilter filter) {
            super(filter);
            this.key = new Key(filter);
        }
    }

    static class Key {
        private final DataScheme scheme;
        private final String spec;

        Key(IPFSymbolFilter filter) {
            scheme = filter.getScheme();
            spec = filter.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof Key))
                return false;
            Key that = (Key) o;
            return scheme.equals(that.scheme) && spec.equals(that.spec);
        }

        @Override
        public int hashCode() {
            return 31 * scheme.hashCode() + spec.hashCode();
        }
    }

    // --- Management ---

    public synchronized int getRegisteredFiltersCount() {
        return REFS.size();
    }

    public synchronized String[] getRegisteredFilters() {
        cleanupQueue();
        Set<String> result = new HashSet<String>();
        for (FilterReference ref : REFS)
            result.add(ref.key.spec);
        return result.toArray(new String[result.size()]);
    }

    public synchronized String reportStats() {
        cleanupQueue();
        List<String[]> data = new ArrayList<String[]>();
        data.add(new String[]{"Filter", "Symbols", "Modified", "Loaded", "Checked"});
        for (FilterReference ref : REFS) {
            String[] a = {ref.key.spec, "N/A", "N/A", "N/A", "N/A"};
            IPFSymbolFilter filter = ref.get();
            if (filter != null) {
                a[1] = Integer.toString(filter.getNumberOfSymbols());
                a[2] = TimeFormat.DEFAULT.format(filter.getLastModified());
                a[3] = TimeFormat.DEFAULT.format(filter.getLastLoaded());
                a[4] = TimeFormat.DEFAULT.format(filter.getLastChecked());
            }
            data.add(a);
        }

        StringBuilder html = new StringBuilder();
        html.append("Total IPF filters: ").append(REFS.size()).append("<br>");
        html.append("<table border=\"1\"><tr>");
        for (String s : data.get(0))
            html.append("<th>").append(s).append("</th>");
        html.append("</tr>");
        for (int i = 1; i < data.size(); i++) {
            html.append("<tr>");
            for (String s : data.get(i))
                html.append("<td>").append(s).append("</td>");
            html.append("</tr>");
        }
        html.append("</table>");

        String filler = "                                                                                                    ";
        for (int i = 0; i < data.get(0).length; i++) {
            int n = 0;
            for (int j = 0; j < data.size(); j++)
                n = Math.max(n, data.get(j)[i].length());
            for (int j = 0; j < data.size(); j++)
                data.get(j)[i] = data.get(j)[i] + filler.substring(Math.max(0, filler.length() - (n - data.get(j)[i].length())));
        }
        StringBuilder text = new StringBuilder();
        text.append("Total IPF filters: ").append(REFS.size());
        for (String[] a : data) {
            text.append("\n\t");
            for (String s : a)
                text.append(s).append("    ");
        }
        log.info(text.toString());

        return html.toString();
    }

    public String forceUpdate(String filterSpec) {
        cleanupQueue();
        int cnt = 0;
        for (FilterReference ref : REFS)
            if (ref.key.spec.equals(filterSpec)) {
                IPFSymbolFilter filter = ref.get();
                if (filter != null) {
                    filter.forceUpdate();
                    cnt++;
                }
            }
        return "Forced update on " + cnt + " instances";
    }
}
