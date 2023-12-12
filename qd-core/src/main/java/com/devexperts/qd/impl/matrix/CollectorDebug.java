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
package com.devexperts.qd.impl.matrix;

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.stats.QDStats;

import java.util.Arrays;

/**
 * This class contains helper methods and classes for debugging {@link Collector}.
 */
public class CollectorDebug {

    private static final Logging log = Logging.getLogging(CollectorDebug.class);

    static void visitAgentSymbols(SymbolReferenceVisitor srv, RehashCrashInfo rci, Agent agent) {
        SymbolReferenceLocation srl = new SymbolReferenceLocation();
        srl.object = agent;
        visitSubMatrixSymbols(srv, rci != null && agent.number == rci.agent ? rci : null, agent.sub, srl);
    }

    static void visitSubMatrixSymbols(SymbolReferenceVisitor srv, RehashCrashInfo rci, SubMatrix sub, SymbolReferenceLocation srl) {
        srl.index = 0;
        srv.visitSubMatrix(srl);
        if (sub.mapping != null) {
            if (rci != null)
                srv.visitMessage("Detected rehash in progress", srl);
            else
                srv.visitMessage("Detected rehash in progress, but no crash info provided; results may be inconsistent", srl);
        } else if (rci != null)
            srv.visitMessage("Rehash should be in progress, but not started", srl);
        srl.rehash = rci != null;
        if (sub.matrix == null) // support partially reconstructed dumps
            return;
        for (int index = sub.matrix.length; (index -= sub.step) >= 0;) {
            srl.index = index;
            boolean payload = sub.isPayload(index);
            int key = sub.getInt(index + Collector.KEY);
            int rid = sub.getInt(index + Collector.RID);
            if (key == 0) {
                if (payload)
                    srv.visitMessage("Payload entry with zero key", srl);
                if (rid != 0)
                    srv.visitMessage("Record id %d with zero key", srl, rid);
                continue;
            }
            if ((key & (SymbolCodec.VALID_CIPHER | Mapping.VALID_KEY)) != 0) {
                srv.visitSymbolReference(key, rid, payload, srl);
            } else {
                srv.visitMessage("Entry with invalid key %d", srl, key);
            }
            if (rci != null && rci.key == key) {
                rci = null; // rehash had stopped here
                srl.rehash = false;
                srv.visitMessage("Rehashing crash key %d found", srl, key);
            }
        }
        srl.rehash = false;
    }

    static class Log {
        public void info(String message) { log.info(message); }
        public void warn(String message) { log.warn(message); }
    }

    public static final Log DEFAULT = new Log();

    public static final Log CONSOLE = new Log() {
        @Override
        public void info(String message) { System.out.println(message); }
        @Override
        public void warn(String message) { System.out.println(message); }
    };

    public static class RehashCrashInfo {
        public int agent;
        public int key;
    }

    static class SymbolReferenceLocation {
        public int index;
        public Object object;
        public boolean added;
        public boolean removed;
        public boolean storage;
        public boolean rehash;

        @Override
        public String toString() {
            return (index > 0 ? index + " of " : "") +
                (object != null ? object.toString() : "") +
                (added ? " added" : "") + (removed ? " removed" : "") +
                (storage ? "storage" : "") +
                (rehash ? " before rehash crash point" : "");
        }
    }

    abstract static class SymbolReferenceVisitor {
        public void visitSymbolReference(int key, int rid, boolean payload, SymbolReferenceLocation srl) {}
        public void visitSubMatrix(SymbolReferenceLocation srl) {}
        public void visitMessage(String message, SymbolReferenceLocation srl) {}
        public void visitMessage(String message, SymbolReferenceLocation srl, int param) {}
    }

    static class VerifySymbolReferences extends SymbolReferenceVisitor {
        private final Log log;
        private final DataScheme scheme;
        private final Mapper collectorMapper;
        private final Mapper verifyMapper;

        private int payloadCipher;
        private int payloadKey;
        private int staleCipher;
        private int staleKey;

        VerifySymbolReferences(Log log, DataScheme scheme, Mapper collectorMapper, Mapper verifyMapper) {
            this.log = log;
            this.scheme = scheme;
            this.collectorMapper = collectorMapper;
            this.verifyMapper = verifyMapper;
        }

