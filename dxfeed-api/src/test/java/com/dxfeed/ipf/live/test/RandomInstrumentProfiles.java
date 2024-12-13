/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.ipf.live.test;

import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileField;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomInstrumentProfiles {

    private static final int MAX_BOUND = 10_000;
    private final Random rnd;

    public RandomInstrumentProfiles(Random rnd) {
        this.rnd = rnd;
    }

    public List<InstrumentProfile> randomProfiles(int count) {
        List<InstrumentProfile> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(randomProfile());
        }
        return result;
    }

    public InstrumentProfile randomProfile() {
        InstrumentProfile ip = new InstrumentProfile();
        for (InstrumentProfileField field : InstrumentProfileField.values()) {
            if (field.isNumericField()) {
                field.setNumericField(ip, rnd.nextInt(MAX_BOUND));
            } else {
                field.setField(ip, String.valueOf(rnd.nextInt(MAX_BOUND)));
            }
        }
        for (int i = 0; i < 10; i++) {
            ip.setField(String.valueOf(rnd.nextInt(MAX_BOUND)), String.valueOf(rnd.nextInt(MAX_BOUND)));
        }
        return ip;
    }
}
