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
package com.devexperts.rmi.impl;

import com.devexperts.util.InvalidFormatException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;


/**
 * ServiceFilter are constant; their values cannot be changed after they are created.
 */
public class ServiceFilter {

    /**
     * Filter which transmits any service.
     */
    public static final ServiceFilter ANYTHING = new ServiceFilter(FilterType.ANYTHING, Atom.ANYTHING);

    /**
     * Filter which does not transmit any service.
     */
    public static final ServiceFilter NOTHING = new ServiceFilter(FilterType.NOTHING, Atom.NOTHING);

    /**
     * Creates a filter on the appropriate pattern. The pattern should consist only of the Java identifier parts,
     * '.' (for namespace separation), '*' (for wildcards) and, ',' (for a list of patterns).
     * If pattern is null this method returns {@link #ANYTHING}.
     * @param serviceNames the pattern for filter
     * @return new filter, if pattern is null returns {@link #ANYTHING}
     */
    public static ServiceFilter valueOf(String serviceNames) {
        if (serviceNames == null)
            return ANYTHING;

        boolean wrapped = serviceNames.startsWith("(") && serviceNames.endsWith(")");
        String str = wrapped ? serviceNames : "(" + serviceNames + ")";
        StringTokenizer st = new StringTokenizer(str.substring(1, str.length() - 1), ",", false);
        int nToken = st.countTokens();
        if (nToken == 0)
            return NOTHING;
        Atom[] services = new Atom[nToken];
        FilterType type = FilterType.SIMPLE;
        for (int i = 0; i < nToken; i++) {
            services[i] = new Atom(st.nextToken());
            if (services[i].type == FilterType.ANYTHING)
                return  ANYTHING;
            if (services[i].type == FilterType.PATTERN)
                type = FilterType.PATTERN;
        }
        return new ServiceFilter(type, services);
    }

    private String serviceNames;
    private final Atom[] services;
    private final FilterType type;

    private ServiceFilter(ServiceFilter filter) {
        this.services = new Atom[filter.services.length];
        System.arraycopy(filter.services, 0, services, 0, services.length);
        this.type = filter.type;
    }

    private ServiceFilter(FilterType type, Atom... services) {
        this.services = services;
        this.type = type;
    }

    private ServiceFilter(Atom service) {
        this(service.type, service);
    }

    public boolean accept(String serviceName) {
        if (type == FilterType.ANYTHING)
            return true;
        if (type == FilterType.NOTHING)
            return false;
        for (Atom service : services) {
            if (service.accept(serviceName))
                return true;
        }
        return false;
    }

    public ServiceFilter intersection(ServiceFilter filter) {
        if (filter.type == FilterType.NOTHING || type == FilterType.NOTHING)
            return NOTHING;
        if (type == FilterType.ANYTHING)
            return new ServiceFilter(filter);
        if (filter.type == FilterType.ANYTHING)
            return new ServiceFilter(this);
        if (type == filter.type && equals(filter))
            return new ServiceFilter(this);
        FilterType newType = FilterType.SIMPLE;
        List<Atom> services = new ArrayList<>();
        ServiceFilter intersect;
        for (Atom s1 : this.services) {
            for (Atom s2 : filter.services) {
                intersect = s1.intersection(s2);
                if (intersect.type == FilterType.NOTHING)
                    continue;
                if (intersect.type == FilterType.PATTERN)
                    newType = FilterType.PATTERN;
                services.addAll(Arrays.asList(intersect.services));
            }
        }
        if (services.isEmpty())
            return NOTHING;
        return new ServiceFilter(newType, services.toArray(new Atom[services.size()]));
    }

    Atom[] getServices() {
        return services;
    }

    FilterType getType() {
        return type;
    }

