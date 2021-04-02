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
package com.devexperts.qd.tools.analysis;

import com.devexperts.io.IOUtil;
import com.devexperts.qd.SerialFieldType;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

class DataCompression {
    private static final int ZERO = 0;
    private static final int ONE = 1;
    private static final int REPEAT = 2;
    private static final int PLUS_1 = 3;
    private static final int MINUS_1 = 4;
    private static final int DELTA16 = 5;
    private static final int DELTA = 6;
    private static final int PLAIN = 7;

    private static final int N_SPECIAL = 2;
    private static final int N_CASES = 8;

    private static final int MAX_BITS = 3;
    private static final int MAX_CASES = 3;

    private static final CompressionCase[] CASES = new CompressionCase[N_CASES];
    private static final List<CompressionAlgo> VALID_ALGOS = new ArrayList<CompressionAlgo>();

    private final Section<SerialFieldType> recordDataByFieldType =
        new Section<SerialFieldType>("Alternative compressions for record data bytes by field type");
    private final Section<String> recordDataByFieldName =
        new Section<String>("Alternative compressions for record data bytes by field name");
    private final Section<String> recordDataByGroup =
        new Section<String>("Alternative compressions for record data bytes by custom group");

    final Pattern pattern;

    DataCompression(Pattern pattern) {
        this.pattern = pattern;
    }

    public void count(Parser.ReportKeys keys, SerialFieldType type, long value, int bytes) {
        if (keys.analyzeFieldTypeCompression)
            recordDataByFieldType.get(keys.fieldType, type).count(value, bytes);
        if (keys.analyzeFieldNameCompression)
            recordDataByFieldName.get(keys.fieldName, type).count(value, bytes);
        for (String group : keys.analyzeCompressionGroups)
            recordDataByGroup.get(group, type).count(value, bytes);
    }

    public void print(PrintWriter out, long fileSize) {
        recordDataByFieldType.print(out, fileSize);
        recordDataByFieldName.print(out, fileSize);
        recordDataByGroup.print(out, fileSize);
    }

    private static boolean isValidCompressionAlgo(int[] bits) {
        if (bits[PLAIN] < 0 && bits[DELTA] < 0)
            return false;
        int n = 0;
        for (int bc : bits)
            if (bc >= 0)
                n++;
        if  (n == 0 || n > MAX_CASES)
            return false;
        int coins = 0; // fixed point at bit 16
        for (int bc : bits)
            for (int i = 0; i < bc; i++)
                coins += (1 << (15 - i));
        return coins == (n - 1) << 16;
    }

    static {
        // initialize compression cases
        CASES[ZERO] = new ExclusionCase("zero", 0);
        CASES[ONE] = new ExclusionCase("one", 1);
        CASES[REPEAT] = new FixedDeltaCase("repeat", 0);
        CASES[PLUS_1] = new FixedDeltaCase("plus1", 1);
        CASES[MINUS_1] = new FixedDeltaCase("minus1", -1);
        CASES[DELTA16] = new DeltaCase("delta16", 16);
        CASES[DELTA] = new DeltaCase("delta", 1);
        CASES[PLAIN] = new PlainCase();
        // verify special cases
        for (int i = 0; i < N_CASES; i++)
            assert CASES[i].special == (i < N_SPECIAL);
        // initialize valid algos
        int[] bits = new int[N_CASES];
        Arrays.fill(bits, -1);
        while (true) {
            if (isValidCompressionAlgo(bits))
                VALID_ALGOS.add(new CompressionAlgo(bits));
            int k = N_CASES - 1;
            while (k >= 0 && bits[k] == MAX_BITS) {
                bits[k] = -1;
                k--;
            }
            if (k < 0)
                break;
            bits[k]++;
        }
        // report
        System.out.printf("Analyzing %d compression algos.%n", VALID_ALGOS.size());
    }

    private static class Params {
        int plainBitSize;
        int[][] caseBitSize = new int[1 << N_SPECIAL][N_CASES];
        long[] prevValue = new long[1 << N_SPECIAL];

        Params() {}
    }

