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
package com.dxfeed.scheme;

import com.dxfeed.scheme.model.NamedEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Exception in scheme model structure, syntax or semantics.
 */
public class SchemeException extends Exception {
    private final List<SchemeException> causes;

    /**
     * Constructs exception with given error message and file which causes this exception.
     * <p>
     * Message will be decorated with formatted file name.
     *
     * @param message error message.
     * @param file name of file which causes this exception.
     */
    public SchemeException(String message, String file) {
        this(message, Collections.singletonList(file));
    }

    /**
     * Constructs exception with given error message and list of files which cause this exception.
     * <p>
     * Message will be decorated with formatted file names.
     *
     * @param message error message.
     * @param files list of names of file which cause this exception.
     */
    public SchemeException(String message, List<String> files) {
        super("Exception in scheme file" + (files.size() > 1 ? "s" : "") + " " + filesAsString(files) + "\n" +
            message);
        this.causes = Collections.emptyList();
    }

    /**
     * Constructs exception with given error message.
     *
     * @param message error message.
     */
    public SchemeException(String message) {
        super(message);
        this.causes = Collections.emptyList();
    }

    /**
     * Constructs exception which is result of one or more other exceptions in one or more files.
     *
     * @param causes list of causes which lead to this exception.
     * @param files list of names of file which cause this exception.
     */
    public SchemeException(List<SchemeException> causes, List<String> files) {
        super("Multiple errors in scheme file" + (files.size() > 1 ? "s" : "") + " " + filesAsString(files),
            causes.get(causes.size() - 1));
        this.causes = Collections.unmodifiableList(new ArrayList<>(causes));
    }

    /**
     * Returns causes for this exception or empty list.
     *
     * @return causes of this exception.
     */
    public List<SchemeException> getCauses() {
        return causes;
    }

    /**
     * Format message about scheme inconsistency.
     *
     * @param entity Scheme model entity which causes this error.
     * @param message Exact message which describes inconsistency.
     * @return formatted error message which can be passed to exception constructor.
     */
    public static <T extends NamedEntity<T>> String formatInconsistencyMessage(NamedEntity<T> entity, String message) {
        return "Scheme inconsistency with " + extractType(entity.getClass()) + " \"" + entity.getFullName() + "\": " + message;
    }

    /**
     * Format message about scheme conflict between entities.
     *
     * @param entity Scheme model entity which causes this error.
     * @param conflictFile file which contains the second instance of a given entity, which causes conflict.
     * @param message Exact message which describes conflict details.
     * @return formatted error message which can be passed to exception constructor.
     */
    public static <T extends NamedEntity<T>> String formatConflictMessage(NamedEntity<T> entity, String conflictFile,
        String message)
    {
        return "Conflict overriding " + extractType(entity.getClass()) + " \"" + entity.getFullName() +
            "\" with update from file \"" + conflictFile + "\": " + message;
    }

    private static String filesAsString(List<String> files) {
        return files.stream().map(s -> '"' + s + '"').collect(Collectors.joining(", "));
    }

    private static String extractType(Class<?> aClass) {
        String name = aClass.getSimpleName();
        return name.startsWith("Scheme") ? name.substring(6) : name;
    }
}
