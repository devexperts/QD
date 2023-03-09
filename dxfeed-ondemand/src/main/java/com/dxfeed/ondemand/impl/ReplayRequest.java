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
package com.dxfeed.ondemand.impl;

import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.ByteArrayOutput;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This internal class is public for implementation purposes only.
 */
public class ReplayRequest {
    private MarketDataToken token;
    private long allowedDelay;
    private long requestTime;
    private final ByteArrayOutput requestKeysOutput = new ByteArrayOutput();
    private ByteArrayInput requestKeysInput;

    public MarketDataToken getToken() {
        return token;
    }

    public void setToken(MarketDataToken token) {
        this.token = token;
    }

    public long getAllowedDelay() {
        return allowedDelay;
    }

    public void setAllowedDelay(long allowedDelay) {
        this.allowedDelay = allowedDelay;
    }

    public long getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(long requestTime) {
        this.requestTime = requestTime;
    }

    public void addRequestKeys(Collection<Key> keys) throws IOException {
        for (Key key : keys) {
            key.writeKey(requestKeysOutput);
        }
    }

    @SuppressWarnings("unused")
    public ByteArrayInput getRequestKeysInput() {
        return requestKeysInput;
    }

    public ByteArrayOutput write() throws IOException {
        Map<String, Object> elements = new LinkedHashMap<>();
        elements.put("contract", token.getTokenContract());
        elements.put("user", token.getTokenUser());
        elements.put("expiration", Long.toString(token.getTokenExpiration()));
        elements.put("digest", token.getTokenDigest());
        elements.put("delay", Long.toString(allowedDelay));
        elements.put("time", Long.toString(requestTime));
        ReplayUtil.addGZippedElement(elements, "keys", requestKeysOutput);
        return ReplayUtil.writeElements(elements);
    }

    public void read(ByteArrayInput in) throws IOException {
        Map<String, byte[]> elements = ReplayUtil.readElements(in);
        token = new MarketDataToken(new String(elements.get("contract"), UTF_8),
            new String(elements.get("user"), UTF_8),
            Long.parseLong(new String(elements.get("expiration"), UTF_8)),
            elements.get("digest"));
        allowedDelay = elements.containsKey("delay") ? Long.parseLong(new String(elements.get("delay"), UTF_8)) : 0;
        requestTime = Long.parseLong(new String(elements.get("time"), UTF_8));
        requestKeysInput = ReplayUtil.getGZippedElement(elements, "keys");
    }
}
