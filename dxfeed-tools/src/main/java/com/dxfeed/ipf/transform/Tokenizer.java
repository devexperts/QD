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
package com.dxfeed.ipf.transform;

import com.devexperts.util.ArrayUtil;
import com.devexperts.util.StringCache;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;

/**
 * Lexical tokenizer for {@link Compiler}. It is a modified {@link java.io.StreamTokenizer}
 * changed to meet specific needs of compiler.
 */
class Tokenizer {

    private final Reader reader;

    private static final byte CT_WHITESPACE = 1;
    private static final byte CT_DIGIT = 2;
    private static final byte CT_ALPHA = 4;
    private static final byte CT_QUOTE = 8;

    private final byte[] ctype = new byte[256];
    private boolean slashSlashComments;
    private boolean slashStarComments;

    private char[] buf = new char[20];
    private final StringCache stringCache = new StringCache();

    private static final int NEED_CHAR = Integer.MAX_VALUE;
    private int peekc = NEED_CHAR; // The next character to be considered by the nextToken method or NEED_CHAR.

    private boolean pushedBack;
    private int lineNumber = 1; // The line number of the last token read

    public static final int TT_EOF = -1;
    public static final int TT_NUMBER = -2;
    public static final int TT_WORD = -3;
    public static final int TT_NOTHING = -4;

    public int ttype = TT_NOTHING; // Type of token returned by nextToken() method. Either TT_* constant or char literal.
    public String sval; // Textual token for TT_WORD token.
    public double nval; // Numeric token for TT_NUMBER token.

    private boolean storeTokenPosition;
    public int tokenLine; // Line number of last token returned by nextToken() method.
    public int tokenPosition; // Character position of last token returned by nextToken() method.

    public final ArrayList<String> lines = new ArrayList<>(); // Processed lines of source code.
    public final StringBuilder line = new StringBuilder(); // Last (current) line of source code as it is read.

    Tokenizer(Reader r) {
        if (r == null)
            throw new NullPointerException();
        reader = r;
        whitespaceChars(0, ' ');
        quoteChar('"');
        quoteChar('\'');
        wordChars('a', 'z');
        wordChars('A', 'Z');
        wordChars('_', '_');
        wordChars('0', '9');
        parseNumbers();
        slashSlashComments(true);
        slashStarComments(true);
        lines.add("");
    }

    public void resetSyntax() {
        ordinaryChars(0, ctype.length - 1);
    }

    public void ordinaryChars(int low, int hi) {
        while (low <= hi)
            ctype[low++] = 0;
    }

    public void whitespaceChars(int low, int hi) {
        while (low <= hi)
            ctype[low++] = CT_WHITESPACE;
    }

    public void quoteChar(int ch) {
        ctype[ch] = CT_QUOTE;
    }

    public void wordChars(int low, int hi) {
        while (low <= hi)
            ctype[low++] |= CT_ALPHA;
    }

    public void parseNumbers() {
        for (int i = '0'; i <= '9'; i++)
            ctype[i] |= CT_DIGIT;
        ctype['.'] |= CT_DIGIT;
    }

    public void slashStarComments(boolean flag) {
        slashStarComments = flag;
    }

    public void slashSlashComments(boolean flag) {
        slashSlashComments = flag;
    }

    private int read() throws IOException {
        while (lines.size() < lineNumber) {
            lines.add(line.toString());
            line.setLength(0);
        }
        if (peekc < 0)
            peekc = NEED_CHAR;
        if (peekc == NEED_CHAR) {
            peekc = reader.read();
            if (peekc >= 0 && peekc != '\r' && peekc != '\n')
                line.append((char) peekc);
        }
        if (storeTokenPosition && peekc > ' ') {
            storeTokenPosition = false;
            tokenLine = lineNumber;
            tokenPosition = line.length() - 1;
        }
        int c = peekc;
        peekc = NEED_CHAR;
        return c;
    }

    public int peekNextChar() throws IOException {
        return peekc = read();
    }

    public void skipNextChar() throws IOException {
        read();
    }

    public void finishLastLine() throws IOException {
        storeTokenPosition = false; // preserve current position while reading rest of line
        for (int c = read(); c >= 0 && c != '\r' && c != '\n'; c = read());
        lines.add(line.toString());
    }

