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

import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * Enumerates given enumerations in a sequence.
*/
class SequenceEnumeration<T> implements Enumeration<T> {
    private final Enumeration<T>[] seq;
    private int index;
    private boolean hasMore;
    private T next;

    SequenceEnumeration(Enumeration<T>... seq) {
        this.seq = seq;
        initNext();
    }

    private void initNext() {
        while (index < seq.length && !seq[index].hasMoreElements())
            index++;
        hasMore = index < seq.length;
        if (hasMore)
            next = seq[index].nextElement();
    }

    public boolean hasMoreElements() {
        return hasMore;
    }

    public T nextElement() {
        if (!hasMore)
            throw new NoSuchElementException();
        T old = next;
        initNext();
        return old;
    }
}
