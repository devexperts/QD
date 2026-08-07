/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.codegen;

class RecordDesc {
    final String name;
    final String plainName;
    final String basePackageName;
    final boolean regional;

    boolean regionalOnly; // used only when "regional" is true
    @Deprecated
    String exchangesOldProperty; // used only when "regional" is true
    String exchangesDefault; // used only when "regional" is true

    @Deprecated
    String suffixesOldProperty;
    String suffixesDefault;
    String suffixesOrderClass;

    String phantomProperty;

    RecordDesc(String basePackageName, String recordName) {
        this.basePackageName = basePackageName;
        this.regional = recordName.endsWith("&");
        this.name = regional ? recordName.substring(0, recordName.length() - 1) : recordName;
        this.plainName = name.replace(".", "");

        exchangesOldProperty = basePackageName + ".impl." + plainName + ".exchanges";
        suffixesOldProperty = basePackageName + ".impl." + plainName + ".suffixes";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof RecordDesc))
            return false;
        return name.equals(((RecordDesc) o).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
