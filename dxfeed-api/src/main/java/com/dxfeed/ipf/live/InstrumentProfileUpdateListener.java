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
package com.dxfeed.ipf.live;

import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileType;

import java.util.Iterator;

/**
 * Notifies about instrument profile changes.
 */
public interface InstrumentProfileUpdateListener {
    /**
     * This method is invoked when a set of instrument profiles in the underlying {@link InstrumentProfileCollector}
     * changes. Each instance of the listeners receive the same instance of {@code instruments} iterator on
     * every invocation of this method. The {@code instruments} iterator used right here or stored and accessed
     * from a different thread.
     *
     * <p>Removal of instrument profile is represented by an {@link InstrumentProfile} instance with a
     * {@link InstrumentProfile#getType() type} equal to
     * <code>{@link InstrumentProfileType InstrumentProfileType}.{@link InstrumentProfileType#REMOVED REMOVED}.{@link InstrumentProfileType#name() name}()</code>.
     *
     * @param instruments iterator that represents pending instrument profile updates.
     */
    public void instrumentProfilesUpdated(Iterator<InstrumentProfile> instruments);
}
