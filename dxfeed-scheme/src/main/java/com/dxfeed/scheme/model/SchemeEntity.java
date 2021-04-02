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
package com.dxfeed.scheme.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Base class for all scheme model entities.
 * <p>
 * Each model entity has a list of files (sources) from which it has been created. Some entities can have
 * only one source, and some can be merged from different sources.
 *
 * @deprecated Will be improved in near future.
 */
@Deprecated
public class SchemeEntity {
    private final List<String> files;

    /**
     * Creates new entity, with initial source file.
     *
     * @param file source file, cannot be {@code null}.
     */
    public SchemeEntity(String file) {
        Objects.requireNonNull(file, "file");
        this.files = new ArrayList<>();
        addNewFile(file);
    }

    protected void addNewFile(String newFile) {
        Objects.requireNonNull(newFile, "newFile");
        if (files.size() == 0 || !files.get(files.size() - 1).equals(newFile)) {
            files.add(newFile);
        }
    }

    /**
     * Returns list of source files.
     */
    public List<String> getFilesList() {
        return Collections.unmodifiableList(files);
    }

    /**
     * Returns the last source file.
     */
    public String getLastFile() {
        return files.get(files.size() - 1);
    }

    protected String getFrom() {
        return files.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", "));
    }
}
