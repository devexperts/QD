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

import com.dxfeed.scheme.model.SchemeModel;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Interface to write {@link SchemeModel scheme model} to external file.
 * <p>
 * To support new scheme model dump file format you must implement this interface.
 */
public interface SchemeModelWriter {
    /**
     * Writes an external representation of the scheme model into the output stream.
     *
     * @param out output stream to write into.
     * @param model scheme model to write. This model will be validated and in consistent state.
     * @throws IOException if there are problems with writing into output stream.
     */
    public void writeModel(OutputStream out, SchemeModel model) throws IOException;
}
