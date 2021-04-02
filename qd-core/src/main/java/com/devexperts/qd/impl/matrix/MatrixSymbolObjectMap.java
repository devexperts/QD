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
package com.devexperts.qd.impl.matrix;

import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.util.SymbolObjectMap;
import com.devexperts.qd.util.SymbolObjectVisitor;

public class MatrixSymbolObjectMap<T> extends SymbolObjectMap<T> {
    private static final class Core<T> extends AbstractPayloadBitsMatrix {
        private Core(Mapper mapper, int capacity, int prev_magic) {
            super(mapper, 1, 1, capacity, prev_magic);
        }

        // ========== Internal ==========

        Core<T> rehash() {
            Core<T> dest = new Core<T>(mapper, payloadSize, magic);
            rehashTo(dest);
            return dest;
        }

        boolean contains(int cipher, String symbol) {
            return isPayload(getIndex(cipher, symbol));
        }

        boolean contains(int cipher, char[] chars, int offset, int length) {
            return isPayload(getIndex(cipher, chars, offset, length));
        }

        @SuppressWarnings("unchecked")
        T get(int cipher, String symbol) {
            return (T) obj_matrix[getIndex(cipher, symbol)];
        }

        @SuppressWarnings("unchecked")
        T get(int cipher, char[] chars, int offset, int length) {
            return (T) obj_matrix[getIndex(cipher, chars, offset, length)];
        }

        T put(int cipher, String symbol, T value) {
            int key = cipher;
            if ((cipher & SymbolCodec.VALID_CIPHER) == 0) {
                if (cipher != 0)
                    throw new IllegalArgumentException("Reserved cipher.");
                 key = mapper.addKey(symbol);
            }
            return put(key, value);
        }

        T put(int cipher, char[] chars, int offset, int length, T value) {
            int key = cipher;
            if ((cipher & SymbolCodec.VALID_CIPHER) == 0) {
                if (cipher != 0)
                    throw new IllegalArgumentException("Reserved cipher.");
                 key = mapper.addKey(chars, offset, length);
            }
            return put(key, value);
        }

        @SuppressWarnings("unchecked")
        private T put(int key, T value) {
            int index = getIndex(key, -1);
            if (matrix[index] == 0) {
                if ((key & SymbolCodec.VALID_CIPHER) == 0)
                    mapper.incCounter(key);
                matrix[index] = key;
                overallSize++;
            }

            T old_value = (T) obj_matrix[index];
            obj_matrix[index] = value;
            markPayload(index);
            return old_value;
        }

        T remove(int cipher, String symbol) {
            return remove(getIndex(cipher, symbol));
        }

        T remove(int cipher, char[] chars, int offset, int length) {
            return remove(getIndex(cipher, chars, offset, length));
        }

        @SuppressWarnings("unchecked")
        private T remove(int index) {
            T old_value = (T) obj_matrix[index];
            clearPayload(index);
            return old_value;
        }

        @SuppressWarnings("unchecked")
        boolean examineEntries(SymbolObjectVisitor<T> visitor) {
            for (int index = matrix.length; --index >= 0;)
                if (isPayload(index)) {
                    int key = matrix[index];
                    int cipher = key;
                    String symbol = null;
                    if ((key & SymbolCodec.VALID_CIPHER) == 0) {
                        cipher = 0;
                        symbol = getMapping().getSymbolIfPresent(key); // do not cache mapping to see concurrent mapping rehash
                        if (symbol == null)
                            continue;  // not found -- was just added, but we don't "see" its mapping (mapping was rehashed, or...)
                    }
                    if (!visitor.hasCapacity())
                        return true;
                    visitor.visitEntry(cipher, symbol, (T) obj_matrix[index]);
                }
            return false;
        }
    }

    //============================================================================================

    private volatile Core<T> core;

    public MatrixSymbolObjectMap() {
        clear();
    }

    @Override
    public int size() {
        return core.payloadSize;
    }

    @Override
    public String getSymbol(char[] chars, int offset, int length) {
        return core.getMapping().getSymbolIfPresent(chars, offset, length);
    }

    @Override
    public boolean contains(int cipher, String symbol) {
        return core.contains(cipher, symbol);
    }

    @Override
    public boolean contains(int cipher, char[] chars, int offset, int length) {
        return core.contains(cipher, chars, offset, length);
    }

    @Override
    public T get(int cipher, String symbol) {
        return core.get(cipher, symbol);
    }

    @Override
    public T get(int cipher, char[] chars, int offset, int length) {
        return core.get(cipher, chars, offset, length);
    }

    @Override
    public T put(int cipher, String symbol, T value) {
        if (Hashing.needRehash(core.shift, core.overallSize, core.payloadSize, Hashing.MAX_SHIFT))
            core = core.rehash();
        return core.put(cipher, symbol, value);
    }

    @Override
    public T put(int cipher, char[] chars, int offset, int length, T value) {
        if (Hashing.needRehash(core.shift, core.overallSize, core.payloadSize, Hashing.MAX_SHIFT))
            core = core.rehash();
        return core.put(cipher, chars, offset, length, value);
    }

    @Override
    public T remove(int cipher, String symbol) {
        T old_value = core.remove(cipher, symbol);
        if (Hashing.needRehash(core.shift, core.overallSize, core.payloadSize, Hashing.MAX_SHIFT))
            core = core.rehash();
        return old_value;
    }

    @Override
    public T remove(int cipher, char[] chars, int offset, int length) {
        T old_value = core.remove(cipher, chars, offset, length);
        if (Hashing.needRehash(core.shift, core.overallSize, core.payloadSize, Hashing.MAX_SHIFT))
            core = core.rehash();
        return old_value;
    }

    @Override
    public void clear() {
        Mapper mapper = new Mapper(this);
        mapper.incMaxCounter(1); // at most one forever
        core = new Core<T>(mapper, 0, 0);
    }

    @Override
    public boolean examineEntries(SymbolObjectVisitor<T> visitor) {
        return core.examineEntries(visitor);
    }
}
