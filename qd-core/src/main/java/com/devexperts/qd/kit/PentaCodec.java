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
package com.devexperts.qd.kit;

import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedOutput;
import com.devexperts.io.IOUtil;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.qtp.ProtocolOption;

import java.io.IOException;
import java.io.UTFDataFormatException;

/**
 * The <code>PentaCodec</code> performs symbol coding and serialization using
 * extensible 5-bit encoding. The eligible characters are assigned penta codes
 * (either single 5-bit or double 10-bit) according to the following table:
 *
 * <pre>
 * 'A' to 'Z'                 - 5-bit pentas from 1 to 26
 * '.'                        - 5-bit penta 27
 * '/'                        - 5-bit penta 28
 * '$'                        - 5-bit penta 29
 * ''' and '`'                - none (ineligible characters)
 * ' ' to '~' except above    - 10-bit pentas from 960 to 1023
 * all other                  - none (ineligible characters)
 * </pre>
 *
 * The 5-bit penta 0 represents empty space and is eligible only at the start.
 * The 5-bit pentas 30 and 31 are used as a transition mark to switch to 10-bit pentas.
 * The 10-bit pentas from 0 to 959 do not exist as they collide with 5-bit pentas.
 *
 * <p> The individual penta codes for character sequence are packed into 64-bit value
 * from high bits to low bits aligned to the low bits. This allows representation
 * of up to 35-bit penta-coded character sequences. If some symbol contains one or
 * more ineligible characters or does not fit into 35-bit penta, then it is not
 * subject to penta-coding and is left as a string. The resulting penta-coded value
 * can be serialized as defined below or encoded into the 32-bit cipher if possible.
 * Please note that penta code 0 is a valid code as it represents empty character
 * sequence - do not confuse it with cipher value 0, which means 'void' or 'null'.
 *
 * <p> The following table defines used serial format (the first byte is given in bits
 * with 'x' representing payload bit; the remaining bytes are given in bit count):
 *
 * <pre>
 * 0xxxxxxx  8x - for 15-bit pentas
 * 10xxxxxx 24x - for 30-bit pentas
 * 110xxxxx ??? - reserved (payload TBD)
 * 1110xxxx 16x - for 20-bit pentas
 * 11110xxx 32x - for 35-bit pentas
 * 11111000     - for most recently used event flags
 * 11111001 zzz - for new event flags in the following compact int
 * 1111101x ??? - reserved (payload TBD)
 * 11111100 zzz - for UTF-8 string with length (&gt;=0) in bytes
 * 11111101 zzz - for CESU-8 string with length (&gt;=0) in characters
 * 11111110     - for 0-bit penta (empty symbol)
 * 11111111     - for repeat of the last symbol
 * </pre>
 *
 * See <A href="http://www.unicode.org/unicode/reports/tr26/tr26-2.html">CESU-8</A>
 * for format basics and {@link IOUtil#writeUTFString} and {@link IOUtil#writeCharArray}
 * for details of string encoding.
 */
public final class PentaCodec implements SymbolCodec {
    /**
     * The instance of {@code PentaCodec}.
     */
    public static final PentaCodec INSTANCE;

    /**
     * Pentas for ASCII characters. Invalid pentas are set to 0.
     */
    private static final int[] PENTA = new int[128];

    /**
     * Lengths (in bits) of pentas for ASCII characters. Invalid lengths are set to 64.
     */
    private static final int[] PLEN = new int[128];

    /**
     * ASCII characters for pentas. Invalid characters are set to 0.
     */
    private static final char[] CHAR = new char[1024];

    private static final int MRU_EVENT_FLAGS = 1;

    private final int wildcardCipher = encode("*");

    /**
     * Encodes penta into cipher. Shall return 0 if encoding impossible.
     * The specified penta must be valid (no more than 35 bits).
     */
    private static int encodePenta(long penta, int plen) {
        if (plen <= 30)
            return (int) penta + 0x40000000;
        int c = (int) (penta >>> 30);
        if (c == PENTA['/']) // Also checks that plen == 35 (high bits == 0).
            return ((int) penta & 0x3FFFFFFF) + 0x80000000;
        if (c == PENTA['$']) // Also checks that plen == 35 (high bits == 0).
            return ((int) penta & 0x3FFFFFFF) + 0xC0000000;
        return 0;
    }

