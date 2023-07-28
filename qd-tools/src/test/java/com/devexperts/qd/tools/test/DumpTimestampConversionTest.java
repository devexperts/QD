/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools.test;

import com.devexperts.io.IOUtil;
import com.devexperts.io.StreamInput;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.HeartbeatPayload;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.file.FileWriterImpl;
import com.devexperts.qd.tools.Tools;
import com.devexperts.qd.util.Decimal;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.isolated.Isolated;
import com.devexperts.test.isolated.IsolatedRunner;
import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimeUtil;
import com.dxfeed.api.impl.SchemeBuilder;
import com.dxfeed.api.impl.SchemeProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests conversion of files between different text and binary formats with different timestamps via dump tool.
 *
 * Test replaces default scheme using global property, so requires isolation.
 */
@SuppressWarnings("SameParameterValue")
@RunWith(IsolatedRunner.class)
@Isolated({"com.dxfeed", "com.devexperts.qd"})
public class DumpTimestampConversionTest {

    private static final DataScheme SCHEME = DumpSchemeProvider.getInstance();
    private static final DataRecord QUOTE_RECORD = SCHEME.getRecord(0);
    private static final int QUOTE_BID_PRICE_INDEX = QUOTE_RECORD.findFieldByName("Bid.Price").getIndex();

    private static final String EXPECTED_SYMBOL = "IBM.TEST";
    private static final String HEX = "0123456789abcdef";

    private static final long EXPECTED_TIMESTAMP_0 = TimeFormat.DEFAULT.parse("2013-09-10T18:00:04.123+0400").getTime();
    private static final double EXPECTED_BID_PRICE_0 = 12.34;

    // 1.5 hours between times to make sure it really hangs if [speed=max] is not working
    private static final long EXPECTED_TIMESTAMP_1 = TimeFormat.DEFAULT.parse("2013-09-10T19:35:00.987+0400").getTime();
    private static final double EXPECTED_BID_PRICE_1_1 = 12.35;
    private static final double EXPECTED_BID_PRICE_1_2 = 12.36;

    private static final String FILE_EXTENSION = ".temp";
    private static final String TIME_EXTENSION = ".time";
    private static final File SOURCE_FILE = new File("DumpTimestampConversionTest.src" + FILE_EXTENSION);
    private static final File DESTINATION_FILE = new File("DumpTimestampConversionTest.dst" + FILE_EXTENSION);

    private static final String SOURCE_FILE_SPLIT = "DumpTimestampConversionTest-~.src" + FILE_EXTENSION;
    private static final String DESTINATION_FILE_SPLIT = "DumpTimestampConversionTest-~.dst" + FILE_EXTENSION;

    private static final String SPLIT_OPT = "[split=10m]";
    private static final String NO_PROTO_OPTS = "[opt=]"; // do not use any protocol extensions

    // first file is expected to start at the time "as is"
    private static final String EXPECTED_FILE_0_TIME = "20130910-100004-0400";
    // second file's time will be rounded to split=10m time
    private static final String EXPECTED_FILE_1_TIME = "20130910-113000-0400";

    private static final String BINARY_TIME_NONE_DESCRIPTOR =
        "31:01:DXP3 #0204 type #04 tape #07 version #08 QDS-TEST #010f0b STREAM_DATA #0000\n";

    private static final String BINARY_QUOTE_DESCRIPTOR =
        "63:02: #0005 Quote #0807 BidTime8 #0f BidExchangeCode #0208 BidPrice #1807 BidSize #0807 AskTime8 #0f AskExchangeCode #0208 AskPrice #1807 AskSize #08\n";

    private static final String EXPECTED_BLOB_CONTENTS =
        " #0000c0 M+ #00000000000000c0 M; #00000000000000c0 MK #0000000000";

    private static final String EXPECTED_BINARY_TIME_NONE_CONTENTS =
        BINARY_TIME_NONE_DESCRIPTOR +
        BINARY_QUOTE_DESCRIPTOR +
        "16:0f: #fd08 IBM.TEST #000000c0 M+ #0000000000\n" +
        "22:0f: #fd08 IBM.TEST #000000c0 M; #0000000000ff000000c0 MK #0000000000\n" +
        "00";

    private static final String BINARY_TIME_LONG_DESCRIPTOR =
        "3b:01:DXP3 #0304 type #04 tape #07 version #08 QDS-TEST #04 time #04 long #010f0b STREAM_DATA #0000\n";

    private static final String EXPECTED_BINARY_TIME_LONG_CONTENTS =
        BINARY_TIME_LONG_DESCRIPTOR +
        BINARY_QUOTE_DESCRIPTOR +
        "16:0f: #fd08 IBM.TEST #000000c0 M+ #0000000000\n" +
        "22:0f: #fd08 IBM.TEST #000000c0 M; #0000000000ff000000c0 MK #0000000000\n" +
        "00";

    private static final String BINARY_TIME_TEXT_DESCRIPTOR =
        "3b:01:DXP3 #0304 type #04 tape #07 version #08 QDS-TEST #04 time #04 text #010f0b STREAM_DATA #0000\n";

    private static final String EXPECTED_BINARY_TIME_TEXT_CONTENTS =
        BINARY_TIME_TEXT_DESCRIPTOR +
        BINARY_QUOTE_DESCRIPTOR +
        "16:0f: #fd08 IBM.TEST #000000c0 M+ #0000000000\n" +
        "22:0f: #fd08 IBM.TEST #000000c0 M; #0000000000ff000000c0 MK #0000000000\n" +
        "00";

    private static final String EXPECTED_BINARY_TIME_NONE_MERGED_CONTENTS =
        BINARY_TIME_NONE_DESCRIPTOR +
        BINARY_QUOTE_DESCRIPTOR +
        "2e:0f: #fd08 IBM.TEST #000000c0 M+ #0000000000ff000000c0 M; #0000000000ff000000c0 MK #0000000000\n" +
        "00";

    private static final String EXPECTED_BINARY_TIME_LONG_MERGED_CONTENTS =
        BINARY_TIME_LONG_DESCRIPTOR +
        BINARY_QUOTE_DESCRIPTOR +
        "2e:0f: #fd08 IBM.TEST #000000c0 M+ #0000000000ff000000c0 M; #0000000000ff000000c0 MK #0000000000\n" +
        "00";

    private static final String EXPECTED_BINARY_TIME_TEXT_MERGED_CONTENTS =
        BINARY_TIME_TEXT_DESCRIPTOR +
        BINARY_QUOTE_DESCRIPTOR +
        "2e:0f: #fd08 IBM.TEST #000000c0 M+ #0000000000ff000000c0 M; #0000000000ff000000c0 MK #0000000000\n" +
        "00";

    private static final String BINARY_TIME_MESSAGE_DESCRIPTOR =
        "3e:01:DXP3 #0304 type #04 tape #07 version #08 QDS-TEST #04 time #07 message #010f0b STREAM_DATA #0000\n";

    private static final String BINARY_TIME_MESSAGE_RAW_DATA_DESCRIPTOR =
        "3b:01:DXP3 #0304 type #04 tape #07 version #08 QDS-TEST #04 time #07 message #010508 RAW_DATA #0000\n";

    private static final String EXPECTED_BINARY_TIME_MESSAGE_CONTENTS =
        BINARY_TIME_MESSAGE_DESCRIPTOR +
        "08:00: #01f9 A #08 , #031b\n" +
        BINARY_QUOTE_DESCRIPTOR +
        "16:0f: #fd08 IBM.TEST #000000c0 M+ #0000000000\n" +
        "08:00: #01f9 A #0882f0 {\n" +
        "22:0f: #fd08 IBM.TEST #000000c0 M; #0000000000ff000000c0 MK #0000000000\n" +
        "00";

    private static final String EXPECTED_BINARY_TIME_MESSAGE_RAW_DATA_CONTENTS =
        BINARY_TIME_MESSAGE_RAW_DATA_DESCRIPTOR +
        "08:00: #01f9 A #08 , #031b\n" +
        BINARY_QUOTE_DESCRIPTOR +
        "16:05: #fd08 IBM.TEST #000000c0 M+ #0000000000\n" +
        "08:00: #01f9 A #0882f0 {\n" +
        "22:05: #fd08 IBM.TEST #000000c0 M; #0000000000ff000000c0 MK #0000000000\n" +
        "00";

    private static final String BINARY_TIME_FIELD_DESCRIPTOR =
        "3c:01:DXP3 #0304 type #04 tape #07 version #08 QDS-TEST #04 time #05 field #010f0b STREAM_DATA #0000\n";

    private static final String BINARY_QUOTE_TIME_FIELD_DESCRIPTOR =
        "7e:02: #0005 Quote #0a09 EventTime8 #0d EventSequence #80 H #07 BidTime8 #0f BidExchangeCode #0208 BidPrice #1807 BidSize #0807 AskTime8 #0f AskExchangeCode #0208 AskPrice #1807 AskSize #08\n";

    private static final String EXPECTED_BINARY_TIME_FIELD_CONTENTS =
        BINARY_TIME_FIELD_DESCRIPTOR +
        BINARY_QUOTE_TIME_FIELD_DESCRIPTOR +
        "20:0f: #fd08 IBM.TEST #00f0 R/% #e4f01ec000000000c0 M+ #0000000000\n" +
        "36:0f: #fd08 IBM.TEST #00f0 R/<$ #f7f6c000000000c0 M; #0000000000ff00f0 R/<$ #f7f6c000000000c0 MK #0000000000\n" +
        "00";

