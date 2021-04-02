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
package com.devexperts.qd.qtp;

import com.devexperts.qd.spi.QDFilterFactory;
import com.devexperts.services.Service;

/**
 * @deprecated Use {@link QDFilterFactory} instead.
 */
@Service(
    upgradeMethod = "com.devexperts.qd.spi.QDFilterFactory.fromFilterFactory",
    combineMethod = "com.devexperts.qd.spi.QDFilterFactory.combineFactories")
public interface SubscriptionFilterFactory extends com.devexperts.qd.SubscriptionFilterFactory {
}
