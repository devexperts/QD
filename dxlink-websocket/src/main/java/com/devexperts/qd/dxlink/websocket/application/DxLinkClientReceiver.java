/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.dxlink.websocket.application;

import com.devexperts.qd.QDContract;
import com.devexperts.qd.ng.RecordBuffer;

import java.io.IOException;
import java.util.List;
import java.util.Map;

interface DxLinkClientReceiver {

    public void receiveSetup(String version, Long keepaliveTimeout, Long acceptKeepaliveTimeout);

    public void receiveError(int channel, String error, String message);

    public void receiveAuthState(int channel, String state);

    public void receiveKeepalive(int channel);

    public void receiveChannelOpened(int channel, String service, String contract);

    public void receiveFeedConfig(int channel, Long aggregationPeriod, String dataFormat,
        Map<String, List<String>> eventFields);

    public void receiveFeedData(EventsParser parser);

    public interface EventsParser {

        public void parse(RecordBuffer recordBuffer) throws IOException;

        public QDContract getContract();
    }
}
