/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

final class SymbolGenerator {
    private static final long GENERATOR_SEED = 1416948710541751L;
    static final int DEFAULT_TOTAL = 100000;
    // Random length generator follows a discrete triangular distribution
    // with values from 1 to 6, with the highest probabilities (25% each) at values 3 and 4.
    static final Function<Random, Integer> DEFAULT_LENGTH_GENERATOR = rnd -> rnd.nextInt(3) + rnd.nextInt(4) + 1;

    static List<String> generateSymbols() {
        return generateSymbols(DEFAULT_TOTAL, DEFAULT_LENGTH_GENERATOR);
    }

    static List<String> generateSymbols(int totalSymbols) {
        return generateSymbols(totalSymbols, DEFAULT_LENGTH_GENERATOR);
    }

    static List<String> generateSymbols(int totalSymbols, int min, int max) {
        return generateSymbols(totalSymbols, rnd -> rnd.nextInt(max - min + 1) + min);
    }

    static List<String> generateSymbols(int totalSymbols, int length) {
        return generateSymbols(totalSymbols, rnd -> length);
    }

    private static List<String> generateSymbols(int totalSymbols, Function<Random, Integer> lengthGenerator) {
        Random rnd = new Random(GENERATOR_SEED);
        Set<String> symbolSet = new HashSet<>(totalSymbols + (totalSymbols >> 1));
        while (symbolSet.size() < totalSymbols) {
            symbolSet.add(generateSymbol(rnd, lengthGenerator.apply(rnd)));
        }
        return Arrays.asList(symbolSet.toArray(new String[totalSymbols]));
    }

    static String generateSymbol(Random r, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (r.nextInt(50) == 0) {
                sb.append((char) ('0' + r.nextInt(10)));
            } else {
                sb.append((char) ('A' + r.nextInt(26)));
            }
        }
        return sb.toString();
    }

    private SymbolGenerator() {
    }
}
