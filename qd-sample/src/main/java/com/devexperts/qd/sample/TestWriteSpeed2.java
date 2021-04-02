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
package com.devexperts.qd.sample;

import com.devexperts.io.BufferedOutput;
import com.devexperts.io.ByteArrayOutput;
import com.devexperts.io.IOUtil;
import com.sun.management.GarbageCollectorMXBean;
import com.sun.management.OperatingSystemMXBean;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Tests different methods to serialize a snapshot of in-memory data structure.
 */
public class TestWriteSpeed2 {
    private static final int SIZE_4K = 4096;
    private static final int SIZE_64K = 65536;

    static class Item {
        int i1;
        int i2;
        int i3;
        int i4;
        int i5;
        int i6;

        void writePlainExternal(DataOutput out) throws IOException {
            out.writeInt(i1);
            out.writeInt(i2);
            out.writeInt(i3);
            out.writeInt(i4);
            out.writeInt(i5);
            out.writeInt(i6);
        }

        void writePlainBuffered(BufferedOutput out) throws IOException {
            out.writeInt(i1);
            out.writeInt(i2);
            out.writeInt(i3);
            out.writeInt(i4);
            out.writeInt(i5);
            out.writeInt(i6);
        }

        void writePlainToByteBuffer(ByteBuffer buf) {
            buf.putInt(i1);
            buf.putInt(i2);
            buf.putInt(i3);
            buf.putInt(i4);
            buf.putInt(i5);
            buf.putInt(i6);
        }

        void writeCompactExternal(DataOutput out) throws IOException {
            IOUtil.writeCompactInt(out, i1);
            IOUtil.writeCompactInt(out, i2);
            IOUtil.writeCompactInt(out, i3);
            IOUtil.writeCompactInt(out, i4);
            IOUtil.writeCompactInt(out, i5);
            IOUtil.writeCompactInt(out, i6);
        }

        void writeCompactBuffered(BufferedOutput out) throws IOException {
            out.writeCompactInt(i1);
            out.writeCompactInt(i2);
            out.writeCompactInt(i3);
            out.writeCompactInt(i4);
            out.writeCompactInt(i5);
            out.writeCompactInt(i6);
        }

        void writeCompactToByteBuffer(ByteBuffer buf) {
            writeCompactInt(buf, i1);
            writeCompactInt(buf, i2);
            writeCompactInt(buf, i3);
            writeCompactInt(buf, i4);
            writeCompactInt(buf, i5);
            writeCompactInt(buf, i6);
        }

    }

    static class Items {
        final Map<String, Item> map = new HashMap<String, Item>();

        void writePlainExternal(DataOutput out) throws IOException {
            out.writeInt(map.size());
            for (Map.Entry<String, Item> entry : map.entrySet()) {
                out.writeUTF(entry.getKey());
                entry.getValue().writePlainExternal(out);
            }
        }

        void writePlainBuffered(BufferedOutput out) throws IOException {
            out.writeInt(map.size());
            for (Map.Entry<String, Item> entry : map.entrySet()) {
                out.writeUTF(entry.getKey());
                entry.getValue().writePlainBuffered(out);
            }
        }

        void writePlainToByteBuffer(ByteBuffer buf) throws IOException {
            buf.putInt(map.size());
            for (Map.Entry<String, Item> entry : map.entrySet()) {
                String key = entry.getKey();
                int len = key.length();
                buf.putShort((short) len);
                for (int i = 0; i < len; i++) {
                    buf.put((byte) key.charAt(i)); // todo: replace with method call
                }
                entry.getValue().writePlainToByteBuffer(buf);
            }
        }

        void writeCompactExternal(DataOutput out) throws IOException {
            IOUtil.writeCompactInt(out, map.size());
            for (Map.Entry<String, Item> entry : map.entrySet()) {
                IOUtil.writeUTFString(out, entry.getKey());
                entry.getValue().writeCompactExternal(out);
            }
        }

        void writeCompactBuffered(BufferedOutput out) throws IOException {
            out.writeCompactInt(map.size());
            for (Map.Entry<String, Item> entry : map.entrySet()) {
                out.writeUTFString(entry.getKey());
                entry.getValue().writeCompactBuffered(out);
            }
        }

        void writeCompactToByteBuffer(ByteBuffer buf) throws IOException {
            writeCompactInt(buf, map.size());
            for (Map.Entry<String, Item> entry : map.entrySet()) {
                String key = entry.getKey();
                int len = key.length();
                writeCompactInt(buf, len);
                for (int i = 0; i < len; i++) {
                    buf.put((byte) key.charAt(i)); // todo: replace with method call
                }
                entry.getValue().writeCompactToByteBuffer(buf);
            }
        }
    }

