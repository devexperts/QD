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
package com.dxfeed.news.impl;

/**
 * The specified news does not exist in the repository.
 */
public class NewsNotFoundException extends RuntimeException
{
    public NewsNotFoundException() {
    }

    public NewsNotFoundException(String message) {
        super(message);
    }
}
