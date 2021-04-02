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
package com.devexperts.qd.qtp.text;

import com.devexperts.io.BufferedInput;

import java.io.IOException;

class StringDecoder {
    private char[] buf = new char[128];

    private void expand(int length) {
        char[] newBuf = new char[Math.max(buf.length * 2, length)];
        System.arraycopy(buf, 0, newBuf, 0, buf.length);
        buf = newBuf;
    }

    /**
     * Converts ascii-encoded &#92;uxxxx to unicode chars
     * and changes special saved chars to their original forms.
     *
     *
     * @param offset an offset to convert string from.
     * @param length a length of string to convert.
     * @return converted string.
     * @throws CorruptedTextFormatException if it is impossible to decode s.
     */
    public String decode(byte[] bytes, int offset, int length) throws CorruptedTextFormatException  {
        if (bytesEquals(bytes, offset, length, "\\NULL"))
            return null;
        if (bytesEquals(bytes, offset, length, "\\0")) // special common case fast path
            return "\0";
        if (length > buf.length)
            expand(length);
        int size = 0;
        int end = offset + length;
        while (offset < end) {
            byte c = bytes[offset++];
            if (c == '\\') {
                // c == '\\'
                offset = decodeEscape(bytes, offset, end, size++);
            } else {
                TextCoding.checkCharRange(c);
                buf[size++] = (char) c;
            }
        }
        return new String(buf, 0, size);
    }

    public String decode(BufferedInput in, int length) throws IOException {
        if (length == 0)
            return "";
        if (length > buf.length)
            expand(length);
        int size = 0;
        boolean possibleNull = false;
        while (length-- > 0) {
            byte c = in.readByte();
            int i = size++;
            if (c == '\\') {
                // c == '\\'
                length = decodeEscape(in, length, i);
                if (i == 0 && buf[i] == 'N') // begins with \N
                    possibleNull = true;
            } else {
                TextCoding.checkCharRange(c);
                buf[i] = (char) c;
            }
        }
        if (possibleNull && size == 4 && buf[1] == 'U' && buf[2] == 'L' && buf[3] == 'L')
            return null;
        return new String(buf, 0, size);
    }

    // this method is separate for performance reasons.It is rarely used and we don't want it to bloat
    // decode method above and to prevent decode method inlining.
    private int decodeEscape(byte[] bytes, int offset, int end, int size) {
        if (offset >= end)
            throw new CorruptedTextFormatException("Character expected after \\");
        byte c = bytes[offset++];
        if (c == 'u')
            return decodeUnicode(bytes, offset, end, size);
        buf[size] = (char) decodeEscapedChar(c);
        return offset;
    }

    private int decodeEscape(BufferedInput in, int length, int size) throws IOException {
        if (length <= 0)
            throw new CorruptedTextFormatException("Character expected after \\");
        byte c = in.readByte();
        if (c == 'u')
            return decodeUnicode(in, length - 1, size);
        buf[size] = (char) decodeEscapedChar(c);
        return length - 1;
    }

    private byte decodeEscapedChar(byte c) {
        switch (c) {
        case '0':
            c = 0;
            break;
        case 't':
            c = '\t';
            break;
        case 'n':
            c = '\n';
            break;
        case 'r':
            c = '\r';
            break;
        case 'f':
            c = '\f';
            break;
        default:
            TextCoding.checkCharRange(c);
        }
        return c;
    }

    private int decodeUnicode(byte[] bytes, int offset, int end, int size) {
        if (offset + 4 > end)
            throw new CorruptedTextFormatException("Four digit hexadecimal number is expected after \\u");
        char code = 0;
        for (int j = 0; j < 4; j++) {
            code <<= 4;
            char c = (char) bytes[offset++];
            if (c >= '0' && c <= '9')
                code += c - '0';
            else if (c >= 'a' && c <= 'f')
                code += c - 'a' + 10;
            else if (c >= 'A' && c <= 'F')
                code += c - 'A' + 10;
            else
                throw new CorruptedTextFormatException("Four digit hexadecimal number is expected after \\u");
        }
        buf[size] = code;
        return offset;
    }

    private int decodeUnicode(BufferedInput in, int length, int size) throws IOException {
        if (length < 4)
            throw new CorruptedTextFormatException("Four digit hexadecimal number is expected after \\u");
        char code = 0;
        for (int j = 0; j < 4; j++) {
            code <<= 4;
            byte c = in.readByte();
            if (c >= '0' && c <= '9')
                code += c - '0';
            else if (c >= 'a' && c <= 'f')
                code += c - 'a' + 10;
            else if (c >= 'A' && c <= 'F')
                code += c - 'A' + 10;
            else
                throw new CorruptedTextFormatException("Four digit hexadecimal number is expected after \\u");
        }
        buf[size] = code;
        return length - 4;
    }

    private static boolean bytesEquals(byte[] bytes, int offset, int length, String s) {
        if (s.length() != length)
            return false;
        for (int i = 0; i < length; i++)
            if (bytes[offset + i] != s.charAt(i))
                return false;
        return true;
    }
}