    private static final String EXPECTED_BINARY_TIME_FIELD_MERGED_CONTENTS =
        BINARY_TIME_FIELD_DESCRIPTOR +
        BINARY_QUOTE_TIME_FIELD_DESCRIPTOR +
        "4c:0f: #fd08 IBM.TEST #00f0 R/% #e4f01ec000000000c0 M+ #0000000000ff00f0 R/<$ #f7f6c000000000c0 M; #0000000000ff00f0 R/<$ #f7f6c000000000c0 MK #0000000000\n" +
        "00";

    private static final String EXPECTED_BINARY_TIME_NONE_SPLIT_0_MESSAGE_CONTENTS =
        BINARY_TIME_NONE_DESCRIPTOR +
        BINARY_QUOTE_DESCRIPTOR +
        "16:0f: #fd08 IBM.TEST #000000c0 M+ #0000000000\n" +
        "00";

    private static final String EXPECTED_BINARY_TIME_NONE_SPLIT_1_MESSAGE_CONTENTS =
        BINARY_TIME_NONE_DESCRIPTOR +
        BINARY_QUOTE_DESCRIPTOR +
        "22:0f: #fd08 IBM.TEST #000000c0 M; #0000000000ff000000c0 MK #0000000000\n" +
        "00";

    private static final String EXPECTED_BINARY_TIME_LONG_SPLIT_0_MESSAGE_CONTENTS =
        BINARY_TIME_LONG_DESCRIPTOR +
        BINARY_QUOTE_DESCRIPTOR +
        "16:0f: #fd08 IBM.TEST #000000c0 M+ #0000000000\n" +
        "00";

    private static final String EXPECTED_BINARY_TIME_LONG_SPLIT_1_MESSAGE_CONTENTS =
        BINARY_TIME_LONG_DESCRIPTOR +
        BINARY_QUOTE_DESCRIPTOR +
        "22:0f: #fd08 IBM.TEST #000000c0 M; #0000000000ff000000c0 MK #0000000000\n" +
        "00";

    private static final String EXPECTED_BINARY_TIME_TEXT_SPLIT_0_MESSAGE_CONTENTS =
        BINARY_TIME_TEXT_DESCRIPTOR +
        BINARY_QUOTE_DESCRIPTOR +
        "16:0f: #fd08 IBM.TEST #000000c0 M+ #0000000000\n" +
        "00";

    private static final String EXPECTED_BINARY_TIME_TEXT_SPLIT_1_MESSAGE_CONTENTS =
        BINARY_TIME_TEXT_DESCRIPTOR +
        BINARY_QUOTE_DESCRIPTOR +
        "22:0f: #fd08 IBM.TEST #000000c0 M; #0000000000ff000000c0 MK #0000000000\n" +
        "00";

    private static final String EXPECTED_BINARY_TIME_MESSAGE_SPLIT_0_MESSAGE_CONTENTS =
        BINARY_TIME_MESSAGE_DESCRIPTOR +
        "08:00: #01f9 A #08 , #031b\n" +
        BINARY_QUOTE_DESCRIPTOR +
        "16:0f: #fd08 IBM.TEST #000000c0 M+ #0000000000\n" +
        "00";

    private static final String EXPECTED_BINARY_TIME_MESSAGE_SPLIT_1_MESSAGE_CONTENTS =
        BINARY_TIME_MESSAGE_DESCRIPTOR +
        "08:00: #01f9 A #0882f0 {\n" +
        BINARY_QUOTE_DESCRIPTOR +
        "22:0f: #fd08 IBM.TEST #000000c0 M; #0000000000ff000000c0 MK #0000000000\n" +
        "00";

    private static final String EXPECTED_BINARY_TIME_FIELD_SPLIT_0_MESSAGE_CONTENTS =
        BINARY_TIME_FIELD_DESCRIPTOR +
        BINARY_QUOTE_TIME_FIELD_DESCRIPTOR +
        "20:0f: #fd08 IBM.TEST #00f0 R/% #e4f01ec000000000c0 M+ #0000000000\n" +
        "00";

    private static final String EXPECTED_BINARY_TIME_FIELD_SPLIT_1_MESSAGE_CONTENTS =
        BINARY_TIME_FIELD_DESCRIPTOR +
        BINARY_QUOTE_TIME_FIELD_DESCRIPTOR +
        "36:0f: #fd08 IBM.TEST #00f0 R/<$ #f7f6c000000000c0 M; #0000000000ff00f0 R/<$ #f7f6c000000000c0 MK #0000000000\n" +
        "00";

    private static final String EXPECTED_BINARY_TIME_LONG_TIME_CONTENTS =
        "1378821604123:60\n" +
        "1378827300987:184\n";

    private static final String EXPECTED_BINARY_TIME_TEXT_TIME_CONTENTS =
        "20130910-100004.123-0400:60\n" +
        "20130910-113500.987-0400:184\n";

    private static final String EXPECTED_TIME_LONG_SPLIT_0_TIME_PATTERN =
        "1378821604123:\\d+\n";

    private static final String EXPECTED_TIME_LONG_SPLIT_1_TIME_PATTERN =
        "1378827300987:\\d+\n";

    private static final String EXPECTED_TIME_TEXT_SPLIT_0_TIME_PATTERN =
        "20130910-100004.123-0400:\\d+\n";

    private static final String EXPECTED_TIME_TEXT_SPLIT_1_TIME_PATTERN =
        "20130910-113500.987-0400:\\d+\n";

    private static final String TEXT_TIME_NONE_DESCRIPTOR =
        "==DXP3\ttype=tape\tversion=QDS-TEST\t+STREAM_DATA\n";

    private static final String TEXT_QUOTE_DESCRIPTOR =
        "=Quote\tEventSymbol\tBidTime\tBidExchangeCode\tBidPrice\tBidSize\tAskTime\tAskExchangeCode\tAskPrice\tAskSize\n";

    private static final String EXPECTED_TEXT_TIME_NONE_CONTENTS =
        TEXT_TIME_NONE_DESCRIPTOR +
        "==STREAM_DATA\n" +
        TEXT_QUOTE_DESCRIPTOR +
        "Quote\tIBM.TEST\t0\t\\0\t12.34\t0\t0\t\\0\tNaN\t0\n" +
        "Quote\tIBM.TEST\t0\t\\0\t12.35\t0\t0\t\\0\tNaN\t0\n" +
        "Quote\tIBM.TEST\t0\t\\0\t12.36\t0\t0\t\\0\tNaN\t0\n" +
        "==\n";

    private static final String TEXT_TIME_LONG_DESCRIPTOR =
        "==DXP3\ttype=tape\tversion=QDS-TEST\ttime=long\t+STREAM_DATA\n";

    private static final String EXPECTED_TEXT_TIME_LONG_CONTENTS =
        TEXT_TIME_LONG_DESCRIPTOR +
        "==STREAM_DATA\n" +
        TEXT_QUOTE_DESCRIPTOR +
        "Quote\tIBM.TEST\t0\t\\0\t12.34\t0\t0\t\\0\tNaN\t0\n" +
        "Quote\tIBM.TEST\t0\t\\0\t12.35\t0\t0\t\\0\tNaN\t0\n" +
        "Quote\tIBM.TEST\t0\t\\0\t12.36\t0\t0\t\\0\tNaN\t0\n" +
        "==\n";

    private static final String TEXT_TIME_TEXT_DESCRIPTOR =
        "==DXP3\ttype=tape\tversion=QDS-TEST\ttime=text\t+STREAM_DATA\n";

    private static final String EXPECTED_TEXT_TIME_TEXT_CONTENTS =
        TEXT_TIME_TEXT_DESCRIPTOR +
        "==STREAM_DATA\n" +
        TEXT_QUOTE_DESCRIPTOR +
        "Quote\tIBM.TEST\t0\t\\0\t12.34\t0\t0\t\\0\tNaN\t0\n" +
        "Quote\tIBM.TEST\t0\t\\0\t12.35\t0\t0\t\\0\tNaN\t0\n" +
        "Quote\tIBM.TEST\t0\t\\0\t12.36\t0\t0\t\\0\tNaN\t0\n" +
        "==\n";

    private static final String EXPECTED_TEXT_TIME_NONE_SPLIT_0_CONTENTS =
        TEXT_TIME_NONE_DESCRIPTOR +
        "==STREAM_DATA\n" +
        TEXT_QUOTE_DESCRIPTOR +
        "Quote\tIBM.TEST\t0\t\\0\t12.34\t0\t0\t\\0\tNaN\t0\n" +
        "==\n";

    private static final String EXPECTED_TEXT_TIME_NONE_SPLIT_1_CONTENTS =
        TEXT_TIME_NONE_DESCRIPTOR +
        "==STREAM_DATA\n" +
        TEXT_QUOTE_DESCRIPTOR +
        "Quote\tIBM.TEST\t0\t\\0\t12.35\t0\t0\t\\0\tNaN\t0\n" +
        "Quote\tIBM.TEST\t0\t\\0\t12.36\t0\t0\t\\0\tNaN\t0\n" +
        "==\n";

