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
package com.devexperts.qd.tools;

import com.devexperts.logging.Logging;
import com.devexperts.mars.common.MARSNode;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.util.SymbolObjectMap;
import com.devexperts.qd.util.SymbolObjectVisitor;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Locale;

final class Comparer implements Runnable {

    private static final Logging log = Logging.getLogging(Comparer.class);

    private final SymbolObjectMap<CompareBuffer>[] buffers1;
    private final SymbolObjectMap<CompareBuffer>[] buffers2;
    private final String alias1;
    private final String alias2;
    private final DataScheme scheme;
    private final int[] remap;
    private final SymbolObjectMap<ComparisonData>[] recordDataMaps;
    private final CompareBufferReviser compareBufferReviser1;
    private final CompareBufferReviser compareBufferReviser2;

    private final MARSNode rootMarsNode;
    private final ComparisonResult globalResult;
    private final ComparisonResult[] recordResult;

    static final NumberFormat percentageFormat;
    static {
        percentageFormat = DecimalFormat.getNumberInstance(Locale.US);
        percentageFormat.setMaximumFractionDigits(2);
    }

    private static int unnamedInstancesNumber = 0;

    Comparer(DataScheme scheme, SymbolObjectMap<CompareBuffer>[] buffers1, SymbolObjectMap<CompareBuffer>[] buffers2, String alias1, String alias2, int[] remap) {
        this.buffers1 = buffers1;
        this.buffers2 = buffers2;
        this.remap = remap;
        this.scheme = scheme;
        this.alias1 = alias1;
        this.alias2 = alias2;

        int n = scheme.getRecordCount();
        //noinspection unchecked
        recordDataMaps = new SymbolObjectMap[n];
        recordResult = new ComparisonResult[n];

        if (alias1.equals("1") && (alias2.equals("2"))) {
            String rootName = "compare" + ((unnamedInstancesNumber == 0) ? "" : Integer.toString(unnamedInstancesNumber));
            rootMarsNode = MARSNode.getRoot().subNode(rootName, "Statistics about data received from two different sources");
            unnamedInstancesNumber++;
        } else {
            rootMarsNode = MARSNode.getRoot().subNode(alias1 + "/" + alias2, "Comparison statistics for " + alias1 + " and " + alias2);
        }
        globalResult = new ComparisonResult(rootMarsNode);

        compareBufferReviser1 = new CompareBufferReviser(scheme, globalResult) {
            @Override
            protected void updateBuffer(ComparisonData data, CompareBuffer compareBuffer) {
                data.buf1 = compareBuffer;
            }
        };
        compareBufferReviser2 = new CompareBufferReviser(scheme, globalResult) {
            @Override
            protected void updateBuffer(ComparisonData data, CompareBuffer compareBuffer) {
                data.buf2 = compareBuffer;
            }
        };
    }

    SymbolObjectVisitor<ComparisonData> comparisonDataProcessor = new SymbolObjectVisitor<ComparisonData>() {
        @Override
        public boolean hasCapacity() {
            return true;
        }

        @Override
        public void visitEntry(int cipher, String symbol, ComparisonData data) {
            data.process();
        }
    };

    @Override
    public void run() {
        globalResult.clear();
        int n = scheme.getRecordCount();
        for (int i = 0; i < n; i++) {
            SymbolObjectMap<CompareBuffer> map1 = buffers1[i];
            SymbolObjectMap<CompareBuffer> map2 = remap == null ? buffers2[i] :
                remap[i] >= 0 ? buffers2[remap[i]] : null;

            processCompareBufferMap(map1, i, compareBufferReviser1);
            processCompareBufferMap(map2, i, compareBufferReviser2);

            if (recordDataMaps[i] != null) {
                recordResult[i].clear();
                recordDataMaps[i].examineEntries(comparisonDataProcessor);
                recordResult[i].ready();
            }
        }
        globalResult.ready();
        globalResult.logToConsole();
    }

    private void processCompareBufferMap(SymbolObjectMap<CompareBuffer> compareBufferMap, int recordIndex, CompareBufferReviser compareBufferReviser) {
        if (compareBufferMap != null) {
            if (recordDataMaps[recordIndex] == null) {
                recordDataMaps[recordIndex] = SymbolObjectMap.createInstance();
                recordResult[recordIndex] = new ComparisonResult(rootMarsNode.subNode(scheme.getRecord(recordIndex).getName()));
            }
            compareBufferReviser.setCurrentRecordStuff(recordDataMaps[recordIndex], recordResult[recordIndex]);
            compareBufferMap.examineEntries(compareBufferReviser);
        }

    }

