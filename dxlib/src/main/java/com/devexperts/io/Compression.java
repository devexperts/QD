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

import com.devexperts.util.LockFreePool;

import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * The utility class that provides methods for data compression and decompression.
 * It uses pooling of {@link Deflater} and {@link Inflater} for better efficiency.
 */
class Compression {
    private static final LockFreePool<Deflater> DEFLATERS_POOL = new LockFreePool<Deflater>("com.devexperts.io.Deflater", 16);
    private static final LockFreePool<Inflater> INFLATERS_POOL = new LockFreePool<Inflater>("com.devexperts.io.Inflater", 16);

    private static final byte[] emptyInput = new byte[0]; // Empty array used to reset input reference and release old one.

    static void deflate(byte[] bytes, int offset, int length, int level, ByteArrayOutput out) {
        Deflater deflater = DEFLATERS_POOL.poll();
        if (deflater == null)
            deflater = new Deflater();
        try {
            deflater.setLevel(level);
            deflater.setInput(bytes, offset, length);
            deflater.finish();
            while (!deflater.finished()) {
                int pos = out.getPosition();
                out.ensureCapacity(pos + 8192);
                int n = deflater.deflate(out.getBuffer(), pos, out.getLimit() - pos);
                out.setPosition(pos + n);
            }
        } finally {
            deflater.reset();
            deflater.setInput(emptyInput); // Reset input reference to release old one.
            if (!DEFLATERS_POOL.offer(deflater))
                deflater.end();
        }
    }

    static void inflate(byte[] bytes, int offset, int length, ByteArrayOutput out) throws DataFormatException {
        Inflater inflater = INFLATERS_POOL.poll();
        if (inflater == null)
            inflater = new Inflater();
        try {
            inflater.setInput(bytes, offset, length);
            while (!inflater.finished()) {
                int pos = out.getPosition();
                out.ensureCapacity(pos + 8192);
                int n = inflater.inflate(out.getBuffer(), pos, out.getLimit() - pos);
                out.setPosition(pos + n);
                if (n == 0 && !inflater.finished()) {
                    if (inflater.needsInput())
                        throw new DataFormatException("needs input");
                    if (inflater.needsDictionary())
                        throw new DataFormatException("needs dictionary");
                }
            }
            if (inflater.getRemaining() != 0)
                throw new DataFormatException("remaining bytes");
        } finally {
            inflater.reset();
            inflater.setInput(emptyInput); // Reset input reference to release old one.
            if (!INFLATERS_POOL.offer(inflater))
                inflater.end();
        }
    }

    /**
     * Determines if specified byte array is compressed or not. This method only checks standard ZLIB header,
     * so it can wronly report positive result on a random array of bytes. Use it as heuristic test only.
     *
     * @param bytes the byte array to be tested
     * @param offset the start offset in the byte array
     * @param length the number of bytes in the array
     * @return <code>true</code> if byte array is compressed, <code>false</code> otherwise
     */
    static boolean isCompressed(byte[] bytes, int offset, int length) {
        if (length < 6)
            return false;
        int header = ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
        return header <= 0x78FF && (header & 0x0F00) == 0x0800 && header % 31 == 0;
    }

    static boolean shallCompress(byte[] bytes, int offset, int length) {
        return IOUtil.isCompressionEnabled() && length >= 8192 && !isCompressed(bytes, offset, length);
    }
}
