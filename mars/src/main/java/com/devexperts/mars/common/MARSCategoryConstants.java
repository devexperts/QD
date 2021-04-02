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
package com.devexperts.mars.common;

public interface MARSCategoryConstants {

    String PROJECT = "project";
    String PLATFORM = "platform";
    String SUBSYSTEM = "subsystem";
    String MODULE = "module";
    String TASK = "task";
    String PARAMETER = "parameter";
    String UNDEFINED = "<undefined>";
    String UNLISTED = "<unlisted>";
    String ANY = "<any>";

    String[] PRESET_CATEGORIES = new String[]{
        PROJECT,
        PLATFORM,
        MODULE,
        SUBSYSTEM,
        TASK
    };
}
