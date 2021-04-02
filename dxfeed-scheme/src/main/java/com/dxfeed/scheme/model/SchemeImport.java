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

import java.util.Objects;

/**
 * Import instruction for {@link SchemeModel scheme model}.
 * <p>
 * This class describe one import instruction for scheme model. Import instruction has only one property:
 * URL to use for import other scheme model file.
 * <p>
 * Import instructions don't have names and can not be merged or overlayed. They are added to {@link SchemeModel model}
 * one by one and are applied one by one in order of adding.
 *
 * @deprecated Will be improved in near future.
 */
@Deprecated
public final class SchemeImport extends SchemeEntity {
    private final String url;

    /**
     * Creates new scheme import instruction.
     *
     * @param url URL to import, cannot be {@code null}.
     * @param file source file of import, cannot be {@code null}.
     */
    public SchemeImport(String url, String file) {
        super(file);
        this.url = Objects.requireNonNull(url, "url");
    }

    /**
     * Returns URL to import.
     */
    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return "SchemeImport{" +
            "url=" + url +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SchemeImport that = (SchemeImport) o;
        return url.equals(that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }
}
