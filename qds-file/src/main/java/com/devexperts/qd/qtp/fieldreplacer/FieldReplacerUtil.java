/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2020 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.fieldreplacer;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.qtp.FieldReplacer;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.services.Services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FieldReplacerUtil {

    private static final String DELIMETER = ":";

    private static final Iterable<FieldReplacer.Factory> FIELD_REPLACER_FACTORIES =
        Services.createServices(FieldReplacer.Factory.class, ClassLoader.getSystemClassLoader());

    /**
     * Creates {@link FieldReplacer field replacers} for specified {@link DataScheme data scheme} from configuration.
     * <p><b>NOTE: size of created list may be less then number of specifications in configuration
     * if any specification is not supported from all {@link FieldReplacer.Factory factories}.</b>
     *
     * @param config     configuration of {@link FieldReplacer field replacers}.
     *                   Either single field replacer specification or list of specification, where each specification
     *                   is enclosed in square [...] or round (...) brackets.
     * @param dataScheme current {@link DataScheme data scheme}
     * @return list of {@link FieldReplacer field replacers} created from specified configuration.
     */
    public static List<FieldReplacer> createFieldReplacersFromConfig(String config, DataScheme dataScheme) {
        List<String> fieldReplacerSpecs;
        if (config.startsWith("(")) {
            fieldReplacerSpecs = QDConfig.splitParenthesisSeparatedString(config);
        } else {
            fieldReplacerSpecs = Collections.singletonList(config);
        }
        List<FieldReplacer> res = new ArrayList<>();
        for (String fieldReplacerSpec : fieldReplacerSpecs) {
            for (FieldReplacer.Factory factory : FIELD_REPLACER_FACTORIES) {
                FieldReplacer fieldReplacer = factory.createFieldReplacer(fieldReplacerSpec, dataScheme);
                if (fieldReplacer != null) {
                    res.add(fieldReplacer);
                    break;
                }
            }
        }
        return Collections.unmodifiableList(res);
    }
}
