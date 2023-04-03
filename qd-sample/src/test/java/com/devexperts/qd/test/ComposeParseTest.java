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
package com.devexperts.qd.test;

import com.devexperts.io.Chunk;
import com.devexperts.io.ChunkList;
import com.devexperts.io.ChunkPool;
import com.devexperts.io.ChunkedInput;
import com.devexperts.io.ChunkedOutput;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.qtp.AbstractQTPComposer;
import com.devexperts.qd.qtp.AbstractQTPParser;
import com.devexperts.qd.qtp.BinaryQTPComposer;
import com.devexperts.qd.qtp.BinaryQTPParser;
import com.devexperts.qd.qtp.ByteArrayComposer;
import com.devexperts.qd.qtp.ByteArrayParser;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.text.TextQTPComposer;
import com.devexperts.qd.qtp.text.TextQTPParser;
import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ComposeParseTest {
    private static final int REPEAT = 1000;

    private static final Random rnd = new Random(20081017);
    private static final DataScheme scheme = new TestDataScheme(rnd.nextLong());

    @Test
    public void testLegacyComposerAndParser() {
        // legacy classes with default parameters do not compose record descriptions and allow records without them.
        checkBufferComposerAndParser(new ByteArrayComposer(scheme), new ByteArrayParser(scheme), false);
    }

    @Test
    public void testComposerAndParserWithDescribe() {
        checkBufferComposerAndParser(new BinaryQTPComposer(scheme, true), new BinaryQTPParser(scheme), false);
    }

    @Test
    public void testTextComposerAndParser() {
        checkBufferComposerAndParser(new TextQTPComposer(scheme), new TextQTPParser(scheme), false);
    }

    @Test
    public void testLegacyComposerAndParserMixed() {
        checkBufferComposerAndParser(new ByteArrayComposer(scheme), new ByteArrayParser(scheme), true);
    }

    @Test
    public void testComposerAndParserMixedWithDescribe() {
        checkBufferComposerAndParser(new BinaryQTPComposer(scheme, true), new BinaryQTPParser(scheme), true);
    }

    @Test
    public void testTextComposerAndParserMixed() {
        checkBufferComposerAndParser(new TextQTPComposer(scheme), new TextQTPParser(scheme), true);
    }

    private void checkBufferComposerAndParser(AbstractQTPComposer composer, AbstractQTPParser parser, boolean mixed) {
        // configure composer byte source
        ByteSource bs;
        if (composer instanceof ByteArrayComposer) {
            // Legacy ByteArrayComposer writes bytes to an internal array of bytes
            final ByteArrayComposer bac = (ByteArrayComposer) composer;
            bs = new ByteSource() {
                public ChunkList get(Object owner) {
                    int processed = bac.getProcessed();
                    ChunkList chunks = ChunkPool.DEFAULT.copyToChunkList(bac.getBuffer(), 0, processed, owner);
                    bac.removeBytes(processed);
                    return chunks;
                }
            };
        } else {
            // New implementations require an external output to be provided
            final ChunkedOutput output = new ChunkedOutput();
            composer.setOutput(output);
            bs = new ByteSource() {
                public ChunkList get(Object owner) {
                    return output.getOutput(owner);
                }
            };
        }

        // configure parser byte destination
        ByteDestination bd;
        if (parser instanceof  ByteArrayParser) {
            // Legacy ByteArrayParser parses from an internal array of bytes
            final ByteArrayParser bap = (ByteArrayParser) parser;
            bd = new ByteDestination() {
                public void put(ChunkList chunks, Object owner) {
                    Chunk chunk;
                    while ((chunk = chunks.poll(owner)) != null)
                        bap.addBytes(chunk.getBytes(), chunk.getOffset(), chunk.getLength());
                    chunks.recycle(owner);
                }
            };
        } else {
            // New implementations require an external input to be provided
            final ChunkedInput input = new ChunkedInput();
            parser.setInput(input);
            bd = new ByteDestination() {
                public void put(ChunkList chunks, Object owner) {
                    input.addAllToInput(chunks, owner);
                }
            };
        }

        // test
        RecordBuffer buf = RecordBuffer.getInstance();
        Map<MessageType,TestDataProvider> providers1 = new EnumMap<MessageType, TestDataProvider>(MessageType.class);
        Map<MessageType,TestDataProvider> providers2 = new EnumMap<MessageType, TestDataProvider>(MessageType.class);

        long seed = rnd.nextLong();
        providers1.put(MessageType.TICKER_DATA, new TestDataProvider(scheme, seed));
        providers2.put(MessageType.TICKER_DATA, new TestDataProvider(scheme, seed));
        if (mixed) {
            providers1.put(MessageType.STREAM_DATA, new TestDataProvider(scheme, seed + 1));
            providers2.put(MessageType.STREAM_DATA, new TestDataProvider(scheme, seed + 1));
            providers1.put(MessageType.HISTORY_DATA, new TestDataProvider(scheme, seed + 2));
            providers2.put(MessageType.HISTORY_DATA, new TestDataProvider(scheme, seed + 2));
        }
        ComparingMessageConsumer cmc = new ComparingMessageConsumer(providers2);
        // Compose data and push it to parser's buffer
        int records = 0;
        for (int repeat = 0; repeat < REPEAT; repeat++) {
            MessageType type = MessageType.TICKER_DATA;
            if (mixed) {
                switch (repeat % 3) {
                case 0: type = MessageType.TICKER_DATA; break;
                case 1: type = MessageType.STREAM_DATA; break;
                case 2: type = MessageType.HISTORY_DATA; break;
                }
            }
            providers1.get(type).retrieveData(buf);
            records += buf.size();
            while (buf.hasNext()) {
                boolean more = composer.visitData(buf, type);
                if (buf.hasNext())
                    assertTrue(more);
                transferBytes(bs, bd);
                parser.parse(cmc);
            }
            buf.clear();
        }
        parser.parse(cmc);
        // Make sure we've done all the stuff
        for (Map.Entry<MessageType, TestDataProvider> e : providers1.entrySet()) {
            assertEquals(e.getValue().getRecordsProvidedCount(), providers2.get(e.getKey()).getRecordsProvidedCount());
        }
        assertEquals(records, cmc.getRecordCounter());
        buf.release();
    }

    // Transfers a random fraction of bytes from composer to parser
    private void transferBytes(ByteSource bs, ByteDestination bd) {
        ChunkList chunks = bs.get(this);
        if (chunks != null)
            bd.put(chunks, this);
    }

    interface ByteSource {
        ChunkList get(Object owner);
    }

    interface ByteDestination {
        void put(ChunkList chunks, Object owner);
    }
}
