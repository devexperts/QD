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
package com.devexperts.qd.impl;

import com.devexperts.qd.QDFilter;
import com.devexperts.util.JMXNameBuilder;

import java.util.Objects;

class AbstractBuilder<I, B extends AbstractBuilder<I, B>> implements Cloneable {
    protected QDFilter filter = QDFilter.ANYTHING;
    protected String keyProperties;

    public QDFilter getFilter() {
        return filter;
    }

    public String getKeyProperties() {
        return keyProperties;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected B clone() {
        try {
            return (B) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    public I withFilter(QDFilter filter) {
        if (filter.equals(this.filter))
            return (I) this;
        B result = clone();
        result.filter = filter;
        return (I) result;
    }

    @SuppressWarnings("unchecked")
    public I withKeyProperties(String keyProperties) {
        JMXNameBuilder.validateKeyProperties(keyProperties);
        if (Objects.equals(keyProperties, this.keyProperties))
            return (I) this;
        B result = clone();
        result.keyProperties = keyProperties;
        return (I) result;
    }

    @Override
    public String toString() {
        return "filter=" + filter + ", keyProperties=" + keyProperties;
    }
}
