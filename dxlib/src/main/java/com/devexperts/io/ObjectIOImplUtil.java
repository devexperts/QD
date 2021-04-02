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
import java.io.ObjectOutputStream;

class ObjectIOImplUtil {
    private ObjectIOImplUtil() {} // do not create

    static final byte[] STREAM_HEADER;
    static final byte[] STREAM_RESET;
    static final long RESET_MAGIC = 1234395879875320345L;

    static {
        try {
            ByteArrayOutput bao = new ByteArrayOutput();
            ObjectOutputStream oos = new ObjectOutputStream(bao);
            oos.flush();
            STREAM_HEADER = bao.toByteArray();
            bao.setPosition(0);
            oos.reset();
            oos.writeLong(RESET_MAGIC);
            oos.flush();
            STREAM_RESET = bao.toByteArray();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }


    /**
     * Computes signature for specified declared types to be used as magic in serial streams.
     * The signature has fixed high byte value <code>0xE8</code> and lower 3 bytes computed
     * as hashcode of declared types names. The resulting value of signature lies in range
     * from <code>-402653184</code> to <code>-385875969</code>, and if it is mistakenly read
     * from stream as CompactInt then it's value lies in range from <code>-134217728</code>
     * to <code>-117440513</code>.
     *
     * @param types the declared types to compute signature
     * @return the signature for specified declared types
     */
    static int getDeclaredTypesSignature(Class<?>[] types) {
        int hash = 0;
        for (Class<?> type : types)
            hash = hash * 239 + type.getName().hashCode();
        return 0xE8000000 | hash & 0x00FFFFFF;
    }
}
