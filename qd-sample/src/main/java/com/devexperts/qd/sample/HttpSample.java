/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.sample;

/**
 * The <code>Sample</code> demonstrates how to build simple server & client GUI with QD.
 */
public class HttpSample {
    public static void main(String[] args) throws Exception {
        // Server QD creation.
        SampleHttpServer.main("8080");
        // Client QD creation.
        SampleClient.initClient("http://localhost:8080/sample/QDServlet", 1234);
    }
}
