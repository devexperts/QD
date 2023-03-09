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
package com.dxfeed.ondemand.impl;

import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.ByteArrayOutput;
import com.devexperts.io.IOUtil;
import com.devexperts.util.LockFreePool;
import com.devexperts.util.SystemProperties;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

/**
 * This internal class is public for implementation purposes only.
 */
public class ReplayUtil {

    public static int getCompactLength(byte[] buffer, int position) {//todo remove it - use Content-Length instead
        int n = 1;
        for (byte b = buffer[position]; b < 0; b <<= 1)
            n++;
        return n;
    }

    // ========== Deflating ==========

    private static final int MAX_BUF = SystemProperties.getIntProperty("com.devexperts.mds.replay.ZipBufferSize", 32_768);

    private static final LockFreePool<Deflater> deflaters = new LockFreePool<>("com.devexperts.mds.replay.deflaters", 16);
    private static final LockFreePool<Inflater> inflaters = new LockFreePool<>("com.devexperts.mds.replay.inflaters", 16);
    private static final byte[] emptyInput = new byte[0]; // Empty array used to reset input reference and release old one.
    private static final byte[] dummyByte = new byte[1]; // Single dummy byte needed for inflater algorithm.

    public static void deflate(byte[] bytes, int offset, int length, int level, ByteArrayOutput out) {
        IOUtil.checkRange(bytes, offset, length);
        Deflater deflater = deflaters.poll();
        if (deflater == null)
            deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            deflater.setLevel(level);
            while (!deflater.finished()) {
                out.ensureCapacity(out.getPosition() + MAX_BUF);

                int bytesRead = (int) deflater.getBytesRead();
                if (bytesRead == length) {
                    // Indicate that the input is read completely and allow to complete compression
                    deflater.finish();
                }
                // Set input in chunks limited by MAX_BUF size
                deflater.setInput(bytes, offset + bytesRead, Math.min(length - bytesRead, MAX_BUF));
                // Process data to output in chunks limited by MAX_BUF size
                int n = deflater.deflate(out.getBuffer(), out.getPosition(),
                    Math.min(out.getLimit() - out.getPosition(), MAX_BUF));

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
        IOUtil.checkRange(bytes, offset, length);
        Inflater inflater = inflaters.poll();
        if (inflater == null)
            inflater = new Inflater(true);
        try {
            int dummyByteUsed = 0;
            while (!inflater.finished()) {
                out.ensureCapacity(out.getPosition() + MAX_BUF);

                // Set input in chunks limited by MAX_BUF size
                int len = Math.min(length, MAX_BUF);
                inflater.setInput(bytes, offset, len);
                // Process data to output in chunks limited by MAX_BUF size
                int n = inflater.inflate(out.getBuffer(), out.getPosition(),
                    Math.min(out.getLimit() - out.getPosition(), MAX_BUF));
                len -= inflater.getRemaining();
                offset += len;
                length -= len;

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
            for (int i = 0; i < length; i++) {
                if (decompressed.getBuffer()[i] != bytes[offset + i])
                    return false;
            }
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
        inflate(in.getBuffer(), in.getPosition(), in.getLimit() - in.getPosition() - 8, out);
    }

    // ========== Element (de)serialization ==========

    public static void addGZippedElement(Map<String, ? super ByteArrayOutput> elements, String key,
        ByteArrayOutput value) throws IOException
    {
        if (value.getPosition() > 1000) {
            ByteArrayOutput compressed = new ByteArrayOutput(value.getPosition() + MAX_BUF);
            gzip(value.getBuffer(), 0, value.getPosition(), 1, compressed);
            if (value.getPosition() - compressed.getPosition() > Math.max(value.getPosition() / 20, 500)) {
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
        ByteArrayOutput decompressed = new ByteArrayOutput(compressed.length * 2 + MAX_BUF);
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
        Map<String, byte[]> elements = new LinkedHashMap<>();
        int elementCount = in.readCompactInt();
        for (int i = 0; i < elementCount; i++)
            elements.put(in.readUTFString(), in.readByteArray());
        return elements;
    }
}
