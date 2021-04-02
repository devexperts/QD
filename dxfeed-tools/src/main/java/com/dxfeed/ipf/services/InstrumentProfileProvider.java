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
package com.dxfeed.ipf.services;

import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.live.InstrumentProfileCollector;

import java.util.List;

/**
 * Provides list of {@link InstrumentProfile} for publishing by {@link InstrumentProfileService}.
 * @deprecated Use {@link InstrumentProfileCollector} with {@link InstrumentProfileServer}
 */
public interface InstrumentProfileProvider {
    /**
     * Provides list of {@link InstrumentProfile} for publishing by {@link InstrumentProfileService}.
     * Instrument profiles will be published in the order of listing.
     */
    public List<InstrumentProfile> getInstrumentProfiles();
}