    public int nextToken() throws IOException {
        if (pushedBack) {
            pushedBack = false;
            return ttype;
        }
        storeTokenPosition = true;

        byte[] ct = ctype;
        sval = null;

        int c = read();
        if (c < 0)
            return ttype = TT_EOF;
        ttype = c; // Just to be safe

        int ctype = c < 256 ? ct[c] : CT_ALPHA;
        while ((ctype & CT_WHITESPACE) != 0) {
            if (c == '\r') {
                lineNumber++;
                c = read();
                if (c == '\n')
                    c = read();
            } else {
                if (c == '\n')
                    lineNumber++;
                c = read();
            }
            if (c < 0)
                return ttype = TT_EOF;
            ctype = c < 256 ? ct[c] : CT_ALPHA;
        }

        if ((ctype & CT_DIGIT) != 0) {
            double amount = 0;
            double divisor = 0;
            while (true) {
                if (c >= '0' && c <= '9') {
                    amount = amount * 10 + (c - '0');
                    divisor = divisor * 10;
                } else if (c == '.' && divisor == 0)
                    divisor = 1;
                else
                    break;
                c = read();
            }
            peekc = c;
            nval = divisor > 1 ? amount / divisor : amount;
            return ttype = TT_NUMBER;
        }

        if ((ctype & CT_ALPHA) != 0) {
            int i = 0;
            do {
                if (i >= buf.length)
                    buf = ArrayUtil.grow(buf, 0);
                buf[i++] = (char) c;
                c = read();
                ctype = c < 0 ? CT_WHITESPACE : c < 256 ? ct[c] : CT_ALPHA;
            } while ((ctype & (CT_ALPHA | CT_DIGIT)) != 0);
            peekc = c;
            sval = stringCache.get(buf, 0, i);
            return ttype = TT_WORD;
        }

        if ((ctype & CT_QUOTE) != 0) {
            ttype = c;
            int i = 0;
            /* Invariants (because \Octal needs a lookahead):
            *   (i)  c contains char value
            *   (ii) d contains the lookahead
            */
            int d = read();
            while (d >= 0 && d != ttype && d != '\n' && d != '\r') {
                if (d == '\\') {
                    c = read();
                    int first = c; // To allow \377, but not \477
                    if (c >= '0' && c <= '7') {
                        c = c - '0';
                        int c2 = read();
                        if ('0' <= c2 && c2 <= '7') {
                            c = (c << 3) + (c2 - '0');
                            c2 = read();
                            if ('0' <= c2 && c2 <= '7' && first <= '3') {
                                c = (c << 3) + (c2 - '0');
                                d = read();
                            } else
                                d = c2;
                        } else
                            d = c2;
                    } else {
                        switch (c) {
                        case 'a':
                            c = 0x7;
                            break;
                        case 'b':
                            c = '\b';
                            break;
                        case 'f':
                            c = 0xC;
                            break;
                        case 'n':
                            c = '\n';
                            break;
                        case 'r':
                            c = '\r';
                            break;
                        case 't':
                            c = '\t';
                            break;
                        case 'v':
                            c = 0xB;
                            break;
                        }
                        d = read();
                    }
                } else {
                    c = d;
                    d = read();
                }
                if (i >= buf.length)
                    buf = ArrayUtil.grow(buf, 0);
                buf[i++] = (char) c;
            }

            /* If we broke out of the loop because we found a matching quote
            * character then arrange to read a new character next time
            * around; otherwise, save the character.
            */
            peekc = d == ttype ? NEED_CHAR : d;

            sval = stringCache.get(buf, 0, i);
            return ttype;
        }

        if (c == '/' && (slashSlashComments || slashStarComments)) {
            c = read();
            if (c == '*' && slashStarComments) {
                int prevc = 0;
                while ((c = read()) != '/' || prevc != '*') {
                    if (c == '\r') {
                        lineNumber++;
                        c = read();
                        if (c == '\n')
                            c = read();
                    } else {
                        if (c == '\n') {
                            lineNumber++;
                            c = read();
                        }
                    }
                    if (c < 0)
                        return ttype = TT_EOF;
                    prevc = c;
                }
                return nextToken();
            } else if (c == '/' && slashSlashComments) {
                while ((c = read()) != '\n' && c != '\r' && c >= 0);
                peekc = c;
                return nextToken();
            } else {
                peekc = c;
                return ttype = '/';
            }
        }

        return ttype = c;
    }

    public void pushBack() {
        if (ttype != TT_NOTHING) // No-op if nextToken() not called
            pushedBack = true;
    }
}
