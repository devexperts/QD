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

import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.util.SystemProperties;

import java.io.PrintWriter;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

class Symbols {
    private static final int L = SystemProperties.getIntProperty("com.devexperts.qd.tool.analysis.Symbols.L", 10);
    private static final int LEN_COUNT = SystemProperties.getIntProperty("com.devexperts.qd.tool.analysis.Symbols.LenCount", 32);
    private static final int CHAR_COUNT = Util.LAST_ASCII_CHAR - Util.FIRST_ASCII_CHAR + 1;

    private final SymbolCodec codec = QDFactory.getDefaultScheme().getCodec();
    private final SymbolFreq allSymbolFreqs = new SymbolFreq("all ASCII symbols", null);
    private final SymbolFreq nonCodedSymbolFreqs = new SymbolFreq("non-coded ASCII symbols", null);
    private final Map<SymbolCategory, SymbolFreq> symbolFreqsByCategory = new EnumMap<SymbolCategory, SymbolFreq>(SymbolCategory.class);
    private final Set<String> codedSymbols = new HashSet<String>();
    private final Set<String> nonCodedSymbols = new HashSet<String>();

    void countSymbol(SymbolCategory category, int cipher, String symbol, int symbolBytes) {
        if (!Util.isAsciiSymbol(cipher, symbol))
            return;
        if (cipher != 0) {
            symbol = codec.decode(cipher);
            codedSymbols.add(symbol);
        } else {
            nonCodedSymbolFreqs.count(symbol, symbolBytes);
            nonCodedSymbols.add(symbol);
        }
        allSymbolFreqs.count(symbol, symbolBytes);
        SymbolFreq summary = symbolFreqsByCategory.get(category);
        if (summary == null)
            symbolFreqsByCategory.put(category, summary = new SymbolFreq(category));
        summary.count(symbol, symbolBytes);
    }

    public void print(PrintWriter out, long fileSize) {
        allSymbolFreqs.print(out, fileSize);
        allSymbolFreqs.printCiphersStats(out);
        nonCodedSymbolFreqs.print(out, fileSize);
        for (SymbolFreq freq : symbolFreqsByCategory.values())
            freq.print(out, fileSize);
    }

    private static String codeToString(int code, int bitCount) {
        StringBuilder csb = new StringBuilder(bitCount);
        for (int j = bitCount; --j >= 0;)
            csb.append((char) ('0' + ((code >> j) & 1)));
        return csb.toString();
    }

    private class SymbolFreq {
        private final String name;
        private final SymbolCategory category;
        private final int prefixLen;
        private long[] lenWeights = new long[LEN_COUNT];
        private long[] charWeights = new long[CHAR_COUNT];

        private final PrefixCode lenCode = new PrefixCode();
        private final PrefixCode charCode = new PrefixCode();

        private long count;
        private long totalOriginalBytes;

        SymbolFreq(String name, SymbolCategory category) {
            this.name = "Distribution of " + name;
            this.category = category;
            this.prefixLen = category == null ? 0 : category.prefix.length();
        }

        SymbolFreq(SymbolCategory category) {
            this(category.toString() + " ASCII symbols", category);
        }

        void count(String symbol, int symbolBytes) {
            count++;
            int n = symbol.length();
            for (int i = prefixLen; i < n; i++)
                charWeights[symbol.charAt(i) - Util.FIRST_ASCII_CHAR]++;
            n -= prefixLen;
            while (n >= LEN_COUNT) {
                lenWeights[LEN_COUNT - 1]++;
                n -= LEN_COUNT - 1;
            }
            lenWeights[n]++;
            totalOriginalBytes += symbolBytes;
        }