    private static class Compression {
        final String name;
        private final SerialFieldType type;
        final CompressionAlgoState[] algos = new CompressionAlgoState[VALID_ALGOS.size()];

        long originalBytes;
        final Params params = new Params();

        Compression(String name, SerialFieldType type) {
            this.name = name;
            this.type = type;
            for (int i = 0, n = VALID_ALGOS.size(); i < n; i++)
                algos[i] = new CompressionAlgoState(VALID_ALGOS.get(i));
        }

        void count(long value, int originalBytes) {
            // compute plainBitSize
            if (type.hasSameSerialTypeAs(SerialFieldType.COMPACT_INT))
                params.plainBitSize = 8 * IOUtil.getCompactLength(value);
            else if (type.hasSameSerialTypeAs(SerialFieldType.UTF_CHAR)) {
                int size = 8;
                if (value > 0x007F)
                    size += 8;
                if (value > 0x07FF)
                    size += 8;
                params.plainBitSize = size;
            } else
                throw new IllegalArgumentException("Unsupported type: " + type);
            // compute caseBitSize
            for (int ss = 0; ss < (1 << N_SPECIAL); ss++)
                for (int i = 0; i < N_CASES; i++) {
                    CompressionCase c = CASES[i];
                    long prevValue = params.prevValue[ss];
                    params.caseBitSize[ss][i] = c.matches(value, prevValue) ?
                        c.bitSize(value, prevValue, params) : -1;
                }
            // process algos
            for (CompressionAlgoState algo : algos)
                algo.count(params);
            // update prevValues
            for (int ss = 0; ss < (1 << N_SPECIAL); ss++) {
                boolean update = true;
                for (int i = 0; i < N_SPECIAL; i++)
                    if ((ss & (1 << i)) != 0 && CASES[i].matches(value, params.prevValue[ss])) {
                        update = false;
                        break;
                    }
                if (update)
                    params.prevValue[ss] = value;
            }
            // update original bytes
            this.originalBytes += originalBytes;
        }

        void flushBits() {
            for (CompressionAlgoState algo : algos)
                algo.flushBits();
        }
    }

    private static class CompressionAlgo {
        final int[] caseIndex;
        final int[] caseBits;
        final CompressionCase[] cases;
        final String name;
        final int signature;
        final int specialSignature;

        CompressionAlgo(int[] bits) {
            List<CompressionCase> cl = new ArrayList<CompressionCase>();
            int as = 0;
            caseIndex = new int[N_CASES];
            caseBits = new int[N_CASES];
            for (int j = 0; j <= MAX_BITS; j++)
                for (int i = 0; i < bits.length; i++)
                    if (bits[i] == j) {
                        caseIndex[cl.size()] = i;
                        caseBits[cl.size()] = j;
                        cl.add(CASES[i]);
                        as |= 1 << i;
                    }
            cases = cl.toArray(new CompressionCase[cl.size()]);
            StringBuilder sb = new StringBuilder();
            for (int i = 0, n = cases.length; i < n; i++) {
                CompressionCase c = cases[i];
                if (sb.length() > 0)
                    sb.append(", ");
                sb.append(c);
                if (caseBits[i] > 0)
                    sb.append(' ').append(caseBits[i]);
            }
            name = sb.toString();
            signature = as;
            specialSignature = as & ((1 << N_SPECIAL) - 1);
        }

        int bitSize(Params params) {
            int bestBits = Integer.MAX_VALUE;
            for (int i = 0, n = cases.length; i < n; i++) {
                int bs = params.caseBitSize[specialSignature][caseIndex[i]];
                if (bs >= 0) {
                    int bits = bs + caseBits[i];
                    if (bits < bestBits)
                        bestBits = bits;
                }
            }
            return bestBits;
        }
    }

    private static class CompressionAlgoState {
        final CompressionAlgo algo;

        long bytes;
        long bits;

        CompressionAlgoState(CompressionAlgo algo) {
            this.algo = algo;
        }

        void count(Params params) {
            bits += algo.bitSize(params);
        }