    @Override
    public int hashCode() {
        return (type.hashCode() * 17) + Arrays.hashCode(services);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ServiceFilter))
            return false;
        ServiceFilter other = (ServiceFilter) obj;
        if ((type == FilterType.ANYTHING && other == ANYTHING) || (type == FilterType.NOTHING && other == NOTHING))
            return true;
        return type == other.type && Arrays.equals(services, ((ServiceFilter) obj).services);
    }

    @Override
    public String toString() {
        return getServiceNames();
    }

    private String getServiceNames() {
        if (serviceNames != null) {
            return serviceNames;
        }
        synchronized (this) {
            if (serviceNames != null)
                return serviceNames;
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < services.length; i++) {
                if (i != 0)
                    sb.append(',');
                sb.append(services[i].toString());
            }
            sb.append(')');
            serviceNames = sb.toString();
            return serviceNames;
        }
    }

    static class Atom {

        private static String stringIntersection(String left, String right) {
            int length = Math.min(left.length(), right.length());
            StringBuilder l = new StringBuilder(left);
            StringBuilder r = new StringBuilder(right);
            if (l.length() > length)
                l.delete(0, l.length() - length);
            else if (r.length() > length)
                r = r.delete(length, r.length());
            while(length != 0) {
                if (l.indexOf(r.toString()) != -1)
                    return l.toString();
                length--;
                l = l.deleteCharAt(0);
                r = r.deleteCharAt(length);
            }
            return "";
        }

        static final Atom ANYTHING = new Atom("*");
        static final Atom NOTHING = new Atom("");

        final ServiceFilter wrapper;
        final String serviceName;
        final FilterType type;
        volatile Pattern pattern;

        Atom(String serviceName) {
            for (int i = 0; i < serviceName.length(); i++) {
                char c = serviceName.charAt(i);
                if (!Character.isJavaIdentifierPart(c) && c != '*' && c != '.' && c != '|')
                    throw new InvalidFormatException("Can only use the Java identifier parts, '.', or \"*\" in service name");
            }
            this.serviceName = serviceName;
            int starIndex = serviceName.indexOf('*');
            if (starIndex >= 0) {
                if (serviceName.length() == 1)
                    type = FilterType.ANYTHING;
                else if (serviceName.indexOf('*', starIndex + 1) >= 0)
                    throw new InvalidFormatException("Cannot have more than one '*' in service name");
                else
                    type = FilterType.PATTERN;
            } else if (serviceName.isEmpty()) {
                type = FilterType.NOTHING;
            } else {
                type = FilterType.SIMPLE;
            }
            wrapper = new ServiceFilter(this);
        }

        boolean accept(String name) {
            if (type == FilterType.ANYTHING)
                return true;
            if (type == FilterType.NOTHING)
                return false;
            if (type == FilterType.SIMPLE)
                return serviceName.equals(name);
            return getPattern().matcher(name).matches();
        }

        ServiceFilter intersection(Atom service) {
            if (equals(service))
                return wrapper;
            if (type == FilterType.SIMPLE && service.type == FilterType.SIMPLE)
                return ServiceFilter.NOTHING;
            if (type == FilterType.PATTERN && service.type == FilterType.PATTERN) {
                String s1 = serviceName;
                String s2 = service.serviceName;
                int pos1 = s1.indexOf('*');
                int pos2 = s2.indexOf('*');
                String left;
                String leftIntersect;
                String rightIntersect;
                String right;
                if (pos1 <= pos2) {
                    if (!s2.startsWith(s1.substring(0, pos1)))
                        return ServiceFilter.NOTHING;
                    left = s2.substring(0, pos2);
                    leftIntersect = left.substring(pos1);
                } else {
                    if (!s1.startsWith(s2.substring(0, pos2)))
                        return ServiceFilter.NOTHING;
                    left = s1.substring(0, pos1);
                    leftIntersect = left.substring(pos2);
                }
                if (s1.length() - pos1 <= s2.length() - pos2) {
                    if (!s2.endsWith(s1.substring(pos1 + 1)))
                        return ServiceFilter.NOTHING;
                    right = s2.substring(pos2 + 1);
                    rightIntersect = right.substring(0, right.length() - s1.length() + pos1 + 1);
                } else {
                    if (!s1.endsWith(s2.substring(pos2 + 1)))
                        return ServiceFilter.NOTHING;
                    right = s1.substring(pos1 + 1);
                    rightIntersect = right.substring(0, right.length() - s2.length() + pos2 + 1);
                }
                Atom pattern = new Atom(left + '*' + right);
                if (left.isEmpty() || right.isEmpty())
                    return pattern.wrapper;
                String intersect = stringIntersection(leftIntersect, rightIntersect);
                if (intersect.isEmpty())
                    return pattern.wrapper;
                String simpleName = left.substring(0, left.length() - intersect.length()) + intersect + right.substring(intersect.length());
                Atom simple = new Atom(simpleName);
                return new ServiceFilter(FilterType.PATTERN, pattern, simple);
            }
            if (type == FilterType.ANYTHING)
                return new ServiceFilter(service.wrapper);
            if (service.type == FilterType.ANYTHING)
                return new ServiceFilter(wrapper);
            if (type == FilterType.NOTHING || service.type == FilterType.NOTHING)
                return ServiceFilter.NOTHING;
            String patName;
            String othName;
            Atom other;
            if (type == FilterType.PATTERN && service.type == FilterType.SIMPLE) {
                patName = serviceName;
                othName = service.serviceName;
                other = service;
            } else if (service.type == FilterType.PATTERN && type == FilterType.SIMPLE) {
                patName = service.serviceName;
                othName = serviceName;
                other = this;
            } else {
                throw new IllegalArgumentException();
            }
            int starPos = patName.indexOf('*');
            return othName.startsWith(patName.substring(0, starPos)) && othName.endsWith(patName.substring(starPos + 1))
                ? new ServiceFilter(other.wrapper) : ServiceFilter.NOTHING;
        }

        @Override
        public int hashCode() {
            return serviceName.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Atom))
                return false;
            if ((type == FilterType.ANYTHING && ((Atom) obj).type == FilterType.ANYTHING) ||
                (type == FilterType.NOTHING && ((Atom) obj).type == FilterType.NOTHING))
            {
                return true;
            }
            return serviceName.equals(((Atom) obj).serviceName);
        }

        @Override
        public String toString() {
            return serviceName;
        }

        private Pattern getPattern() {
            Pattern pattern = this.pattern;
            if (pattern != null)
                return pattern;
            String regex = "\\Q" + serviceName.replace("*" , "\\E.*\\Q").replace("|" , "\\E|\\Q") + "\\E";
            return this.pattern = Pattern.compile(regex);
        }


    }

    enum FilterType {
        ANYTHING,
        NOTHING,
        PATTERN,
        SIMPLE
    }
}
