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

import com.devexperts.logging.Logging;
import com.devexperts.qd.QDFactory;
import com.devexperts.transport.stats.ConnectionStats;
import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.TimeFormat;

import java.io.IOException;
import java.util.Date;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class DataExtractorServlet extends HttpServlet {
    private static final Logging log = Logging.getLogging(DataExtractorServlet.class);

    @Override
    public void init() throws ServletException {
        log.info("Started with " + DataExtractorConfig.INSTANCE);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getParameter("help") != null) {
            help(resp, null);
            return;
        }
        DataExtractorRequest dataReq;
        try {
            dataReq = new DataExtractorRequest(req);
        } catch (InvalidFormatException e) {
            help(resp, e);
            return;
        }
        String userAgent = req.getHeader("User-Agent");
        String acceptEncoding = req.getHeader("Accept-Encoding");
        log.info("Processing " + dataReq +
            " via \"" + userAgent + "\"" +
            (acceptEncoding != null ? " accepts " + acceptEncoding : ""));
        try {
            ConnectionStats connectionStats = dataReq.execute(resp);
            log.info("Completed successfully. Parsed " + connectionStats.getReadBytes() + " bytes while processing " + dataReq);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            log.error("Completed abruptly " + dataReq, t);
            throw new ServletException(t);
        }
    }

    private void help(HttpServletResponse resp, InvalidFormatException e) throws IOException {
        resp.setContentType("text/plain");
        ServletOutputStream out = resp.getOutputStream();
        if (e != null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.println("Invalid usage format: " + e.getMessage());
            out.println();
        }
        out.println("Data Extractor, Version " + QDFactory.getVersion() + ", (C) Devexperts");
        out.println();
        out.println("Usage: data?help");
        out.println("  Prints this help.");
        out.println();
        out.println("Usage: data?records=<records>&symbols=<symbols>&start=<start>&stop=<stop>[&format=<format>]");
        out.println("Where:");
        out.println("    <records> - comma-separated list of records (for example \"Quote,Trade\")");
        out.println("    <symbols> - comma-separated list of symbols (for example \"IBM,MSFT\")");
        out.println("    <start>   - start time for data extraction as YYYYMMDD-HHMMSS[.sss][zone]");
        out.println("    <stop>    - stop time for data extraction in the same format as above");
        out.println("    <format>  - output format: \"binary\", \"text\", or \"csv\" (text by default)");
        out.println("  Extracts data for the specified records, symbol and time range in the specified format.");
        out.println("  Note, that current time and zone is " + TimeFormat.DEFAULT.withTimeZone().format(new Date()));
        out.println();
        out.println("Usage: data.<format>?...");
        out.println("  The same as above, but the format is specified as extension.");
    }
}