        void flushBits() {
            bytes += (bits + 7) / 8;
            bits = 0;
        }

        @Override
        public String toString() {
            return algo.toString();
        }
    }

    private abstract static class CompressionCase {
        final String caseName;
        final boolean special;

        CompressionCase(String caseName, boolean special) {
            this.caseName = caseName;
            this.special = special;
        }

        boolean matches(long value, long prevValue) {
            return true;
        }

        int bitSize(long value, long prevValue, Params params) {
            return 0;
        }

        @Override
        public String toString() {
            return caseName;
        }
    }

    private static class ExclusionCase extends CompressionCase {
        final long exclusion;

        ExclusionCase(String caseName, long exclusion) {
            super(caseName, true);
            this.exclusion = exclusion;
        }

        @Override
        boolean matches(long value, long prevValue) {
            return value == exclusion;
        }
    }

    private static class FixedDeltaCase extends CompressionCase {
        final long delta;

        FixedDeltaCase(String caseName, long delta) {
            super(caseName, false);
            this.delta = delta;
        }

        @Override
        boolean matches(long value, long prevValue) {
            return value == prevValue + delta;
        }
    }

    private static class DeltaCase extends CompressionCase {
        final long divider;

        DeltaCase(String caseName, long divider) {
            super(caseName, false);
            this.divider = divider;
        }

        @Override
        boolean matches(long value, long prevValue) {
            return (value - prevValue) % divider == 0;
        }


        @Override
        int bitSize(long value, long prevValue, Params params) {
            return 8 * IOUtil.getCompactLength((value - prevValue) / divider);
        }
    }

    private static class PlainCase extends CompressionCase {
        PlainCase() {
            super("plain", false);
        }

        @Override
        int bitSize(long value, long prevValue, Params params) {
            return params.plainBitSize;
        }
    }

    private class Section<K> {
        final String name;
        final Map<K, Compression> compressions = new HashMap<K, Compression>();

        Section(String name) {
            this.name = name;
        }

        Compression get(K key, SerialFieldType type) {
            Compression compression = compressions.get(key);
            if (compression == null)
                compressions.put(key, compression = new Compression(key == null ? "NULL" : key.toString(), type));
            return compression;
        }

        void flushBits() {
            for (Compression compression : compressions.values())
                compression.flushBits();
        }

        void print(PrintWriter out, long fileSize) {
            flushBits();
            List<Compression> cs = new ArrayList<Compression>(compressions.values());
            if (cs.isEmpty())
                return;

            Collections.sort(cs, new Comparator<Compression>() {
                public int compare(Compression o1, Compression o2) {
                    return o1.name.compareTo(o2.name);
                }
            });

            int maxNameLen = 0;
            int maxAlgoNameLen = 0;
            int maxCountLen = 0;
            for (Compression c : cs) {
                maxNameLen = Math.max(maxNameLen, c.name.length());
                for (CompressionAlgoState algo : c.algos) {
                    maxAlgoNameLen = Math.max(maxAlgoNameLen, algo.toString().length());
                    maxCountLen = Math.max(maxCountLen, Util.formatCount(algo.bytes).length());
                }
            }

            Util.printHeader(name, out);
            String format = "%-" + maxNameLen + "s %-" + maxAlgoNameLen + "s %," + maxCountLen + "d %6.2f%%%n";
            for (Compression c : cs) {
                boolean first = true;
                List<CompressionAlgoState> cass = new ArrayList<CompressionAlgoState>(Arrays.asList(c.algos));
                Collections.sort(cass, new Comparator<CompressionAlgoState>() {
                    public int compare(CompressionAlgoState o1, CompressionAlgoState o2) {
                        return o1.bytes < o2.bytes ? -1 : o1.bytes > o2.bytes ? 1 :
                            o1.algo.name.compareTo(o2.algo.name);
                    }
                });

                for (CompressionAlgoState cas : cass) {
                    out.printf(Locale.US, format, first ? c.name : "",
                        cas.algo.name, cas.bytes, cas.bytes * 100.0 / fileSize);
                    first = false;
                }
            }
        }
    }
}
