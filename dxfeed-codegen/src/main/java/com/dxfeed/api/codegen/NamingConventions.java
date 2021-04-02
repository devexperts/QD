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
package com.dxfeed.api.codegen;

class NamingConventions {
    private static final String DELEGATE_SUFFIX = "Delegate";
    private static final String MAPPING_PACKAGE = ".impl";
    private static final String MAPPING_SUFFIX = "Mapping";

    static ClassName buildFactoryName(String basePackageName) {
        int i = basePackageName.lastIndexOf('.');
        String lastComponent = i < 0 ? basePackageName : basePackageName.substring(i + 1);
        return new ClassName(basePackageName,
            Character.toUpperCase(lastComponent.charAt(0)) + lastComponent.substring(1) + "FactoryImpl");
    }

    static ClassName buildDelegateName(String basePackageName, String baseDelegateName) {
        return new ClassName(basePackageName, baseDelegateName + DELEGATE_SUFFIX);
    }

    static ClassName buildDelegateName(ClassName eventClassName) {
        return buildDelegateName(eventClassName.getPackageName(), eventClassName.getSimpleName());
    }

    static ClassName buildMappingName(String basePackageName, String baseMappingName) {
        return new ClassName(basePackageName + MAPPING_PACKAGE, baseMappingName + MAPPING_SUFFIX);
    }

    static ClassName buildMappingName(ClassName eventClassName) {
        return buildMappingName(eventClassName.getPackageName(), eventClassName.getSimpleName());
    }

    static String getMappingNameFromRecord(String recordName) {
        return recordName.replaceAll("[&.]", "");
    }

    private NamingConventions() {
    }
}
