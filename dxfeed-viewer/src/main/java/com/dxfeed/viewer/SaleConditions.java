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

import java.util.HashMap;
import java.util.Map;

public class SaleConditions {
    private static final Map<Character, String> NYSE_SALE_CONDITIONS = new HashMap<Character, String>();
    private static final Map<Character, String> NASDAQ_SALE_CONDITIONS = new HashMap<Character, String>();

    public static String getSaleConditionsDescription(String symbol, String saleConditions) {
        String result = "";
        String cond = "";
        if (saleConditions != null && saleConditions.length() > 0) {
            if (symbol.startsWith(".")) {
            // options
            } else if (symbol.startsWith("/")) {
            // futures
            } else {
            // stocks
                if (saleConditions.startsWith("0") || saleConditions.startsWith("1")) {
                // NASDAQ
                    if (saleConditions.startsWith("1")) result = "Trade Through Exempt";
                    for (int i = 1; i < saleConditions.length(); i++) {
                        cond = NASDAQ_SALE_CONDITIONS.get(saleConditions.charAt(i));
                        if (cond != null) result += result.length() > 0 ? ", " + cond : cond;
                    }

                } else {
                // NYSE
                    if (saleConditions.startsWith("X")) result = "Trade Through Exempt";
                    for (int i = 1; i < saleConditions.length(); i++) {
                        cond = NYSE_SALE_CONDITIONS.get(saleConditions.charAt(i));
                        if (cond != null) result += result.length() > 0 ? ", " + cond : cond;
                    }
                }
            }
        }
        return result;
    }

    static {
        NYSE_SALE_CONDITIONS.put('@', "Regular");
        NYSE_SALE_CONDITIONS.put('C', "Cash");
        NYSE_SALE_CONDITIONS.put('E', "Autoexec");
        NYSE_SALE_CONDITIONS.put('F', "Inter Market Sweep");
        NYSE_SALE_CONDITIONS.put('H', "Price Variation");
        NYSE_SALE_CONDITIONS.put('I', "CAP Election");
        NYSE_SALE_CONDITIONS.put('K', "Rule127/Rule155");
        NYSE_SALE_CONDITIONS.put('L', "Sold Last");
        NYSE_SALE_CONDITIONS.put('M', "Market Center Official Close");
        NYSE_SALE_CONDITIONS.put('N', "Next Day");
        NYSE_SALE_CONDITIONS.put('O', "Market Center Opening");
        NYSE_SALE_CONDITIONS.put('P', "Prior Reference");
        NYSE_SALE_CONDITIONS.put('Q', "Market Center Official Open");
        NYSE_SALE_CONDITIONS.put('R', "Seller");
        NYSE_SALE_CONDITIONS.put('T', "ETH"); // Form T
        NYSE_SALE_CONDITIONS.put('U', "ETH Out Of Seq.");
        NYSE_SALE_CONDITIONS.put('V', "Stock Option");
        NYSE_SALE_CONDITIONS.put('B', "Average Price");
        NYSE_SALE_CONDITIONS.put('X', "Cross");
        NYSE_SALE_CONDITIONS.put('Z', "Out Of Seq.");
        NYSE_SALE_CONDITIONS.put('4', "Derivatively Priced");
        NYSE_SALE_CONDITIONS.put('5', "Market Center Reopen");
        NYSE_SALE_CONDITIONS.put('6', "Market Center Close");

        NASDAQ_SALE_CONDITIONS.put('@', "Regular");
        NASDAQ_SALE_CONDITIONS.put('A', "Acquisition");
        NASDAQ_SALE_CONDITIONS.put('B', "Bunched");
        NASDAQ_SALE_CONDITIONS.put('C', "Cash");
        NASDAQ_SALE_CONDITIONS.put('D', "Distribution");
        NASDAQ_SALE_CONDITIONS.put('F', "Inter Market Sweep");
        NASDAQ_SALE_CONDITIONS.put('G', "Bunched Sold");
        NASDAQ_SALE_CONDITIONS.put('H', "Price Variation");
        NASDAQ_SALE_CONDITIONS.put('K', "Rule127/Rule155");
        NASDAQ_SALE_CONDITIONS.put('L', "Sold Last");
        NASDAQ_SALE_CONDITIONS.put('M', "Market Center Official Close");
        NASDAQ_SALE_CONDITIONS.put('N', "NextDay");
        NASDAQ_SALE_CONDITIONS.put('O', "Market Center Opening");
        NASDAQ_SALE_CONDITIONS.put('P', "PriorRef");
        NASDAQ_SALE_CONDITIONS.put('Q', "Market Center Official Open");
        NASDAQ_SALE_CONDITIONS.put('R', "Seller");
        NASDAQ_SALE_CONDITIONS.put('S', "Split");
        NASDAQ_SALE_CONDITIONS.put('T', "ETH"); // Form T
        NASDAQ_SALE_CONDITIONS.put('U', "ETH Out Of Seq.");
        NASDAQ_SALE_CONDITIONS.put('V', "Stock Option");
        NASDAQ_SALE_CONDITIONS.put('W', "Average Price");
        NASDAQ_SALE_CONDITIONS.put('X', "Cross");
        NASDAQ_SALE_CONDITIONS.put('Y', "Yellow Flag");
        NASDAQ_SALE_CONDITIONS.put('Z', "Out Of Seq.");
        NASDAQ_SALE_CONDITIONS.put('1', "Stopped");
        NASDAQ_SALE_CONDITIONS.put('2', "Stopped Sold Last");
        NASDAQ_SALE_CONDITIONS.put('3', "Stopped Out Of Seq.");
    }
}
