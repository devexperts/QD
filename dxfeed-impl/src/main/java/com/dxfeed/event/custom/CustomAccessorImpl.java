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
package com.dxfeed.event.custom;

import com.dxfeed.event.impl.EventUtil;

/**
 * Helper SPI-style class to provide middleware with public access to package-private constants and methods.
 */
@SuppressWarnings("UnusedDeclaration")
public class CustomAccessorImpl {
    /*
     * Design principles:
     * - single accessor class per package to simplify static import
     * - public static methods for static import
     * - method names contain name of corresponding event to avoid name collision and method overload hell
     * - each flag property has 3 methods: converter from value to flags, getter from flags, and setter to flags
     * - additional methods can be added
     */

    private CustomAccessorImpl() {}

    // ========== NuamOrder accessor methods ==========

    public static int getNuamFlags(NuamOrder order) {
        return order.getNuamFlags();
    }

    public static void setNuamFlags(NuamOrder order, int flags) {
        order.setNuamFlags(flags);
    }

    public static int nuamOrderType(NuamOrderType orderType) {
        return orderType.getCode() << NuamOrder.NUAM_ORDER_TYPE_SHIFT;
    }

    public static NuamOrderType getNuamOrderType(int flags) {
        return NuamOrderType.valueOf(
            EventUtil.getBits(flags, NuamOrder.NUAM_ORDER_TYPE_MASK, NuamOrder.NUAM_ORDER_TYPE_SHIFT));
    }

    public static int setNuamOrderType(int flags, NuamOrderType orderType) {
        return EventUtil.setBits(
            flags, NuamOrder.NUAM_ORDER_TYPE_MASK, NuamOrder.NUAM_ORDER_TYPE_SHIFT, orderType.getCode());
    }

    public static int nuamTimeInForceType(NuamTimeInForceType timeInForceType) {
        return timeInForceType.getCode() << NuamOrder.NUAM_TIME_IN_FORCE_TYPE_SHIFT;
    }

    public static NuamTimeInForceType getNuamTimeInForceType(int flags) {
        return NuamTimeInForceType.valueOf(
            EventUtil.getBits(flags, NuamOrder.NUAM_TIME_IN_FORCE_TYPE_MASK, NuamOrder.NUAM_TIME_IN_FORCE_TYPE_SHIFT));
    }

    public static int setNuamTimeInForceType(int flags, NuamTimeInForceType tifType) {
        return EventUtil.setBits(
            flags, NuamOrder.NUAM_TIME_IN_FORCE_TYPE_MASK, NuamOrder.NUAM_TIME_IN_FORCE_TYPE_SHIFT, tifType.getCode());
    }
}
