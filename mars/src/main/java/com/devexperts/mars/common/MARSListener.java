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
package com.devexperts.mars.common;

/**
 * The listener attached to {@link MARSAgent} to receive notifications about new {@link MARSEvent} events.
 */
public interface MARSListener {

    /**
     * Invoked when specified {@link MARSAgent} has new {@link MARSEvent} events.
     * <p>
     * <b>NOTE:</b> the listener is invoked in a thread that has put new events into {@link MARS} instance.
     * Therefore, proper thread-safety measures must be taken by the listener to avoid data corruption.
     * Also, notification processing shall be reasonably fast and shall not call external I/O operations
     * that may block invoking thread for a long time.
     * <p>
     * <b>NOTE:</b> because of parallel multi-thread processing it may happen that certain notifications
     * are delayed and performed after all events are already retrieved and processed. That is, despite
     * contract, it may happen that there are no new events in the specified agent when notification is
     * actually performed.
     */
    public void marsChanged(MARSAgent agent);
}
