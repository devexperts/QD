/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.impl;

import java.util.concurrent.Executor;

import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.task.RMIChannelType;

public interface RMIChannelOwner {
    public Executor getExecutor();
    public RMIRequestMessage<?> getRequestMessage();
    public RMIChannelType getChannelType();
}
