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
package com.dxfeed.webservice.rest;

import java.util.Collections;
import java.util.Map;

public class HttpErrorException extends Exception {
    private final int statusCode;
    private final Map<String, String> headers;

    public HttpErrorException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.headers = null;
    }

    public HttpErrorException(int statusCode, String message, Map<String, String> headers) {
        super(message);
        this.statusCode = statusCode;
        this.headers = headers;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, String> getHeaders() {
        return headers != null ? headers : Collections.emptyMap();
    }
}
