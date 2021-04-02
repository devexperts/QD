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
package com.devexperts.auth;

/**
 * The listener for notifications from {@link AuthSession} that session had closed for some reason.
 */
public interface SessionCloseListener {
    /**
     * This method provides notification from {@link AuthSession} that session had closed.
     * @param session that has been closed.
     * @param closeReason the closing reason.
     */
    public void close(AuthSession session, String closeReason);
}
