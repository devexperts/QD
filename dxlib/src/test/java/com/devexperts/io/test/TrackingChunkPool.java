/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.io.test;

import com.devexperts.io.Chunk;
import com.devexperts.io.ChunkList;
import com.devexperts.io.ChunkPool;

import static org.junit.Assert.assertEquals;

class TrackingChunkPool extends ChunkPool {
    TrackingChunkPool() {
        super("<test>", 3, 1, 1, 100, 1);
    }

    int gc, rc;
    int gl, rl;

    @Override
    public Chunk getChunk(Object owner) {
        gc++;
        return super.getChunk(owner);
    }

    @Override
    public ChunkList getChunkList(Object owner) {
        gl++;
        return super.getChunkList(owner);
    }

    @Override
    protected void recycleChunk(Chunk chunk, Object owner) {
        rc++;
        super.recycleChunk(chunk, owner);
    }

    @Override
    protected void recycleChunkList(ChunkList chunkList, Object owner) {
        rl++;
        super.recycleChunkList(chunkList, owner);
    }

    void checkCounters(int gc, int rc, int gl, int rl) {
        if (gc >= 0) assertEquals("acquired chunks", gc, this.gc);
        if (rc >= 0) assertEquals("recycled chunks", rc, this.rc);
        if (gl >= 0) assertEquals("acquired chunk lists", gl, this.gl);
        if (rl >= 0) assertEquals("recycled chunk lists", rl, this.rl);
    }

    void clearCounters() {
        gc = rc = gl = rl = 0;
    }

    void checkAndClearCounters(int gc, int rc, int gl, int rl) {
        checkCounters(gc, rc, gl, rl);
        clearCounters();
    }
}
