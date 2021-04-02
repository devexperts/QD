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

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Description of one {@link SchemeRecord record} or {@link SchemeRecord.Field field} visibility rule.
 * <p>
 * Visibility rules don't have names and can not be merged or overlayed. They are added to {@link SchemeModel model}
 * one by one and are applied one by one in order of adding.
 *
 * @deprecated Will be improved in near future.
 */
@Deprecated
public final class VisibilityRule extends SchemeEntity {
    /**
     * Type of visibility rule.
     */
    public enum Type {
        /**
         * Rule is applicable to whole {@link SchemeRecord records}.
         */
        RECORD,
        /**
         * Rule is applicable to {@link SchemeRecord.Field fields} in records.
         */
        FIELD
    }

    private final Pattern record;
    @Deprecated
    private final boolean useEventName;
    private final Pattern field;
    private final boolean enable;
    private final Set<String> incTags = new HashSet<>();
    private final Set<String> excTags = new HashSet<>();

    /**
     * Creates new visibility rule.
     *
     * @param record regexp (as in {@link Pattern regex pattern}) which match record names for whose this rule is
     * applicable. Cannot be {@code null}.
     * @param useEventName flag to use record's {@link SchemeRecord#getEventName() event name} instead of name.
     * Use only for backward compatibility.
     * @param field regexp (as in {@link Pattern regex pattern}) which match fields names for whose this rule is
     * applicable. Can be {@code null} for {@link Type#RECORD record} visibility rules.
     * @param enable is this enable or disable visibility rule.
     * @param file source file of rule, cannot be {@code null}.
     * @throws java.util.regex.PatternSyntaxException if {@code record} or {@code field} patterns cannot be compiled.
     */
    public VisibilityRule(String record, boolean useEventName, String field, boolean enable, String file) {
        this(
            Pattern.compile(record),
            useEventName,
            field == null || field.isEmpty() ? null : Pattern.compile(field),
            enable, file
        );
    }

    /**
     * Creates new visibility rule.
     *
     * @param record {@link Pattern pattern} which match record names for whose this rule is
     * applicable. Cannot be {@code null}.
     * @param useEventName flag to use record's {@link SchemeRecord#getEventName() event name} instead of name.
     * Use only for backward compatibility.
     * @param field {@link Pattern pattern} which match fields names for whose this rule is
     * applicable. Can be {@code null} for {@link Type#RECORD record} visibility rules.
     * @param enable is this enable or disable visibility rule.
     * @param file source file of rule, cannot be {@code null}.
     */
    public VisibilityRule(Pattern record, boolean useEventName, Pattern field, boolean enable, String file) {
        super(file);
        this.record = Objects.requireNonNull(record, "record");
        this.useEventName = useEventName;
        this.field = field;
        this.enable = enable;
    }

    /**
     * Returns type of this rule.
     */
    public Type getType() {
        return field != null || !incTags.isEmpty() || !excTags.isEmpty() ? Type.FIELD : Type.RECORD;
    }

    /**
     * Returns record pattern of this rule.
     */
    public Pattern getRecord() {
        return record;
    }

    /**
     * Returns even name flag of this rule.
     */
    @Deprecated
    public boolean useEventName() {
        return useEventName;
    }

    /**
     * Returns field pattern of this rule. Can be called only for rules with type {@link Type#FIELD FIELD}.
     */
    public Pattern getField() {
        if (getType() != Type.FIELD) {
            throw new IllegalStateException("Cannot provide field pattern for record-type rule");
        }
        return field;
    }

    /**
     * Returns is it enable ({@code true}) or disable ({@code false} rule.
     */
    public boolean isEnable() {
        return enable;
    }

    /**
     * Adds tag which must be set on filed to match this rule. This method is only valid for rules with
     * type {@link Type#FIELD FIELD}.
     */
    public void addIncludedTag(String tag) {
        if (getType() != Type.FIELD) {
            throw new IllegalStateException("Cannot add tag to record-type rule");
        }
        incTags.add(tag);
    }

    /**
     * Returns set of tags, all of which must be set on field, to match this rule.
     * This method is only valid for rules with type {@link Type#FIELD FIELD}.
     */
    public Set<String> getIncludedTags() {
        if (getType() != Type.FIELD) {
            throw new IllegalStateException("Cannot return tags for record-type rule");
        }
        return Collections.unmodifiableSet(incTags);
    }

    /**
     * Adds tag which must <em>not</em> be set on filed to match this rule. This method is only valid for rules with
     * type {@link Type#FIELD FIELD}.
     */
    public void addExcludedTag(String tag) {
        if (getType() != Type.FIELD) {
            throw new IllegalStateException("Cannot add tag to record-type rule");
        }
        excTags.add(tag);
    }

    /**
     * Returns set of tags, all of which must <em>not</em> be set on field, to match this rule.
     * This method is only valid for rules with type {@link Type#FIELD FIELD}.
     */
    public Set<String> getExcludedTags() {
        if (getType() != Type.FIELD) {
            throw new IllegalStateException("Cannot return tags for record-type rule");
        }
        return Collections.unmodifiableSet(excTags);
    }

    /**
     * Returns, does this rule match given {@link SchemeRecord record}.
     *
     * @param r record to match.
     * @param actualRecordName record name to use instead of default one, for generated or regional records.
     * @return {@code true} if this is record rule and it matches, {@code false} otherwise.
     */
    public boolean match(SchemeRecord r, String actualRecordName) {
        if (getType() == Type.FIELD) {
            return false;
        }
        String recordNameToUse = useEventName ? r.getEventName() : actualRecordName;
        return record.matcher(recordNameToUse).matches();
    }

    /**
     * Returns, does this rule match given {@link SchemeRecord.Field field}.
     *
     * @param actualRecordName record name to use.
     * @param f field to match.
     * @return {@code true} if this is field rule and it matches, {@code false} otherwise.
     */
    public boolean match(String actualRecordName, SchemeRecord.Field f) {
        if (getType() == Type.RECORD) {
            return false;
        }
        SchemeRecord r = f.getParent();
        String recordNameToUse = useEventName ? r.getEventName() : actualRecordName;
        // Fast skip
        if (!record.matcher(recordNameToUse).matches()) {
            return false;
        }

        String fieldNameToUse = useEventName ? f.getEventName() : f.getName();
        // Fast skip
        if (field != null && !field.matcher(fieldNameToUse).matches()) {
            return false;
        }

        boolean tagMatch = true;
        Set<String> fTags = f.getTags();
        for (String t : incTags) {
            tagMatch &= fTags.contains(t);
        }
        for (String t : excTags) {
            tagMatch &= !fTags.contains(t);
        }

        return tagMatch;
    }

    @Override
    public String toString() {
        return "VisibilityRule{" +
            "from=" + getFrom() +
            ", record='" + record + '\'' +
            (field != null ? ", field='" + field + '\'' : "") +
            ", enable=" + enable +
            ", useEventName=" + useEventName +
            (incTags.isEmpty() ? "" : ", incTags='" + incTags.toString() + '\'') +
            (excTags.isEmpty() ? "" : ", excTags='" + excTags.toString() + '\'') +
            '}';
    }
}