    static class ChunkedOutput extends BufferedOutput {
        final int chunkSize;
        final List<ByteBuffer> chunks = new ArrayList<ByteBuffer>();
        ByteBuffer curChunk;
        int nextChunkIndex;

        ChunkedOutput(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        protected void needSpace() throws IOException {
            if (curChunk != null)
                curChunk.limit(position);
            while (nextChunkIndex >= chunks.size())
                chunks.add(ByteBuffer.allocate(chunkSize));
            curChunk = chunks.get(nextChunkIndex++);
            buffer = curChunk.array();
            position = 0;
            limit = curChunk.capacity();
        }

        public int size() {
            int size = position;
            for (int i = 0; i < nextChunkIndex - 1; i++)
                size += chunks.get(i).limit();
            return size;
        }

        public void clear() {
            curChunk = null;
            nextChunkIndex = 0;
            buffer = null;
            position = 0;
            limit = 0;
        }
    }

    final Items items = new Items();
    int sizePlain;
    int sizeCompact;

    ByteArrayOutputStream pooledByteArrayOutputStream;
    ByteArrayOutput pooledByteArrayOutput;
    ChunkedOutput pooledChunkedOutput4k;
    ChunkedOutput pooledChunkedOutput64k;
    ByteBuffer pooledHeapBuf;
    ByteBuffer pooledDirectBuf;

    private int updateSize(int size, int length) {
        if (size > 0 && size != length)
            throw new AssertionError("Bad size: " + size + " != " + length);
        return length;
    }

    int testPlainExternalToDataOutputStream() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        items.writePlainExternal(new DataOutputStream(baos));
        return sizePlain = updateSize(sizePlain, baos.size());
    }

    int testPlainExternalToByteArrayOutput() throws IOException {
        ByteArrayOutput bao = new ByteArrayOutput();
        items.writePlainExternal(bao);
        return sizePlain = updateSize(sizePlain, bao.getPosition());
    }

    int testPlainBufferedToByteArrayOutput() throws IOException {
        ByteArrayOutput bao = new ByteArrayOutput();
        items.writePlainBuffered(bao);
        return sizePlain = updateSize(sizePlain, bao.getPosition());
    }

    int testPlainExternalToChunkedOutput4k() throws IOException {
        ChunkedOutput co = new ChunkedOutput(SIZE_4K);
        items.writePlainExternal(co);
        return sizePlain = updateSize(sizePlain, co.size());
    }

    int testPlainBufferedToChunkedOutput4k() throws IOException {
        ChunkedOutput co = new ChunkedOutput(SIZE_4K);
        items.writePlainBuffered(co);
        return sizePlain = updateSize(sizePlain, co.size());
    }

    int testPlainExternalToChunkedOutput64k() throws IOException {
        ChunkedOutput co = new ChunkedOutput(SIZE_64K);
        items.writePlainExternal(co);
        return sizePlain = updateSize(sizePlain, co.size());
    }

    int testPlainBufferedToChunkedOutput64k() throws IOException {
        ChunkedOutput co = new ChunkedOutput(SIZE_64K);
        items.writePlainBuffered(co);
        return sizePlain = updateSize(sizePlain, co.size());
    }

    int testPlainExternalToPreallocatedByteArrayOutput() throws IOException {
        ByteArrayOutput bao = new ByteArrayOutput(sizePlain);
        items.writePlainExternal(bao);
        return sizePlain = updateSize(sizePlain, bao.getPosition());
    }

    int testPlainBufferedToPreallocatedByteArrayOutput() throws IOException {
        ByteArrayOutput bao = new ByteArrayOutput(sizePlain);
        items.writePlainBuffered(bao);
        return sizePlain = updateSize(sizePlain, bao.getPosition());
    }

    int testPlainExternalToPooledDataOutputStream() throws IOException {
        ByteArrayOutputStream baos = pooledByteArrayOutputStream;
        if (baos == null)
            pooledByteArrayOutputStream = baos = new ByteArrayOutputStream();
        else
            baos.reset();
        items.writePlainExternal(new DataOutputStream(baos));
        return sizePlain = updateSize(sizePlain, baos.size());
    }

