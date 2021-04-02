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
package com.devexperts.io;

import java.io.IOException;

/**
 * This exception is thrown by {@link Marshalled} when it fails to serialize/deserialize object.
 */
public class MarshallingException extends RuntimeException {
    private static final long serialVersionUID = 0;

    public MarshallingException(IOException cause) {
        super(cause);
    }

    @Override
    public IOException getCause() {
        return (IOException) super.getCause();
    }
}
