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

import com.devexperts.io.BufferedOutput;
import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.ByteArrayOutput;
import com.devexperts.io.IOUtil;
import com.devexperts.logging.Logging;
import com.devexperts.util.TimeFormat;

import java.io.DataInput;
import java.io.IOException;
import java.util.Comparator;
import java.util.zip.DataFormatException;

/**
 * This internal class is public for implementation purposes only.
 */
public class Block extends Key {
    private static final Logging log = Logging.getLogging(Block.class);

    public static final Comparator<Block> COMPARATOR = new Comparator<Block>() {
        public int compare(Block block1, Block block2) {
            int i = Key.COMPARATOR.compare(block1, block2);
            if (i == 0)
                i = Long.compare(block1.getStartTime(), block2.getStartTime());
            if (i == 0)
                i = Long.compare(block1.getEndTime(), block2.getEndTime());
            return i;
        }
    };

    public static final int COMPRESSION_MASK = 0x07;
    public static final int COMPRESSION_NONE = 0x00;
    public static final int COMPRESSION_DEFLATE = 0x01;

    protected int version;
    protected long startTime;
    protected long endTime;
    protected byte[] body;
    protected int bodyOffset;
    protected int bodyLength;

    public long getVersion() {
        return version;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public boolean containsTime(long time) {
        return startTime <= time && time < endTime;
    }

    public int getBodyLength() {
        return bodyLength;
    }

    public boolean isIdentical(Block block) {
        if (!equals(block) || version != block.version || startTime != block.startTime || endTime != block.endTime || bodyLength != block.bodyLength)
            return false;
        for (int i = 0; i < bodyLength; i++)
            if (body[bodyOffset + i] != block.body[block.bodyOffset + i])
                return false;
        return true;
    }

    public void decompress() {
        if ((version & COMPRESSION_MASK) == COMPRESSION_NONE) {
            if (body.length != bodyLength) {
                byte[] tmp = new byte[bodyLength];
                System.arraycopy(body, bodyOffset, tmp, 0, bodyLength);
                body = tmp;
                bodyOffset = 0;
            }
        } else if ((version & COMPRESSION_MASK) == COMPRESSION_DEFLATE) {
            try {
                ByteArrayOutput out = new ByteArrayOutput(Math.max(bodyLength * 2, 4096));
                ReplayUtil.inflate(body, bodyOffset, bodyLength, out);
                version = version & ~COMPRESSION_MASK | COMPRESSION_NONE;
                body = out.toByteArray();
                bodyOffset = 0;
                bodyLength = body.length;
            } catch (DataFormatException e) {
                log.error("Decompression failed for " + this, e);
            }
        } else
            log.error("Unknown compression method " + (version & COMPRESSION_MASK) + " for " + this);
        // If we are still in "compressed" state - switch to empty body.
        if ((version & COMPRESSION_MASK) != COMPRESSION_NONE) {
            version = version & ~COMPRESSION_MASK | COMPRESSION_NONE;
            body = new byte[0];
            bodyOffset = 0;
            bodyLength = 0;
        }
    }

    public ByteArrayInput getInput() {
        decompress();
        return new ByteArrayInput(body, bodyOffset, bodyLength);
    }

    public void setData(byte[] data, int dataOffset, int dataLength) {
        //todo cleanup code that (de)compresses individual blocks
        version = version & ~COMPRESSION_MASK | COMPRESSION_NONE;
        body = data;
        bodyOffset = dataOffset;
        bodyLength = dataLength;
    }

    public void readBlock(DataInput in) throws IOException {
        long length = IOUtil.readCompactLong(in);
        if (length < -1 || length > Integer.MAX_VALUE)
            throw new IOException("Illegal length.");
        byte[] bytes = new byte[(int) length];
        in.readFully(bytes);
        ByteArrayInput blockInput = new ByteArrayInput(bytes);

        if (blockInput.available() < 6)
            throw new IOException("Insufficient length.");
        version = blockInput.readCompactInt();
        if ((version & ~COMPRESSION_MASK) != 0)
            throw new IOException("Unknown version.");
        symbol = blockInput.readUTFString();
        exchange = (char) blockInput.readUTFChar();
        type = (char) blockInput.readUTFChar();
        startTime = blockInput.readCompactLong() * 1000;
        endTime = startTime + blockInput.readCompactLong() * 1000;
        body = new byte[blockInput.available()];
        blockInput.readFully(body);
        bodyOffset = 0;
        bodyLength = body.length;
    }

    public void writeBlock(ByteArrayOutput out) throws IOException {
        if (symbol == null || body == null)
            throw new NullPointerException();
        long started = startTime / 1000;
        long duration = endTime / 1000 - started;
        int estimateLength = 4 + symbol.length() + IOUtil.getCompactLength(started) + IOUtil.getCompactLength(duration) + bodyLength;
        int blockPosition = out.getPosition();
        out.writeCompactInt(estimateLength);
        int payloadPosition = out.getPosition();
        out.writeCompactInt(version);
        out.writeUTFString(symbol);
        out.writeUTFChar(exchange);
        out.writeUTFChar(type);
        out.writeCompactLong(started);
        out.writeCompactLong(duration);
        out.write(body, bodyOffset, bodyLength);
        int actualLength = out.getPosition() - payloadPosition;
        if (actualLength != estimateLength) {
            int shift = IOUtil.getCompactLength(actualLength) - (payloadPosition - blockPosition);
            log.info("Moving block for " + shift + " bytes; estimate = " + estimateLength + ", actual = " + actualLength); // todo remove logging
            if (shift != 0) {
                out.ensureCapacity(out.getPosition() + shift);
                System.arraycopy(out.getBuffer(), payloadPosition, out.getBuffer(), payloadPosition + shift, actualLength);
            }
            out.setPosition(blockPosition);
            out.writeCompactInt(actualLength);
            out.setPosition(out.getPosition() + actualLength);
        }
    }

    public void writeBlock(BufferedOutput out, ByteArrayOutput header) throws IOException {
        if (symbol == null || body == null)
            throw new NullPointerException();
        long started = startTime / 1000;
        long duration = endTime / 1000 - started;
        header.setPosition(0);
        header.writeCompactInt(version);
        header.writeUTFString(symbol);
        header.writeUTFChar(exchange);
        header.writeUTFChar(type);
        header.writeCompactLong(started);
        header.writeCompactLong(duration);
        out.writeCompactInt(header.getPosition() + bodyLength);
        out.write(header.getBuffer(), 0, header.getPosition());
        out.write(body, bodyOffset, bodyLength);
    }

    public String toString() {
        return super.toString() + "(" + TimeFormat.DEFAULT.format(startTime) + ", " + TimeFormat.DEFAULT.format(endTime) + ")";
    }
}
