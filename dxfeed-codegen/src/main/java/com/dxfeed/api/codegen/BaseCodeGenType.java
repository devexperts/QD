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
package com.dxfeed.api.codegen;

abstract class BaseCodeGenType implements CodeGenType {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof CodeGenType)) return false;

        CodeGenType that = (CodeGenType) o;

        return getClassName().equals(that.getClassName());
    }

    @Override
    public int hashCode() {
        return getClassName().hashCode();
    }

    @Override
    public String toString() {
        return getClassName().toString();
    }

}