    /**
     * Decodes cipher into penta code. The specified cipher must not be 0.
     * The returning penta code must be valid (no more than 35 bits).
     *
     * @throws IllegalArgumentException if specified cipher could not be decoded.
     */
    private static long decodeCipher(int cipher) {
        switch (cipher >>> 30) {
        case 0:
            throw new IllegalArgumentException("Unknown or reserved cipher.");
        case 1:
            return cipher & 0x3FFFFFFF;
        case 2:
            return ((long) PENTA['/'] << 30) + (cipher & 0x3FFFFFFF);
        case 3:
            return ((long) PENTA['$'] << 30) + (cipher & 0x3FFFFFFF);
        default:
            throw new InternalError("'int' has more than 32 bits.");
        }
    }

    /**
     * Converts penta into string.
     * The specified penta must be valid (no more than 35 bits).
     */
    private static String pentaToString(long penta) {
        int plen = 0;
        while ((penta >>> plen) != 0)
            plen += 5;
        char[] chars = new char[plen / 5];
        int length = 0;
        while (plen > 0) {
            plen -= 5;
            int code = (int) (penta >>> plen) & 0x1F;
            if (code >= 30 && plen > 0) {
                plen -= 5;
                code = (int) (penta >>> plen) & 0x3FF;
            }
            chars[length++] = CHAR[code];
        }
        return new String(chars, 0, length);
    }

    private int getChartAt(long penta, int i) {
        int plen = 0;
        while ((penta >>> plen) != 0)
            plen += 5;
        while (i >= 0 && plen > 0) {
            plen -= 5;
            int code = (int) (penta >>> plen) & 0x1F;
            if (code >= 30 && plen > 0) {
                plen -= 5;
                code = (int) (penta >>> plen) & 0x3FF;
            }
            if (i == 0)
                return CHAR[code];
            i--;
        }
        return -1;
    }

    // ========== SymbolCodec Implementation ==========

    /**
     * Creates new PentaCodec.
     * @deprecated Use {@link #INSTANCE}.
     */
    public PentaCodec() {}

    @Override
    public int encode(String symbol) {
        if (symbol == null)
            return 0;
        int length = symbol.length();
        if (length > 7)
            return 0;
        long penta = 0;
        int plen = 0;
        for (int i = 0; i < length; i++) {
            int c = symbol.charAt(i);
            if (c >= 128)
                return 0;
            int l = PLEN[c];
            penta = (penta << l) + PENTA[c];
            plen += l;
        }
        if (plen > 35)
            return 0;
        return encodePenta(penta, plen);
    }

    @Override
    public int encode(char[] chars, int offset, int length) {
        if ((offset | length | (offset + length) | (chars.length - (offset + length))) < 0)
            throw new IndexOutOfBoundsException();
        if (length > 7)
            return 0;
        long penta = 0;
        int plen = 0;
        for (length += offset; offset < length; offset++) {
            int c = chars[offset];
            if (c >= 128)
                return 0;
            int l = PLEN[c];
            penta = (penta << l) + PENTA[c];
            plen += l;
        }
        if (plen > 35)
            return 0;
        return encodePenta(penta, plen);
    }

    @Override
    public String decode(int cipher) {
        if (cipher == 0)
            return null;
        return pentaToString(decodeCipher(cipher));
    }

    @Override
    public String decode(int cipher, String symbol) {
        return symbol != null ? symbol : decode(cipher);
    }

    @Override
    public long decodeToLong(int cipher) {
        if (cipher == 0)
            return 0;
        long penta = decodeCipher(cipher);
        int plen = 0;
        while ((penta >>> plen) != 0)
            plen += 5;
        long result = 0;
        int shift = 64;
        while (plen > 0) {
            plen -= 5;
            int code = (int) (penta >>> plen) & 0x1F;
            if (code >= 30 && plen > 0) {
                plen -= 5;
                code = (int) (penta >>> plen) & 0x3FF;
            }
            result = (result << 8) | CHAR[code];
            shift -= 8;
        }
        return result << shift;
    }

    @Override
    public int decodeCharAt(int cipher, int i) {
        if (i < 0)
            throw new IllegalArgumentException("Negative index");
        if (cipher == 0)
            return -1;
        return getChartAt(decodeCipher(cipher), i);
    }