    private static final String EXPECTED_TEXT_TIME_LONG_SPLIT_0_CONTENTS =
        TEXT_TIME_LONG_DESCRIPTOR +
        "==STREAM_DATA\n" +
        TEXT_QUOTE_DESCRIPTOR +
        "Quote\tIBM.TEST\t0\t\\0\t12.34\t0\t0\t\\0\tNaN\t0\n" +
        "==\n";

    private static final String EXPECTED_TEXT_TIME_LONG_SPLIT_1_CONTENTS =
        TEXT_TIME_LONG_DESCRIPTOR +
        "==STREAM_DATA\n" +
        TEXT_QUOTE_DESCRIPTOR +
        "Quote\tIBM.TEST\t0\t\\0\t12.35\t0\t0\t\\0\tNaN\t0\n" +
        "Quote\tIBM.TEST\t0\t\\0\t12.36\t0\t0\t\\0\tNaN\t0\n" +
        "==\n";

    private static final String EXPECTED_TEXT_TIME_TEXT_SPLIT_0_CONTENTS =
        TEXT_TIME_TEXT_DESCRIPTOR +
        "==STREAM_DATA\n" +
        TEXT_QUOTE_DESCRIPTOR +
        "Quote\tIBM.TEST\t0\t\\0\t12.34\t0\t0\t\\0\tNaN\t0\n" +
        "==\n";

    private static final String EXPECTED_TEXT_TIME_TEXT_SPLIT_1_CONTENTS =
        TEXT_TIME_TEXT_DESCRIPTOR +
        "==STREAM_DATA\n" +
        TEXT_QUOTE_DESCRIPTOR +
        "Quote\tIBM.TEST\t0\t\\0\t12.35\t0\t0\t\\0\tNaN\t0\n" +
        "Quote\tIBM.TEST\t0\t\\0\t12.36\t0\t0\t\\0\tNaN\t0\n" +
        "==\n";

    private static final String TEXT_TIME_FIELD_DESCRIPTOR =
        "==DXP3\ttype=tape\tversion=QDS-TEST\ttime=field\t+STREAM_DATA\n";

    private static final String TEXT_TIME_FIELD_DESCRIPTOR_RAW_DATA =
        "==DXP3\ttype=tape\tversion=QDS-TEST\ttime=field\t+RAW_DATA\n";

    private static final String TEXT_QUOTE_TIME_FIELD_DESCRIPTOR =
        "=Quote\tEventSymbol\tEventTime\tBidTime\tBidExchangeCode\tBidPrice\tBidSize\tAskTime\tAskExchangeCode\tAskPrice\tAskSize\n";

    private static final String TEXT_TIME_FIELD_DATA =
        "Quote\tIBM.TEST\t20130910-100004.123-0400\t0\t\\0\t12.34\t0\t0\t\\0\tNaN\t0\n" +
        "Quote\tIBM.TEST\t20130910-113500.987-0400\t0\t\\0\t12.35\t0\t0\t\\0\tNaN\t0\n" +
        "Quote\tIBM.TEST\t20130910-113500.987-0400\t0\t\\0\t12.36\t0\t0\t\\0\tNaN\t0\n" +
        "==\n";

    private static final String EXPECTED_TEXT_TIME_FIELD_CONTENTS =
        TEXT_TIME_FIELD_DESCRIPTOR +
        "==STREAM_DATA\n" +
        TEXT_QUOTE_TIME_FIELD_DESCRIPTOR +
        TEXT_TIME_FIELD_DATA;

    private static final String EXPECTED_TEXT_TIME_FIELD_CONTENTS_RAW_DATA =
        TEXT_TIME_FIELD_DESCRIPTOR_RAW_DATA +
        "==RAW_DATA\n" +
        TEXT_QUOTE_TIME_FIELD_DESCRIPTOR +
        TEXT_TIME_FIELD_DATA;

    private static final String EXPECTED_TEXT_CURRENT_TIME_FIELD_CONTENTS_PATTERN =
        TEXT_TIME_FIELD_DESCRIPTOR.replace("+", "\\+") +
        "==STREAM_DATA\n" +
        TEXT_QUOTE_TIME_FIELD_DESCRIPTOR +
        "Quote\tIBM.TEST\t\\d{8}-\\d{6}.\\d{3}-0[45]00\t0\t\\\\0\t12.34\t0\t0\t\\\\0\tNaN\t0\n" +
        "Quote\tIBM.TEST\t\\d{8}-\\d{6}.\\d{3}-0[45]00\t0\t\\\\0\t12.35\t0\t0\t\\\\0\tNaN\t0\n" +
        "Quote\tIBM.TEST\t\\d{8}-\\d{6}.\\d{3}-0[45]00\t0\t\\\\0\t12.36\t0\t0\t\\\\0\tNaN\t0\n" +
        "==\n";

    private static final String EXPECTED_TEXT_TIME_FIELD_SPLIT_0_CONTENTS =
        TEXT_TIME_FIELD_DESCRIPTOR +
        "==STREAM_DATA\n" +
        TEXT_QUOTE_TIME_FIELD_DESCRIPTOR +
        "Quote\tIBM.TEST\t20130910-100004.123-0400\t0\t\\0\t12.34\t0\t0\t\\0\tNaN\t0\n" +
        "==\n";

    private static final String EXPECTED_TEXT_TIME_FIELD_SPLIT_1_CONTENTS =
        TEXT_TIME_FIELD_DESCRIPTOR +
        "==STREAM_DATA\n" +
        TEXT_QUOTE_TIME_FIELD_DESCRIPTOR +
        "Quote\tIBM.TEST\t20130910-113500.987-0400\t0\t\\0\t12.35\t0\t0\t\\0\tNaN\t0\n" +
        "Quote\tIBM.TEST\t20130910-113500.987-0400\t0\t\\0\t12.36\t0\t0\t\\0\tNaN\t0\n" +
        "==\n";


    private static final String TEXT_TIME_MESSAGE_DESCRIPTOR =
        "==DXP3\ttype=tape\tversion=QDS-TEST\ttime=message\t+STREAM_DATA\n";

    private static final String EXPECTED_TEXT_TIME_MESSAGE_CONTENTS =
        TEXT_TIME_MESSAGE_DESCRIPTOR +
        "==\ttime=20130910-100004.123-0400\n" +
        "==STREAM_DATA\n" +
        TEXT_QUOTE_DESCRIPTOR +
        "Quote\tIBM.TEST\t0\t\\0\t12.34\t0\t0\t\\0\tNaN\t0\n" +
        "==\ttime=20130910-113500.987-0400\n" +
        "Quote\tIBM.TEST\t0\t\\0\t12.35\t0\t0\t\\0\tNaN\t0\n" +
        "Quote\tIBM.TEST\t0\t\\0\t12.36\t0\t0\t\\0\tNaN\t0\n" +
        "==\n";

    private static final String EXPECTED_TEXT_CURRENT_TIME_MESSAGE_CONTENTS_PATTERN =
        TEXT_TIME_MESSAGE_DESCRIPTOR.replace("+", "\\+") +
        "==\ttime=\\d{8}-\\d{6}.\\d{3}-0[45]00\n" +
        "==STREAM_DATA\n" +
        TEXT_QUOTE_DESCRIPTOR +
        "Quote\tIBM.TEST\t0\t\\\\0\t12.34\t0\t0\t\\\\0\tNaN\t0\n" +
        "Quote\tIBM.TEST\t0\t\\\\0\t12.35\t0\t0\t\\\\0\tNaN\t0\n" +
        "Quote\tIBM.TEST\t0\t\\\\0\t12.36\t0\t0\t\\\\0\tNaN\t0\n" +
        "==\n";

    private static final String EXPECTED_TEXT_TIME_MESSAGE_SPLIT_0_CONTENTS =
        TEXT_TIME_MESSAGE_DESCRIPTOR +
        "==\ttime=20130910-100004.123-0400\n" +
        "==STREAM_DATA\n" +
        TEXT_QUOTE_DESCRIPTOR +
        "Quote\tIBM.TEST\t0\t\\0\t12.34\t0\t0\t\\0\tNaN\t0\n" +
        "==\n";

    private static final String EXPECTED_TEXT_TIME_MESSAGE_SPLIT_1_CONTENTS =
        TEXT_TIME_MESSAGE_DESCRIPTOR +
        "==\ttime=20130910-113500.987-0400\n" +
        "==STREAM_DATA\n" +
        TEXT_QUOTE_DESCRIPTOR +
        "Quote\tIBM.TEST\t0\t\\0\t12.35\t0\t0\t\\0\tNaN\t0\n" +
        "Quote\tIBM.TEST\t0\t\\0\t12.36\t0\t0\t\\0\tNaN\t0\n" +
        "==\n";

    private static final String CSV_QUOTE_DESCRIPTOR =
        "#=Quote,EventSymbol,BidTime,BidExchangeCode,BidPrice,BidSize,AskTime,AskExchangeCode,AskPrice,AskSize\n";

    private static final String EXPECTED_CSV_TIME_NONE_CONTENTS =
        CSV_QUOTE_DESCRIPTOR +
        "Quote,IBM.TEST,0,\\0,12.34,0,0,\\0,NaN,0\n" +
        "Quote,IBM.TEST,0,\\0,12.35,0,0,\\0,NaN,0\n" +
        "Quote,IBM.TEST,0,\\0,12.36,0,0,\\0,NaN,0\n";

    private static final String CSV_QUOTE_TIME_FIELD_DESCRIPTOR =
        "#=Quote,EventSymbol,EventTime,BidTime,BidExchangeCode,BidPrice,BidSize,AskTime,AskExchangeCode,AskPrice,AskSize\n";

