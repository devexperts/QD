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
package com.dxfeed.scheme.impl;

import com.dxfeed.scheme.SchemeException;
import com.dxfeed.scheme.model.SchemeImport;

import java.io.IOException;

/**
 * Callback to load import ahead of current loading file.
 */
@FunctionalInterface
public interface ImportProcessor {
    /**
     * This method must be called by implementation of {@link SchemeModelReader scheme model reader} as soon
     * as it encounters and parse import instruction.
     * <p>
     * Loading driver will postpone loading of current file and load imported one.
     *
     * @param parent name of file which contains this import instruction.
     * @param imp import instruction.
     */
    public void processImport(String parent, SchemeImport imp) throws IOException, SchemeException;
}