    @Override
    public int hashCode(int cipher) {
        if (cipher == 0)
            return 0;
        long penta = decodeCipher(cipher);
        int plen = 0;
        while ((penta >>> plen) != 0)
            plen += 5;
        int hash = 0;
        while (plen > 0) {
            plen -= 5;
            int code = (int) (penta >>> plen) & 0x1F;
            if (code >= 30 && plen > 0) {
                plen -= 5;
                code = (int) (penta >>> plen) & 0x3FF;
            }
            hash = 31 * hash + CHAR[code];
        }
        return hash;
    }

    /**
     * Returns cipher that is used by the "wildcard" symbol, this implementation returns value that
     * is equal to <code>encode("*")</code>.
     */
    @Override
    public int getWildcardCipher() {
        return wildcardCipher;
    }

    @Override
    public Reader createReader() {
        return new ReaderImpl();
    }

    @Override
    public Writer createWriter() {
        return new WriterImpl();
    }

    private static class ReaderImpl extends Reader {
        private int mruEventFlags = MRU_EVENT_FLAGS;
        private final char[] buffer = new char[64];

        @Override
        public void reset(ProtocolOption.Set optSet) {
            // Current version ignores optSet (can read anything).
            // optSet is reserved for the future, to support backward-incompatible (semantic-changing) extensions
            cipher = 0;
            symbol = null;
            eventFlags = 0;
            mruEventFlags = MRU_EVENT_FLAGS;
        }

        @Override
        public void readSymbol(BufferedInput in, Resolver resolver) throws IOException {
            eventFlags = 0;
            eventFlagsBytes = 0;
            while (true) {
                int i = in.readUnsignedByte();
                long penta;
                if (i < 0x80) { // 15-bit
                    penta = (i << 8) + in.readUnsignedByte();
                } else if (i < 0xC0) { // 30-bit
                    penta = ((i & 0x3F) << 24) + (in.readUnsignedByte() << 16) + in.readUnsignedShort();
                } else if (i < 0xE0) { // reserved (first range)
                    throw new IOException("Reserved bit sequence");
                } else if (i < 0xF0) { // 20-bit
                    penta = ((i & 0x0F) << 16) + in.readUnsignedShort();
                } else if (i < 0xF8) { // 35-bit
                    penta = ((long) (i & 0x07) << 32) + (in.readInt() & 0xFFFFFFFFL);
                } else if (i == 0xF8) { // mru event flags
                    if (eventFlagsBytes > 0)
                        throw new IOException("Duplicated event flags prefix");
                    eventFlags = mruEventFlags;
                    eventFlagsBytes = 1;
                    continue; // read next byte
                } else if (i == 0xF9) { // new event flags
                    if (eventFlagsBytes > 0)
                        throw new IOException("Duplicated event flags prefix");
                    long eventFlagsPosition = in.totalPosition();
                    mruEventFlags = in.readCompactInt();
                    eventFlags = mruEventFlags;
                    eventFlagsBytes = (int) (in.totalPosition() - eventFlagsPosition);
                    continue; // read next byte
                } else if (i < 0xFC) { // reserved (second range)
                    throw new IOException("Reserved bit sequence");
                } else if (i == 0xFC) { // UTF-8
                    readUTF(in, resolver); // NOTE: not actually used on write
                    return;
                } else if (i == 0xFD) { // CESU-8
                    readCESU(in, resolver);
                    return;
                } else if (i == 0xFE) { // 0-bit
                    penta = 0;
                } else { // repeat of the last symbol
                    if (cipher == 0 && symbol == null)
                        throw new IOException("Symbol is undefined");
                    return;
                }
                int plen = 0;
                while ((penta >>> plen) != 0)
                    plen += 5;
                cipher = encodePenta(penta, plen);
                // Note: Generally pentaToString is inefficient (does not use resolver),
                // but this use-case shall not actually happen
                symbol = cipher == 0 ? pentaToString(penta) : null;
                return;
            }
        }

        private void readUTF(BufferedInput in, Resolver resolver) throws IOException {
            long longUtfLength = in.readCompactLong();
            if (longUtfLength < 0 || longUtfLength > Integer.MAX_VALUE)
                throw new IOException("Illegal length");
            int utfLength = (int) longUtfLength;
            char[] chars = utfLength <= buffer.length ? buffer : new char[utfLength];
            resolve(chars, in.readUTFBody(utfLength, chars, 0), resolver);
        }

