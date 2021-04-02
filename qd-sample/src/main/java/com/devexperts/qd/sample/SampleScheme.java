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
package com.devexperts.qd.sample;

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.kit.CompactIntField;
import com.devexperts.qd.kit.DecimalField;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.kit.PlainIntField;
import com.devexperts.qd.kit.StringField;

/**
 * The <code>SampleScheme</code> creates sample DataScheme for demonstration.
 */
public class SampleScheme extends DefaultScheme {
    private static final SampleScheme INSTANCE = new SampleScheme();

    public static SampleScheme getInstance() {
        return INSTANCE;
    }

    private SampleScheme() {
        super(new PentaCodec(), createRecords());
    }

    private static DataRecord[] createRecords() {
        return new DataRecord[] {
            new DefaultRecord(0, "Quote", false, new DataIntField[] {
                new CompactIntField(0, "Quote.Sequence"),
                new DecimalField(1, "Quote.Bid.Price"),
                new CompactIntField(2, "Quote.Bid.Size"),
                new DecimalField(3, "Quote.Ask.Price"),
                new CompactIntField(4, "Quote.Ask.Size")
            }, null),
            new DefaultRecord(1, "Trade", true, new DataIntField[] {
                new PlainIntField(0, "Trade.Time"),
                new CompactIntField(1, "Trade.Sequence"),
                new DecimalField(2, "Trade.Last.Price"),
                new CompactIntField(3, "Trade.Last.Size")
            }, new DataObjField[] {
                new StringField(0, "Trade.Code")
            }),
            new DefaultRecord(2, "Candle", true, new DataIntField[] {
                new PlainIntField(0, "Candle.Time"),
                new CompactIntField(1, "Candle.Sequence"),
                new DecimalField(2, "Candle.High"),
                new DecimalField(3, "Candle.Low"),
                new DecimalField(4, "Candle.Open"),
                new DecimalField(5, "Candle.Close"),
            }, null),
            new DefaultRecord(3, "Profile", false, new DataIntField[] {
                new DecimalField(0, "Profile.Beta"),
                new DecimalField(1, "Profile.Eps")
            }, new DataObjField[] {
                new StringField(0, "Profile.Description", true)
            })
        };
    }
}

