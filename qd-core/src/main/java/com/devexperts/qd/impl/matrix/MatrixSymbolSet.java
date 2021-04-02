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
import com.devexperts.qd.SymbolReceiver;
import com.devexperts.qd.util.SymbolSet;

public class MatrixSymbolSet extends SymbolSet {
    private static final int LIST_SET_LIMIT = 2;

    private volatile Core core;
    private final boolean unmodifiable;

    public MatrixSymbolSet() {
        clearImpl();
        unmodifiable = false;
    }

    private MatrixSymbolSet(Core core) {
        this.core = core;
        unmodifiable = true;
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
    public boolean add(int cipher, String symbol) {
        checkUnmodifiable();
        if (Hashing.needRehash(core.shift, core.overallSize, core.payloadSize, Hashing.MAX_SHIFT))
            core = core.rehash();
        return core.add(cipher, symbol);
    }

    @Override
    public boolean add(int cipher, char[] chars, int offset, int length) {
        checkUnmodifiable();
        if (Hashing.needRehash(core.shift, core.overallSize, core.payloadSize, Hashing.MAX_SHIFT))
            core = core.rehash();
        return core.add(cipher, chars, offset, length);
    }

    @Override
    public boolean remove(int cipher, String symbol) {
        checkUnmodifiable();
        boolean result = core.remove(cipher, symbol);
        if (Hashing.needRehash(core.shift, core.overallSize, core.payloadSize, Hashing.MAX_SHIFT))
            core = core.rehash();
        return result;
    }

    @Override
    public boolean remove(int cipher, char[] chars, int offset, int length) {
        checkUnmodifiable();
        boolean result = core.remove(cipher, chars, offset, length);
        if (Hashing.needRehash(core.shift, core.overallSize, core.payloadSize, Hashing.MAX_SHIFT))
            core = core.rehash();
        return result;
    }

    @Override
    public void clear() {
        checkUnmodifiable();
        clearImpl();
    }

    @Override
    public void examine(SymbolReceiver receiver) {
        core.examine(receiver);
    }

    @Override
    public SymbolSet unmodifiable() {
        if (unmodifiable)
            return this;
        if (size() <= LIST_SET_LIMIT)
            return new ListSet(this);
        return new MatrixSymbolSet(core);
    }

    private void clearImpl() {
        Mapper mapper = new Mapper(this);
        mapper.incMaxCounter(1); // at most one forever
        core = new Core(mapper, 0, 0);
    }

    private void checkUnmodifiable() {
        if (unmodifiable)
            throw new UnsupportedOperationException("unmodifiable");
    }

    //============================================================================================

    private static final class Core extends AbstractPayloadBitsMatrix {
        Core(Mapper mapper, int capacity, int prev_magic) {
            super(mapper, 1, 0, capacity, prev_magic);
        }

        // ========== Internal ==========

        Core rehash() {
            Core dest = new Core(mapper, payloadSize, magic);
            rehashTo(dest);
            return dest;
        }

        boolean contains(int cipher, String symbol) {
            return isPayload(getIndex(cipher, symbol));
        }

        boolean contains(int cipher, char[] chars, int offset, int length) {
            return isPayload(getIndex(cipher, chars, offset, length));
        }

        boolean add(int cipher, String symbol) {
            int key = cipher;
            if ((cipher & SymbolCodec.VALID_CIPHER) == 0) {
                if (cipher != 0)
                    throw new IllegalArgumentException("Reserved cipher.");
                key = mapper.addKey(symbol);
            }
            return add(key);
        }

        boolean add(int cipher, char[] chars, int offset, int length) {
            int key = cipher;
            if ((cipher & SymbolCodec.VALID_CIPHER) == 0) {
                if (cipher != 0)
                    throw new IllegalArgumentException("Reserved cipher.");
                key = mapper.addKey(chars, offset, length);
            }
            return add(key);
        }

        private boolean add(int key) {
            int index = getIndex(key, -1);
            if (matrix[index] == 0) {
                if ((key & SymbolCodec.VALID_CIPHER) == 0)
                    mapper.incCounter(key);
                matrix[index] = key;
                overallSize++;
            }
            if (isPayload(index))
                return false;
            markPayload(index);
            return true;
        }

        boolean remove(int cipher, String symbol) {
            return remove(getIndex(cipher, symbol));
        }

        boolean remove(int cipher, char[] chars, int offset, int length) {
            return remove(getIndex(cipher, chars, offset, length));
        }

        private boolean remove(int index) {
            return clearPayload(index);
        }

        void examine(SymbolReceiver receiver) {
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
                    receiver.receiveSymbol(cipher, symbol);
                }
        }
    }
}