    int testPlainExternalToPooledByteArrayOutput() throws IOException {
        ByteArrayOutput bao = pooledByteArrayOutput;
        if (bao == null)
            pooledByteArrayOutput = bao = new ByteArrayOutput();
        else
            bao.clear();
        items.writePlainExternal(bao);
        return sizePlain = updateSize(sizePlain, bao.getPosition());
    }

    int testPlainBufferedToPooledByteArrayOutput() throws IOException {
        ByteArrayOutput bao = pooledByteArrayOutput;
        if (bao == null)
            pooledByteArrayOutput = bao = new ByteArrayOutput();
        else
            bao.clear();
        items.writePlainBuffered(bao);
        return sizePlain = updateSize(sizePlain, bao.getPosition());
    }

    int testPlainExternalToPooledChunkedOutput4k() throws IOException {
        ChunkedOutput co = pooledChunkedOutput4k;
        if (co == null)
            pooledChunkedOutput4k = co = new ChunkedOutput(SIZE_4K);
        else
            co.clear();
        items.writePlainExternal(co);
        return sizePlain = updateSize(sizePlain, co.size());
    }

    int testPlainBufferedToPooledChunkedOutput4k() throws IOException {
        ChunkedOutput co = pooledChunkedOutput4k;
        if (co == null)
            pooledChunkedOutput4k = co = new ChunkedOutput(SIZE_4K);
        else
            co.clear();
        items.writePlainBuffered(co);
        return sizePlain = updateSize(sizePlain, co.size());
    }

    int testPlainExternalToPooledChunkedOutput64k() throws IOException {
        ChunkedOutput co = pooledChunkedOutput64k;
        if (co == null)
            pooledChunkedOutput64k = co = new ChunkedOutput(SIZE_64K);
        else
            co.clear();
        items.writePlainExternal(co);
        return sizePlain = updateSize(sizePlain, co.size());
    }

    int testPlainBufferedToPooledChunkedOutput64k() throws IOException {
        ChunkedOutput co = pooledChunkedOutput64k;
        if (co == null)
            pooledChunkedOutput64k = co = new ChunkedOutput(SIZE_64K);
        else
            co.clear();
        items.writePlainBuffered(co);
        return sizePlain = updateSize(sizePlain, co.size());
    }

    int testPlainExternalToPooledHeapByteBuffer() throws IOException {
        ByteBuffer buf = pooledHeapBuf;
        if (buf == null)
            pooledHeapBuf = buf = ByteBuffer.allocate(sizePlain);
        else
            buf.clear();
        items.writePlainToByteBuffer(buf);
        return sizePlain = updateSize(sizePlain, buf.position());
    }

    int testPlainExternalToPooledDirectByteBuffer() throws IOException {
        ByteBuffer buf = pooledDirectBuf;
        if (buf == null)
            pooledDirectBuf = buf = ByteBuffer.allocateDirect(sizePlain);
        else
            buf.clear();
        items.writePlainToByteBuffer(buf);
        return sizePlain = updateSize(sizePlain, buf.position());
    }

