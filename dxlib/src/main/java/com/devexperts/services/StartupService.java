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
package com.devexperts.services;

/**
 * Classes implementing this interface and registering this implementation as a
 * service will be created and invoked the first time
 * {@link Services#startup()} is called.
 * @deprecated No replacement
 */
@Service
public interface StartupService {
    public void start();
}
