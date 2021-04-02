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
package com.dxfeed.api.codegen.event;

import com.dxfeed.annotation.EventFieldMapping;
import com.dxfeed.annotation.EventFieldType;
import com.dxfeed.annotation.EventTypeMapping;
import com.dxfeed.event.market.MarketEvent;

/**
 * Testing simple custom market event
 */
@EventTypeMapping(recordName = "Custom")
public class CustomMarketEvent extends MarketEvent {
    private int customInt;
    private long customLong;
    private String customShortString;

    public CustomMarketEvent() {
    }

    public CustomMarketEvent(String eventSymbol) {
        super(eventSymbol);
    }

    public int getCustomInt() {
        return customInt;
    }

    public void setCustomInt(int customInt) {
        this.customInt = customInt;
    }

    public long getCustomLong() {
        return customLong;
    }

    public void setCustomLong(long customLong) {
        this.customLong = customLong;
    }

    @EventFieldMapping(type = EventFieldType.SHORT_STRING, fieldName = "Str")
    public String getCustomShortString() {
        return customShortString;
    }

    public void setCustomShortString(String customShortString) {
        this.customShortString = customShortString;
    }
}
