/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.market;

import java.util.List;

class OrderBaseDelegateSet<T extends OrderBase, D extends OrderBaseDelegateImpl<T>>
    extends SourceBasedDelegateSet<T, D>
{
    OrderBaseDelegateSet(Class<T> eventType) {
        super(eventType);
    }

    @Override
    public List<D> getPubDelegatesByEvent(T event) {
        return getPubDelegatesBySourceId(event.getSource().id());
    }
}
