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
package com.devexperts.qd;

import com.devexperts.services.Service;

/**
 * Creates {@link DataScheme data scheme} instance for the given specification.
 */
@Service
public interface DataSchemeFactory {
    /**
     * Creates {@link DataScheme data scheme} instance for the given specification.
     *
     * @param specification specification to use.
     * @return created {@link DataScheme data scheme} or {@code null} if specification is not supported by this factory.
     * @throws IllegalArgumentException if specification is supported by this factory, but {@link DataScheme data scheme} cannot be created.
     */
    public DataScheme createDataScheme(String specification);
}
