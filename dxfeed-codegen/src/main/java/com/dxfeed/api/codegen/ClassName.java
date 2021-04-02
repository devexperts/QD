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

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

class ClassName {
    private final String packageName;
    private final String simpleName;

    ClassName(String packageName, String simpleName) {
        this.packageName = packageName;
        this.simpleName = simpleName;
    }

    ClassName(String fullClassName) {
        int i = fullClassName.lastIndexOf('.');
        packageName = i < 0 ? null : fullClassName.substring(0, i);
        simpleName = fullClassName.substring(i + 1);
    }

    ClassName(Class<?> aClass) {
        this(aClass.getCanonicalName());
    }

    String getPackageName() {
        return packageName;
    }

    String getSimpleName() {
        return simpleName;
    }

    Path toSourcePath() {
        return Paths.get(packageName.replace('.', File.separatorChar), simpleName + ".java");
    }

    @Override
    public String toString() {
        return packageName == null ? simpleName : packageName + '.' + simpleName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ClassName))
            return false;
        ClassName className = (ClassName) o;
        return simpleName.equals(className.simpleName) && Objects.equals(packageName, className.packageName);

    }

    @Override
    public int hashCode() {
        int result = packageName == null ? 0 : packageName.hashCode();
        return 31 * result + simpleName.hashCode();
    }
}