        void printSummary() {
            log.info(" Payload entries: " + payloadCipher + " ciphers, " + payloadKey + " keys");
            log.info("   Stale entries: " + staleCipher + " ciphers, " + staleKey + " keys");
        }

        @Override
        public void visitSubMatrix(SymbolReferenceLocation srl) {
            verifyMapper.incMaxCounter(srl.storage ? 1 : scheme.getRecordCount());
        }

        @Override
        public void visitSymbolReference(int key, int rid, boolean payload,
            SymbolReferenceLocation srl)
        {
            if ((key & SymbolCodec.VALID_CIPHER) != 0) {
                if (payload)
                    payloadCipher++;
                else
                    staleCipher++;
            } else if ((key & Mapping.VALID_KEY) != 0) {
                if (payload)
                    payloadKey++;
                else
                    staleKey++;
                String symbol = collectorMapper.getMapping().getSymbolAnyway(key);
                if (symbol != null) {
                    if (payload || !srl.rehash) {
                        // We don't count stale key that was already rehashed
                        int verifyKey = verifyMapper.addKey(symbol);
                        verifyMapper.incCounter(verifyKey);
                    }
                } else
                    log.warn("Unknown key " + key + " at " + srl);
            } else
                log.warn("Invalid key " + key + " at " + srl);
        }

        @Override
        public void visitMessage(String message, SymbolReferenceLocation srl) {
            log.warn(message + " at " + srl);
        }

        @Override
        public void visitMessage(String message, SymbolReferenceLocation srl, int param) {
            log.warn(String.format(message, param) + " at " + srl);
        }
    }

    static class VerifySubscription extends SymbolReferenceVisitor {
        private final Log log;
        private final DataScheme scheme;
        private final Mapper mapper;
        private SubMatrix allSub;

        int agentNumber;
        int totalSize;

        VerifySubscription(Log log, DataScheme scheme, Mapper mapper) {
            this.log = log;
            this.scheme = scheme;
            this.mapper = mapper;
            allSub = new SubMatrix(null, 2, 1, SubMatrix.KEY, 0, 0, Hashing.MAX_SHIFT, QDStats.VOID);
        }

        @Override
        public void visitSymbolReference(int key, int rid, boolean payload, SymbolReferenceLocation srl) {
            if (payload) {
                add(key, rid).add(agentNumber, srl.index);
                totalSize++;
            }
        }

        private AgentList add(int key, int rid) {
            if (allSub.needRehash(Hashing.MAX_SHIFT))
                allSub = allSub.rehash(Hashing.MAX_SHIFT);
            int index = allSub.addIndex(key, rid);
            AgentList list = (AgentList) allSub.getObj(index, 0);
            if (list == null)
                allSub.setObj(index, 0, list = new AgentList());
            return list;
        }

        public void verifyTotal(SubMatrix totalSub, Agent[] agents) {
            AgentList tmp = new AgentList();
            for (int index = totalSub.matrix.length; (index -= totalSub.step) >= 0;) {
                if (!totalSub.isPayload(index))
                    continue;
                int key = totalSub.getInt(index + Collector.KEY);
                int rid = totalSub.getInt(index + Collector.RID);
                int allSubIndex = allSub.getIndex(key, rid, 0);
                if (!allSub.isPayload(allSubIndex)) {
                    log.warn("Found " + fmtKeyRid(key, rid) + " in total sub but not in agent subs");
                    continue;
                }
                AgentList list = (AgentList) allSub.getObj(allSubIndex, 0);
                int nagent = totalSub.getInt(index + Collector.NEXT_AGENT);
                int nindex = totalSub.getInt(index + Collector.NEXT_INDEX);
                tmp.clear();
                while (nagent > 0) {
                    if (nagent >= agents.length) {
                        log.warn("Invalid NEXT_AGENT " + nagent + " in total sub chain for " + fmtKeyRid(key, rid));
                        break;
                    }
                    Agent next = agents[nagent];
                    if (next == null) {
                        log.warn("Missing next agent in total sub chain for record " + rid + " key " + key);
                        break;
                    }
                    tmp.add(nagent, nindex);
                    int nextKey = next.sub.getInt(nindex + SubMatrix.KEY);
                    int nextRid = next.sub.getInt(nindex + SubMatrix.RID);
                    if (nextKey != key || nextRid != rid) {
                        log.warn("Reference to wrong " + fmtKeyRid(nextKey, nextRid) + " at " + nindex + " of " + next +
                            " in total sub chain for " + fmtKeyRid(key, rid));
                        break;
                    }
                    nagent = next.sub.getInt(nindex + Collector.NEXT_AGENT);
                    nindex = next.sub.getInt(nindex + Collector.NEXT_INDEX);
                }
                list.sort();
                tmp.sort();
                int i = 0;
                int j = 0;
                while (i < list.size || j < tmp.size) {
                    int ni = i < list.size ? list.list[i + AgentList.NUMBER] : Integer.MAX_VALUE;
                    int nj = j < tmp.size ? tmp.list[j + AgentList.NUMBER] : Integer.MAX_VALUE;
                    if (ni < nj) {
                        log.warn("Subscription in " + fmtAgent(ni, agents) + " at " + list.list[i + AgentList.INDEX] +
                            " is missing in total sub chain for " + fmtKeyRid(key, rid));
                        i += AgentList.STEP;
                        continue;
                    }
                    if (ni > nj) {
                        log.warn("Subscription in " + fmtAgent(nj, agents) + " at " + tmp.list[j + AgentList.INDEX] +
                            " is not found, but is in total sub chain for " + fmtKeyRid(key, rid));
                        j += AgentList.STEP;
                        continue;
                    }
                    i += AgentList.STEP;
                    j += AgentList.STEP;
                }
            }
        }

