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

/**
 * Description of one mapping between record and event.
 * <p>
 * This class does nothing now, as mappings are not implemented yet.
 *
 * @deprecated Will be improved in near future.
 */
@Deprecated
public final class SchemeMapping extends NamedEntity<SchemeMapping> {
    SchemeMapping(String name, Mode mode, String doc, String file) {
        super(name, mode, doc, file);
    }
}
