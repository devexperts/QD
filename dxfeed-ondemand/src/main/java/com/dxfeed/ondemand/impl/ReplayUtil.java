/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.ondemand.impl;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.*;

import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.ByteArrayOutput;
import com.devexperts.util.LockFreePool;

class ReplayUtil {

    public static int getCompactLength(byte[] buffer, int position) {//todo remove it - use Content-Length instead
        int n = 1;
        for (byte b = buffer[position]; b < 0; b <<= 1)
            n++;
        return n;
    }

    // ========== Deflating ==========

    private static final LockFreePool<Deflater> deflaters = new LockFreePool<Deflater>(16);
    private static final LockFreePool<Inflater> inflaters = new LockFreePool<Inflater>(16);
    private static final byte[] emptyInput = new byte[0]; // Empty array used to reset input reference and release old one.
    private static final byte[] dummyByte = new byte[1]; // Single dummy byte needed for inflater algorithm.

    public static void deflate(byte[] bytes, int offset, int length, int level, ByteArrayOutput out) {
        Deflater deflater = deflaters.poll();
        if (deflater == null)
            deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            deflater.setLevel(level);
            deflater.setInput(bytes, offset, length);
            deflater.finish();
            while (!deflater.finished()) {
                out.ensureCapacity(out.getPosition() + Math.max(1024, Math.min(length - out.getPosition(), 4096)));
                int n = deflater.deflate(out.getBuffer(), out.getPosition(), out.getLimit() - out.getPosition());
                out.setPosition(out.getPosition() + n);
            }
        } finally {
            deflater.reset();
            deflater.setInput(emptyInput); // Reset input reference to release old one.
            if (!deflaters.offer(deflater))
                deflater.end();
        }
    }

    public static void inflate(byte[] bytes, int offset, int length, ByteArrayOutput out) throws DataFormatException {
        Inflater inflater = inflaters.poll();
        if (inflater == null)
            inflater = new Inflater(true);
        try {
            int dummyByteUsed = 0;
            inflater.setInput(bytes, offset, length);
            while (!inflater.finished()) {
                out.ensureCapacity(out.getPosition() + Math.max(1024, Math.min(length - out.getPosition(), 4096)));
                int n = inflater.inflate(out.getBuffer(), out.getPosition(), out.getLimit() - out.getPosition());
                out.setPosition(out.getPosition() + n);
                if (n == 0 && !inflater.finished()) {
                    if (inflater.needsInput())
                        if (dummyByteUsed == 0)
                            inflater.setInput(dummyByte, 0, dummyByteUsed = 1);
                        else
                            throw new DataFormatException("Needs input.");
                    if (inflater.needsDictionary())
                        throw new DataFormatException("Needs dictionary.");
                }
            }
            if (inflater.getRemaining() != dummyByteUsed)
                throw new DataFormatException("Remaining bytes.");
        } finally {
            inflater.reset();
            inflater.setInput(emptyInput); // Reset input reference to release old one.
            if (!inflaters.offer(inflater))
                inflater.end();
        }
    }

    public static int MAX_BUF = 256 * 1024;

    public static void inflateFast(byte[] bytes, int offset, int length, ByteArrayOutput out) throws DataFormatException {
        if ((offset | length | (offset + length) | (bytes.length - (offset + length))) < 0)
            throw new IndexOutOfBoundsException();
        Inflater inflater = inflaters.poll();
        if (inflater == null)
            inflater = new Inflater(true);
        try {
//          long counts = 0;
//          long lens = 0;
//          long remains = 0;
//          long minRemain = 999999;
//          long maxRemain = 0;
            int dummyByteUsed = 0;
            long inRate = 1;
            long outRate = 2;
            while (!inflater.finished()) {
                int capacity = (int) Math.min(length * outRate / inRate * 1.1 + 32768, MAX_BUF);
                out.ensureCapacity(out.getPosition() + capacity);
                capacity = Math.min(capacity, out.getLimit() - out.getPosition());
                int len = (int) Math.min(capacity * inRate / outRate + 1024, length);
                inflater.setInput(bytes, offset, len);
                int n = inflater.inflate(out.getBuffer(), out.getPosition(), capacity);
//              f (bytes == dummyByte)
//                  System.out.println("  Dummy byte inflated to " + n + " bytes");
//              if (len == length)
//                  System.out.println("  Last pass: len " + len + ", remains " + inflater.getRemaining() + ", n " + n + ", capacity " + capacity);
//              else {
//                  counts++;
//                  lens += len;
//                  remains += inflater.getRemaining();
//                  minRemain = Math.min(minRemain, inflater.getRemaining());
//                  maxRemain = Math.max(maxRemain, inflater.getRemaining());
//              }
                len -= inflater.getRemaining();
                offset += len;
                length -= len;
                inRate = inRate / 2 + len + 1;
                outRate = outRate / 2 + n + 1;
                out.setPosition(out.getPosition() + n);
                if (n == 0 && length == 0 && !inflater.finished()) {
                    if (inflater.needsInput())
                        if (dummyByteUsed == 0)
                            inflater.setInput(bytes = dummyByte, offset = 0, length = dummyByteUsed = 1);
                        else
                            throw new DataFormatException("Needs input.");
                    if (inflater.needsDictionary())
                        throw new DataFormatException("Needs dictionary.");
                }
            }
            if (inflater.getRemaining() != dummyByteUsed)
                throw new DataFormatException("Remaining bytes.");
//          System.out.println("  " + counts + " blocks with " +
//              lens + " (" + (lens + counts / 2) / Math.max(counts, 1) + ") lengths and " +
//              remains + " (" + (remains + counts / 2) / Math.max(counts, 1) + ") remains" +
//              " (" + minRemain + " - " + maxRemain + ")");
        } finally {
            inflater.reset();
            inflater.setInput(emptyInput); // Reset input reference to release old one.
            if (!inflaters.offer(inflater))
                inflater.end();
        }
    }

    public static boolean verifyDeflate(byte[] bytes, int offset, int length, ByteArrayOutput compressed) {
        try {
            ByteArrayOutput decompressed = new ByteArrayOutput(length);
            inflate(compressed.getBuffer(), 0, compressed.getPosition(), decompressed);
            if (decompressed.getPosition() != length)
                return false;
            for (int i = 0; i < length; i++)
                if (decompressed.getBuffer()[i] != bytes[offset + i])
                    return false;
        } catch (DataFormatException e) {
            return false;
        }
        return true;
    }

    // ========== GZipping ==========

    public static void gzip(byte[] bytes, int offset, int length, int level, ByteArrayOutput out) throws IOException {
        out.writeShort(0x1f8b); // Write header magic
        out.writeLong(0x0800000000000000L); // Write defaults for other fields
        deflate(bytes, offset, length, level, out);
        CRC32 crc = new CRC32();
        crc.update(bytes, offset, length);
        out.writeInt(Integer.reverseBytes((int) crc.getValue()));
        out.writeInt(Integer.reverseBytes(length));
    }

    public static void gunzip(byte[] bytes, int offset, int length, ByteArrayOutput out) throws IOException, DataFormatException {
        ByteArrayInput in = new ByteArrayInput(bytes, offset, length);
        int startPosition = in.getPosition();
        // Check header magic
        if (in.readShort() != 0x1f8b)
            throw new IOException("Not a GZIP format.");
        // Check compression method
        if (in.readByte() != 8)
            throw new IOException("Unsupported compression method.");
        // Read flags
        int flg = in.readUnsignedByte();
        // Skip MTIME, XFL, and OS fields
        in.readInt();
        in.readUnsignedByte();
        in.readUnsignedByte();
        // Skip optional extra field
        if ((flg & 4) != 0)
            for (int i = 0, n = Short.reverseBytes(in.readShort()) & 0xFFFF; i < n; i++)
                in.readUnsignedByte();
        // Skip optional file name
        if ((flg & 8) != 0)
            while (in.readUnsignedByte() != 0);
        // Skip optional file comment
        if ((flg & 16) != 0)
            while (in.readUnsignedByte() != 0);
        // Check optional header CRC
        if ((flg & 2) != 0) {
            CRC32 crc = new CRC32();
            crc.update(in.getBuffer(), startPosition, in.getPosition());
            if ((Short.reverseBytes(in.readShort()) & 0xFFFF) != (crc.getValue() & 0xFFFF))
                throw new IOException("Corrupt GZIP header.");
        }
        if (in.getLimit() < in.getPosition() + 8)
            throw new IOException("Unexpected end of stream.");
        inflateFast(in.getBuffer(), in.getPosition(), in.getLimit() - in.getPosition() - 8, out);
    }

    // ========== Element (de)serialization ==========

    public static void addGZippedElement(Map<String, ? super ByteArrayOutput> elements, String key, ByteArrayOutput value) throws IOException {
        if (value.getPosition() > 1000) {
            ByteArrayOutput compressed = new ByteArrayOutput(value.getPosition() + 1000);
            gzip(value.getBuffer(), 0, value.getPosition(), 1, compressed);
            if (value.getPosition() - compressed.getPosition() > Math.max(value.getPosition() / 20,  500)) {
                elements.put(key + ".gz", compressed);
                return;
            }
        }
        elements.put(key, value);
    }

    public static ByteArrayInput getGZippedElement(Map<String, byte[]> elements, String key) throws IOException {
        if (elements.containsKey(key))
            return new ByteArrayInput(elements.get(key));
        byte[] compressed = elements.get(key + ".gz");
        if (compressed == null)
            return new ByteArrayInput();
        ByteArrayOutput decompressed = new ByteArrayOutput(compressed.length * 2 + 1000);
        try {
            gunzip(compressed, 0, compressed.length, decompressed);
        } catch (DataFormatException e) {
            throw new ZipException(e.getMessage());
        }
        return new ByteArrayInput(decompressed.getBuffer(), 0, decompressed.getPosition());
    }

    public static ByteArrayOutput writeElements(Map<String, ?> elements) throws IOException {
        ByteArrayOutput body = new ByteArrayOutput();
        body.writeCompactInt(2); // Version
        body.writeCompactInt(elements.size());
        for (Map.Entry<String, ?> element : elements.entrySet()) {
            body.writeUTFString(element.getKey());
            Object value = element.getValue();
            if (value instanceof ByteArrayOutput) {
                ByteArrayOutput bao = (ByteArrayOutput) value;
                body.writeCompactInt(bao.getPosition());
                body.write(bao.getBuffer(), 0, bao.getPosition());
            } else if (value instanceof byte[] || value == null)
                body.writeByteArray((byte[]) value);
            else
                body.writeUTFString(value.toString());
        }
        ByteArrayOutput out = new ByteArrayOutput(5 + body.getPosition());
        out.writeCompactInt(body.getPosition());
        out.write(body.getBuffer(), 0, body.getPosition());
        return out;
    }

    public static Map<String, byte[]> readElements(ByteArrayInput in) throws IOException {
        long length = in.readCompactLong();
        if (length < -1 || length > Integer.MAX_VALUE)
            throw new IOException("Illegal length.");
        if (length < 2)
            throw new IOException("Insufficient length.");
        long version = in.readCompactLong();
        if (version != 2)
            throw new IOException("Unrecognized version: " + version);
        Map<String, byte[]> elements = new LinkedHashMap<String, byte[]>();
        int elementCount = in.readCompactInt();
        for (int i = 0; i < elementCount; i++)
            elements.put(in.readUTFString(), in.readByteArray());
        return elements;
    }
}