        private String fmtAgent(int index, Agent[] agents) {
            Agent agent = index >= 0 && index < agents.length ? agents[index] : null;
            return agent != null ? agent.toString() : "agent #" + index;
        }

        private String fmtKeyRid(int key, int rid) {
            return CollectorDebug.fmtKeyRid(scheme, mapper, key, rid);
        }
    }

    static class AgentList {
        static final int NUMBER = 0;
        static final int INDEX = 1;
        static final int STEP = 2;

        int[] list = new int[4 * STEP];
        int size;

        public void add(int agentNumber, int agentIndex) {
            if (size + STEP >= list.length)
                list = Arrays.copyOf(list, 2 * list.length);
            list[size + NUMBER] = agentNumber;
            list[size + INDEX] = agentIndex;
            size += STEP;
        }

        public void clear() {
            size = 0;
        }

        public void sort() {
            for (int i = 0; i < size; i += STEP)
                for (int j = i + STEP; j < size; j += STEP)
                    if (list[i + NUMBER] > list[j + NUMBER]) {
                        swap(i, j, NUMBER);
                        swap(i, j, INDEX);
                    }
        }

        private void swap(int i, int j, int offset) {
            int tmp = list[i + offset];
            list[i + offset] = list[j + offset];
            list[j + offset] = tmp;
        }
    }

    static class AnalyzeKeyRid extends SymbolReferenceVisitor {
        private final Log log;
        private final int key;
        private final int rid;
        private final DataScheme scheme;
        private final Mapper mapper;

        AnalyzeKeyRid(Log log, int key, int rid, DataScheme scheme, Mapper mapper) {
            this.log = log;
            this.key = key;
            this.rid = rid;
            this.scheme = scheme;
            this.mapper = mapper;
        }

        @Override
        public void visitSymbolReference(int key, int rid, boolean payload, SymbolReferenceLocation srl) {
            if ((this.key != -1 && this.key != key) || (this.rid != -1 && this.rid != rid))
                return;
            log.info("Found " + fmtKeyRid(scheme, mapper, key, rid) + " " + (payload ? "payload" : "stale") + " at " + srl);
        }
    }

    public interface AgentVisitor {
        public void visitAgent(QDAgent agent);
    }

    public static String fmtKeyRid(DataScheme scheme, Mapper mapper, int key, int rid) {
        String record;
        if (rid >= 0 && rid < scheme.getRecordCount())
            record = scheme.getRecord(rid).getName();
        else
            record = "(invalid)";
        String symbol;
        if ((key & SymbolCodec.VALID_CIPHER) != 0)
            symbol = scheme.getCodec().decode(key);
        else
            symbol = mapper.getMapping().getSymbolIfPresent(key);
        return "record #" + rid + " " + record + " key " + key + ", symbol " + symbol;
    }

}
