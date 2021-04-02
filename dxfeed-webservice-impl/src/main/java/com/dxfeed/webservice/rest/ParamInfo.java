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
package com.dxfeed.webservice.rest;

import java.lang.reflect.Field;

public class ParamInfo {
    public final String name;
    public final ParamType type;
    public final String description;
    public final Field field;// == null for arguments

    public ParamInfo(String name, ParamType type, String description, Field field) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.field = field;
    }

}
