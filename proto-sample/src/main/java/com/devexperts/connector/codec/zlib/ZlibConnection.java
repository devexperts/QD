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
package com.devexperts.connector.codec.zlib;

import com.devexperts.connector.codec.CodecConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.io.Chunk;
import com.devexperts.io.ChunkList;
import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZStream;
import com.jcraft.jzlib.ZStreamException;

import java.io.IOException;

class ZlibConnection extends CodecConnection<ZlibConnectionFactory> {

    private final ZStream dStream = new ZStream();
    private final ZStream iStream = new ZStream();

    ZlibConnection(ApplicationConnectionFactory delegateFactory, ZlibConnectionFactory factory,
        TransportConnection transportConnection) throws IOException
    {
        super(delegateFactory, factory, transportConnection);
        try {
            validate(dStream, dStream.deflateInit(factory.compression, factory.suppressHeaders));
            validate(iStream, iStream.inflateInit(factory.suppressHeaders));
        } catch (ZStreamException e) {
            log.error("failed to initialize jzlib stream", e);
            close();
        }
    }

    @Override
    public ChunkList retrieveChunks(Object owner) {
        try {
            ChunkList infChunks = delegate.retrieveChunks(this);
            if (infChunks == null)
                return null;
            if (infChunks.getTotalLength() == 0) {
                infChunks.recycle(this);
                return null;
            }

            int infChunkIndex = -1;
            ChunkList defChunks = factory.getChunkPool().getChunkList(this);
            Chunk defChunk = factory.getChunkPool().getChunk(this);
            setNextOut(dStream, defChunk);
            boolean ateEverything = false;
            while (!ateEverything || dStream.avail_out == 0) {
                if (!ateEverything && dStream.avail_in == 0) {
                    if (++infChunkIndex >= infChunks.size())
                        ateEverything = true;
                    else {
                        setNextIn(dStream, infChunks.get(infChunkIndex));
                        continue;
                    }
                }
                if (dStream.avail_out == 0) {
                    defChunks.add(defChunk, this);
                    setNextOut(dStream, defChunk = factory.getChunkPool().getChunk(this));
                }
                validate(dStream, dStream.deflate(ateEverything ? JZlib.Z_SYNC_FLUSH : JZlib.Z_NO_FLUSH));
            }
            defChunk.setLength(defChunk.getLength() - dStream.avail_out, this);
            if (defChunk.getLength() == 0)
                defChunk.recycle(this);
            else
                defChunks.add(defChunk, this);
            infChunks.recycle(this);
            defChunks.handOver(this, owner);
            return defChunks;
        } catch (ZStreamException e) {
            log.error("Failed to deflate outgoing data", e);
            close();
            return null;
        } catch (Throwable t) {
            log.error("Unexpected error", t);
            close();
            return null;
        }
    }

    @Override
    public boolean processChunks(ChunkList defChunks, Object owner) {
        if (defChunks == null)
            throw new NullPointerException();
        if (defChunks.getTotalLength() == 0) {
            defChunks.recycle(owner);
            return true;
        }
        try {
            int defChunkIndex = -1;
            ChunkList infChunks = factory.getChunkPool().getChunkList(this);
            Chunk infChunk = factory.getChunkPool().getChunk(this);
            setNextOut(iStream, infChunk);
            while (true) {
                if (iStream.avail_in == 0) {
                    if (++defChunkIndex >= defChunks.size())
                        break;
                    setNextIn(iStream, defChunks.get(defChunkIndex));
                    continue;
                }
                if (iStream.avail_out == 0) {
                    infChunks.add(infChunk, this);
                    setNextOut(iStream, infChunk = factory.getChunkPool().getChunk(this));
                }
                validate(iStream, iStream.inflate(JZlib.Z_SYNC_FLUSH));
            }
            infChunk.setLength(infChunk.getLength() - iStream.avail_out, this);
            if (infChunk.getLength() == 0)
                infChunk.recycle(this);
            else
                infChunks.add(infChunk, this);
            defChunks.recycle(owner);
            return delegate.processChunks(infChunks, this);
        } catch (ZStreamException e) {
            log.error("Failed to inflate incoming data", e);
            close();
            return false;
        } catch (Throwable t) {
            log.error("Unexpected error", t);
            close();
            return false;
        }
    }

    private static void setNextIn(ZStream zStream, Chunk chunk) {
        zStream.next_in = chunk.getBytes();
        zStream.next_in_index = chunk.getOffset();
        zStream.avail_in = chunk.getLength();
    }

    private static void setNextOut(ZStream zStream, Chunk chunk) {
        zStream.next_out = chunk.getBytes();
        zStream.next_out_index = chunk.getOffset();
        zStream.avail_out = chunk.getLength();
    }

    private static void validate(ZStream zStream, int errorCode) throws ZStreamException {
        if (errorCode != JZlib.Z_OK)
            throw new ZStreamException(zStream.msg + " (error code: " + errorCode + ")");
    }

}
