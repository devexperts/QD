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
package com.dxfeed.viewer;

import com.dxfeed.event.market.MarketEvent;

import java.util.TimeZone;

interface EventTableColumn<E extends MarketEvent> {
    public String getCaption();
    public int getPreferredWidth();
    public ViewerCellValue getValue(E event, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone);
}
