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
package com.devexperts.qd.dataextractor;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.kit.RecordOnlyFilter;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.AbstractQTPComposer;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.qd.qtp.file.FileFormat;
import com.devexperts.qd.qtp.file.FileReader;
import com.devexperts.qd.qtp.file.FileReaderParams;
import com.devexperts.qd.qtp.file.OutputStreamMessageConsumer;
import com.devexperts.qd.qtp.file.TimestampsType;
import com.devexperts.transport.stats.ConnectionStats;
import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.TimeFormat;

import java.io.IOException;
import java.util.Date;
import java.util.Locale;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class DataExtractorRequest {
    private final DataScheme scheme = QDFactory.getDefaultScheme();
    private final RecordOnlyFilter records;
    private final QDFilter symbols;
    private final Date start;
    private final Date stop;
    private final FileFormat format;

    public DataExtractorRequest(HttpServletRequest req) throws InvalidFormatException {
        records = RecordOnlyFilter.valueOf(getRequired(req, "records"), scheme);
        symbols = CompositeFilters.valueOf(getRequired(req, "symbols"), scheme);
        start = TimeFormat.DEFAULT.parse(getRequired(req, "start"));
        stop = TimeFormat.DEFAULT.parse(getRequired(req, "stop"));
        String formatStr = req.getParameter("format");
        format =
            req.getServletPath().endsWith(".binary") ? FileFormat.BINARY :
            req.getServletPath().endsWith(".text") ? FileFormat.TEXT :
            req.getServletPath().endsWith(".csv") ? FileFormat.CSV :
            formatStr == null ? FileFormat.TEXT : FileFormat.valueOf(formatStr);
    }

    @Override
    public String toString() {
        return "DataExtractorRequest{" +
            "records=" + records +
            ", symbols=" + symbols +
            ", start=" + TimeFormat.DEFAULT.format(start) +
            ", stop=" + TimeFormat.DEFAULT.format(stop) +
            ", format=" + format +
            '}';
    }

    private static String getRequired(HttpServletRequest req, String param) throws InvalidFormatException {
        String s = req.getParameter(param);
        if (s == null)
            throw new InvalidFormatException(param + " is not specified");
        return s;
    }

    public ConnectionStats execute(HttpServletResponse resp) throws InterruptedException, IOException {
        DataExtractorConfig config = DataExtractorConfig.INSTANCE;
        // initialize format, protocol descriptor, and other stuff for writer
        resp.setContentType(format.getContentType());
        ProtocolDescriptor descriptor = ProtocolDescriptor.newSelfProtocolDescriptor("extract");
        descriptor.addSend(descriptor.newMessageDescriptor(config.getReadAs()));
        TimestampsType timestampsType = format.getTimestampsType();
        if (timestampsType != TimestampsType.NONE)
            descriptor.setProperty(ProtocolDescriptor.TIME_PROPERTY, timestampsType.toString().toLowerCase(Locale.US));
        AbstractQTPComposer composer = format.createQTPComposer(scheme);
        ConnectionStats connectionStats = new ConnectionStats();
        // Initialize reader
        FileReaderParams.Default params = new FileReaderParams.Default();
        params.setStartTime(start.getTime());
        params.setStopTime(stop.getTime());
        params.setSpeed(FileReaderParams.MAX_SPEED);
        params.setReadAs(config.getReadAs());
        FileReader reader = new FileReader(config.getFile(), connectionStats, params);
        reader.setScheme(scheme);
        try {
            ServletOutputStream out = resp.getOutputStream();
            OutputStreamMessageConsumer writer = new OutputStreamMessageConsumer(out, reader, composer) {
                @Override
                protected boolean acceptCursor(QDContract contract, RecordCursor cursor) {
                    return records.acceptRecord(cursor.getRecord()) &&
                        symbols.accept(contract, cursor.getRecord(), cursor.getCipher(), cursor.getSymbol());
                }
            };
            writer.write(descriptor);
            return connectionStats;
        } finally {
            reader.close();
        }
    }

}
