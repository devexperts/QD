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

import com.dxfeed.annotation.ClassValueMapping;
import com.dxfeed.annotation.EventFieldMapping;
import com.dxfeed.annotation.EventFieldType;
import com.dxfeed.annotation.EventTypeMapping;
import com.dxfeed.event.market.Order;

import java.util.Arrays;

/**
 * Test order inheritance
 */
@EventTypeMapping(recordName = "Order")
public class BetterOrder extends Order {
    private OrderType type;

    public BetterOrder() {
    }

    public BetterOrder(String eventSymbol) {
        super(eventSymbol);
    }

    @EventFieldMapping(type = EventFieldType.INT)
    public OrderType getType() {
        return type;
    }

    public void setType(OrderType type) {
        this.type = type;
    }

    // testing enum serialization
    public enum OrderType {
        NO_TYPE(0), TYPE_A(1), TYPE_B(2);

        private final int intType;

        OrderType(int intType) {
            this.intType = intType;
        }

        @ClassValueMapping
        int getIntType() {
            return intType;
        }

        @ClassValueMapping
        static OrderType of(int type) {
            return Arrays.stream(values())
                .filter(t -> t.intType == type)
                .findFirst().orElseThrow(IllegalArgumentException::new);
        }
    }
}
