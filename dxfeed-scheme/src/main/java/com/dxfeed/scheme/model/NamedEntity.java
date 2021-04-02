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

import com.dxfeed.scheme.SchemeException;

import java.util.Objects;

/**
 * Base class for all named {@link SchemeModel scheme model} entities.
 * <p>
 * Named model entities has fixed name, which acts as primary key among other entities of same type
 * in same model or other parent entity.
 * <p>
 * Each named entity has mode, either {@link Mode#NEW NEW} or {@link Mode#UPDATE UPDATE} which determine
 * role of given entity in process of merging scheme models. Only entities with mode set to {@link Mode#NEW NEW}
 * can be updated by entities with same name and mode set to {@link Mode#UPDATE UPDATE}. It is error to update
 * entity with mode set to {@link Mode#UPDATE UPDATE} or use entity with mode set to {@link Mode#NEW NEW} to update
 * other entity.
 * <p>
 * Each named entity also has optional documentation string.
 *
 * @deprecated Will be improved in near future.
 */
@Deprecated
public class NamedEntity<T extends NamedEntity<T>> extends SchemeEntity {
    /**
     * Mode of creation of named entity.
     */
    public enum Mode {
        /**
         * This entity is new one and can be updated later by entities with same name and mode {@link #UPDATE UPDATE}.
         */
        NEW,
        /**
         * This entity is update for existing entity with same name and mode {@link #NEW NEW}.
         */
        UPDATE
    }

    private final String name;
    private final Mode mode;
    private String doc;

    /**
     * Creates new entity.
     *
     * @param name name of entity, cannot be {@code null}.
     * @param mode node of entity, cannot be {@code null}.
     * @param doc documentation string of entity, can be {@code null}.
     * @param file source file of entity, cannot be {@code null}.
     */
    public NamedEntity(String name, Mode mode, String doc, String file) {
        super(file);
        this.name = Objects.requireNonNull(name, "name");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.doc = doc;
    }

    /**
     * Returns name of entity.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns full name of entity, which is equivalent to its name.
     */
    public String getFullName() { return name; }

    /**
     * Returns documentation string of entity, can return {@code null}.
     */
    public String getDoc() {
        return doc;
    }

    /**
     * Returns mode of entity.
     */
    public Mode getMode() {
        return mode;
    }

    void override(T newInstance) throws SchemeException {
        if (!getName().equals(newInstance.getName())) {
            throw new SchemeException(
                SchemeException.formatConflictMessage(this, newInstance.getLastFile(),
                    "Name could not be changed from \"" + getName() + "\" to \"" + newInstance.getName() + "\"."
                ), getFilesList());
        }
        if (mode != Mode.NEW) {
            throw new SchemeException(SchemeException.formatConflictMessage(this, newInstance.getLastFile(),
                "Object with mode=\"update\" could not be used as base object."), getFilesList());
        }
        if (newInstance.getMode() != Mode.UPDATE) {
            throw new SchemeException(SchemeException.formatConflictMessage(this, newInstance.getLastFile(),
                "Object with mode=\"new\" could not be used to update existing object."), getFilesList());
        }

        String newDoc = newInstance.getDoc();
        if (newDoc != null) {
            doc = newDoc;
        }
        addNewFile(newInstance.getLastFile());
    }

    void validateState(SchemeModel parent) throws SchemeException {
        if (mode != Mode.NEW) {
            throw new SchemeException(SchemeException.formatInconsistencyMessage(this,
                "Object with mode=\"" + mode.name().toLowerCase() + "\" must be update, not new object"),
                getFilesList());
        }
    }
}
