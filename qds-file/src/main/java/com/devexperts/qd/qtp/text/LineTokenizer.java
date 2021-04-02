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
package com.devexperts.qd.qtp.text;

import com.devexperts.io.BufferedInput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class LineTokenizer {
    private TextDelimiters delimiters;

    private final StringDecoder sharedBuf = new StringDecoder();
    private ArrayList<String> tokens;
    private int nextIndex;

    private BufferedInput in;
    private byte cur;
    private boolean lineEnd;

    LineTokenizer() {
        setDelimiters(TextDelimiters.TAB_SEPARATED);
        tokens = new ArrayList<String>();
        nextIndex = 0;
    }

    public void setDelimiters(TextDelimiters delimiters) {
        if (delimiters == null)
            throw new NullPointerException();
        this.delimiters = delimiters;
    }

    public TextDelimiters getDelimiters() {
        return delimiters;
    }

    public List<String> getTokens() {
        return tokens;
    }

    public int getNextIndex() {
        return nextIndex;
    }

    public String nextToken() {
        return nextIndex < tokens.size() ? tokens.get(nextIndex++) : null;
    }

    public boolean hasMoreTokens() {
        return nextIndex < tokens.size();
    }

    /**
     * Resets data source and parses tokens from it until first 'end of line'
     * character is reached (or limit exceeded).
     * @throws CorruptedTextFormatException if the data is corrupted.
     */
    public boolean reset(BufferedInput in) throws CorruptedTextFormatException, IOException {
        tokens.clear();
        nextIndex = 0;
        lineEnd = false;

        this.in = in;
        if (!in.hasAvailable())
            return false;

        in.mark();
        cur = in.readByte();
        while (parseToken()) {}
        if (lineEnd)
            return true;
        // No end of line was reached until limit was exceeded.
        in.reset();
        in.unmark();
        tokens.clear();
        return false;

    }

    /**
     * Parses next token from <code>source</code> starting from <code>position</code>.
     * @return true, if it didn't reach end of line character and limit was not exceeded
     * (so more tokens can follow).
     * @throws CorruptedTextFormatException if the data is corrupted.
     */
    private boolean parseToken() throws CorruptedTextFormatException, IOException {
        if (!skipSpaces())
            return false;
        if (cur == '"') {
            if (!nextCharacter())
                return false;
            if (!readToken(true))
                return false;
            if (!nextCharacter())
                return false;
        } else
            if (!readToken(false))
                return false;
        if (delimiters == TextDelimiters.COMMA_SEPARATED) {
            if (!skipSpaces())
                return false;
            if (cur != ',')
                throw new CorruptedTextFormatException("Missing comma after token");
            if (!nextCharacter())
                return false;
        }
        return true;
    }

    private boolean skipSpaces() throws IOException {
        while (true) {
            if (isEndOfLineChar(cur)) {
                lineEnd = true;
                return false;
            }
            if (!isSpaceChar(cur))
                return true;
            if (!nextCharacter())
                return false;
        }
    }

    private boolean readToken(boolean endByQuote) throws IOException {
        long tokenStart = in.totalPosition();
        byte prev = 0;
        while (true) {
            if ((endByQuote ? isQuoteChar(cur) : isTokenEndChar(cur)) && prev != '\\')
                break;
            prev = cur;
            if (!nextCharacter())
                return false;
            if (isEndOfLineChar(cur)) {
                if (endByQuote)
                    throw new CorruptedTextFormatException("End of line inside quoted token");
                lineEnd = true;
                addToken(tokenStart);
                return false;
            }
        }
        addToken(tokenStart);
        return true;
    }

    private void addToken(long tokenStart) throws CorruptedTextFormatException, IOException {
        int length = (int) (in.totalPosition() - tokenStart);
        in.rewind(length + 1);
        tokens.add(sharedBuf.decode(in, length));
        nextCharacter(); // reread cur char after token
    }

    private boolean isEndOfLineChar(byte c) {
        return c == '\r' || c == '\n';
    }

    private boolean isSpaceChar(byte c) {
        return c == ' ' || c == '\t';
    }

    private boolean isQuoteChar(byte c) {
        return c == '"';
    }

    private boolean isTokenEndChar(byte c) {
        return c == ' ' || c == '\t' || (c == ',' && delimiters == TextDelimiters.COMMA_SEPARATED);
    }

    /**
     * Proceeds to the next character.
     * @return false if limit was exceeded.
     */
    private boolean nextCharacter() throws IOException {
        int read = in.read();
        if (read < 0)
            return false;
        cur = (byte) read;
        return true;
    }
}
