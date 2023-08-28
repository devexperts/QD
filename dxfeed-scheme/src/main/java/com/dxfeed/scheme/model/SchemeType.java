/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.scheme.model;

import com.dxfeed.scheme.SchemeException;

import java.util.Objects;

/**
 * Description of one custom type for {@link SchemeModel scheme model}.
 * <p>
 * Each custom type is only new name (alias) for some other type, custom or embedded.
 *
 * @deprecated Will be improved in near future.
 */
@Deprecated
public final class SchemeType extends NamedEntity<SchemeType> {
    private String base;
    private String resolved;

    /**
     * Create new custom type.
     *
     * @param name name of enum, cannot be {@code null}.
     * @param mode mode of enum, cannot be {@code null}.
     * @param base name of type for which new one will be an alias, cannot be {@code null}.
     * @param doc documentation string of enum, can be {@code null}.
     * @param file source file of enum, cannot be {@code null}.
     */
    public SchemeType(String name, Mode mode, String base, String doc, String file) {
        super(name, mode, doc, file);
        Objects.requireNonNull(base, "base");
        this.base = base;
        this.resolved = null;
    }

    /**
     * Returns name of base (aliased) type.
     */
    public String getBase() {
        return base;
    }

    /**
     * Returns of type has been resolved via chain of aliases to some embedded type.
     * <p>
     * Resolving of types is performed by {@link SchemeModel model} when it validates its state.
     */
    public boolean isResolved() {
        return resolved != null;
    }

    /**
     * Returns name of embedded type if this custom type is {@link #isResolved resolved} or {@code null} otherwise.
     */
    public String getResolvedType() {
        return resolved;
    }

    @Override
    void validateState(SchemeModel parent) throws SchemeException {
        super.validateState(parent);
        // Detect loops: empty string is "gray" marker, we know here
        if ("".equals(resolved)) {
            throw new SchemeException(SchemeException.formatInconsistencyMessage(this,
                "Loop in type definitions detected"), getFilesList());
        }
        if (resolved != null) {
            return;
        }
        // Mark as gray
        resolved = "";
        if (parent.getEmbeddedTypes().isKnownType(base)) {
            resolved = base;
        } else {
            SchemeType bt = parent.getTypes().get(base);
            if (bt == null) {
                throw new SchemeException(SchemeException.formatInconsistencyMessage(this,
                    "Base type \"" + base + "\" is unknown"), getFilesList());
            }
            if (bt == this) {
                throw new SchemeException(SchemeException.formatInconsistencyMessage(this,
                    "Base type \"" + base + "\" is same as this type"), getFilesList());
            }
            bt.validateState(parent);
            resolved = bt.getResolvedType();
        }
    }

    @Override
    void override(SchemeType newInstance) throws SchemeException {
        super.override(newInstance);
        base = newInstance.base;
        resolved = null;
    }

    @Override
    public String toString() {
        return "SchemeType{" +
            "name='" + getName() + '\'' +
            "base='" + base + '\'' +
            "resolved='" + (resolved == null ? "<unresolved>" : resolved) + '\'' +
            '}';
    }

    public void setUnresolved() {
        resolved = null;
    }
}
