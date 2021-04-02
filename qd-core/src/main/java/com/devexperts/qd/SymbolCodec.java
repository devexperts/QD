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
package com.devexperts.qd;

import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedOutput;
import com.devexperts.qd.qtp.ProtocolOption;
import com.devexperts.qd.util.ShortString;

import java.io.IOException;

/**
 * The <code>SymbolCodec</code> defines coding and serialization of symbols.
 * It is believed that most symbols can be effectively encoded into a single
 * 32-bit value with full decoding capability. Taking into account that 32-bit
 * values occupy much less memory and are processed much faster than strings,
 * the symbol coding is used to achieve high performance.
 *
 * <p> The encoded representation of the symbol is called <code>cipher</code>; it
 * uses Java <code>int</code> primitive data type for physical representation.
 * The <code>int cipher</code> together with <code>String symbol</code> constitute
 * <i>cipher-symbol</i> pair which is used throughout the system as a key to the symbol.
 *
 * <p> The table below defines allowed cipher-symbol pairs and their meaning:
 * <pre>
 * cipher == 0, symbol == null    - undefined, unknown, void, null
 * cipher == 0, symbol != null    - unencodeable, defined by symbol
 * cipher != 0, symbol == null    - encoded, defined by cipher
 * cipher != 0, symbol != null    - encoded, defined by cipher, symbol must correspond to cipher
 * </pre>
 *
 * <b>NOTE:</b> it is prohibited <b>not</b> to encode symbol if it is encodeable,
 * but it is allowed to omit raw symbol string (use null) for encoded ciphers.
 *
 * <p><b>NOTE:</b> the range from <code>1</code> to <code>0x3FFFFFFF</code> inclusive
 * is reserved - no valid cipher is allowed to have value in this range. More precisely,
 * for any valid encoded cipher the expression {@code ((cipher & VALID_CIPHER) != 0)}
 * must be true. This range is reserved for private subsystem implementations - any subsystem
 * may use this range internally as long as reserved values do not appear in its public API.
 *
 * <p> The <code>SymbolCodec</code> must be provided to QD by APS. It must perform coding
 * and serialization on-the-fly without need to look into external resources each time.
 *
 * <p>{@link com.devexperts.qd.kit.PentaCodec PentaCodec} is the only supported
 * implementation of the symbol codec.
 * <b>Other implementations are deprecated and will not be supported in the future.</b>
 */
public interface SymbolCodec {
    /**
     * VALID_CIPHER defines range of valid encoded ciphers.
     * See documentation of <code>SymbolCodec</code> for details.
     */
    public static final int VALID_CIPHER = 0xC0000000;

    /**
     * Returns encoded cipher for specified symbol.
     * Returns 0 if specified symbol is null or is unencodeable.
     */
    public int encode(String symbol);

    /**
     * Returns encoded cipher for specified symbol represented in
     * a character array. Returns 0 if specified symbol is unencodeable.
     * This method must produce the same result as the following code:
     * <br><code>encode(new String(chars, offset, length));</code>.
     *
     * @throws NullPointerException if specified array is null.
     * @throws IndexOutOfBoundsException if specified parameters refers to
     *         characters outside specified array.
     */
    public int encode(char[] chars, int offset, int length);

    /**
     * Returns decoded symbol for specified cipher.
     * Returns null if specified cipher is 0.
     *
     * @throws IllegalArgumentException if specified cipher could not be decoded.
     */
    public String decode(int cipher);

    /**
     * Returns decoded symbol for specified cipher-symbol pair.
     * This is a shortcut with the following implementation:
     * <br><code>return symbol != null ? symbol : decode(cipher);</code>.
     */
    public String decode(int cipher, String symbol);

    /**
     * Returns decoded symbol for specified cipher packed in the primitive long value.
     * Each decoded character occupies exactly 8 bits started from highest bit of returned value.
     * Unused (lowest) bits of returned value are filled with 0.
     * Returns 0 if specified cipher is 0.
     * <p>
     * This is the same encoding as specified by {@link ShortString} class.
     * The result of
     * <code>{@link ShortString#decode(long) ShortString.decode}(decodeToLong(cipher))</code>
     * expression
     * shall be the same as result of {@link #decode(int) decode(cipher)} call except for null vs empty string discrepancy.
     * However this method always aligns bytes in returned value to the highest one rather than lowest one.
     *
     * @throws IllegalArgumentException if specified cipher could not be decoded.
     */
    public long decodeToLong(int cipher);

    /**
     * Decodes one character from the given cipher at the given position.
     * This method should be used only when few (one or two) characters are needed.
     *
     * @throws IllegalArgumentException if specified cipher could not be decoded or {@code i < 0}.
     * @return Decoded character or {@code -1} if {@code i >= decode(cipher).length()}.
     */
    public int decodeCharAt(int cipher, int i);

    /**
     * Returns a hash code for the specified cipher.
     * Returns 0 if specified cipher is 0.
     * The hash code is the same as {@link #decode(int) decode(cipher)}.{@link String#hashCode() hashCode()}
     * except it does not throw {@link NullPointerException} for 0 cipher.
     */
    public int hashCode(int cipher);

    /**
     * Returns cipher that is used by the "wildcard" symbol. Wildcard symbol shall be
     * chosen from a space of encodable symbols.
     */
    public int getWildcardCipher();

    /**
     * Creates stateful symbol reader.
     */
    public Reader createReader();

    /**
     * Creates stateful symbol writer.
     */
    public Writer createWriter();

    /**
     * Symbol resolver to reuse string symbol instances.
     */
    public interface Resolver {
        /**
         * Returns symbol used for specified characters or <code>null</code> if not found.
         */
        public String getSymbol(char[] chars, int offset, int length);
    }

    /**
     * Stateful symbol reader.
     */
    public abstract class Reader {
        protected int cipher;
        protected String symbol;
        protected int eventFlags;
        protected int eventFlagsBytes; // number of bytes consumed by eventFlags

        /**
         * Resets reader at the beginning of packet.
         * @param optSet the set of supported protocol options.
         */
        public abstract void reset(ProtocolOption.Set optSet);

        public abstract void readSymbol(BufferedInput in, Resolver resolver) throws IOException;

        public final int getCipher() {
            return cipher;
        }

        public final String getSymbol() {
            return symbol;
        }

        public final int getEventFlags() {
            return eventFlags;
        }

        public final int getEventFlagsBytes() {
            return eventFlagsBytes;
        }
    }

    /**
     * Stateful symbol writer.
     */
    public abstract class Writer {
        /**
         * Resets writer at the beginning of packet.
         * @param optSet the set of supported protocol options.
         */
        public abstract void reset(ProtocolOption.Set optSet);

        public abstract void writeSymbol(BufferedOutput out, int cipher, String symbol, int eventFlags) throws IOException;
    }
}
