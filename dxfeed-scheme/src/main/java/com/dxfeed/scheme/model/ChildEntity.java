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
 * Base class of named entities which has parents other than {@link SchemeModel scheme model}.
 * <p>
 * Such entities are {@link NamedEntity name entites} with additional property for parent entity.
 *
 * @deprecated Will be improved in near future.
 */
@Deprecated
public class ChildEntity<P extends NamedEntity<P>, T extends ChildEntity<P, T>> extends NamedEntity<T> {
    private P parent;

    /**
     * Creates new entity.
     *
     * @param parent parent of new entity, cannot be {@code null}.
     * @param name name of entity, cannot be {@code null}.
     * @param mode node of entity, cannot be {@code null}.
     * @param doc documentation string of entity, can be {@code null}.
     * @param file source file of entity, cannot be {@code null}.
     */
    public ChildEntity(P parent, String name, Mode mode, String doc, String file) {
        super(name, mode, doc, file);
        this.parent = Objects.requireNonNull(parent, "parent");
    }

    /**
     * Returns parent of this entity.
     */
    public P getParent() {
        return parent;
    }

    /**
     * Sets new parent of this entity.
     */
    void setParent(P newParent) {
        parent = newParent;
    }

    /**
     * Returns full name of this entity, which consists of parent's name and entity name delimited by dot ({@code '.'}).
     */
    @Override
    public String getFullName() {
        return parent.getFullName() + "." + super.getFullName();
    }
}
