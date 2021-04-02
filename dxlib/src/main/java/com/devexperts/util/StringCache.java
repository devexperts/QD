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

/**
 * Caches strings and provides lookup methods by raw character data to avoid string construction.
 * It is intended to be used in various parsers to reduce memory footprint and garbage.
 * <p>
 * The <tt>StringCache</tt> is a <b>N-way set associative cache</b> which distributes all strings
 * into buckets based on hash function and then uses LRU algorithm within each bucket.
 * The <tt>StringCache</tt> is a thread-safe, asynchronous, wait-free data structure.
 */
public class StringCache {

    private final int bucketNumber;
    private final int bucketSize;
    private final String[] cache;

    private long requestCount; // counts requests except null and empty strings
    private long compareCount; // counts compares except first checks (i.e. add requestCount to get total)
    private long missCount; // counts misses when new string was placed into cache

    /**
     * Creates a <tt>StringCache</tt> with default parameters.
     */
    public StringCache() {
        this(997, 4);
    }

    /**
     * Creates a <tt>StringCache</tt> with the specified number of elements and default bucket size.
     *
     * @param size the number of elements
     * @throws IllegalArgumentException if size is not positive or result in too large cache
     */
    @SuppressWarnings("UnusedDeclaration")
    public StringCache(int size) {
        this((size + 3) / 4, 4);
    }

    /**
     * Creates a <tt>StringCache</tt> with the specified number of buckets and their size.
     * <p>
     * <b>NOTE:</b> cache uses linear search within each bucket, so do not use very large bucket sizes.
     *
     * @param bucketNumber the number of buckets
     * @param bucketSize the size of each bucket
     * @throws IllegalArgumentException if parameters are not positive or result in too large cache
     */
    public StringCache(int bucketNumber, int bucketSize) {
        if (bucketNumber <= 0 || bucketSize <= 0 || bucketSize >= Integer.MAX_VALUE / 2 / bucketNumber)
            throw new IllegalArgumentException();
        this.bucketNumber = bucketNumber;
        this.bucketSize = bucketSize;
        cache = new String[bucketNumber * bucketSize];
    }

    /**
     * Returns string from the cache that matches specified string.
     */
    public String get(String s) {
        return get(s, false);
    }

    /**
     * Returns string from the cache that matches specified string.
     * If <tt>copy</tt> parameter is <tt>true</tt> then specified string will be copied before being put to cache.
     */
    public String get(String s, boolean copy) {
        if (s == null)
            return null;
        if (s.isEmpty())
            return "";
        requestCount++;
        int hash = s.hashCode();
        int n = Math.abs(hash % bucketNumber) * bucketSize;
        String cached = cache[n];
        if (eq(cached, s, hash))
            return cached;
        for (int k = 1; k < bucketSize; k++)
            if (eq(cached = cache[n + k], s, hash))
                return finish(cached, n, k);
        missCount++;
        //noinspection RedundantStringConstructorCall
        return finish(copy ? new String(s) : s, n, bucketSize - 1);
    }

    /**
     * Returns string from the cache that matches specified character sequence.
     */
    public String get(String s, int offset, int length) {
        if (length == 0)
            return "";
        if (offset == 0 && length == s.length())
            return get(s, false);
        requestCount++;
        int hash = 0;
        for (int i = 0; i < length; i++)
            hash = 31 * hash + s.charAt(offset + i);
        int n = Math.abs(hash % bucketNumber) * bucketSize;
        String cached = cache[n];
        if (eq(cached, s, offset, length, hash))
            return cached;
        for (int k = 1; k < bucketSize; k++)
            if (eq(cached = cache[n + k], s, offset, length, hash))
                return finish(cached, n, k);
        missCount++;
        return finish(s.substring(offset, offset + length), n, bucketSize - 1);
    }

    /**
     * Returns string from the cache that matches specified character sequence.
     */
    public String get(CharSequence cs) {
        if (cs instanceof String)
            return get((String) cs, false);
        if (cs == null)
            return null;
        int length = cs.length();
        if (length == 0)
            return "";
        requestCount++;
        int hash = 0;
        for (int i = 0; i < length; i++)
            hash = 31 * hash + cs.charAt(i);
        int n = Math.abs(hash % bucketNumber) * bucketSize;
        String cached = cache[n];
        if (eq(cached, cs, hash))
            return cached;
        for (int k = 1; k < bucketSize; k++)
            if (eq(cached = cache[n + k], cs, hash))
                return finish(cached, n, k);
        missCount++;
        return finish(cs.toString(), n, bucketSize - 1);
    }

    /**
     * Returns string from the cache that matches specified character sequence.
     */
    public String get(CharSequence cs, int offset, int length) {
        if (length == 0)
            return "";
        if (offset == 0 && length == cs.length())
            return get(cs);
        requestCount++;
        int hash = 0;
        for (int i = 0; i < length; i++)
            hash = 31 * hash + cs.charAt(offset + i);
        int n = Math.abs(hash % bucketNumber) * bucketSize;
        String cached = cache[n];
        if (eq(cached, cs, offset, length, hash))
            return cached;
        for (int k = 1; k < bucketSize; k++)
            if (eq(cached = cache[n + k], cs, offset, length, hash))
                return finish(cached, n, k);
        missCount++;
        return finish(cs.subSequence(offset, offset + length).toString(), n, bucketSize - 1);
    }

    /**
     * Returns string from the cache that matches specified character sequence.
     */
    public String get(char[] c) {
        if (c == null)
            return null;
        return get(c, 0, c.length);
    }

