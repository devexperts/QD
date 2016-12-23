/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.api.codegen;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

class CodeGenContext {
    private static final Path RECORD_MAPPING_FACTORY_NAME = Paths.get("META-INF/services/com.devexperts.qd.ng.RecordMappingFactory");
    private static final Path EVENT_DELEGATE_FACTORY_NAME = Paths.get("META-INF/services/com.dxfeed.api.impl.EventDelegateFactory");

    private final Map<String, FactoryImplGen> factoryGens = new LinkedHashMap<>();
    private final Map<ClassName, DelegateGen> delegateGens = new LinkedHashMap<>();
    private final Map<ClassName, MappingGen> mappingGens = new LinkedHashMap<>();
    private final Map<String, RecordDesc> recordsDescs = new LinkedHashMap<>();

    private final CodeGenEnvironment env;

    CodeGenContext(CodeGenEnvironment env) {
        this.env = env;
    }

    private FactoryImplGen getFactoryImplGen(String basePackageName) {
        return factoryGens.computeIfAbsent(basePackageName, bpn -> new FactoryImplGen(NamingConventions.buildFactoryName(bpn), env));
    }

    private MappingGen getMappingGen(String basePackageName, String mappingName) {
        ClassName mappingClassName = NamingConventions.buildMappingName(basePackageName, mappingName);
        return mappingGens.computeIfAbsent(mappingClassName, cn -> new MappingGen(cn, env));
    }

    private RecordDesc getRecordDesc(String basePackageName, String recordName) {
        return recordsDescs.computeIfAbsent(recordName, ign -> new RecordDesc(basePackageName, recordName));
    }

    DelegateGen delegate(String baseDelegateName, CodeGenType eventClass, String mappingName, String recordName) {
        String basePackageName = eventClass.getClassName().getPackageName();
        FactoryImplGen factoryGen = getFactoryImplGen(basePackageName);
        MappingGen mapping = getMappingGen(basePackageName, mappingName);
        RecordDesc record = getRecordDesc(basePackageName, recordName);
        factoryGen.addRecordMapping(record, mapping);
        ClassName delegateName = NamingConventions.buildDelegateName(basePackageName, baseDelegateName);
        DelegateGen result = new DelegateGen(delegateName, eventClass, record, factoryGen, mapping, env);
        factoryGen.addRecordDelegate(record, result);
        delegateGens.put(delegateName, result);
        return result;
    }

    DelegateGen delegate(String baseDelegateName, Class<?> eventClass, String recordName) {
        return delegate(baseDelegateName, new JavaClassType(eventClass), NamingConventions.getMappingNameFromRecord(recordName), recordName);
    }

    DelegateGen delegate(String baseDelegateName, Class<?> eventClass, String mappingName, String recordName) {
        return delegate(baseDelegateName, new JavaClassType(eventClass), mappingName, recordName);
    }

    DelegateGen record(String basePackageName, String mappingName, String recordName) {
        FactoryImplGen factoryGen = getFactoryImplGen(basePackageName);
        RecordDesc record = getRecordDesc(basePackageName, recordName);
        MappingGen mapping = getMappingGen(basePackageName, mappingName);
        factoryGen.addRecordMapping(record, mapping);
        return new DelegateGen(null, null, record, factoryGen, mapping, env);
    }

    DelegateGen record(String basePackageName, String recordName) {
        return record(basePackageName, NamingConventions.getMappingNameFromRecord(recordName), recordName);
    }

    FactoryImplGen factory(String basePackageName) {
        return getFactoryImplGen(basePackageName);
    }

    void generateSources() throws IOException {
        for (DelegateGen gen : delegateGens.values()) {
            gen.generate();
        }
        for (FactoryImplGen gen : factoryGens.values()) {
            gen.generate();
        }
    }

    void generateResources() throws IOException {
        List<String> factories = factoryGens.values().stream()
            .map(factory -> factory.getClassName().toString())
            .collect(Collectors.toList());
        env.writeTextResourceFile(RECORD_MAPPING_FACTORY_NAME, factories);
        env.writeTextResourceFile(EVENT_DELEGATE_FACTORY_NAME, factories);
    }
}
