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
package com.devexperts.qd.qtp.auth;

import com.devexperts.auth.AuthSession;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.ChannelShaper;
import com.devexperts.services.Service;

/**
 * The <code>ChannelShapersFactory</code> created of actual {@link ChannelShaper ChannelShapers} based on
 * the {@link AuthSession session}, which is provided by the corresponding {@link QDAuthRealm}.
 */
@Service
public interface ChannelShapersFactory {
    /**
     * Creates array of channel shapers.
     * Returns {@code null} if the session does not contain the information that this factory is supposed to see there,
     * e.g. it was created by a non-compatible {@link QDAuthRealm}.
     *
     * @param agentAdapter the agent adapter.
     * @param session the auth session.
     * @return array of channel shapers.
     */
    public ChannelShaper[] createChannelShapers(AgentAdapter agentAdapter, AuthSession session);
}