    /**
     * Returns string from the cache that matches specified character sequence.
     */
    public String get(char[] c, int offset, int length) {
        if (length == 0)
            return "";
        requestCount++;
        int hash = 0;
        for (int i = 0; i < length; i++)
            hash = 31 * hash + c[offset + i];
        int n = Math.abs(hash % bucketNumber) * bucketSize;
        String cached = cache[n];
        if (eq(cached, c, offset, length, hash))
            return cached;
        for (int k = 1; k < bucketSize; k++)
            if (eq(cached = cache[n + k], c, offset, length, hash))
                return finish(cached, n, k);
        missCount++;
        return finish(new String(c, offset, length), n, bucketSize - 1);
    }

    /**
     * Returns string from the cache that matches specified character sequence.
     * This method uses only 7 lowest bits (ASCII range) of each character ignoring higher bits.
     */
    public String getASCII(byte[] b) {
        if (b == null)
            return null;
        return getASCII(b, 0, b.length);
    }

    /**
     * Returns string from the cache that matches specified character sequence.
     * This method uses only 7 lowest bits (ASCII range) of each character ignoring higher bits.
     */
    public String getASCII(byte[] b, int offset, int length) {
        if (length == 0)
            return "";
        requestCount++;
        int hash = 0;
        for (int i = 0; i < length; i++)
            hash = 31 * hash + (b[offset + i] & 0x7F);
        int n = Math.abs(hash % bucketNumber) * bucketSize;
        String cached = cache[n];
        if (eqASCII(cached, b, offset, length, hash))
            return cached;
        for (int k = 1; k < bucketSize; k++)
            if (eqASCII(cached = cache[n + k], b, offset, length, hash))
                return finish(cached, n, k);
        missCount++;
        char[] c = new char[length];
        for (int i = 0; i < length; i++)
            c[i] = (char) (b[offset + i] & 0x7F);
        return finish(new String(c), n, bucketSize - 1);
    }

    /**
     * Returns string from the cache that matches specified character sequence.
     * This method uses string encoding technique defined in {@code ShortString} class.
     */
    public String getShortString(long code) {
        if (code == 0)
            return null;
        requestCount++;
        long reverse = 0; // normalized code in reverse order with zero bytes removed
        int length = 0;
        do {
            byte c = (byte) code;
            if (c != 0) {
                reverse = (reverse << 8) | (c & 0xFF);
                length++;
            }
        } while ((code >>>= 8) != 0);
        int hash = 0;
        for (int i = 0; i < length; i++)
            hash = 31 * hash + ((int) (reverse >>> (i << 3)) & 0xFF);
        int n = Math.abs(hash % bucketNumber) * bucketSize;
        String cached = cache[n];
        if (eqShortString(cached, reverse, length, hash))
            return cached;
        for (int k = 1; k < bucketSize; k++)
            if (eqShortString(cached = cache[n + k], reverse, length, hash))
                return finish(cached, n, k);
        missCount++;
        char[] c = new char[length];
        for (int i = 0; i < length; i++)
            c[i] = (char) ((int) (reverse >>> (i << 3)) & 0xFF);
        return finish(new String(c, 0, length), n, bucketSize - 1);
    }

    public String toString() {
        long rc = requestCount;
        return "StringCache(" + bucketNumber + "x" + bucketSize + ", " + rc + " requests, " +
            Math.max(rc - missCount, 0) * 10000 / Math.max(rc, 1) / 100.0 + "% hits, " +
            (rc + compareCount) * 100 / Math.max(rc, 1) + "% compares)";
    }

    private String finish(String cached, int n, int k) {
        compareCount += k;
        while (k > 0)
            cache[n + k] = cache[n + --k];
        return cache[n] = cached;
    }

    private static boolean eq(String cached, String s, int hash) {
        if (cached == null || cached.hashCode() != hash)
            return false;
        return cached.equals(s);
    }

    private static boolean eq(String cached, String s, int offset, int length, int hash) {
        if (cached == null || cached.hashCode() != hash || cached.length() != length)
            return false;
        return cached.regionMatches(0, s, offset, length);
    }

    private static boolean eq(String cached, CharSequence cs, int hash) {
        if (cached == null || cached.hashCode() != hash)
            return false;
        return cached.contentEquals(cs);
    }

    private static boolean eq(String cached, CharSequence cs, int offset, int length, int hash) {
        if (cached == null || cached.hashCode() != hash || cached.length() != length)
            return false;
        for (int i = 0; i < length; i++)
            if (cached.charAt(i) != cs.charAt(offset + i))
                return false;
        return true;
    }

    private static boolean eq(String cached, char[] c, int offset, int length, int hash) {
        if (cached == null || cached.hashCode() != hash || cached.length() != length)
            return false;
        for (int i = 0; i < length; i++)
            if (cached.charAt(i) != c[offset + i])
                return false;
        return true;
    }

    private static boolean eqASCII(String cached, byte[] b, int offset, int length, int hash) {
        if (cached == null || cached.hashCode() != hash || cached.length() != length)
            return false;
        for (int i = 0; i < length; i++)
            if (cached.charAt(i) != (b[offset + i] & 0x7F))
                return false;
        return true;
    }

    private static boolean eqShortString(String cached, long reverse, int length, int hash) {
        if (cached == null || cached.hashCode() != hash || cached.length() != length)
            return false;
        for (int i = 0; i < length; i++)
            if (cached.charAt(i) != ((int) (reverse >>> (i << 3)) & 0xFF))
                return false;
        return true;
    }
}
