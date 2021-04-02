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
package com.dxfeed.impl;

import java.util.AbstractList;

// It is immutable for outside world via List interface, modifiable via xxxImpl methods
public abstract class AbstractIndexedList<E> extends AbstractList<E> {
    private static final int INITIAL_MASK = 15; // must be 2^k - 1

    @SuppressWarnings("unchecked")
    private E[] events = (E[]) new Object[INITIAL_MASK + 1];
    private int min;
    private int max;
    private int mask = INITIAL_MASK;

    protected abstract long getIndex(E event);

    protected void removed(E event) {}

    @Override
    public boolean isEmpty() {
        return min == max;
    }

    @Override
    public int size() {
        return (max - min) & mask;
    }

    @Override
    public E get(int i) {
        if (i < 0 || i >= size())
            throw new IndexOutOfBoundsException("i=" + i + ", size=" + size());
        return events[(min + i) & mask];
    }

    // If found     -> returns            position >= 0
    // If not found -> returns -insertPosition - 1  < 0
    public int findIndex(long index) {
        if (min == max)
            return -1; // not found, insert position == 0
        long minIndex = getIndex(events[min]);
        if (index < minIndex)
            return -1; // insert below min, insert position == 0
        if (index == minIndex)
            return 0; // replace min
        long maxIndex = getIndex(events[(max - 1) & mask]);
        if (index > maxIndex)
            return -size() - 1; // insert above max
        if (index == maxIndex)
            return size() - 1; // replace max
        // otherwise -- binary search of event by index between min and max
        int a = min;
        int b = max;
        if (b < a) // note, here b != a
            b += mask + 1;
        a++; // min already checked, will definitely insert above min
        // Invariant: a <= insert_index <= b
        while (a < b) {
            int m = (a + b) >> 1;
            long mIndex = getIndex(events[m & mask]);
            if (index > mIndex)
                a = m + 1;
            else if (index < mIndex)
                b = m;
            else
                return m - min;
        }
        return -(a - min) - 1;
    }

    public void clearImpl() {
        for (int i = min; i != max; i = (i + 1) & mask) {
            removed(events[i]);
            events[i] = null;
        }
        min = 0;
        max = 0;
    }

    public void updateImpl(E event, boolean remove) {
        if (!remove) // potentially adding new element, grow array in advance (if needed) for simplicity
            growIfNeededImpl();
        int a = findIndex(getIndex(event));
        if (a < 0) { // not found
            if (!remove) // insert new entry if not removing
                insertImpl(-a - 1, event);
        } else if (remove) { // remove existing
            removeImpl(a);
        } else { // replace existing
            events[(min + a) & mask] = event;
        }
    }

    public void removeImpl(int i) {
        removed(events[(min + i) & mask]);
        int left = i;
        int right = size() - i - 1;
        if (left < right) {
            moveRight(min, left);
            events[min] = null;
            min = (min + 1) & mask;
        } else {
            moveLeft((min + i + 1) & mask, right);
            max = (max - 1) & mask;
            events[max] = null;
        }
    }

    public void insertImpl(int i, E event) {
        int left = i;
        int right = size() - i;
        if (left < right) {
            moveLeft(min, left);
            min = (min - 1) & mask;
            events[(min + i) & mask] = event;
        } else {
            int at = (min + i) & mask;
            moveRight(at, right);
            max = (max + 1) & mask;
            events[at] = event;
        }
    }

    private void moveLeft(int at, int n) {
        if (n == 0)
            return;
        if (at == 0)
            at = mask + 1;
        int m = mask + 1 - at;
        System.arraycopy(events, at, events, at - 1, Math.min(n, m));
        if (n > m) {
            events[mask] = events[0];
            System.arraycopy(events, 1, events, 0, n - m - 1);
        }
    }

    private void moveRight(int at, int n) {
        if (n == 0)
            return;
        int m = mask - at;
        if (n > m) {
            System.arraycopy(events, 0, events, 1, n - m - 1);
            events[0] = events[mask];
        }
        System.arraycopy(events, at, events, at + 1, Math.min(n, m));
    }

    public void growIfNeededImpl() {
        if (((max + 1) & mask) == min)
            grow();
    }

    private void grow() {
        int oldLen = events.length;
        int newLen = oldLen << 1;
        @SuppressWarnings("unchecked")
        E[] newEvents = (E[]) new Object[newLen];
        if (max < min) {
            System.arraycopy(events, min, newEvents, 0, oldLen - min);
            System.arraycopy(events, 0, newEvents, oldLen - min, max);
        } else
            System.arraycopy(events, min, newEvents, 0, max - min);
        events = newEvents;
        max = size();
        min = 0;
        mask = newLen - 1;
    }
}
