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
package com.devexperts.rmi.task;

/**
 * This interface is used to support on service of the functional {@link RMIChannel}.
 * Exported interfaces must implement this interface to support channels.
 */
public interface RMIChannelSupport<T> {
    /**
     * Performs initialization of {@link RMIChannel} before it opens.
     * The implementation of this method shall add all channel handlers to the channel using
     * <code>task.{@link RMITask#getChannel() getChannel}().{@link RMIChannel#addChannelHandler(RMIService) addChannelHandler}(...)</code>
     *
     * @param task the {@link RMITask}.
     */
    public void openChannel(RMITask<T> task);
}
