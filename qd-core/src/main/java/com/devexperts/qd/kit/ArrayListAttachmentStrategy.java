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
package com.devexperts.qd.kit;

import com.devexperts.qd.QDAgent;
import com.devexperts.qd.ng.RecordCursor;

import java.util.Arrays;

// WARNING: This is an EXPERIMENTAL interface. DO NOT IMPLEMENT
public abstract class ArrayListAttachmentStrategy<T, C> implements QDAgent.AttachmentStrategy<Object> {
    @SuppressWarnings("unchecked")
    @Override
    public Object updateAttachment(Object oldAttachment, RecordCursor cursor, boolean remove) {
        Object att = cursor.getAttachment();
        if (oldAttachment == null)
            return remove ? null : att;
        if (att == null)
            return oldAttachment;
        if (oldAttachment instanceof Object[]) {
            Object[] a = (Object[]) oldAttachment;
            return remove ? removeAttachment(a, att) : addAttachment(a, att);
        }
        // old attachment is non-null object
        if (remove)
            return att.equals(oldAttachment) && !decrementAndNotEmpty((T) oldAttachment) ? null : oldAttachment;
        return att.equals(oldAttachment) && incrementCombines((T) oldAttachment) ? oldAttachment :
            new Object[] { oldAttachment, att };
    }

    @SuppressWarnings("unchecked")
    private Object addAttachment(Object[] a, Object att) {
        int i = 0;
        for (; i < a.length; i++) {
            if (a[i] == null)
                break;
            if (att.equals(a[i]) && incrementCombines((T) a[i]))
                return a;
        }
        if (i == a.length)
            a = Arrays.copyOf(a, a.length * 2);
        a[i] = att;
        return a;
    }

    @SuppressWarnings("unchecked")
    private Object removeAttachment(Object[] a, Object att) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] == null)
                break;
            if (att.equals(a[i])) {
                if (decrementAndNotEmpty((T) a[i]))
                    return a; // nothing changes -- counter is still positive
                if (i == 0 && a[1] == null)
                    return null; // now empty
                System.arraycopy(a, i + 1, a, i, a.length - i - 1);
                a[a.length - 1] = null;
                break;
            }
        }
        return a;
    }

    @SuppressWarnings("unchecked")
    public void processEach(RecordCursor cursor, C ctx) {
        Object att = cursor.getAttachment();
        if (att instanceof Object[]) {
            Object[] a = (Object[]) att;
            for (Object o : a) {
                if (o == null)
                    break;
                process(cursor, (T) o, ctx);
            }
        } else
            process(cursor, (T) att, ctx);
    }

    protected boolean incrementCombines(T attachment) {
        return false;
    }

    protected boolean decrementAndNotEmpty(T attachment) {
        return false;
    }

    protected abstract void process(RecordCursor cursor, T attachment, C ctx);
}