        private void readCESU(BufferedInput in, Resolver resolver) throws IOException {
            long longLength = in.readCompactLong();
            if (longLength < 0 || longLength > Integer.MAX_VALUE)
                throw new IOException("Illegal length");
            int length = (int) longLength;
            char[] chars = length <= buffer.length ? buffer : new char[length];
            for (int k = 0; k < length; k++) {
                int codePoint = in.readUTFChar();
                if (codePoint > 0xFFFF)
                    throw new UTFDataFormatException("Code point is beyond BMP");
                chars[k] = (char) codePoint;
            }
            resolve(chars, length, resolver);
        }

        private void resolve(char[] chars, int length, Resolver resolver) {
            cipher = 0;
            if (resolver != null) {
                symbol = resolver.getSymbol(chars, 0, length);
                if (symbol != null)
                    return;
            }
            symbol = length == 0 ? "" : new String(chars, 0, length);
        }
    }

    private static class WriterImpl extends Writer {
        private int lastCipher;
        private String lastSymbol;
        private int mruEventFlags = MRU_EVENT_FLAGS;

        @Override
        public void reset(ProtocolOption.Set optSet) {
            // optSet is not currently used. It is reserved for future extensions.
            // It is user's responsibility to narrow down eventFlags according to protocol options.
            lastCipher = 0;
            lastSymbol = null;
            mruEventFlags = MRU_EVENT_FLAGS;
        }

        @Override
        public void writeSymbol(BufferedOutput out, int cipher, String symbol, int eventFlags) throws IOException {
            if (cipher == 0 && symbol == null)
                throw new IllegalArgumentException();
            if (eventFlags != 0) {
                if (eventFlags == mruEventFlags)
                    out.writeByte(0xF8); // mru event flags
                else {
                    out.writeByte(0xF9); // new event flags
                    out.writeCompactInt(eventFlags);
                    mruEventFlags = eventFlags;
                }
            }
            if (cipher == lastCipher && (cipher != 0 || symbol.equals(lastSymbol))) {
                // Symbol is the same as previous one. We write null symbol instead
                out.writeByte(0xFF);
                return;
            }
            if (cipher != 0) {
                long penta = decodeCipher(cipher);
                if (penta == 0) { // 0-bit
                    out.writeByte(0xFE);
                } else if (penta < 0x8000L) { // 15-bit
                    out.writeShort((int) penta);
                } else if (penta < 0x100000L) { // 20-bit
                    out.writeByte(0xE0 | ((int) penta >>> 16));
                    out.writeShort((int) penta);
                } else if (penta < 0x40000000L) { // 30-bit
                    out.writeInt(0x80000000 | (int) penta);
                } else if (penta < 0x0800000000L) { //35-bit
                    out.writeByte(0xF0 | (int) (penta >>> 32));
                    out.writeInt((int) penta);
                } else { // more than 35-bit
                    throw new IOException("Penta has more than 35 bits");
                }
                lastCipher = cipher;
                lastSymbol = null;
            } else { // CESU-8
                out.writeByte(0xFD);
                IOUtil.writeCharArray(out, symbol);
                lastCipher = 0;
                lastSymbol = symbol;
            }
        }
    }

    // ========== Data Initialization ==========

    private static void initPenta(char c, int penta, int plen) {
        PENTA[c] = penta;
        PLEN[c] = plen;
        CHAR[penta] = c;
    }

    static {
        for (int i = PLEN.length; --i >= 0; )
            PLEN[i] = 64;
        for (int i = 'A'; i <= 'Z'; i++)
            initPenta((char) i, i - 'A' + 1, 5);
        initPenta('.', 27, 5);
        initPenta('/', 28, 5);
        initPenta('$', 29, 5);
        int penta = 0x03C0;
        for (int i = 32; i <= 126; i++)
            if (PENTA[i] == 0 && i != '\'' && i != '`')
                initPenta((char) i, penta++, 10);
        if (penta != 0x0400)
            throw new InternalError("Number of pentas is incorrect.");
        INSTANCE = new PentaCodec();
    }
}
