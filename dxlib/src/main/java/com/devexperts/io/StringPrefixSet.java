/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.io;

import java.util.*;
import javax.annotation.Nonnull;

/**
 * Class for working with set of string prefixes.
 * PrefixSet is immutable.
 */
class StringPrefixSet {

    /**
     * Delimiter symbol between the different names by default.
     */
    public static final String DEFAULT_NAMES_SEPARATOR = ",";

    /**
     * All possible prefixes.
     */
    public static final StringPrefixSet ANYTHING_SET = new StringPrefixSet(Collections.<String>emptySet(), new TreeSet<>(Collections.singletonList("")));

    /**
     * None of the possible prefix.
     */
    public static final StringPrefixSet NOTHING_SET = new StringPrefixSet(Collections.<String>emptySet(), new TreeSet<String>());

    private static final String ANYTHING_SYMBOL = "*";

    @Nonnull
    private final TreeSet<String> prefixSet = new TreeSet<>();

    @Nonnull
    private final Set<String> fullNamesSet = new HashSet<>();

    /**
     * Constructs set of prefix by names and separators. Names can use wildcard only symbol "*".
     * <p> <b>Syntax of wildcard is the same as the specifications of {@code java import ...}</b> .
     *
     * @param namesSeparator      delimiter symbol between the different names.
     * @param names               A string of names.
     * @return set of prefix.
     * @throws IllegalArgumentException if even one name has an invalid format.
     */
    static StringPrefixSet valueOf(String namesSeparator, String names) {
        if (names == null)
            return NOTHING_SET;
        return valueOf(Arrays.asList(names.split(namesSeparator)));
    }

    /**
     * Constructs set of prefix by names and separators. {@link #DEFAULT_NAMES_SEPARATOR} used as a separator.
     * Names can use wildcard only symbol "*".
     * <p> <b>Syntax of wildcard is the same as the specifications of {@code java import ...}</b> .
     * This is a shortcut for
     * <code>{@link #valueOf(String, String) valueOf(DEFAULT_NAMES_SEPARATOR, names)}</code>.
     *
     * @param names A string of names.
     * @return set of prefix.
     * @throws IllegalArgumentException if even one name has an invalid format.
     */
    static StringPrefixSet valueOf(String names) {
        if (names == null)
            return NOTHING_SET;
        return valueOf(Arrays.asList(names.split(DEFAULT_NAMES_SEPARATOR)));
    }

    /**
     * Constructs set of prefix by names according with a separator. Names can use wildcard only symbol "*".
     * <p> <b>Syntax of wildcard is the same as the specifications of {@code java import ...}</b> .
     *
     * @param names               collection names.
     * @return set of prefix.
     * @throws IllegalArgumentException if even one name has an invalid format.
     */
    static StringPrefixSet valueOf(Collection<String> names) {
        if (names == null)
            return NOTHING_SET;
        boolean isAnything = false;
        Set<String> fullNamesSet = new HashSet<>();
        TreeSet<String> prefixTree = new TreeSet<>();
        int i = 0;
        for (String name : names) {
            if (name.length() == 0)
                continue;
            int index = name.indexOf(ANYTHING_SYMBOL);
            if (index == -1) {
                fullNamesSet.add(name);
                continue;
            }
            if (index != name.length() - 1)
                throw new IllegalArgumentException("Name at number " + i + " has a wrong format: " + name);
            if (index == 0) {
                isAnything = true;
                continue;
            }
            prefixTree.add(name.substring(0, name.length() - 1));
            i++;
        }
        if (isAnything)
            return ANYTHING_SET;
        return optimize(fullNamesSet, prefixTree);
    }