    private static final String EXPECTED_CSV_TIME_FIELD_CONTENTS =
        CSV_QUOTE_TIME_FIELD_DESCRIPTOR +
        "Quote,IBM.TEST,20130910-100004.123-0400,0,\\0,12.34,0,0,\\0,NaN,0\n" +
        "Quote,IBM.TEST,20130910-113500.987-0400,0,\\0,12.35,0,0,\\0,NaN,0\n" +
        "Quote,IBM.TEST,20130910-113500.987-0400,0,\\0,12.36,0,0,\\0,NaN,0\n";

    private static final String EXPECTED_TEXT_TIME_LONG_TIME_CONTENTS_PATTERN =
        "1378821604123:\\d+\n" +
        "1378827300987:\\d+\n";

    private static final String EXPECTED_CURRENT_TIME_LONG_TIME_CONTENTS_PATTERN =
        "\\d+:\\d+\n";

    private static final String EXPECTED_TEXT_TIME_TEXT_TIME_CONTENTS_PATTERN =
        "20130910-100004.123-0400:\\d+\n" +
        "20130910-113500.987-0400:\\d+\n";

    private static final String EXPECTED_CURRENT_TIME_TEXT_TIME_CONTENTS_PATTERN =
        "\\d{8}-\\d{6}.\\d{3}-0[45]00:\\d+\n";

    private static final String SCHEME_PROP = "scheme";

    private String savedSchemeProp;

    private File getTimeFile(File file) {
        String s = file.toString();
        assertTrue(s.endsWith(FILE_EXTENSION));
        return new File(s.substring(0, s.length() - FILE_EXTENSION.length()) + TIME_EXTENSION);
    }

    private File getSplitFile(String fileSplit, String time) {
        assertTrue(fileSplit.contains("~"));
        return new File(fileSplit.replace("~", time));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void deleteFile(File file) {
        file.delete();
        getTimeFile(file).delete();
    }

    private void deleteSplitFile(String fileSplit) {
        deleteFile(getSplitFile(fileSplit, EXPECTED_FILE_0_TIME));
        deleteFile(getSplitFile(fileSplit, EXPECTED_FILE_1_TIME));
    }

    private void deleteFiles() {
        deleteFile(SOURCE_FILE);
        deleteFile(DESTINATION_FILE);
        deleteSplitFile(SOURCE_FILE_SPLIT);
        deleteSplitFile(DESTINATION_FILE_SPLIT);
    }

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        deleteFiles();
        savedSchemeProp = System.setProperty(SCHEME_PROP, DumpSchemeProvider.class.getName());
        TimeFormat.setDefaultTimeZone(TimeUtil.getTimeZone("America/New_York"));
        QDFactory.setVersion("QDS-TEST");
    }

    @After
    public void tearDown() throws Exception {
        deleteFiles();
        TimeFormat.setDefaultTimeZone(TimeZone.getDefault());
        QDFactory.setVersion(null);
        ThreadCleanCheck.after();
        if (savedSchemeProp == null) {
            System.clearProperty(SCHEME_PROP);
        } else {
            System.setProperty(SCHEME_PROP, savedSchemeProp);
        }
    }

    private void write(String dataFilePath) {
        HeartbeatPayload heartbeatPayload = new HeartbeatPayload();
        RecordBuffer buf = RecordBuffer.getInstance();
        RecordCursor cursor;

        // use file writer to generate source file
        FileWriterImpl writer = FileWriterImpl.open(dataFilePath, SCHEME);
        writer.addSendMessageType(MessageType.STREAM_DATA);

        // write timestamp0 in heartbeat
        heartbeatPayload.setTimeMillis(EXPECTED_TIMESTAMP_0);
        writer.visitHeartbeat(heartbeatPayload);

        // write quote event 0
        cursor = buf.add(QUOTE_RECORD, 0, EXPECTED_SYMBOL);
        cursor.setInt(QUOTE_BID_PRICE_INDEX, Decimal.compose(EXPECTED_BID_PRICE_0));
        writer.visitStreamData(buf);
        buf.clear();

        // write timestamp1 in heartbeat
        heartbeatPayload.setTimeMillis(EXPECTED_TIMESTAMP_1);
        writer.visitHeartbeat(heartbeatPayload);

        // write quote events 1.1 & 1.2
        cursor = buf.add(QUOTE_RECORD, 0, EXPECTED_SYMBOL);
        cursor.setInt(QUOTE_BID_PRICE_INDEX, Decimal.compose(EXPECTED_BID_PRICE_1_1));
        cursor = buf.add(QUOTE_RECORD, 0, EXPECTED_SYMBOL);
        cursor.setInt(QUOTE_BID_PRICE_INDEX, Decimal.compose(EXPECTED_BID_PRICE_1_2));
        writer.visitStreamData(buf);
        buf.clear();

        // done with source file
        writer.close();
    }

    private void writeSourceFile(String opt) {
        write(SOURCE_FILE + NO_PROTO_OPTS + opt);
    }

    private void writeSourceFileSplit(String opt) {
        write(SOURCE_FILE_SPLIT + NO_PROTO_OPTS + SPLIT_OPT + opt);
    }

