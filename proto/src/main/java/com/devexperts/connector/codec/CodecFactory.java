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
package com.devexperts.connector.codec;

import com.devexperts.connector.proto.ApplicationConnectionFactory;

/**
 * Factory class for creating {@link CodecConnection codecs} over existing
 * {@link com.devexperts.connector.proto.ApplicationConnection application protocols}.
 */
public interface CodecFactory {
    /**
     * Wraps specified {@link ApplicationConnectionFactory} by codec and returns
     * the new factory that produces "codeced" connections.
     * @param name name of a codec to apply.
     * @param delegate wrapped {@link ApplicationConnectionFactory}.
     * @return new {@link ApplicationConnectionFactory} that produces "codeced" connections
     * or {@code delegate} if the {@code name} doesn't correspond to any codec handled by
     * this factory.
     */
    public ApplicationConnectionFactory createCodec(String name, ApplicationConnectionFactory delegate);
}
