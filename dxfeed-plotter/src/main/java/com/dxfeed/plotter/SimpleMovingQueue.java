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
package com.dxfeed.plotter;

import java.util.AbstractQueue;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import javax.annotation.Nonnull;

final class SimpleMovingQueue<E> extends AbstractQueue<E> implements List<E> {
    private final int mask;
    private final E[] elements;
    private int head;
    private int tail;

    @SuppressWarnings("unchecked")
    SimpleMovingQueue(int requiredCapacity) {
        // invariant: head == tail <=> queue is empty, so we need one extra element
        if ((requiredCapacity & requiredCapacity + 1) != 0) {
            requiredCapacity = Integer.highestOneBit(requiredCapacity) << 1;
        }
        this.mask = requiredCapacity - 1; // requiredCapacity is now power of two
        elements = (E[]) new Object[requiredCapacity];
    }

    /* Collection methods */

    @Override
    public Iterator<E> iterator() {
        return listIterator();
    }

    @Override
    public int size() {
        return (tail - head) & mask;
    }

    @Override
    public void clear() {
        Arrays.fill(elements, null);
        head = tail = 0;
    }

    /* Queue methods */

    @Override
    public boolean offer(E e) {
        if (size() == mask) {
            poll(); // store no more then last *capacity* elements
        }
        elements[tail] = e;
        tail = succ(tail);
        return true;
    }

    @Override
    public E poll() {
        if (head == tail) {
            return null;
        }
        E result = elements[head];
        head = succ(head);
        return result;
    }

    @Override
    public E peek() {
        if (head == tail) {
            return null;
        }
        return elements[head];
    }

    /* List methods */

    @Override
    public boolean addAll(int index, @Nonnull Collection<? extends E> c) {
        throw new UnsupportedOperationException("addAll(index, collection)");
    }

    @Override
    public E get(int index) {
        if (index >= size()) {
            throw new IndexOutOfBoundsException(index + " >= size = " + size());
        }
        return elements[(tail - index - 1) & mask];
    }

    @Override
    public E set(int index, E element) {
        throw new UnsupportedOperationException("set(index, element)");
    }

    @Override
    public void add(int index, E element) {
        throw new UnsupportedOperationException("add(index, element)");
    }

    @Override
    public E remove(int index) {
        throw new UnsupportedOperationException("remove(index)");
    }

    @Override
    public int indexOf(Object o) {
        final int size = size();
        for (int i = 0; i < size; ++i) {
            if (Objects.equals(o, elements[(head + i) & mask])) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        for (int i = size() - 1; i >= 0; --i) {
            if (Objects.equals(o, elements[(head + i) & mask])) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public ListIterator<E> listIterator() {
        return listIterator(0);
    }

    @Nonnull
    @Override
    public ListIterator<E> listIterator(final int index) {
        if (0 < index || index >= size()) {
            throw new IllegalArgumentException(index + " not in range [0, " + size() + ")");
        }
        return new ListIterator<E>() {
            int i = index;

            @Override
            public boolean hasNext() {
                return i < size();
            }

            @Override
            public E next() {
                if (i >= size()) {
                    throw new NoSuchElementException("next");
                }
                return get(i++);
            }

            @Override
            public boolean hasPrevious() {
                return i > 0;
            }

            @Override
            public E previous() {
                if (i <= 0) {
                    throw new NoSuchElementException("previous");
                }
                return get(--i);
            }

            @Override
            public int nextIndex() {
                return i;
            }

            @Override
            public int previousIndex() {
                return i - 1;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("remove");
            }

            @Override
            public void set(E e) {
                throw new UnsupportedOperationException("set");
            }

            @Override
            public void add(E e) {
                throw new UnsupportedOperationException("add");
            }
        };
    }

    @Nonnull
    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException("subList(from, to)");
    }

    /* Helper methods */
    private int succ(int i) {
        return (i + 1) & mask;
    }
}