    private String getContents(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private void assertMatches(String pattern, String contents) {
        if (!contents.matches(pattern)) {
            // find mismatch line
            int line = 1;
            while (true) {
                String contentsLines = takeNLines(contents, line);
                String patternLines = takeNLines(pattern, line);
                if (!contentsLines.matches(patternLines))
                    break;
                line++;
            }
            fail("Contents do not match to pattern at line " + line + "\n" +
                "--- contents ---\n" + contents +
                "--- pattern ---\n" + pattern +
                "--- end ---");
        }
    }

    private String takeNLines(String s, int n) {
        StringBuilder sb = new StringBuilder();
        String[] lines = s.split("\n", n + 1);
        for (int i = 0; i < lines.length && i < n; i++) {
            sb.append(lines[i]).append('\n');
        }
        return sb.toString();
    }

    private String getBlobContents(File file) throws IOException {
        try (StreamInput in = new StreamInput(new FileInputStream(file))) {
            StringBuilder sb = new StringBuilder();
            appendBlob(in, sb, in.available());
            return sb.toString();
        }
    }

    private String getBinaryContents(File file) throws IOException {
        try (StreamInput in = new StreamInput(new FileInputStream(file))) {
            StringBuilder sb = new StringBuilder();
            while (in.hasAvailable()) {
                int len = in.readCompactInt();
                appendHexByte(sb, len); // len will be at most byte
                if (len <= 0)
                    continue;
                sb.append(':');
                int type = in.readCompactInt();
                appendHexByte(sb, type); // type will be at most byte
                len -= IOUtil.getCompactLength(type);
                if (len <= 0)
                    continue;
                sb.append(':');
                appendBlob(in, sb, len);
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    private void appendBlob(StreamInput in, StringBuilder sb, int len) throws IOException {
        boolean text = true;
        while (len > 0) {
            int b = in.read();
            len--;
            if (b > 32 && b < 127 && b != '#') {
                if (!text) {
                    sb.append(" ");
                    text = true;
                }
                sb.append((char) b);
            } else {
                if (text) {
                    sb.append(" #");
                    text = false;
                }
                appendHexByte(sb, b);
            }
        }
    }

    private void appendHexByte(StringBuilder sb, int b) {
        assertTrue(b >= 0 && b <= 255);
        sb.append(HEX.charAt(b >> 4));
        sb.append(HEX.charAt(b & 0x0f));
    }

    // ---------------------------- convertion helpers ----------------------------

    private void convert(String srcOpt, String destOpt) {
        Tools
            .invoke("dump", SOURCE_FILE + "[speed=max]" + srcOpt, "--tape", DESTINATION_FILE + NO_PROTO_OPTS + destOpt);
    }

    private void convertToSplit(String destOpt) {
        Tools.invoke("dump", SOURCE_FILE + "[speed=max]", "--tape",
            DESTINATION_FILE_SPLIT + NO_PROTO_OPTS + SPLIT_OPT + destOpt);
    }

    private void convertSplit(String destOpt) {
        Tools.invoke("dump", SOURCE_FILE_SPLIT + "[speed=max]", "--tape", DESTINATION_FILE + NO_PROTO_OPTS + destOpt);
    }

    private void convertSplitToSplit(String destOpt) {
        Tools.invoke("dump", SOURCE_FILE_SPLIT + "[speed=max]", "--tape",
            DESTINATION_FILE_SPLIT + NO_PROTO_OPTS + SPLIT_OPT + destOpt);
    }

    // ---------------------------- helpers assertions for file existence ----------------------------

    private void assertDataFileOnly(File file) {
        assertTrue(file.exists());
        assertFalse(getTimeFile(file).exists());
    }

    private void assertDataAndTimeFiles(File file) {
        assertTrue(file.exists());
        assertTrue(getTimeFile(file).exists());
    }

    private void assertSplitDataFileOnly(String fileSplit) {
        assertDataFileOnly(getSplitFile(fileSplit, EXPECTED_FILE_0_TIME));
        assertDataFileOnly(getSplitFile(fileSplit, EXPECTED_FILE_1_TIME));
    }

    private void assertSplitDataAndTimeFiles(String fileSplit) {
        assertDataAndTimeFiles(getSplitFile(fileSplit, EXPECTED_FILE_0_TIME));
        assertDataAndTimeFiles(getSplitFile(fileSplit, EXPECTED_FILE_1_TIME));
    }

    // ---------------------------- assertion for various formats ----------------------------

    private void assertBlob(File file) throws IOException {
        assertDataFileOnly(file);
        assertEquals(EXPECTED_BLOB_CONTENTS, getBlobContents(file));
    }

    private void assertBinaryTimeNone(File file) throws IOException {
        assertDataFileOnly(file);
        assertEquals(EXPECTED_BINARY_TIME_NONE_CONTENTS, getBinaryContents(file));
    }

    private void assertBinaryTimeNoneMerged(File file) throws IOException {
        assertDataFileOnly(file);
        assertEquals(EXPECTED_BINARY_TIME_NONE_MERGED_CONTENTS, getBinaryContents(file));
    }

    private void assertBinarySplitTimeNone(String fileSplit) throws IOException {
        assertSplitDataFileOnly(fileSplit);
        assertEquals(EXPECTED_BINARY_TIME_NONE_SPLIT_0_MESSAGE_CONTENTS,
            getBinaryContents(getSplitFile(fileSplit, EXPECTED_FILE_0_TIME)));
        assertEquals(EXPECTED_BINARY_TIME_NONE_SPLIT_1_MESSAGE_CONTENTS,
            getBinaryContents(getSplitFile(fileSplit, EXPECTED_FILE_1_TIME)));
    }

    private void assertBinaryTimeMessage(File file) throws IOException {
        assertDataFileOnly(file);
        assertEquals(EXPECTED_BINARY_TIME_MESSAGE_CONTENTS, getBinaryContents(file));
    }

    private void assertBinaryTimeMessageRawData(File file) throws IOException {
        assertDataFileOnly(file);
        assertEquals(EXPECTED_BINARY_TIME_MESSAGE_RAW_DATA_CONTENTS, getBinaryContents(file));
    }

    private void assertBinarySplitTimeMessage(String fileSplit) throws IOException {
        assertSplitDataFileOnly(fileSplit);
        assertEquals(EXPECTED_BINARY_TIME_MESSAGE_SPLIT_0_MESSAGE_CONTENTS,
            getBinaryContents(getSplitFile(fileSplit, EXPECTED_FILE_0_TIME)));
        assertEquals(EXPECTED_BINARY_TIME_MESSAGE_SPLIT_1_MESSAGE_CONTENTS,
            getBinaryContents(getSplitFile(fileSplit, EXPECTED_FILE_1_TIME)));
    }

    private void assertBinaryTimeField(File file) throws IOException {
        assertDataFileOnly(file);
        assertEquals(EXPECTED_BINARY_TIME_FIELD_CONTENTS, getBinaryContents(file));
    }

    private void assertBinaryTimeFieldMerged(File file) throws IOException {
        assertDataFileOnly(file);
        assertEquals(EXPECTED_BINARY_TIME_FIELD_MERGED_CONTENTS, getBinaryContents(file));
    }

    private void assertBinarySplitTimeField(String fileSplit) throws IOException {
        assertSplitDataFileOnly(fileSplit);
        assertEquals(EXPECTED_BINARY_TIME_FIELD_SPLIT_0_MESSAGE_CONTENTS,
            getBinaryContents(getSplitFile(fileSplit, EXPECTED_FILE_0_TIME)));
        assertEquals(EXPECTED_BINARY_TIME_FIELD_SPLIT_1_MESSAGE_CONTENTS,
            getBinaryContents(getSplitFile(fileSplit, EXPECTED_FILE_1_TIME)));
    }

    private void assertBinaryTimeLong(File file) throws IOException {
        assertDataAndTimeFiles(file);
        assertEquals(EXPECTED_BINARY_TIME_LONG_CONTENTS, getBinaryContents(file));
        assertEquals(EXPECTED_BINARY_TIME_LONG_TIME_CONTENTS, getContents(getTimeFile(file)));
    }

    private void assertBinaryCurrentTimeLong(File file) throws IOException {
        assertDataAndTimeFiles(file);
        assertEquals(EXPECTED_BINARY_TIME_LONG_MERGED_CONTENTS, getBinaryContents(file));
        assertMatches(EXPECTED_CURRENT_TIME_LONG_TIME_CONTENTS_PATTERN, getContents(getTimeFile(file)));
    }

    private void assertBinarySplitTimeLong(String fileSplit) throws IOException {
        assertSplitDataAndTimeFiles(fileSplit);
        assertEquals(EXPECTED_BINARY_TIME_LONG_SPLIT_0_MESSAGE_CONTENTS,
            getBinaryContents(getSplitFile(fileSplit, EXPECTED_FILE_0_TIME)));
        assertMatches(EXPECTED_TIME_LONG_SPLIT_0_TIME_PATTERN,
            getContents(getTimeFile(getSplitFile(fileSplit, EXPECTED_FILE_0_TIME))));
        assertEquals(EXPECTED_BINARY_TIME_LONG_SPLIT_1_MESSAGE_CONTENTS,
            getBinaryContents(getSplitFile(fileSplit, EXPECTED_FILE_1_TIME)));
        assertMatches(EXPECTED_TIME_LONG_SPLIT_1_TIME_PATTERN,
            getContents(getTimeFile(getSplitFile(fileSplit, EXPECTED_FILE_1_TIME))));
    }

    private void assertBinaryTimeText(File file) throws IOException {
        assertDataAndTimeFiles(file);
        assertEquals(EXPECTED_BINARY_TIME_TEXT_CONTENTS, getBinaryContents(file));
        assertEquals(EXPECTED_BINARY_TIME_TEXT_TIME_CONTENTS, getContents(getTimeFile(file)));
    }

    private void assertBinaryCurrentTimeText(File file) throws IOException {
        assertDataAndTimeFiles(file);
        assertEquals(EXPECTED_BINARY_TIME_TEXT_MERGED_CONTENTS, getBinaryContents(file));
        assertMatches(EXPECTED_CURRENT_TIME_TEXT_TIME_CONTENTS_PATTERN, getContents(getTimeFile(file)));
    }

    private void assertBinarySplitTimeText(String fileSplit) throws IOException {
        assertSplitDataAndTimeFiles(fileSplit);
        assertEquals(EXPECTED_BINARY_TIME_TEXT_SPLIT_0_MESSAGE_CONTENTS,
            getBinaryContents(getSplitFile(fileSplit, EXPECTED_FILE_0_TIME)));
        assertMatches(EXPECTED_TIME_TEXT_SPLIT_0_TIME_PATTERN,
            getContents(getTimeFile(getSplitFile(fileSplit, EXPECTED_FILE_0_TIME))));
        assertEquals(EXPECTED_BINARY_TIME_TEXT_SPLIT_1_MESSAGE_CONTENTS,
            getBinaryContents(getSplitFile(fileSplit, EXPECTED_FILE_1_TIME)));
        assertMatches(EXPECTED_TIME_TEXT_SPLIT_1_TIME_PATTERN,
            getContents(getTimeFile(getSplitFile(fileSplit, EXPECTED_FILE_1_TIME))));
    }

    private void assertTextTimeNone(File file) throws IOException {
        assertDataFileOnly(file);
        assertEquals(EXPECTED_TEXT_TIME_NONE_CONTENTS, getContents(file));
    }

    private void assertTextSplitTimeNone(String fileSplit) throws IOException {
        assertSplitDataFileOnly(fileSplit);
        assertEquals(EXPECTED_TEXT_TIME_NONE_SPLIT_0_CONTENTS,
            getContents(getSplitFile(fileSplit, EXPECTED_FILE_0_TIME)));
        assertEquals(EXPECTED_TEXT_TIME_NONE_SPLIT_1_CONTENTS,
            getContents(getSplitFile(fileSplit, EXPECTED_FILE_1_TIME)));
    }

    private void assertTextTimeField(File file) throws IOException {
        assertDataFileOnly(file);
        assertEquals(EXPECTED_TEXT_TIME_FIELD_CONTENTS, getContents(file));
    }

    private void assertTextTimeFieldRawData(File file) throws IOException {
        assertDataFileOnly(file);
        assertEquals(EXPECTED_TEXT_TIME_FIELD_CONTENTS_RAW_DATA, getContents(file));
    }

    private void assertTextCurrentTimeField(File file) throws IOException {
        assertDataFileOnly(file);
        assertMatches(EXPECTED_TEXT_CURRENT_TIME_FIELD_CONTENTS_PATTERN, getContents(file));
    }

    private void assertTextSplitTimeField(String fileSplit) throws IOException {
        assertSplitDataFileOnly(fileSplit);
        assertEquals(EXPECTED_TEXT_TIME_FIELD_SPLIT_0_CONTENTS,
            getContents(getSplitFile(fileSplit, EXPECTED_FILE_0_TIME)));
        assertEquals(EXPECTED_TEXT_TIME_FIELD_SPLIT_1_CONTENTS,
            getContents(getSplitFile(fileSplit, EXPECTED_FILE_1_TIME)));
    }

    private void assertTextTimeMessage(File file) throws IOException {
        assertDataFileOnly(file);
        assertEquals(EXPECTED_TEXT_TIME_MESSAGE_CONTENTS, getContents(file));
    }

    private void assertTextCurrentTimeMessage(File file) throws IOException {
        assertDataFileOnly(file);
        assertMatches(EXPECTED_TEXT_CURRENT_TIME_MESSAGE_CONTENTS_PATTERN, getContents(file));
    }

    private void assertTextSplitTimeMessage(String fileSplit) throws IOException {
        assertSplitDataFileOnly(fileSplit);
        assertEquals(EXPECTED_TEXT_TIME_MESSAGE_SPLIT_0_CONTENTS,
            getContents(getSplitFile(fileSplit, EXPECTED_FILE_0_TIME)));
        assertEquals(EXPECTED_TEXT_TIME_MESSAGE_SPLIT_1_CONTENTS,
            getContents(getSplitFile(fileSplit, EXPECTED_FILE_1_TIME)));
    }

    private void assertTextTimeLong(File file) throws IOException {
        assertDataAndTimeFiles(file);
        assertEquals(EXPECTED_TEXT_TIME_LONG_CONTENTS, getContents(file));
        assertMatches(EXPECTED_TEXT_TIME_LONG_TIME_CONTENTS_PATTERN, getContents(getTimeFile(file)));
    }

    private void assertTextCurrentTimeLong(File file) throws IOException {
        assertDataAndTimeFiles(file);
        assertEquals(EXPECTED_TEXT_TIME_LONG_CONTENTS, getContents(file));
        assertMatches(EXPECTED_CURRENT_TIME_LONG_TIME_CONTENTS_PATTERN, getContents(getTimeFile(file)));
    }

    private void assertTextSplitTimeLong(String fileSplit) throws IOException {
        assertSplitDataAndTimeFiles(fileSplit);
        assertEquals(EXPECTED_TEXT_TIME_LONG_SPLIT_0_CONTENTS,
            getContents(getSplitFile(fileSplit, EXPECTED_FILE_0_TIME)));
        assertMatches(EXPECTED_TIME_LONG_SPLIT_0_TIME_PATTERN,
            getContents(getTimeFile(getSplitFile(fileSplit, EXPECTED_FILE_0_TIME))));
        assertEquals(EXPECTED_TEXT_TIME_LONG_SPLIT_1_CONTENTS,
            getContents(getSplitFile(fileSplit, EXPECTED_FILE_1_TIME)));
        assertMatches(EXPECTED_TIME_LONG_SPLIT_1_TIME_PATTERN,
            getContents(getTimeFile(getSplitFile(fileSplit, EXPECTED_FILE_1_TIME))));
    }

    private void assertTextTimeText(File file) throws IOException {
        assertDataAndTimeFiles(file);
        assertEquals(EXPECTED_TEXT_TIME_TEXT_CONTENTS, getContents(file));
        assertMatches(EXPECTED_TEXT_TIME_TEXT_TIME_CONTENTS_PATTERN, getContents(getTimeFile(file)));
    }

    private void assertTextCurrentTimeText(File file) throws IOException {
        assertDataAndTimeFiles(file);
        assertEquals(EXPECTED_TEXT_TIME_TEXT_CONTENTS, getContents(file));
        assertMatches(EXPECTED_CURRENT_TIME_TEXT_TIME_CONTENTS_PATTERN, getContents(getTimeFile(file)));
    }

    private void assertTextSplitTimeText(String fileSplit) throws IOException {
        assertSplitDataAndTimeFiles(fileSplit);
        assertEquals(EXPECTED_TEXT_TIME_TEXT_SPLIT_0_CONTENTS,
            getContents(getSplitFile(fileSplit, EXPECTED_FILE_0_TIME)));
        assertMatches(EXPECTED_TIME_TEXT_SPLIT_0_TIME_PATTERN,
            getContents(getTimeFile(getSplitFile(fileSplit, EXPECTED_FILE_0_TIME))));
        assertEquals(EXPECTED_TEXT_TIME_TEXT_SPLIT_1_CONTENTS,
            getContents(getSplitFile(fileSplit, EXPECTED_FILE_1_TIME)));
        assertMatches(EXPECTED_TIME_TEXT_SPLIT_1_TIME_PATTERN,
            getContents(getTimeFile(getSplitFile(fileSplit, EXPECTED_FILE_1_TIME))));
    }

    private void assertCsvTimeNone(File file) throws IOException {
        assertDataFileOnly(file);
        assertEquals(EXPECTED_CSV_TIME_NONE_CONTENTS, getContents(file));
    }

    private void assertCsvTimeField(File file) throws IOException {
        assertDataFileOnly(file);
        assertEquals(EXPECTED_CSV_TIME_FIELD_CONTENTS, getContents(file));
    }

    private void assertCsvTimeMessage(File file) throws IOException {
        assertDataFileOnly(file);
        // CSV is bare bones and does not support time message, so it is the same as TIME_NONE
        assertEquals(EXPECTED_CSV_TIME_NONE_CONTENTS, getContents(file));
    }

    private void assertCsvTimeLong(File file) throws IOException {
        assertDataAndTimeFiles(file);
        assertEquals(EXPECTED_CSV_TIME_NONE_CONTENTS, getContents(file));
        assertMatches(EXPECTED_TEXT_TIME_LONG_TIME_CONTENTS_PATTERN, getContents(getTimeFile(file)));
    }

    private void assertCsvTimeText(File file) throws IOException {
        assertDataAndTimeFiles(file);
        assertEquals(EXPECTED_CSV_TIME_NONE_CONTENTS, getContents(file));
        assertMatches(EXPECTED_TEXT_TIME_TEXT_TIME_CONTENTS_PATTERN, getContents(getTimeFile(file)));
    }

    // =========================================== TEST METHODS ===========================================

    // -------------- convert default binary file to other time formats

    @Test
    public void testConvertToTimeNone() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[time=none]");
        assertBinaryTimeNone(DESTINATION_FILE);
    }

    @Test
    public void testConvertToTimeMessage() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[time=message]");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertToTimeField() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[time=field]");
        assertBinaryTimeField(DESTINATION_FILE);
    }

    @Test
    public void testConvertToTimeLong() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[time=long]");
        assertBinaryTimeLong(DESTINATION_FILE);
    }

