/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.ipf.tools;

import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileField;
import com.dxfeed.ipf.InstrumentProfileType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility methods for IPF.
 */
public class InstrumentProfileUtil {
    /**
     * Returns list of PRODUCT profiles created for the FUTURE instruments in the given list.
     *
     * @param profiles list of instruments
     * @return list of PRODUCT instrument profiles
     */
    public static List<InstrumentProfile> createProducts(List<InstrumentProfile> profiles) {
        Map<String, InstrumentProfile> products = new HashMap<>();
        List<InstrumentProfile> result = new ArrayList<>();
        for (InstrumentProfile ip : profiles) {
            if (ip.getType().equals(InstrumentProfileType.FUTURE.name()) && ip.getProduct().length() > 0) {
                InstrumentProfile product = products.get(ip.getProduct());
                if (product == null) {
                    product = new InstrumentProfile();
                    product.setType(InstrumentProfileType.PRODUCT.name());
                    product.setSymbol(ip.getProduct());
                    products.put(ip.getProduct(), product);
                    result.add(product);
                }
                mergeText(product, ip, InstrumentProfileField.DESCRIPTION);
                mergeText(product, ip, InstrumentProfileField.LOCAL_DESCRIPTION);
                mergeFirst(product, ip, InstrumentProfileField.COUNTRY);
                mergeFirst(product, ip, InstrumentProfileField.OPOL);
                mergeFirst(product, ip, InstrumentProfileField.EXCHANGES);
                mergeFirst(product, ip, InstrumentProfileField.CURRENCY);
                mergeFirst(product, ip, InstrumentProfileField.CFI);
                mergeFirst(product, ip, InstrumentProfileField.ICB);
                mergeFirst(product, ip, InstrumentProfileField.SIC);
                mergeFirst(product, ip, InstrumentProfileField.PRICE_INCREMENTS);
                mergeFirst(product, ip, InstrumentProfileField.TRADING_HOURS);
            }
        }
        return result;
    }

    private static void mergeText(InstrumentProfile target, InstrumentProfile source, InstrumentProfileField field) {
        String t = field.getField(target);
        String s = field.getField(source);
        if (t.isEmpty()) {
            field.setField(target, s);
        } else if (s.length() != 0) {
            int n = Math.min(t.length(), s.length());
            for (int i = 0; i < n; i++) {
                if (t.charAt(i) != s.charAt(i)) {
                    n = i;
                }
            }
            while (n > 0 && (t.charAt(n - 1) <= ' ' ||
                t.charAt(n - 1) == ',' || t.charAt(n - 1) == ';' || t.charAt(n - 1) == '-'))
            {
                n--;
            }
            if (n > 0 && n < t.length()) {
                field.setField(target, t.substring(0, n));
            }
        }
    }

    private static void mergeFirst(InstrumentProfile target, InstrumentProfile source, InstrumentProfileField field) {
        if (field.getField(target).isEmpty()) {
            field.setField(target, field.getField(source));
        }
    }
}