        public void print(PrintWriter out, long fileSize) {
            if (count == 0)
                return;
            Util.printHeader(name, out);
            out.printf(Locale.US, "Analyzed %,d symbols%n", count);
            if (prefixLen > 0)
                out.println("Prefix \"" + category.prefix + "\" is excluded");

            int maxWeightLen = 0;
            for (long weight : lenWeights)
                maxWeightLen = Math.max(maxWeightLen, Util.formatCount(weight).length());
            for (long weight : charWeights)
                maxWeightLen = Math.max(maxWeightLen, Util.formatCount(weight).length());
            String format = "   %3s %," + maxWeightLen + "d %2d %-32s%n";

            lenCode.build(lenWeights, L);
            out.println("Length frequencies and prefix codes");
            for (int i = 0; i < LEN_COUNT; i++) {
                int index = lenCode.getIndex(i);
                int bitCount = lenCode.getBitCount(index);
                out.printf(Locale.US, format,
                    Integer.toString(index),
                    lenWeights[index], bitCount, codeToString(lenCode.getCode(index), bitCount));
            }

            charCode.build(charWeights, L);
            out.printf("Character frequencies and prefix codes (built with up to %d bits for all ASCII chars, but shown for actually seen characters only)%n", L);
            for (int i = 0; i < CHAR_COUNT; i++) {
                int index = charCode.getIndex(i);
                int bitCount = charCode.getBitCount(index);
                if (charWeights[index] > 0)
                    out.printf(Locale.US, format,
                        "'" + (char) (Util.FIRST_ASCII_CHAR + index) + "'",
                        charWeights[index], bitCount, codeToString(charCode.getCode(index), bitCount));
            }

            out.printf(Locale.US, "Total bytes in original file encoding: %,d (%.2f avg bytes per symbol, %.2f%% of file size)%n",
                totalOriginalBytes,
                (double) totalOriginalBytes / count,
                100.0 * totalOriginalBytes / fileSize);

            long totalCodedBits = 0;
            for (int i = 0; i < LEN_COUNT; i++)
                totalCodedBits += lenCode.getBitCount(i) * lenWeights[i];
            for (int i = 0; i < CHAR_COUNT; i++)
                totalCodedBits += charCode.getBitCount(i) * charWeights[i];

            long totalCodedBytes;
            if (this == allSymbolFreqs) {
                totalCodedBytes = (totalCodedBits + 7) / 8;
                out.printf(Locale.US, "Total bytes in optimal (new) encoding: %,d (%.2f avg bytes per symbol, %.2f%% of file size). Saves %,d bytes or %.2f%% of file size%n",
                    totalCodedBytes,
                    (double) totalCodedBytes / count,
                    100.0 * totalCodedBytes / fileSize,
                    totalOriginalBytes - totalCodedBytes,
                    100.0 * (totalOriginalBytes - totalCodedBytes) / fileSize);
            }

            totalCodedBits += 3 * count; // 3 bits per symbol to indicate new encoding
            totalCodedBytes = (totalCodedBits + 7) / 8;
            out.printf(Locale.US, "Total bytes in optimal (+3b) encoding: %,d (%.2f avg bytes per symbol, %.2f%% of file size). Saves %,d bytes or %.2f%% of file size%n",
                totalCodedBytes,
                (double) totalCodedBytes / count,
                100.0 * totalCodedBytes / fileSize,
                totalOriginalBytes - totalCodedBytes,
                100.0 * (totalOriginalBytes - totalCodedBytes) / fileSize);
        }

        void printCiphersStats(PrintWriter out) {
            out.printf(Locale.US, "Original file used ciphers for %,d unique symbols and strings for %,d unique symbols%n",
                codedSymbols.size(), nonCodedSymbols.size());
            int ccc = countCoded(codedSymbols);
            int ccn = countCoded(nonCodedSymbols);
            out.printf(Locale.US, "Of those, new encoding accommodates %,d + %,d = %,d unique symbols in a 32-bit integer " +
                "(minus 5 bits for length)%n",
                ccc, ccn, ccc + ccn);
        }

        private int countCoded(Set<String> symbols) {
            int cc = 0;
            for (String s : symbols) {
                int n = s.length();
                int bc = 0;
                for (int i = 0; i < n; i++)
                    bc += charCode.getBitCount(s.charAt(i) - Util.FIRST_ASCII_CHAR);
                while (n >= LEN_COUNT) {
                    bc += lenCode.getBitCount(LEN_COUNT - 1);
                    n -= LEN_COUNT - 1;
                }
                bc += lenCode.getBitCount(n);
                if (bc <= 32 - 5)
                    cc++;
            }
            return cc;
        }
    }
}