    private static StringPrefixSet optimize(Set<String> fullNamesSet, TreeSet<String> prefixSet) {
        Iterator<String> it = prefixSet.iterator();
        String cur;
        String last = null;
        if (it.hasNext())
            last = it.next();
        while (it.hasNext()) {
            cur = it.next();
            if (cur.startsWith(last))
                it.remove();
            else
                last = cur;
        }
        it = fullNamesSet.iterator();
        String prefix;
        while (it.hasNext()) {
            cur = it.next();
            prefix = prefixSet.floor(cur);
            if (prefix != null && cur.startsWith(prefix))
                it.remove();
        }
        if (fullNamesSet.isEmpty()) {
            if (prefixSet.isEmpty())
                return NOTHING_SET;
            if (prefixSet.first().length() == 0)
                return ANYTHING_SET;
        }
        return new StringPrefixSet(fullNamesSet, prefixSet);
    }

    private StringPrefixSet(@Nonnull Set<String> fullNamesSet, @Nonnull TreeSet<String> prefixSet) {
        this.fullNamesSet.addAll(fullNamesSet);
        this.prefixSet.addAll(prefixSet);
    }

    private StringPrefixSet(StringPrefixSet prefixSet) {
        this.fullNamesSet.addAll(prefixSet.fullNamesSet);
        this.prefixSet.addAll(prefixSet.prefixSet);
    }

    /**
     * Returns the union of two sets of prefix.
     *
     * @param other set of prefix
     * @return the union of two sets of prefix.
     * @throws IllegalArgumentException if the sets are different delimiter symbol between the prefix.
     */
    StringPrefixSet add(StringPrefixSet other) {
        if (this == ANYTHING_SET || other == ANYTHING_SET)
            return ANYTHING_SET;
        if (this == NOTHING_SET && other == NOTHING_SET)
            return NOTHING_SET;
        if (this != NOTHING_SET && other != NOTHING_SET) {
            HashSet<String> fullNamesSetUnion = new HashSet<>(other.fullNamesSet);
            fullNamesSetUnion.addAll(fullNamesSet);
            TreeSet<String> prefixSetUnion = new TreeSet<>(other.prefixSet);
            prefixSetUnion.addAll(prefixSet);
            return optimize(fullNamesSetUnion, prefixSetUnion);
        }
        return this == NOTHING_SET ? new StringPrefixSet(other) : new StringPrefixSet(this);
    }

    StringPrefixSet copy() {
        if (this == ANYTHING_SET || this == NOTHING_SET)
            return this;
        return new StringPrefixSet(this);
    }

    /**
     * Return {@code true} if name is contained in set, otherwise returns {@code false}.
     *
     * @param name the class name.
     * @return {@code true} if name is contained in list.
     */
    boolean accept(String name) {
        if (isAnything())
            return true;
        if (isNothing())
            return false;
        if (fullNamesSet.contains(name))
            return true;
        String prefix = prefixSet.floor(name);
        return prefix != null && name.startsWith(prefix);
    }

    /**
     * Returns {@code true} if set contains any prefix name.
     *
     * @return {@code true} if set contains any prefix name.
     */
    boolean isAnything() {
        return fullNamesSet.isEmpty() && (prefixSet.size() == 1 && prefixSet.first().length() == 0);
    }

    /**
     * Returns {@code true} if set not contains any prefix name.
     *
     * @return {@code true} if set not contains any prefix name.
     */
    boolean isNothing() {
        return fullNamesSet.isEmpty() && prefixSet.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || !(o instanceof StringPrefixSet))
            return false;
        StringPrefixSet other = (StringPrefixSet) o;
        return fullNamesSet.equals(other.fullNamesSet)
            && prefixSet.equals(other.prefixSet);
    }

    @Override
    public int hashCode() {
        return 31 * fullNamesSet.hashCode() + prefixSet.hashCode();
    }

    @Override
    public String toString() {
        String className = "SerialClassList{";
        if (this == NOTHING_SET)
            return className + "NOTHING}";
        if (this == ANYTHING_SET)
            return className + "ANYTHING}";
        return className + "prefixes=" + prefixSet
                + ", full names = " + new TreeSet<>(fullNamesSet)
                + "}";
    }

    public List<String> getList() {
        List<String> result = new ArrayList<>();
        if (fullNamesSet != null)
            result.addAll(fullNamesSet);
        if (prefixSet != null) {
            for (String prefix : prefixSet)
                result.add(prefix + ANYTHING_SYMBOL);
        }
        return result;
    }
}
