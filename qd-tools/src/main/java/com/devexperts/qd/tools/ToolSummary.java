/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools;

import java.lang.annotation.*;

/**
 * Describes QDS tool usage and arguments.
 */
@SuppressWarnings({"JavaDoc"})
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ToolSummary {
    /**
     * Short description of a tool.
     */
    String info();

    /**
     * Argument string pattern(s).
     */
    String[] argString();

    /**
     * Tool arguments with their descriptions.
     * Use "--" to separate argument names from their descriptions.
     */
    String[] arguments();
}