    private abstract class CompareBufferReviser implements SymbolObjectVisitor<CompareBuffer> {
        private final SymbolCodec schemeCodec;
        private final ComparisonResult globalResult;
        private ComparisonResult recordResult;
        private SymbolObjectMap<ComparisonData> dataMap = null;

        CompareBufferReviser(DataScheme scheme, ComparisonResult globalResult) {
            this.schemeCodec = scheme.getCodec();
            this.globalResult = globalResult;
        }

        @Override
        public boolean hasCapacity() {
            return true;
        }

        public void setCurrentRecordStuff(SymbolObjectMap<ComparisonData> recordComparisonData, ComparisonResult recordResult) {
            this.dataMap = recordComparisonData;
            this.recordResult = recordResult;
        }

        @Override
        public void visitEntry(int cipher, String symbol, CompareBuffer compareBuffer) {
            ComparisonData data = dataMap.get(cipher, symbol);
            if (data == null) {
                dataMap.put(cipher, symbol, data = new ComparisonData(recordResult, globalResult, schemeCodec.decode(cipher, symbol)));
            }
            updateBuffer(data, compareBuffer);
        }

        protected abstract void updateBuffer(ComparisonData data, CompareBuffer compareBuffer);
    }

    private class ComparisonResult {
        long matched;
        long received1;
        long received2;
        long unmatchedLeft1;
        long unmatchedLeft2;
        long totalDelayDelta;
        double droppedPercentage1;
        double droppedPercentage2;

        final MARSNode rootNode;

        ComparisonResult(MARSNode marsNode) {
            clear();
            this.rootNode = marsNode;
        }

        public MARSNode getMarsNode() {
            return rootNode;
        }

        /**
         * Resets all the values to 0
         */
        void clear() {
            matched = 0;
            received1 = received2 = 0;
            unmatchedLeft1 = unmatchedLeft2 = 0;
            totalDelayDelta = 0;
        }

        /**
         * Calculates dropped percentage and average delays,
         * updates information in corresponding MARS node.
         */
        void ready() {
            MARSNode matchedNode = rootNode.subNode("matched", "number matched records");
            matchedNode.setValue(Long.toString(matched));

            MARSNode delay1Node = rootNode.subNode("delay_" + alias1, "average delay of the first connection data in comparison with the second (in milliseconds)");
            MARSNode delay2Node = rootNode.subNode("delay_" + alias2, "average delay of the second connection data in comparison with the first (in milliseconds)");
            if (matched == 0) {
                delay1Node.setValue("N/A");
                delay2Node.setValue("N/A");
            } else {
                long averageDelayDelta = totalDelayDelta / matched;
                delay1Node.setValue(Long.toString(averageDelayDelta > 0 ? averageDelayDelta : 0));
                delay2Node.setValue(Long.toString(averageDelayDelta < 0 ? -averageDelayDelta : 0));
            }

            long totalReceived = received1 + received2 - matched;
            if (totalReceived == 0) {
                droppedPercentage1 = droppedPercentage2 = Double.NaN;
            } else {
                droppedPercentage1 = (totalReceived - received1) * 100.0d / totalReceived;
                droppedPercentage2 = (totalReceived - received2) * 100.0d / totalReceived;
            }
            MARSNode dropped1Node = rootNode.subNode("dropped_" + alias1, "percentage of dropped data in the first connection");
            MARSNode dropped2Node = rootNode.subNode("dropped_" + alias2, "percentage of dropped data in the second connection");
            dropped1Node.setValue(Double.isNaN(droppedPercentage1) ? "N/A" : percentageFormat.format(droppedPercentage1));
            dropped2Node.setValue(Double.isNaN(droppedPercentage2) ? "N/A" : percentageFormat.format(droppedPercentage2));

            MARSNode unmatched1Node = rootNode.subNode("unmatched_" + alias1, "number of records of the first connection left unmatched in the buffer");
            MARSNode unmatched2Node = rootNode.subNode("unmatched_" + alias2, "number of records of the second connection left unmatched in the buffer");
            unmatched1Node.setValue(Long.toString(unmatchedLeft1));
            unmatched2Node.setValue(Long.toString(unmatchedLeft2));
        }

