/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.hub.config;

import com.devexperts.qd.config.ConfigUtil;
import com.devexperts.util.InvalidFormatException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;

@SuppressWarnings("unused")
public class UniverseConfig {

    private Map<String, SpaceConfig> spaces = Collections.emptyMap();
    private Map<String, List<ChannelConfig>> products = Collections.emptyMap();

    private List<String> activeSpaces = Collections.emptyList();
    private List<String> enableWildcardsSpaces = Collections.emptyList();
    private List<String> eventTimeSequenceSpaces = Collections.emptyList();
    private List<String> storeEverythingSpaces = Collections.emptyList();

    public UniverseConfig() {
    }

    @Nonnull
    public Map<String, SpaceConfig> getSpaces() {
        return spaces;
    }

    public void setSpaces(@Nonnull Map<String, SpaceConfig> spaces) {
        this.spaces =
            ConfigUtil.resolveNames(Objects.requireNonNull(spaces), SpaceConfig::getName, SpaceConfig::setName, true);
    }

    @Nonnull
    public Map<String, List<ChannelConfig>> getProducts() {
        return products;
    }

    public void setProducts(@Nonnull Map<String, List<ChannelConfig>> products) {
        this.products = Objects.requireNonNull(products);
    }

    public List<String> getActiveSpaces() {
        return activeSpaces;
    }

    public void setActiveSpaces(@Nonnull List<String> activeSpaces) {
        this.activeSpaces = Objects.requireNonNull(activeSpaces);
    }

    public List<String> getEnableWildcardsSpaces() {
        return enableWildcardsSpaces;
    }

    public void setEnableWildcardsSpaces(@Nonnull List<String> enableWildcardsSpaces) {
        this.enableWildcardsSpaces = Objects.requireNonNull(enableWildcardsSpaces);
    }

    public List<String> getEventTimeSequenceSpaces() {
        return eventTimeSequenceSpaces;
    }

    public void setEventTimeSequenceSpaces(@Nonnull List<String> eventTimeSequenceSpaces) {
        this.eventTimeSequenceSpaces = Objects.requireNonNull(eventTimeSequenceSpaces);
    }

    public List<String> getStoreEverythingSpaces() {
        return storeEverythingSpaces;
    }

    public void setStoreEverythingSpaces(@Nonnull List<String> storeEverythingSpaces) {
        this.storeEverythingSpaces = Objects.requireNonNull(storeEverythingSpaces);
    }

    public void applyOverrides() {
        applyOverride(activeSpaces, SpaceConfig::setActive);
        applyOverride(enableWildcardsSpaces, SpaceConfig::setEnableWildcards);
        applyOverride(eventTimeSequenceSpaces, SpaceConfig::setEventTimeSequence);
        applyOverride(storeEverythingSpaces, SpaceConfig::setStoreEverything);
    }

    // FIXME: javadoc
    private void applyOverride(List<String> override, BiConsumer<SpaceConfig, Boolean> setter) {
        // IDEA: use space tags for
        // FIXME: support '!*' pattern
        if (!override.isEmpty()) {
            boolean all = override.contains("*");
            // FIXME: should mentioning an unknown space be an error?
            spaces.values().forEach(space -> setter.accept(space, all || override.contains(space.getName())));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof UniverseConfig))
            return false;
        UniverseConfig that = (UniverseConfig) o;
        return Objects.equals(spaces, that.spaces) &&
            Objects.equals(products, that.products) &&
            Objects.equals(activeSpaces, that.activeSpaces) &&
            Objects.equals(enableWildcardsSpaces, that.enableWildcardsSpaces) &&
            Objects.equals(eventTimeSequenceSpaces, that.eventTimeSequenceSpaces) &&
            Objects.equals(storeEverythingSpaces, that.storeEverythingSpaces);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spaces, products, activeSpaces, enableWildcardsSpaces, eventTimeSequenceSpaces, storeEverythingSpaces);
    }

    @Override
    public String toString() {
        return "Universe{spaces=" + spaces +
            ", products=" + products +
            ", activeSpaces=" + activeSpaces +
            ", enableWildcardsSpaces=" + enableWildcardsSpaces +
            ", eventTimeSequenceSpaces=" + eventTimeSequenceSpaces +
            ", storeEverythingSpaces=" + storeEverythingSpaces +
            "}";
    }

    // builder methods

    public UniverseConfig withSpaces(Map<String, SpaceConfig> spaces) {
        setSpaces(spaces);
        return this;
    }

    public UniverseConfig withSpace(SpaceConfig space) {
        String name = space.getName();
        if (name == null)
            throw new InvalidFormatException("Explicit name is absent for " + space);
        spaces = ConfigHelper.ensureLinkedHashMap(spaces);
        spaces.put(name, space);
        return this;
    }

    public UniverseConfig withProducts(Map<String, List<ChannelConfig>> products) {
        setProducts(products);
        return this;
    }

    public UniverseConfig withProduct(@Nonnull String name, @Nonnull List<ChannelConfig> product) {
        products = ConfigHelper.ensureLinkedHashMap(products);
        products.put(name, product);
        return this;
    }

    public UniverseConfig withProduct(@Nonnull String name, ChannelConfig... product) {
        products = ConfigHelper.ensureLinkedHashMap(products);
        products.put(name, Arrays.asList(product));
        return this;
    }

    public UniverseConfig withActiveSpaces(List<String> activeSpaces) {
        setActiveSpaces(activeSpaces);
        return this;
    }

    public UniverseConfig withActiveSpace(String activeSpace) {
        activeSpaces = ConfigHelper.ensureArrayList(activeSpaces);
        activeSpaces.add(activeSpace);
        return this;
    }

    public UniverseConfig withEnableWildcardsSpaces(List<String> enableWildcardsSpaces) {
        setEnableWildcardsSpaces(enableWildcardsSpaces);
        return this;
    }

    public UniverseConfig withEnableWildcardsSpace(String enableWildcardsSpace) {
        enableWildcardsSpaces = ConfigHelper.ensureArrayList(enableWildcardsSpaces);
        enableWildcardsSpaces.add(enableWildcardsSpace);
        return this;
    }

    public UniverseConfig withEventTimeSequenceSpaces(List<String> eventTimeSequenceSpaces) {
        setEventTimeSequenceSpaces(eventTimeSequenceSpaces);
        return this;
    }

    public UniverseConfig withEventTimeSequenceSpace(String eventTimeSequenceSpace) {
        eventTimeSequenceSpaces = ConfigHelper.ensureArrayList(eventTimeSequenceSpaces);
        eventTimeSequenceSpaces.add(eventTimeSequenceSpace);
        return this;
    }

    public UniverseConfig withStoreEverythingSpaces(List<String> storeEverythingSpaces) {
        setStoreEverythingSpaces(storeEverythingSpaces);
        return this;
    }

    public UniverseConfig withStoreEverythingSpace(String storeEverythingSpace) {
        storeEverythingSpaces = ConfigHelper.ensureArrayList(storeEverythingSpaces);
        storeEverythingSpaces.add(storeEverythingSpace);
        return this;
    }
}
