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
package com.devexperts.qd.impl.matrix.management.impl;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.impl.matrix.management.CollectorManagement;
import com.devexperts.qd.impl.matrix.management.CollectorManagementFactory;

public class CollectorManagementFactoryImpl implements CollectorManagementFactory {
    public CollectorManagement getInstance(DataScheme scheme, QDContract contract, String keyProperties) {
        return CollectorManagementImplOneContract.getInstance(scheme, contract, keyProperties);
    }
}
