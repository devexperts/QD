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

import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedOutput;
import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.ByteArrayOutput;
import com.devexperts.io.ChunkPool;
import com.devexperts.io.ChunkedInput;
import com.devexperts.io.ChunkedOutput;
import com.devexperts.io.IOUtil;
import com.devexperts.io.StreamInput;
import com.devexperts.io.StreamOutput;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class IOTest {
    private static abstract class Item {
        Item() {}
        abstract void writeDataOutput(DataOutput out) throws IOException;
        abstract void writeBufferedOutput(BufferedOutput out) throws IOException;
        abstract void readDataInput(DataInput in) throws IOException;
        abstract void readBufferedInput(BufferedInput in) throws IOException;
    }

    private static class PlainInt extends Item {
        final int i;

        PlainInt(int i) {
            this.i = i;
        }

        void writeDataOutput(DataOutput out) throws IOException {
            out.writeInt(i);
        }

        void writeBufferedOutput(BufferedOutput out) throws IOException {
            out.writeInt(i);
        }

        void readDataInput(DataInput in) throws IOException {
            assertEquals(i, in.readInt());
        }

        void readBufferedInput(BufferedInput in) throws IOException {
            assertEquals(i, in.readInt());
        }
    }

    private static class CompactInt extends Item {
        final int i;

        CompactInt(int i) {
            this.i = i;
        }

        void writeDataOutput(DataOutput out) throws IOException {
            IOUtil.writeCompactInt(out, i);
        }

        void writeBufferedOutput(BufferedOutput out) throws IOException {
            out.writeCompactInt(i);
        }

        void readDataInput(DataInput in) throws IOException {
            assertEquals(i, IOUtil.readCompactInt(in));
        }

        void readBufferedInput(BufferedInput in) throws IOException {
            assertEquals(i, in.readCompactInt());
        }
    }

    private static class UTFChar extends Item {
        final int i;

        UTFChar(int i) {
            this.i = i;
        }

        void writeDataOutput(DataOutput out) throws IOException {
            IOUtil.writeUTFChar(out, i);
        }

        void writeBufferedOutput(BufferedOutput out) throws IOException {
            out.writeUTFChar(i);
        }

        void readDataInput(DataInput in) throws IOException {
            assertEquals(i, IOUtil.readUTFChar(in));
        }

        void readBufferedInput(BufferedInput in) throws IOException {
            assertEquals(i, in.readUTFChar());
        }
    }

    private static class PlainLong extends Item {
        final long l;

        PlainLong(long l) {
            this.l = l;
        }

        void writeDataOutput(DataOutput out) throws IOException {
            out.writeLong(l);
        }

        void writeBufferedOutput(BufferedOutput out) throws IOException {
            out.writeLong(l);
        }

        void readDataInput(DataInput in) throws IOException {
            assertEquals(l, in.readLong());
        }

        void readBufferedInput(BufferedInput in) throws IOException {
            assertEquals(l, in.readLong());
        }
    }

    private static class CompactLong extends Item {
        final long l;

        CompactLong(long l) {
            this.l = l;
        }

        void writeDataOutput(DataOutput out) throws IOException {
            IOUtil.writeCompactLong(out, l);
        }

        void writeBufferedOutput(BufferedOutput out) throws IOException {
            out.writeCompactLong(l);
        }

        void readDataInput(DataInput in) throws IOException {
            assertEquals(l, IOUtil.readCompactLong(in));
        }

        void readBufferedInput(BufferedInput in) throws IOException {
            assertEquals(l, in.readCompactLong());
        }
    }

    private static class UTF extends Item {
        final String s;

        UTF(String s) {
            this.s = s;
        }

        void writeDataOutput(DataOutput out) throws IOException {
            out.writeUTF(s);
        }

        void writeBufferedOutput(BufferedOutput out) throws IOException {
            out.writeUTF(s);
        }

        void readDataInput(DataInput in) throws IOException {
            assertEquals(s, in.readUTF());
        }

        void readBufferedInput(BufferedInput in) throws IOException {
            assertEquals(s, in.readUTF());
        }
    }

    private static class UTFString extends Item {
        final String s;

        UTFString(String s) {
            this.s = s;
        }

        void writeDataOutput(DataOutput out) throws IOException {
            IOUtil.writeUTFString(out, s);
        }

        void writeBufferedOutput(BufferedOutput out) throws IOException {
            out.writeUTFString(s);
        }

        void readDataInput(DataInput in) throws IOException {
            assertEquals(s, IOUtil.readUTFString(in));
        }

        void readBufferedInput(BufferedInput in) throws IOException {
            assertEquals(s, in.readUTFString());
        }
    }

    private static class ByteArray extends Item {
        final byte[] b;

        ByteArray(byte[] b) {
            this.b = b;
        }

        void writeDataOutput(DataOutput out) throws IOException {
            IOUtil.writeByteArray(out, b);
        }

        void writeBufferedOutput(BufferedOutput out) throws IOException {
            out.writeByteArray(b);
        }

        void readDataInput(DataInput in) throws IOException {
            assertArrayEquals(b, IOUtil.readByteArray(in));
        }

        void readBufferedInput(BufferedInput in) throws IOException {
            assertArrayEquals(b, in.readByteArray());
        }
    }

    private static class CharArray extends Item {
        final char[] chars;

        CharArray(String s) {
            this.chars = (s == null) ? null : s.toCharArray();
        }

        void writeDataOutput(DataOutput out) throws IOException {
            IOUtil.writeCharArray(out, chars);
        }

        void writeBufferedOutput(BufferedOutput out) throws IOException {
            IOUtil.writeCharArray(out, chars);
        }

        void readDataInput(DataInput in) throws IOException {
            assertArrayEquals(chars, IOUtil.readCharArray(in));
        }

        void readBufferedInput(BufferedInput in) throws IOException {
            assertArrayEquals(chars, IOUtil.readCharArray(in));
        }
    }

    private static class CharArrayString extends Item {
        final String s;

        CharArrayString(String s) {
            this.s = s;
        }

        void writeDataOutput(DataOutput out) throws IOException {
            IOUtil.writeCharArray(out, s);
        }

        void writeBufferedOutput(BufferedOutput out) throws IOException {
            IOUtil.writeCharArray(out, s);
        }

        @SuppressWarnings({"deprecation"})
        void readDataInput(DataInput in) throws IOException {
            assertEquals(s, IOUtil.readCharArrayString(in));
        }

        @SuppressWarnings({"deprecation"})
        void readBufferedInput(BufferedInput in) throws IOException {
            assertEquals(s, IOUtil.readCharArrayString(in));
        }
    }

    private static class ObjectItem extends Item {
        final Object o;

        ObjectItem(Object o) {
            this.o = o;
        }

        void writeDataOutput(DataOutput out) throws IOException {
            IOUtil.writeObject(out, o);
        }

        void writeBufferedOutput(BufferedOutput out) throws IOException {
            out.writeObject(o);
        }

        void readDataInput(DataInput in) throws IOException {
            assertEquals(o, IOUtil.readObject(in));
        }

        void readBufferedInput(BufferedInput in) throws IOException {
            assertEquals(o, in.readObject());
        }
    }

    @Test
    public void testIO() throws IOException {
        List<Item> items = makeItems();
        // test IOUtil/BAOS/BAIS write/read
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutput dout = new DataOutputStream(baos);
        for (Item item : items) {
            item.writeDataOutput(dout);
        }
        byte[] bytes = baos.toByteArray();
        DataInput din = new DataInputStream(new ByteArrayInputStream(bytes));
        for (Item item : items) {
            item.readDataInput(din);
        }
        // test DataOutput/InputStream over BufferedOutput/Input
        ByteArrayOutput bout = new ByteArrayOutput();
        dout = new DataOutputStream(bout);
        for (Item item : items) {
            item.writeDataOutput(dout);
        }
        assertArrayEquals(bytes, bout.toByteArray());
        din = new DataInputStream(new ByteArrayInput(bytes));
        for (Item item : items) {
            item.readDataInput(din);
        }
        // test buffered output/input write/read
        bout = new ByteArrayOutput();
        for (Item item : items) {
            item.writeBufferedOutput(bout);
        }
        assertArrayEquals(bytes, bout.toByteArray());
        ByteArrayInput bin = new ByteArrayInput(bytes);
        for (Item item : items) {
            item.readBufferedInput(bin);
        }
        // test StreamOutput/Input over BAOS/BAIS
        baos = new ByteArrayOutputStream();
        StreamOutput sout = new StreamOutput(baos);
        for (Item item : items) {
            item.writeBufferedOutput(sout);
        }
        sout.flush();
        assertArrayEquals(bytes, baos.toByteArray());
        StreamInput sin = new StreamInput(new ByteArrayInputStream(bytes));
        for (Item item : items) {
            item.readBufferedInput(sin);
        }
        // test ChunkedOutput/Input
        ChunkedOutput cout = new ChunkedOutput(ChunkPool.DEFAULT);
        for (Item item : items) {
            item.writeBufferedOutput(cout);
        }
        ChunkedInput cin = new ChunkedInput();
        cin.addAllToInput(cout.getOutput(this), this);
        cin.mark();
        bout = new ByteArrayOutput();
        assertEquals(bytes.length, cin.readToOutputStream(bout, bytes.length));
        assertFalse(cin.hasAvailable());
        assertArrayEquals(bytes, bout.toByteArray());
        cin.rewind(bytes.length);
        for (Item item : items) {
            item.readBufferedInput(cin);
        }
    }

    private static List<Item> makeItems() {
        Random r = new Random(20081221);
        List<Item> items = new ArrayList<Item>();
        addLong(items, 0);
        addLong(items, Long.MAX_VALUE);
        addLong(items, Long.MIN_VALUE);
        addInt(items, 0);
        addInt(items, Integer.MAX_VALUE);
        addInt(items, Integer.MIN_VALUE);
        items.add(new UTFChar(0));
        items.add(new UTFChar(Character.MAX_CODE_POINT));
        for (int i = 0; i < 10000; i++) {
            int len = r.nextInt(62) + 1;
            long num = ((r.nextLong() & ((1L << len) - 1)) | (1L << len)) * (r.nextInt(2) * 2 - 1);
            addLong(items, num);
            int inum = (int) num;
            if (inum == num) {
                addInt(items, inum);
                if (inum >= 0 && inum <= Character.MAX_CODE_POINT)
                    items.add(new UTFChar(inum));
            }
        }
        addString(items, null);
        addString(items, "");
        for (int i = 0; i < 100; i++) {
            int slen = r.nextInt(100);
            StringBuilder sb = new StringBuilder(slen);
            for (int j = 0; j < slen; j++) {
                int len = r.nextInt(15) + 1;
                char ch = (char) (r.nextInt(1 << len) | (1 << len));
                sb.append(ch);
            }
            String s = sb.toString();
            addString(items, s);
        }
        items.add(new ByteArray(null));
        items.add(new ByteArray(new byte[0]));
        items.add(new ByteArray(new byte[]{1, 0, -1, 111, 127, -128}));
        items.add(new ObjectItem(null));
        items.add(new ObjectItem(123));
        items.add(new ObjectItem("hi there!"));
        items.add(new ObjectItem(Arrays.asList(1, 2, 3, Integer.MAX_VALUE, -1, 0, Integer.MIN_VALUE)));
        Collections.shuffle(items, r);
        return items;
    }

    private static void addString(List<Item> items, String s) {
        if (s != null)
            items.add(new UTF(s));
        items.add(new UTFString(s));
        items.add(new CharArray(s));
        items.add(new CharArrayString(s));
    }

    private static void addInt(List<Item> items, int inum) {
        items.add(new CompactInt(inum));
        items.add(new PlainInt(inum));
    }

    private static void addLong(List<Item> items, long num) {
        items.add(new CompactLong(num));
        items.add(new PlainLong(num));
    }

    private static void assertArrayEquals(byte[] a1, byte[] a2) {
        if (a1 == a2)
            return;
        assertEquals(a1.length, a2.length);
        for (int i = 0; i < a1.length; i++)
            assertEquals(a1[i], a2[i]);
    }

    private static void assertArrayEquals(char[] a1, char[] a2) {
        if (a1 == a2)
            return;
        assertEquals(a1.length, a2.length);
        for (int i = 0; i < a1.length; i++)
            assertEquals(a1[i], a2[i]);
    }
}
