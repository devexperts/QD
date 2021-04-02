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
package com.devexperts.connector.codec.xor;

import com.devexperts.io.ChunkList;
import com.devexperts.io.ChunkedInput;
import com.devexperts.io.ChunkedOutput;

import java.io.IOException;
import java.security.MessageDigest;

class ChunksXorer {
    private final MessageDigest algorithm;
    private byte[] bytes;
    private int position;

    private final ChunkedInput in;
    private final ChunkedOutput out;

    ChunksXorer(XorConnectionFactory factory) {
        try {
            algorithm = (MessageDigest) factory.algorithm.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
        bytes = algorithm.digest(factory.secret.getBytes());
        in = new ChunkedInput();
        out = new ChunkedOutput(factory.getChunkPool());
    }

    ChunkList xorChunks(ChunkList srcChunks) {
        if (srcChunks == null)
            return null;
        in.addAllToInput(srcChunks, this);
        try {
            while (in.hasAvailable())
                out.write(in.read() ^ nextByte());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        ChunkList dstChunks = out.getOutput(this);
        return dstChunks == null ? ChunkList.EMPTY : dstChunks;
    }

    private byte nextByte() {
        if (position >= bytes.length)
            nextBlock();
        return bytes[position++];
    }

    private void nextBlock() {
        bytes = algorithm.digest(bytes);
        position = 0;
    }
}