        public void logToConsole() {
            StringBuilder sb = new StringBuilder();
            sb.append("\b=== ");

            sb.append("Matched: ").append(matched);
            sb.append(" / Compare: Dropped ");
            sb.append(percentageFormat.format(droppedPercentage1));
            sb.append("% vs ");
            sb.append(percentageFormat.format(droppedPercentage2));
            sb.append("%; Delay ");
            if (matched == 0) {
                sb.append("N/A");
            } else {
                long averageDelayDelta = totalDelayDelta / matched;
                sb.append(averageDelayDelta > 0 ? averageDelayDelta : 0).append(" ms vs ");
                sb.append(averageDelayDelta < 0 ? -averageDelayDelta : 0).append(" ms");
            }
            sb.append(" / Unmatched buf: ").append(unmatchedLeft1);
            sb.append(" vs ").append(unmatchedLeft2);
            log.info(sb.toString());
        }
    }

    // Auxiliary common arrays that are used when matching two CompareBuffers using dynamic programming
    private static final int INITIAL_DYNAMIC_SIZE = 10;
    private int[][] lcsMatrix = new int[INITIAL_DYNAMIC_SIZE][INITIAL_DYNAMIC_SIZE];
    private int[] match = new int[INITIAL_DYNAMIC_SIZE];

    private class ComparisonData {
        CompareBuffer buf1 = CompareBuffer.EMPTY;
        CompareBuffer buf2 = CompareBuffer.EMPTY;

        private final ComparisonResult localResult;
        private final ComparisonResult[] results;

        ComparisonData(ComparisonResult recordResult, ComparisonResult globalResult, String symbolName) {
            // '.' and '=' are not allowed in MARS nodes
            symbolName = symbolName.replace('.', '_').replace('=', '-');
            localResult = new ComparisonResult(recordResult.getMarsNode().subNode(symbolName));
            results = new ComparisonResult[]{localResult, recordResult, globalResult};
        }

        @SuppressWarnings({"SynchronizeOnNonFinalField"})
        public void process() {
            synchronized (buf1) {
                synchronized (buf2) {
                    processImpl();
                }
            }
        }

        private void processImpl() {
            int n1 = buf1.size();
            int n2 = buf2.size();
            if (lcsMatrix.length <= n1 || lcsMatrix[0].length <= n2)
                lcsMatrix = new int[n1 + 1][n2 + 1];
            if (match.length < n1)
                match = new int[n1];
            Arrays.fill(match, 0, n1, -1);

            // use lcsMatrix to find length of the largest common subsequence (LCS)
            for (int i = 0; i < n1; i++)
                for (int j = 0; j < n2; j++)
                    lcsMatrix[i + 1][j + 1] = buf1.matches(buf2, i, j) ?
                        lcsMatrix[i][j] + 1 :
                        Math.max(lcsMatrix[i + 1][j], lcsMatrix[i][j + 1]);

            // find actual LCS and put its into match, also store position of last match point + 1
            int m1 = 0;
            int m2 = 0;
            int i = n1 - 1;
            int j = n2 - 1;
            while (i >= 0 && j >= 0) {
                if (buf1.matches(buf2, i, j)) {
                    match[i] = j;
                    if (m1 == 0 && m2 == 0) {
                        m1 = i + 1;
                        m2 = j + 1;
                    }
                    i--;
                    j--;
                } else if (lcsMatrix[i + 1][j + 1] == lcsMatrix[i + 1][j]) {
                    j--;
                } else {
                    i--;
                }
            }

            // NOTE: we should only consider up to last match (m1 and m2), leave remainder for next compare

            int matchedNumber = lcsMatrix[m1][m2];
            // calculate total delay delta for all matched records
            long delayDelta = 0;
            for (i = 0; i < n1; i++) {
                if (match[i] >= 0) {
                    long t1 = buf1.getTimestamp(i);
                    long t2 = buf2.getTimestamp(match[i]);
                    delayDelta += t1 - t2;
                }
            }
            // leave non-matched end in buffer
            buf1.clearFirst(m1);
            buf2.clearFirst(m2);

            // update current pair <record, symbol>, current record and total results
            localResult.clear();
            for (ComparisonResult result : results) {
                result.matched += matchedNumber;
                result.received1 += m1;
                result.received2 += m2;
                result.totalDelayDelta += delayDelta;
                result.unmatchedLeft1 += buf1.size();
                result.unmatchedLeft2 += buf2.size();
            }
            localResult.ready();
        }
    }
}
