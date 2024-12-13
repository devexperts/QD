/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools.launcher;

import com.devexperts.qd.tools.module.Module;
import com.devexperts.qd.tools.module.ModuleContext;
import com.devexperts.qd.tools.module.ModuleFactory;

public class TestModuleFactory implements ModuleFactory<TestModuleConfig> {

    private static final Builder DEFAULT_BUILDER = TestModule::new;

    @FunctionalInterface
    public interface Builder {
        Module<TestModuleConfig> build(ModuleContext context);
    }

    private Builder moduleBuilder = DEFAULT_BUILDER;

    @Override
    public String getType() {
        return TestModuleConfig.MODULE_TYPE;
    }

    @Override
    public Class<TestModuleConfig> getConfigClass() {
        return TestModuleConfig.class;
    }

    @Override
    public Module<TestModuleConfig> createModule(ModuleContext context) {
        return moduleBuilder.build(context);
    }

    /**
     * Set a method for creating next module instances
     * @param moduleBuilder module building strategy: (name, props) -> TestModule
     */
    public void setModuleBuilder(Builder moduleBuilder) {
        this.moduleBuilder = moduleBuilder;
    }

    /**
     * Return factory to a default state
     */
    public void reset() {
        moduleBuilder = DEFAULT_BUILDER;
    }
}