    @Test
    public void testConvertToTimeText() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[time=text]");
        assertBinaryTimeText(DESTINATION_FILE);
    }

    // -------------- convert default binary file to text with various time formats

    @Test
    public void testConvertToText() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[format=text]");
        assertTextTimeField(DESTINATION_FILE);
    }

    @Test
    public void testConvertToTextTimeNone() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[format=text,time=none]");
        assertTextTimeNone(DESTINATION_FILE);
    }

    @Test
    public void testConvertToTextTimeField() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[format=text,time=field]");
        assertTextTimeField(DESTINATION_FILE);
    }

    @Test
    public void testConvertToTextTimeMessage() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[format=text,time=message]");
        assertTextTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertToTextTimeLong() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[format=text,time=long]");
        assertTextTimeLong(DESTINATION_FILE);
    }

    @Test
    public void testConvertToTextTimeText() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[format=text,time=text]");
        assertTextTimeText(DESTINATION_FILE);
    }

    // -------------- convert various time formats to default binary

    @Test
    public void testConvertTimeField() throws IOException {
        writeSourceFile("[time=field]");
        assertBinaryTimeField(SOURCE_FILE);
        convert("", "");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertTimeLong() throws IOException {
        writeSourceFile("[time=long]");
        assertBinaryTimeLong(SOURCE_FILE);
        convert("", "");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertTimeText() throws IOException {
        writeSourceFile("[time=text]");
        assertBinaryTimeText(SOURCE_FILE);
        convert("", "");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    // -------------- convert text files with various time formats to default binary

    @Test
    public void testConvertText() throws IOException {
        writeSourceFile("[format=text]");
        assertTextTimeField(SOURCE_FILE);
        convert("", "");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertTextTimeNone() throws IOException {
        writeSourceFile("[format=text,time=none]");
        assertTextTimeNone(SOURCE_FILE);
        convert("", "[time=none]"); // otherwise, current time will get written
        assertBinaryTimeNoneMerged(DESTINATION_FILE);
    }

    @Test
    public void testConvertTextTimeField() throws IOException {
        writeSourceFile("[format=text,time=field]");
        assertTextTimeField(SOURCE_FILE);
        convert("", "");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertTextTimeMessage() throws IOException {
        writeSourceFile("[format=text,time=message]");
        assertTextTimeMessage(SOURCE_FILE);
        convert("", "");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertTextTimeLong() throws IOException {
        writeSourceFile("[format=text,time=long]");
        assertTextTimeLong(SOURCE_FILE);
        convert("", "");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertTextTimeText() throws IOException {
        writeSourceFile("[format=text,time=text]");
        assertTextTimeText(SOURCE_FILE);
        convert("", "");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    // -------------- convert binary file with long times (often used in legacy practice) to various time formats

    @Test
    public void testConvertTimeLongToTimeNone() throws IOException {
        writeSourceFile("[time=long]");
        assertBinaryTimeLong(SOURCE_FILE);
        convert("", "[time=none]");
        assertBinaryTimeNone(DESTINATION_FILE);
    }

    @Test
    public void testConvertTimeLongToTimeMessage() throws IOException {
        writeSourceFile("[time=long]");
        assertBinaryTimeLong(SOURCE_FILE);
        convert("", "[time=message]");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertTimeLongToTimeField() throws IOException {
        writeSourceFile("[time=long]");
        assertBinaryTimeLong(SOURCE_FILE);
        convert("", "[time=field]");
        assertBinaryTimeField(DESTINATION_FILE);
    }

    @Test
    public void testConvertTimeLongToTimeLong() throws IOException {
        writeSourceFile("[time=long]");
        assertBinaryTimeLong(SOURCE_FILE);
        convert("", "[time=long]");
        assertBinaryTimeLong(DESTINATION_FILE);
    }

    @Test
    public void testConvertTimeLongToTimeText() throws IOException {
        writeSourceFile("[time=long]");
        assertBinaryTimeLong(SOURCE_FILE);
        convert("", "[time=text]");
        assertBinaryTimeText(DESTINATION_FILE);
    }

    // -------------- convert binary file with long times (often used in legacy practice) to text with various time formats

    @Test
    public void testConvertTimeLongToText() throws IOException {
        writeSourceFile("[time=long]");
        assertBinaryTimeLong(SOURCE_FILE);
        convert("", "[format=text]");
        assertTextTimeField(DESTINATION_FILE);
    }

    @Test
    public void testConvertTimeLongToTextTimeLong() throws IOException {
        writeSourceFile("[time=long]");
        assertBinaryTimeLong(SOURCE_FILE);
        convert("", "[format=text, time=long]");
        assertTextTimeLong(DESTINATION_FILE);
    }

    @Test
    public void testConvertTimeFieldToTextTimeMessage() throws IOException {
        writeSourceFile("[time=field]");
        assertBinaryTimeField(SOURCE_FILE);
        convert("", "[format=text,time=message]");
        assertTextTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertTimeFieldToTextTimeField() throws IOException {
        writeSourceFile("[time=field]");
        assertBinaryTimeField(SOURCE_FILE);
        convert("", "[format=text,time=field]");
        assertTextTimeField(DESTINATION_FILE);
    }

    @Test
    public void testConvertTimeMessageToTextTimeMessage() throws IOException {
        writeSourceFile("[time=message]");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[format=text,time=message]");
        assertTextTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertTimeMessageToTextTimeField() throws IOException {
        writeSourceFile("[time=message]");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[format=text,time=field]");
        assertTextTimeField(DESTINATION_FILE);
    }

    // -------------- convert text file to various time formats in binary

    @Test
    public void testConvertTextToTimeNone() throws IOException {
        writeSourceFile("[format=text]");
        assertTextTimeField(SOURCE_FILE);
        convert("", "[time=none]");
        assertBinaryTimeNoneMerged(DESTINATION_FILE);
    }

    @Test
    public void testConvertTextToTimeLong() throws IOException {
        writeSourceFile("[format=text]");
        assertTextTimeField(SOURCE_FILE);
        convert("", "[time=long]");
        assertBinaryTimeLong(DESTINATION_FILE);
    }

    @Test
    public void testConvertTextToTimeText() throws IOException {
        writeSourceFile("[format=text]");
        assertTextTimeField(SOURCE_FILE);
        convert("", "[time=text]");
        assertBinaryTimeText(DESTINATION_FILE);
    }

    @Test
    public void testConvertTextToTimeMessage() throws IOException {
        writeSourceFile("[format=text]");
        assertTextTimeField(SOURCE_FILE);
        convert("", "[time=message]");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertTextToTimeField() throws IOException {
        writeSourceFile("[format=text]");
        assertTextTimeField(SOURCE_FILE);
        convert("", "[time=field]");
        assertBinaryTimeFieldMerged(DESTINATION_FILE);
    }

    // -------------- convert text file to various time formats in text

    @Test
    public void testConvertTextToTextTimeNone() throws IOException {
        writeSourceFile("[format=text]");
        assertTextTimeField(SOURCE_FILE);
        convert("", "[format=text,time=none]");
        assertTextTimeNone(DESTINATION_FILE);
    }

    @Test
    public void testConvertTextToTextTimeMessage() throws IOException {
        writeSourceFile("[format=text]");
        assertTextTimeField(SOURCE_FILE);
        convert("", "[format=text,time=message]");
        assertTextTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertTextToTextTimeLong() throws IOException {
        writeSourceFile("[format=text]");
        assertTextTimeField(SOURCE_FILE);
        convert("", "[format=text,time=long]");
        assertTextTimeLong(DESTINATION_FILE);
    }

    @Test
    public void testConvertTextToTextTimeText() throws IOException {
        writeSourceFile("[format=text]");
        assertTextTimeField(SOURCE_FILE);
        convert("", "[format=text,time=text]");
        assertTextTimeText(DESTINATION_FILE);
    }

    @Test
    public void testConvertTextToTextTimeField() throws IOException {
        writeSourceFile("[format=text]");
        assertTextTimeField(SOURCE_FILE);
        convert("", "[format=text,time=field]");
        assertTextTimeField(DESTINATION_FILE);
    }

    // -------------- misc conversions

    @Test
    public void testConvertTextTimeMessageToTextTimeMessage() throws IOException {
        writeSourceFile("[format=text,time=message]");
        assertTextTimeMessage(SOURCE_FILE);
        convert("", "[format=text,time=message]");
        assertTextTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertTextTimeMessageToTextTimeField() throws IOException {
        writeSourceFile("[format=text,time=message]");
        assertTextTimeMessage(SOURCE_FILE);
        convert("", "[format=text,time=field]");
        assertTextTimeField(DESTINATION_FILE);
    }

    // -------------- convert default binary to cvs with various time formats

    @Test
    public void testConvertToCsv() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[format=csv]");
        assertCsvTimeField(DESTINATION_FILE);
    }

    @Test
    public void testConvertToCsvTimeNone() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[format=csv,time=none]");
        assertCsvTimeNone(DESTINATION_FILE);
    }

    @Test
    public void testConvertToCsvTimeField() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[format=csv,time=field]");
        assertCsvTimeField(DESTINATION_FILE);
    }

    @Test
    public void testConvertToCsvTimeMessage() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[format=csv,time=message]");
        assertCsvTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertToCsvTimeLong() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[format=csv,time=long]");
        assertCsvTimeLong(DESTINATION_FILE);
    }

    @Test
    public void testConvertToCsvTimeText() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[format=csv,time=text]");
        assertCsvTimeText(DESTINATION_FILE);
    }

    // -------------- convert from cvs with various time formats to default binary

    @Test
    public void testConvertCsv() throws IOException {
        writeSourceFile("[format=csv]");
        assertCsvTimeField(SOURCE_FILE);
        convert("", "");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertCsvTimeNone() throws IOException {
        writeSourceFile("[format=csv,time=none]");
        assertCsvTimeNone(SOURCE_FILE);
        convert("", "[time=none]"); // otherwise, current time will get written
        assertBinaryTimeNoneMerged(DESTINATION_FILE);
    }

    @Test
    public void testConvertCsvTimeField() throws IOException {
        writeSourceFile("[format=csv,time=field]");
        assertCsvTimeField(SOURCE_FILE);
        convert("", "");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertCsvTimeLong() throws IOException {
        writeSourceFile("[format=csv,time=long]");
        assertCsvTimeLong(SOURCE_FILE);
        convert("", "");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertCsvTimeText() throws IOException {
        writeSourceFile("[format=csv,time=text]");
        assertCsvTimeText(SOURCE_FILE);
        convert("", "");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    // -------------- convert binary without time to text with various time format that should include current time

    @Test
    public void testConvertTimeNoneToTextCurrentTime() throws IOException {
        writeSourceFile("[time=none]");
        assertBinaryTimeNone(SOURCE_FILE);
        convert("", "[format=text]");
        assertTextCurrentTimeField(DESTINATION_FILE);
    }

    @Test
    public void testConvertTimeNoneToTextCurrentField() throws IOException {
        writeSourceFile("[time=none]");
        assertBinaryTimeNone(SOURCE_FILE);
        convert("", "[format=text,time=field]");
        assertTextCurrentTimeField(DESTINATION_FILE);
    }

    @Test
    public void testConvertTimeNoneToTextCurrentTimeMessage() throws IOException {
        writeSourceFile("[time=none]");
        assertBinaryTimeNone(SOURCE_FILE);
        convert("", "[format=text,time=message]");
        assertTextCurrentTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertTimeNoneToTextCurrentTimeLong() throws IOException {
        writeSourceFile("[time=none]");
        assertBinaryTimeNone(SOURCE_FILE);
        convert("", "[format=text,time=long]");
        assertTextCurrentTimeLong(DESTINATION_FILE);
    }

    @Test
    public void testConvertTimeNoneToTextCurrentTimeText() throws IOException {
        writeSourceFile("[time=none]");
        assertBinaryTimeNone(SOURCE_FILE);
        convert("", "[format=text,time=text]");
        assertTextCurrentTimeText(DESTINATION_FILE);
    }

    @Test
    public void testConvertTimeNoneToCurrentTimeLong() throws IOException {
        writeSourceFile("[time=none]");
        assertBinaryTimeNone(SOURCE_FILE);
        convert("", "[time=long]");
        assertBinaryCurrentTimeLong(DESTINATION_FILE);
    }

    @Test
    public void testConvertTimeNoneToCurrentTimeText() throws IOException {
        writeSourceFile("[time=none]");
        assertBinaryTimeNone(SOURCE_FILE);
        convert("", "[time=text]");
        assertBinaryCurrentTimeText(DESTINATION_FILE);
    }

    // -------------- convert to various binary split formats

    @Test
    public void testConvertToSplit() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convertToSplit("");
        assertBinarySplitTimeMessage(DESTINATION_FILE_SPLIT);
    }

    @Test
    public void testConvertToSplitTimeNone() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convertToSplit("[time=none]");
        assertBinarySplitTimeNone(DESTINATION_FILE_SPLIT);
    }

    @Test
    public void testConvertToSplitTimeMessage() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convertToSplit("[time=message]");
        assertBinarySplitTimeMessage(DESTINATION_FILE_SPLIT);
    }

    @Test
    public void testConvertToSplitTimeField() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convertToSplit("[time=field]");
        assertBinarySplitTimeField(DESTINATION_FILE_SPLIT);
    }

    @Test
    public void testConvertToSplitTimeLong() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convertToSplit("[time=long]");
        assertBinarySplitTimeLong(DESTINATION_FILE_SPLIT);
    }

    @Test
    public void testConvertToSplitTimeText() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convertToSplit("[time=text]");
        assertBinarySplitTimeText(DESTINATION_FILE_SPLIT);
    }

    // -------------- convert split binary file to various binary formats

    @Test
    public void testConvertSplit() throws IOException {
        writeSourceFileSplit("");
        assertBinarySplitTimeMessage(SOURCE_FILE_SPLIT);
        convertSplit("");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertSplitTimeMessage() throws IOException {
        writeSourceFileSplit("[time=message]");
        assertBinarySplitTimeMessage(SOURCE_FILE_SPLIT);
        convertSplit("");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertSplitTimeField() throws IOException {
        writeSourceFileSplit("[time=field]");
        assertBinarySplitTimeField(SOURCE_FILE_SPLIT);
        convertSplit("");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertSplitTimeLong() throws IOException {
        writeSourceFileSplit("[time=long]");
        assertBinarySplitTimeLong(SOURCE_FILE_SPLIT);
        convertSplit("");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    @Test
    public void testConvertSplitTimeText() throws IOException {
        writeSourceFileSplit("[time=text]");
        assertBinarySplitTimeText(SOURCE_FILE_SPLIT);
        convertSplit("");
        assertBinaryTimeMessage(DESTINATION_FILE);
    }

    // -------------- convert split binary file to text

    @Test
    public void testConvertSplitToText() throws IOException {
        writeSourceFileSplit("");
        assertBinarySplitTimeMessage(SOURCE_FILE_SPLIT);
        convertSplit("[format=text]");
        assertTextTimeField(DESTINATION_FILE);
    }

    @Test
    public void testConvertSplitTimeMessageToText() throws IOException {
        writeSourceFileSplit("[time=message]");
        assertBinarySplitTimeMessage(SOURCE_FILE_SPLIT);
        convertSplit("[format=text]");
        assertTextTimeField(DESTINATION_FILE);
    }

    @Test
    public void testConvertSplitTimeFieldToText() throws IOException {
        writeSourceFileSplit("[time=field]");
        assertBinarySplitTimeField(SOURCE_FILE_SPLIT);
        convertSplit("[format=text]");
        assertTextTimeField(DESTINATION_FILE);
    }

    @Test
    public void testConvertSplitTimeLongToText() throws IOException {
        writeSourceFileSplit("[time=long]");
        assertBinarySplitTimeLong(SOURCE_FILE_SPLIT);
        convertSplit("[format=text]");
        assertTextTimeField(DESTINATION_FILE);
    }

    @Test
    public void testConvertSplitTimeTextToText() throws IOException {
        writeSourceFileSplit("[time=text]");
        assertBinarySplitTimeText(SOURCE_FILE_SPLIT);
        convertSplit("[format=text]");
        assertTextTimeField(DESTINATION_FILE);
    }

    // -------------- convert to various text split formats

    @Test
    public void testConvertToTextSplit() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convertToSplit("[format=text]");
        assertTextSplitTimeField(DESTINATION_FILE_SPLIT);
    }

    @Test
    public void testConvertToTextSplitTimeNone() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convertToSplit("[format=text,time=none]");
        assertTextSplitTimeNone(DESTINATION_FILE_SPLIT);
    }

    @Test
    public void testConvertToTextSplitTimeMessage() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convertToSplit("[format=text,time=message]");
        assertTextSplitTimeMessage(DESTINATION_FILE_SPLIT);
    }

    @Test
    public void testConvertToTextSplitTimeField() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convertToSplit("[format=text,time=field]");
        assertTextSplitTimeField(DESTINATION_FILE_SPLIT);
    }

    @Test
    public void testConvertToTextSplitTimeLong() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convertToSplit("[format=text,time=long]");
        assertTextSplitTimeLong(DESTINATION_FILE_SPLIT);
    }

    @Test
    public void testConvertToTextSplitTimeText() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convertToSplit("[format=text,time=text]");
        assertTextSplitTimeText(DESTINATION_FILE_SPLIT);
    }

    // -------------- misc conversions between split formats

    @Test
    public void testConvertSplitToTextSplit() throws IOException {
        writeSourceFileSplit("");
        assertBinarySplitTimeMessage(SOURCE_FILE_SPLIT);
        convertSplitToSplit("[format=text]");
        assertTextSplitTimeField(DESTINATION_FILE_SPLIT);
    }

    @Test
    public void testConvertSplitTimeLongToTextSplit() throws IOException {
        writeSourceFileSplit("[time=long]");
        assertBinarySplitTimeLong(SOURCE_FILE_SPLIT);
        convertSplitToSplit("[format=text]");
        assertTextSplitTimeField(DESTINATION_FILE_SPLIT);
    }

    @Test
    public void testConvertTextSplitToSplitTimeLong() throws IOException {
        writeSourceFileSplit("[format=text]");
        assertTextSplitTimeField(SOURCE_FILE_SPLIT);
        convertSplitToSplit("[time=long]");
        assertBinarySplitTimeLong(DESTINATION_FILE_SPLIT);
    }

    // -------------- conversion with RAW_DATA

    @Test
    public void testConvertRawData() throws IOException {
        writeSourceFile("[saveAs=raw_data]");
        assertBinaryTimeMessageRawData(SOURCE_FILE);
        convert("", "");
        assertBinaryTimeMessageRawData(DESTINATION_FILE);
    }

    @Test
    public void testConvertToRawData() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("", "[saveAs=raw_data]");
        assertBinaryTimeMessageRawData(DESTINATION_FILE);
    }

    @Test
    public void testConvertReadAsRawData() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("[readAs=raw_data]", "");
        assertBinaryTimeMessageRawData(DESTINATION_FILE);
    }

    @Test
    public void testConvertRawDataText() throws IOException {
        writeSourceFile("[saveAs=raw_data,format=text]");
        assertTextTimeFieldRawData(SOURCE_FILE);
        convert("", "");
        assertBinaryTimeMessageRawData(DESTINATION_FILE);
    }

    @Test
    public void testConvertReadAsRawDataToText() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("[readAs=raw_data]", "[format=text]");
        assertTextTimeFieldRawData(DESTINATION_FILE);
    }

    // -------------- to/from blob

    @Test
    public void testConvertBlobToBinaryTimeNone() throws IOException {
        writeSourceFile("[saveAs=history_data,format=blob:Quote:IBM.TEST]");
        assertBlob(SOURCE_FILE);
        convert("[format=blob:Quote:IBM.TEST]", "[time=none,saveAs=stream_data]");
        assertBinaryTimeNoneMerged(DESTINATION_FILE);
    }

    @Test
    public void testConvertToBlob() throws IOException {
        writeSourceFile("");
        assertBinaryTimeMessage(SOURCE_FILE);
        convert("[readAs=history_data]", "[format=blob:Quote:IBM.TEST]");
        assertBlob(DESTINATION_FILE);
    }

    public static class DumpSchemeProvider {

        public static final DataScheme INSTANCE = buildScheme();

        public static DataScheme getInstance() {
            return INSTANCE;
        }

        private static DefaultScheme buildScheme() {
            SchemeBuilder sb = new SchemeBuilder(new SchemeProperties(new Properties()));
            sb.addOptionalField("Quote", "Sequence", SerialFieldType.SEQUENCE, "Quote", "Sequence", false);
            sb.addOptionalField("Quote", "TimeNanoPart", SerialFieldType.COMPACT_INT, "Quote", "TimeNanoPart", false);
            sb.addOptionalField("Quote", "Bid.Time", SerialFieldType.TIME_SECONDS, "Quote", "BidTime", true);
            sb.addOptionalField("Quote", "Bid.Exchange", SerialFieldType.UTF_CHAR, "Quote", "BidExchangeCode", true);
            sb.addRequiredField("Quote", "Bid.Price", SerialFieldType.DECIMAL);
            sb.addRequiredField("Quote", "Bid.Size", SerialFieldType.COMPACT_INT);
            sb.addOptionalField("Quote", "Ask.Time", SerialFieldType.TIME_SECONDS, "Quote", "AskTime", true);
            sb.addOptionalField("Quote", "Ask.Exchange", SerialFieldType.UTF_CHAR, "Quote", "AskExchangeCode", true);
            sb.addRequiredField("Quote", "Ask.Price", SerialFieldType.DECIMAL);
            sb.addRequiredField("Quote", "Ask.Size", SerialFieldType.COMPACT_INT);

            return new DefaultScheme(PentaCodec.INSTANCE, sb.buildRecords());
        }
    }
}