    int testCompactExternalToDataOutputStream() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        items.writeCompactExternal(new DataOutputStream(baos));
        return sizeCompact = updateSize(sizeCompact, baos.size());
    }

    int testCompactExternalToByteArrayOutput() throws IOException {
        ByteArrayOutput bao = new ByteArrayOutput();
        items.writeCompactExternal(bao);
        return sizeCompact = updateSize(sizeCompact, bao.getPosition());
    }

    int testCompactBufferedToByteArrayOutput() throws IOException {
        ByteArrayOutput bao = new ByteArrayOutput();
        items.writeCompactBuffered(bao);
        return sizeCompact = updateSize(sizeCompact, bao.getPosition());
    }

    int testCompactExternalToChunkedOutput4k() throws IOException {
        ChunkedOutput co = new ChunkedOutput(SIZE_4K);
        items.writeCompactExternal(co);
        return sizeCompact = updateSize(sizeCompact, co.size());
    }

    int testCompactBufferedToChunkedOutput4k() throws IOException {
        ChunkedOutput co = new ChunkedOutput(SIZE_4K);
        items.writeCompactBuffered(co);
        return sizeCompact = updateSize(sizeCompact, co.size());
    }

    int testCompactExternalToChunkedOutput64k() throws IOException {
        ChunkedOutput co = new ChunkedOutput(SIZE_64K);
        items.writeCompactExternal(co);
        return sizeCompact = updateSize(sizeCompact, co.size());
    }

    int testCompactBufferedToChunkedOutput64k() throws IOException {
        ChunkedOutput co = new ChunkedOutput(SIZE_64K);
        items.writeCompactBuffered(co);
        return sizeCompact = updateSize(sizeCompact, co.size());
    }

    int testCompactExternalToPreallocatedByteArrayOutput() throws IOException {
        ByteArrayOutput bao = new ByteArrayOutput(sizeCompact + 10);
        items.writeCompactExternal(bao);
        return sizeCompact = updateSize(sizeCompact, bao.getPosition());
    }

    int testCompactBufferedToPreallocatedByteArrayOutput() throws IOException {
        // Note: we need "+10" here due to peculiarities of "writeCompactInt/Long"
        // todo: shall we change BufferedOutput implementation?
        ByteArrayOutput bao = new ByteArrayOutput(sizeCompact + 10);
        items.writeCompactBuffered(bao);
        return sizeCompact = updateSize(sizeCompact, bao.getPosition());
    }

    int testCompactExternalToPooledOutputStream() throws IOException {
        ByteArrayOutputStream baos = pooledByteArrayOutputStream;
        if (baos == null)
            pooledByteArrayOutputStream = baos = new ByteArrayOutputStream();
        else
            baos.reset();
        items.writeCompactExternal(new DataOutputStream(baos));
        return sizeCompact = updateSize(sizeCompact, baos.size());
    }

    int testCompactExternalToPooledByteArrayOutput() throws IOException {
        ByteArrayOutput bao = pooledByteArrayOutput;
        if (bao == null)
            pooledByteArrayOutput = bao = new ByteArrayOutput();
        else
            bao.clear();
        items.writeCompactExternal(bao);
        return sizeCompact = updateSize(sizeCompact, bao.getPosition());
    }

    int testCompactBufferedToPooledByteArrayOutput() throws IOException {
        ByteArrayOutput bao = pooledByteArrayOutput;
        if (bao == null)
            pooledByteArrayOutput = bao = new ByteArrayOutput();
        else
            bao.clear();
        items.writeCompactBuffered(bao);
        return sizeCompact = updateSize(sizeCompact, bao.getPosition());
    }

    int testCompactExternalToPooledChunkedOutput4k() throws IOException {
        ChunkedOutput co = pooledChunkedOutput4k;
        if (co == null)
            pooledChunkedOutput4k = co = new ChunkedOutput(SIZE_4K);
        else
            co.clear();
        items.writeCompactExternal(co);
        return sizeCompact = updateSize(sizeCompact, co.size());
    }

    int testCompactBufferedToPooledChunkedOutput4k() throws IOException {
        ChunkedOutput co = pooledChunkedOutput4k;
        if (co == null)
            pooledChunkedOutput4k = co = new ChunkedOutput(SIZE_4K);
        else
            co.clear();
        items.writeCompactBuffered(co);
        return sizeCompact = updateSize(sizeCompact, co.size());
    }

    int testCompactExternalToPooledChunkedOutput64k() throws IOException {
        ChunkedOutput co = pooledChunkedOutput64k;
        if (co == null)
            pooledChunkedOutput64k = co = new ChunkedOutput(SIZE_64K);
        else
            co.clear();
        items.writeCompactExternal(co);
        return sizeCompact = updateSize(sizeCompact, co.size());
    }

    int testCompactBufferedToPooledChunkedOutput64k() throws IOException {
        ChunkedOutput co = pooledChunkedOutput64k;
        if (co == null)
            pooledChunkedOutput64k = co = new ChunkedOutput(SIZE_64K);
        else
            co.clear();
        items.writeCompactBuffered(co);
        return sizeCompact = updateSize(sizeCompact, co.size());
    }

    int testCompactToPooledHeapByteBuffer() throws IOException {
        ByteBuffer buf = pooledHeapBuf;
        buf.clear();
        items.writeCompactToByteBuffer(buf);
        return sizeCompact = updateSize(sizeCompact, buf.position());
    }

    int testCompactToPooledDirectByteBuffer() throws IOException {
        ByteBuffer buf = pooledDirectBuf;
        buf.clear();
        items.writeCompactToByteBuffer(buf);
        return sizeCompact = updateSize(sizeCompact, buf.position());
    }

    public static void main(String[] args) throws InvocationTargetException, IllegalAccessException {
        int count = Integer.parseInt(args[0]);
        int repeat = Integer.parseInt(args[1]);
        int phases = Integer.parseInt(args[2]);
        new TestWriteSpeed2(count, repeat, phases).go();
    }

    private final OperatingSystemMXBean osManagement;
    private final List<GarbageCollectorMXBean> gcManagement = new ArrayList<GarbageCollectorMXBean>();

    private final int count;
    private final int repeat;
    private final int phases;

    public TestWriteSpeed2(int count, int repeat, int phases) {
        this.count = count;
        this.repeat = repeat;
        this.phases = phases;
        osManagement = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        for (Object bean : ManagementFactory.getGarbageCollectorMXBeans())
            gcManagement.add((GarbageCollectorMXBean) bean);
    }

    static class Times {
        long processCpuTime;
        long collectionTime;
    }

    private void collectTimes(Times times, int multiplier) {
        times.processCpuTime += multiplier * osManagement.getProcessCpuTime();
        for (GarbageCollectorMXBean bean : gcManagement)
            times.collectionTime += multiplier * bean.getCollectionTime();
    }

    private void go() throws InvocationTargetException, IllegalAccessException {
        System.out.printf("Writing %d items %d times in %d phases%n", count, repeat, phases);
        generate();
        Method[] methods = getClass().getDeclaredMethods();
        for (int phase = 1; phase <= phases; phase++) {
            System.out.println("Phase #" + phase + " ------------------------------------");
            for (Method method : methods) {
                if (method.getName().startsWith("test"))
                        runTest(method);
            }
        }
    }

    private void runTest(Method method) throws InvocationTargetException, IllegalAccessException {
        System.gc();
        Times times = new Times();
        collectTimes(times, -1);
        int size = 0;
        for (int i = 0; i < repeat; i++) {
            size = (Integer) method.invoke(this);
        }
        collectTimes(times, 1);
        String name = method.getName();
        double sizeUnit = 1024;
        String unitStr = "K";
        if (size >= 100 * sizeUnit) {
            sizeUnit *= 1024;
            unitStr = "M";
        }
        long time = times.processCpuTime / 1000000;
        double mb = 1024 * 1024;
        System.out.printf(Locale.US, "%8s%7s%50s in %5d ms (%5.2f%% in GC), block of %.2f%s bytes " +
            "(%.2fM bps, %.2fM items per second)%n",
            (name.contains("Compact") ? "COMPACT" : "PLAIN"),
            (name.contains("Pooled") ? "POOLED" : name.contains("Preallocated") ? "PRE" : "ALLOC"),
            name, time, times.collectionTime * 100.0 / time,
            size / sizeUnit, unitStr,
            size * 1000L * repeat / mb / time, count * 1000L * repeat / mb / time);
    }

    private void generate() {
        Random r = new Random(1);
        for (int i = 0; i < count; i++) {
            Item item = new Item();
            item.i1 = nextInt(r);
            item.i2 = nextInt(r);
            item.i3 = nextInt(r);
            item.i4 = nextInt(r);
            item.i5 = nextInt(r);
            item.i6 = nextInt(r);
            StringBuilder sb = new StringBuilder();
            int len = r.nextInt(3) + 3;
            for (int j = 0; j < len; j++) {
                sb.append((char) ('A' + r.nextInt(26)));
            }
            items.map.put(sb.toString(), item);
        }
    }

    private static int nextInt(Random r) {
        return r.nextInt(1 << (r.nextInt(7) * r.nextInt(6))) * (r.nextInt(2) * 2 - 1);
    }

    private static void writeCompactInt(ByteBuffer buf, int v) {
        if (v >= 0) {
            if (v < 0x40) {
                buf.put((byte) v);
            } else if (v < 0x2000) {
                buf.put((byte) (0x80 | v >>> 8));
                buf.put((byte) v);
            } else if (v < 0x100000) {
                buf.put((byte) (0xC0 | v >>> 16));
                buf.put((byte) (v >>> 8));
                buf.put((byte) v);
            } else if (v < 0x08000000) {
                buf.putInt(0xE0000000 | v);
            } else {
                buf.put((byte) 0xF0);
                buf.putInt(v);
            }
        } else {
            if (v >= -0x40) {
                buf.put((byte) (0x7F & v));
            } else if (v >= -0x2000) {
                buf.put((byte) (0xBF & v >>> 8));
                buf.put((byte) v);
            } else if (v >= -0x100000) {
                buf.put((byte) (0xDF & v >>> 16));
                buf.put((byte) (v >>> 8));
                buf.put((byte) v);
            } else if (v >= -0x08000000) {
                buf.putInt(0xEFFFFFFF & v);
            } else {
                buf.put((byte) 0xF7);
                buf.putInt(v);
            }
        }
    }
}
