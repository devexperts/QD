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

import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.Scope;

import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import javax.swing.SwingConstants;

import static com.dxfeed.viewer.OrderCellSupport.exchangeValue;
import static com.dxfeed.viewer.OrderCellSupport.priceValue;
import static com.dxfeed.viewer.OrderCellSupport.sizeValue;
import static com.dxfeed.viewer.OrderCellSupport.textValue;
import static com.dxfeed.viewer.OrderCellSupport.timeValue;

@SuppressWarnings({"UnusedDeclaration"})
enum OrderTableColumn implements EventTableColumn<Order> {

    EXCHANGE("X", 1) {
        public ViewerCellValue getValue(Order order, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return exchangeValue(order.getExchangeCode(), isUpdated, isDisabled, tag, scheme);
        }
    },
    MARKET_MAKER("ID", 20) {
        public ViewerCellValue getValue(Order order, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            String symbol = order.getEventSymbol();

            String id = order.getMarketMaker();
            if (order.getScope() == Scope.REGIONAL) {
                Map<Character, String> mapping = (symbol.startsWith(".")) ? OPTION_EXCHANGE_MAP : STOCK_EXCHANGE_MAP;
                id = mapping.get(order.getExchangeCode());
                if (id == null)
                    id = Character.toString(order.getExchangeCode());
            } else if (order.getScope() == Scope.ORDER && (id == null || id.isEmpty())) {
                assert "JKYZ".indexOf(order.getExchangeCode()) >= 0 : "illegal exchange code: " + order.getExchangeCode(); // only BATS Z, BATS Y, EDGE A, EDGE X
                id = STOCK_EXCHANGE_MAP.get(order.getExchangeCode());
            }

            return textValue(id, isUpdated, isDisabled, tag, scheme, SwingConstants.CENTER);
        }
    },
    PRICE("Price", 50){
        public ViewerCellValue getValue(Order order, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return priceValue(order.getPrice(), isUpdated, isDisabled, tag, scheme);
        }
    },
    SIZE("Size", 50){
        public ViewerCellValue getValue(Order order, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return sizeValue(order.getSizeAsDouble(), isUpdated, isDisabled, tag, scheme);
        }
    },
    TIME("Time", 130){
        public ViewerCellValue getValue(Order order, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return timeValue(order.getTime(), isUpdated, isDisabled, tag, scheme, timeZone);
        }
    },
    SCOPE("Scope", 30){
        public ViewerCellValue getValue(Order order, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return textValue(order.getScope().name(), isUpdated, isDisabled, tag, scheme, SwingConstants.CENTER);
        }
    },
    PRICE_GROUP("Price Group", 30){
        public ViewerCellValue getValue(Order order, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return textValue(Integer.toString(tag), isUpdated, isDisabled, tag, scheme, SwingConstants.CENTER);
        }
    };

    final String caption;
    final int preferredWidth;

    OrderTableColumn(String caption, int preferredWidth) {
        this.caption = caption;
        this.preferredWidth = preferredWidth;
    }

    public String getCaption() {
        return caption;
    }

    public int getPreferredWidth() {
        return preferredWidth;
    }

    private static final Map<Character, String> STOCK_EXCHANGE_MAP = new HashMap<>();
    private static final Map<Character, String> OPTION_EXCHANGE_MAP = new HashMap<>();

    static {
        STOCK_EXCHANGE_MAP.put('A', "AMEX");
        STOCK_EXCHANGE_MAP.put('B', "BSE");
        STOCK_EXCHANGE_MAP.put('C', "NSX");
        STOCK_EXCHANGE_MAP.put('D', "FINRA");
        STOCK_EXCHANGE_MAP.put('F', "MF/MM");
        STOCK_EXCHANGE_MAP.put('I', "ISE");
        STOCK_EXCHANGE_MAP.put('J', "EDGE A");
        STOCK_EXCHANGE_MAP.put('K', "EDGE X");
        STOCK_EXCHANGE_MAP.put('M', "CHX");
        STOCK_EXCHANGE_MAP.put('N', "NYSE");
        STOCK_EXCHANGE_MAP.put('P', "ARCA");
        STOCK_EXCHANGE_MAP.put('Q', "NSDQ");
        STOCK_EXCHANGE_MAP.put('S', "NSDQ");
        STOCK_EXCHANGE_MAP.put('T', "NSDQ");
        STOCK_EXCHANGE_MAP.put('U', "OTCBB");
        STOCK_EXCHANGE_MAP.put('V', "OTC Other");
        STOCK_EXCHANGE_MAP.put('W', "CBOE");
        STOCK_EXCHANGE_MAP.put('X', "PSX");
        STOCK_EXCHANGE_MAP.put('G', "GLOBEX");
        STOCK_EXCHANGE_MAP.put('Y', "BATS Y");
        STOCK_EXCHANGE_MAP.put('Z', "BATS Z");

        OPTION_EXCHANGE_MAP.put('A', "AMEX");
        OPTION_EXCHANGE_MAP.put('B', "BOX");
        OPTION_EXCHANGE_MAP.put('C', "CBOE");
        OPTION_EXCHANGE_MAP.put('I', "ISE");
        OPTION_EXCHANGE_MAP.put('M', "MIAX");
        OPTION_EXCHANGE_MAP.put('N', "ARCA");
        OPTION_EXCHANGE_MAP.put('Q', "NSDQ");
        OPTION_EXCHANGE_MAP.put('T', "BSE");
        OPTION_EXCHANGE_MAP.put('W', "C2");
        OPTION_EXCHANGE_MAP.put('X', "PHLX");
        OPTION_EXCHANGE_MAP.put('G', "GLOBEX");
        OPTION_EXCHANGE_MAP.put('Z', "BATS");
    }
}
