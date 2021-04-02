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
package com.devexperts.util;

import java.util.Arrays;

/**
 * Utility class for Base64 encoding and decoding.
 * Supports configuration with arbitrary alphabet.
 * Instances for common alphabets (as specified by RFC 2045 and RFC 4648)
 * are provided via static fields {@link #DEFAULT} and {@link #URLSAFE}.
 */
public final class Base64 {
    public static final Base64 DEFAULT = new Base64("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=");
    public static final Base64 URLSAFE = new Base64("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_=");

    // Unpadded versions do not use pad character, they correctly decode output of both padded and unpadded versions.
    public static final Base64 DEFAULT_UNPADDED = new Base64(DEFAULT.getAlphabet().substring(0, 64));
    public static final Base64 URLSAFE_UNPADDED = new Base64(URLSAFE.getAlphabet().substring(0, 64));

    private final String alphabet;
    private final char[] ca;
    private final int[] ia;
    private final int pad;

    /**
     * Creates Base64 coder with a given alphabet.
     * @param alphabet an alphabet of 64 characters.
     */
    public Base64(String alphabet) {
        if (alphabet.length() != 64 && alphabet.length() != 65)
            throw new IllegalArgumentException("Alphabet should have 64 characters plus optional pad character.");
        this.alphabet = alphabet;
        ca = alphabet.toCharArray();
        ia = new int[256];
        Arrays.fill(ia, -1);
        for (int i = 0; i < 64; i++) {
            if (ia[ca[i]] != -1)
                throw new IllegalArgumentException("Alphabet contains duplicate character '" + ca[i] + "'.");
            ia[ca[i]] = i;
        }
        pad = alphabet.length() == 64 ? -1 : alphabet.charAt(64);
        if (pad >= 0 && ia[pad] != -1)
            throw new IllegalArgumentException("Alphabet contains pad character '" + (char) pad + "'.");
    }

    /**
     * Returns Bas64 alphabet that is used by this coder.
     * @return Bas64 alphabet that is used by this coder.
     */
    public String getAlphabet() {
        return alphabet;
    }

    /**
     * Encodes source bytes.
     * @param source source bytes.
     * @return encoded string.
     */
    public String encode(byte[] source) {
        if (source == null)
            return "";
        int sourceLength = source.length;
        int resultLength = pad >= 0 ? (sourceLength + 2) / 3 * 4 : (sourceLength * 4 + 2) / 3;
        char[] result = new char[resultLength];
        for (int si = 0, ri = 0; si < sourceLength; si += 3) {
            int val = (source[si] & 0xFF) << 16;
            if (si + 1 < sourceLength)
                val |= (source[si + 1] & 0xFF) << 8;
            if (si + 2 < sourceLength)
                val |= source[si + 2] & 0xFF;
            result[ri++] = ca[(val >> 18) & 0x3F];
            result[ri++] = ca[(val >> 12) & 0x3F];
            if (ri < resultLength)
                result[ri++] = si + 1 < sourceLength ? ca[(val >> 6) & 0x3F] : (char) pad;
            if (ri < resultLength)
                result[ri++] = si + 2 < sourceLength ? ca[val & 0x3F] : (char) pad;
        }
        return new String(result);
    }

    /**
     * Decodes source string.
     * @param source source string.
     * @return decoded bytes.
     * @throws InvalidFormatException if the string do not have the correct Base64 format.
     */
    public byte[] decode(String source) {
        if (source == null)
            return new byte[0];
        int sourceLength = source.length();
        int validCount = 0;
        int padCount = 0;
        for (int i = 0; i < sourceLength; i++) {
            char c = source.charAt(i);
            if (ia[c] >= 0) {
                validCount++;
                if (padCount > 0)
                    throw new InvalidFormatException("Encoded text has pad character in the middle.");
            } else if (c == pad)
                padCount++;
        }
        if (validCount % 4 == 1)
            throw new InvalidFormatException("Encoded text has incomplete character.");
        if (pad >= 0 && (validCount + padCount) % 4 != 0)
            throw new InvalidFormatException("Encoded text length is not multiple of 4.");
        int resultLength = validCount * 3 / 4;
        byte[] result = new byte[resultLength];
        for (int si = 0, ri = 0; ri < resultLength;) {
            int val = 0;
            for (int shift = 24; shift > 0 && si < sourceLength;) {
                int index = ia[source.charAt(si++)];
                if (index >= 0)
                    val |= index << (shift -= 6);
            }
            result[ri++] = (byte) (val >> 16);
            if (ri < resultLength)
                result[ri++] = (byte) (val >> 8);
            if (ri < resultLength)
                result[ri++] = (byte) val;
        }
        return result;
    }

    /**
     * Returns a string representation of the object.
     * @return a string representation of the object.
     */
    public String toString() {
        return "Base64[" + alphabet + "]";
    }
}
