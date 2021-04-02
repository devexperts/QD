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
import com.dxfeed.scheme.model.SchemeModel;

import java.io.IOException;
import java.io.InputStream;

/**
 * Interface to read one {@link SchemeModel scheme model} from external file.
 * <p>
 * To support new scheme model file format you must implement this interface.
 */
public interface SchemeModelReader {
    /**
     * Reads one scheme model file. The resulting model can be an overlay and not a stand-alone model.
     * <p>
     * This method must not validate the state of the loaded model, as it could be only an overlay for a larger model.
     *
     * @param model model which is being built now. Reader must update this model on entity-per-entity basis.
     * @param parent name of parent model, if this is imported model overlay.
     * @param name name of this model (typically name of file).
     * @param in stream to load data from.
     * @param importProcessor callback to process import instructions, as imports must be loaded before
     * importing file.
     * @throws IOException if file can not be read or there are other I/O error, like file syntax format violation.
     * @throws SchemeException if loaded model contains inconsistencies.
     */
    public void readModel(SchemeModel model, String parent, String name, InputStream in,
        ImportProcessor importProcessor) throws IOException, SchemeException;
}
