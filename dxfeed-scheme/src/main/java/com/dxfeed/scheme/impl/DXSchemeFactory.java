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
package com.dxfeed.scheme.impl;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.DataSchemeFactory;
import com.devexperts.services.ServiceProvider;
import com.dxfeed.scheme.DXScheme;
import com.dxfeed.scheme.EmbeddedTypes;
import com.dxfeed.scheme.SchemeException;

import java.io.IOException;

@ServiceProvider
public class DXSchemeFactory implements DataSchemeFactory {
    protected static final String EXTERNAL_SCHEME = "ext:";

    private static final EmbeddedTypes DEFAULT_TYPES = new DefaultEmbeddedTypes();

    public DXSchemeFactory() {
        // Do nothing, but to be sure that it could be loaded as service
    }

    @Override
    public DataScheme createDataScheme(String specification) {
        // Check, could we load this specification or not
        if (!specification.startsWith(EXTERNAL_SCHEME)) {
            return null;
        }
        // Cut off "ext:"
        specification = specification.substring(EXTERNAL_SCHEME.length());
        try {
            return DXScheme.newLoader()
                .withTypes(DEFAULT_TYPES)
                .fromSpecification(specification)
                .load();
        } catch (SchemeException e) {
            if (e.getCauses().isEmpty()) {
                throw new IllegalArgumentException("Cannot load scheme from \"" + EXTERNAL_SCHEME +
                    specification + "\": " + e.getMessage(), e);
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("Cannot load scheme from \"" + EXTERNAL_SCHEME)
                    .append(specification)
                    .append("\": ")
                    .append(e.getMessage())
                    .append("\n");
                for (SchemeException cex : e.getCauses()) {
                    sb.append(cex.getMessage())
                        .append("\n");
                }
                // Remove last "\n"
                sb.setLength(sb.length() - 1);
                throw new IllegalArgumentException(sb.toString(), e);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot load scheme from \"" + EXTERNAL_SCHEME +
                specification + "\": " + e.getMessage(), e);
        }
    }
}
