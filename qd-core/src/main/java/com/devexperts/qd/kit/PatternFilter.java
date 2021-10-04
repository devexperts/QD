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
package com.devexperts.qd.kit;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.util.SymbolSet;
import com.devexperts.util.LongHashSet;
import com.devexperts.util.SystemProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * {@code SubscriptionFilter} that understands a simple GLOB-like grammar
 * to specify matching symbols.
 *
 * <p>This filter always accepts {@link SymbolCodec#getWildcardCipher wildcard} symbol regardless
 * of an actual pattern for all records that are accepted by it.
 */
public final class PatternFilter extends QDFilter {
    private static final int MAX_SYMBOL_SET_SIZE =
        SystemProperties.getIntProperty(PatternFilter.class, "maxSymbolSetSize", 10000);

    private static final int MAX_MID_PATTERNS =
        SystemProperties.getIntProperty(PatternFilter.class, "maxMidPatterns", 1000);

    private static final int[] EMPTY_BITS = new int[0];

    private static final int N_CHARS_SHIFT = 7;
    private static final int MAX_CHAR = (1 << N_CHARS_SHIFT) - 1; // Note: max char is invalid character by itself, groups here all >= MAX_CHAR

    public static final int BITS_CHAR_SHIFT = 5;
    public static final int BITS_CHAR_MASK = (1 << BITS_CHAR_SHIFT) - 1;

    private static final int BITS_LENGTH_SHIFT = N_CHARS_SHIFT - BITS_CHAR_SHIFT;

    private final String pattern;
    private final boolean negated;
    private final boolean fixedLength; // when true, implies that no '*' in pattern and acceptsSuffix.length == 0
    private final int[] prefixBits;
    private final int prefixLength;

    private final int[] midBits;
    private final int midLength;
    private final int rollingHashOutMultiplier;
    private final LongHashSet midPatternHashes;
    //Boyer-Moore part for single mid pattern
    private final char[] bmMidPattern;
    private final int[] bmSkipArray;

    private final int[] suffixBits;
    private final int suffixLength;
    private final int symbolSetSize;
    private SymbolSet set;

    /**
     * Quotes all reserved characters in the given string, so that it can be used as a pattern filter.
     * For example, {@code quote("Trade&N")} will be equal to {@code "Trade[&]N"}. Special
     * characters that are quoted with braces are:
     * '!', ':', ';', '|', '&amp;', '*', '?', '+',
     * '(', ')', '{', '}', '&lt;', '&gt;',
     * '~', ',', '"', ''', and '`'.
     * These characters are quoted by adding backspace before them: ' ', '[', ']', '\'.
     *
     * <p>If the input string starts with a lower-case characters (from 'a' to 'z') it is also quoted with braces,
     * because patterns starting with a lower case letter are reserved. For example,
     * {@code quote("feed")} is equal to {@code "[f]eed"}.
     *
     * @throws NullPointerException if the string is {@code null}.
     * @throws FilterSyntaxException if the string contains characters that cannot be represented in
     *           pattern filter in any form.
     *           All characters with codes larger than 32 and lower than 128 are supported with the exception of '[' and ']'.
     */
    public static String quote(String string) throws FilterSyntaxException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            charRangeCheck(c);
            boolean quote = false;
            if (i == 0 && c >= 'a' && c <= 'z')
                quote = true; // quote first low-case character (because they are reserved for special patterns)
            boolean quote_next = false;
            switch (c) {
            // These symbols were not supported before QD 3.134, so we use QD 3.134+ quoting approach with
            // backspace for them. The only backwards incompatible change is that backspace cannot be
            // quoted with character class '[..]'. It has to be quoted with backspace.
            case ' ': case '[': case ']': case '\\':
                quote_next = true;
                break;
            // reserved characters (for future extensions) are not allowed outside char class
            // NOTE: Quote all characters that were reserved in the initial version for backwards compatibility
            // NOTE2: They are quoted in '[...]' character class for backwards compatibility, too
            case '!': case ':': case ';': case '|': case '&': case '*': case '?': case '+':
            case '(': case ')': case '{': case '}': case '<': case '>':
            case '~':  case ',': case '"': case '\'': case '`':
                quote = true;
                break;
            }
            if (quote_next)
                sb.append('\\').append(c);
            else if (quote)
                sb.append('[').append(c).append(']');
            else
                sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Returns filter with a specified pattern. Returns null if pattern
     * is empty or matches everything. Note, that this method does not support project-specific filters and
     * works only for patterns. Use {@link CompositeFilters#valueOf(String, DataScheme)} method if
     * you need to parse an arbitrary filter string.
     *
     * <p> Use {@link #valueOf(String, String, DataScheme)} to create project-specific
     * named filter.
     *
     * <p> <b>This is a legacy method, because it has a legacy return type.</b>
     * @throws NullPointerException if pattern is null.
     * @throws FilterSyntaxException if pattern is invalid.
     * @deprecated Use {@link #valueOf(String, String, DataScheme)} to create project-specific named filters
     *     or {@link CompositeFilters#valueOf(String, DataScheme)} to parse an arbitrary string into a filter.
     */
    public static SubscriptionFilter valueOf(String pattern, DataScheme scheme) throws FilterSyntaxException {
        QDFilter result = valueOfImpl(pattern, pattern, scheme);
        return result == ANYTHING ? null : result;
    }

    /**
     * Returns filter with a specified pattern and a project-specific name.
     * Returns {@link #ANYTHING} if pattern is empty or matches anything.
     * Complex filter transformations (logical operations) try to retain the specified name.
     *
     * @throws NullPointerException if pattern is null.
     * @throws FilterSyntaxException if pattern is invalid.
     * @throws IllegalArgumentException if name is invalid.
     */
    public static QDFilter valueOf(String pattern, String name, DataScheme scheme) throws FilterSyntaxException {
        checkShortName(name);
        return valueOfImpl(pattern, name, scheme);
    }

    static QDFilter valueOfImpl(String pattern, String name, DataScheme scheme) throws FilterSyntaxException {
        if (pattern.isEmpty())
            return ANYTHING;
        boolean record = pattern.startsWith(":");
        PatternFilter filter = new PatternFilter(record ? pattern.substring(1) : pattern, name, scheme);
        if (filter.isEverythingPattern())
            return ANYTHING;
        if (filter.isNothingPattern())
            return NOTHING;
        if (record)
            return new FastRecordFilter(scheme, name, r -> filter.acceptString(r.getName())).warnOnPotentialTypo(true);
        return filter;
    }

    private PatternFilter(String pattern, String name, DataScheme scheme) throws FilterSyntaxException {
        super(scheme);
        if (!pattern.isEmpty() && pattern.charAt(0) >= 'a' && pattern.charAt(0) <= 'z')
            throw new FilterSyntaxException(
                "Patterns with the first lower-case letter are reserved for application-specific extensions. " +
                    "Check spelling of the pattern or " +
                    "use '[' and ']' to quote first characters if needed: \"" + pattern + "\"");
        this.pattern = pattern;
        setName(name);
        negated = !pattern.isEmpty() && pattern.startsWith("!");
        int[] pos = {negated ? 1 : 0};
        prefixBits = parse(pattern, pos);
        prefixLength = prefixBits.length >> BITS_LENGTH_SHIFT;
        fixedLength = pos[0] == pattern.length();
        if (!fixedLength)
            pos[0]++; // skip '*' to the next char
        int[] nextBits = parse(pattern, pos);
        if (pos[0] < pattern.length()) {
            if (pattern.charAt(pos[0] - 1) == '*')
                throw new FilterSyntaxException("Double '**' wild-card is not supported in pattern");
            pos[0]++; // second asterisk was found, skip '*' to the next char
            suffixBits = parse(pattern, pos);
            midBits = nextBits;
        } else {
            midBits = new int[0];
            suffixBits = nextBits;
        }
        midLength = midBits.length >> BITS_LENGTH_SHIFT;
        int outMultiplier = 1;
        for (int i = 0; i < midLength; i++)
            outMultiplier *= 31;
        rollingHashOutMultiplier = outMultiplier;
        suffixLength = suffixBits.length >> BITS_LENGTH_SHIFT;
        if (pos[0] < pattern.length())
            throw new FilterSyntaxException("Third '*' wild-card is not supported in pattern");
        List<char[]> midPatterns = generateMidPatterns();
        midPatternHashes = computeMidHashes(midPatterns);
        bmMidPattern = computeBoyerMoorePattern(midPatterns);
        bmSkipArray = computeBoyerMooreSkipArray();
        this.symbolSetSize = computeSymbolSetSize();
    }

    private PatternFilter(PatternFilter source, boolean forNegation) {
        super(source.getScheme());
        if (!forNegation)
            throw new FilterSyntaxException("Allowed only for negate pattern construction");

        this.pattern = negateName(source.pattern);
        this.negated = !source.negated;

        this.fixedLength = source.fixedLength;
        this.prefixBits = source.prefixBits;
        this.prefixLength = source.prefixLength;
        this.midBits = source.midBits;
        this.midLength = source.midLength;
        this.rollingHashOutMultiplier = source.rollingHashOutMultiplier;
        this.midPatternHashes = source.midPatternHashes;
        this.bmMidPattern = source.bmMidPattern;
        this.bmSkipArray = source.bmSkipArray;
        this.suffixBits = source.suffixBits;
        this.suffixLength = source.suffixLength;
        this.symbolSetSize = computeSymbolSetSize();
    }

    private int computeSymbolSetSize() {
        if (!fixedLength || negated)
            return Integer.MAX_VALUE;
        int symbolSetSize = 1;
        for (int i = 0; i < prefixLength; i++) {
            if (hasChar(prefixBits, i, MAX_CHAR))
                return Integer.MAX_VALUE; // accepts all non-ascii chars (will produce too many symbols)
            int acceptCnt = 0;
            for (int c = 0; c < MAX_CHAR; c++) {
                if (hasChar(prefixBits, i, c))
                    acceptCnt++;
            }
            symbolSetSize *= acceptCnt;
            if (symbolSetSize > MAX_SYMBOL_SET_SIZE)
                return Integer.MAX_VALUE; // accepts too many symbols
        }
        return symbolSetSize;
    }

    private boolean isEverythingPattern() {
        return !fixedLength && !negated && prefixLength == 0 && midLength == 0 && suffixLength == 0;
    }

    private boolean isNothingPattern() {
        return !fixedLength && negated && prefixLength == 0 && midLength == 0 && suffixLength == 0;
    }

    @Override
    public Kind getKind() {
        return symbolSetSize <= MAX_SYMBOL_SET_SIZE ? Kind.SYMBOL_SET : Kind.PATTERN;
    }

    @Override
    public SymbolSet getSymbolSet() {
        if (symbolSetSize > MAX_SYMBOL_SET_SIZE)
            return null;
        SymbolSet symbolSet = this.set;
        if (symbolSet == null) {
            SymbolSet set = SymbolSet.createInstance();
            StringBuilder sb = new StringBuilder(prefixLength);
            sb.setLength(prefixLength);
            generateSet(set, sb, 0);
            symbolSet = set.unmodifiable();
            this.set = symbolSet;
        }
        return symbolSet;
    }

    private void generateSet(SymbolSet set, StringBuilder sb, int i) {
        if (i >= prefixLength) {
            String symbol = sb.toString();
            set.add(getScheme().getCodec().encode(symbol), symbol);
            return;
        }
        for (char c = 0; c < MAX_CHAR; c++) {
            if (hasChar(prefixBits, i, c)) {
                sb.setCharAt(i, c);
                generateSet(set, sb, i + 1);
            }
        }
    }

    @Override
    public QDFilter toStableFilter() {
        return this;
    }

    @Override
    public boolean isFast() {
        return true;
    }

    /**
     * Returns filter patter that can be always parsed back to this filter.
     */
    public String getPattern() {
        return pattern;
    }

    @Override
    public QDFilter negate() {
        PatternFilter filter = new PatternFilter(this, true);
        filter.setName(negateName(toString()));
        return filter;
    }

    @Nonnull
    private String negateName(String name) {
        return negated ? name.substring(1) : ("!" + name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        return pattern.equals(((PatternFilter) o).pattern);

    }

    @Override
    public int hashCode() {
        return pattern.hashCode();
    }

    @Override
    public String getDefaultName() {
        return pattern;
    }

    private static int[] parse(String pattern, int[] pos) throws FilterSyntaxException {
        // fast-path for empty pattern or empty suffix
        int i = pos[0];
        if (i >= pattern.length())
            return EMPTY_BITS;
        // state variables for parser
        int length = 0; // current length
        int[] bits = new int[pattern.length() << BITS_LENGTH_SHIFT]; // guess of the length required (may be overestimation, but never underestimation)
        boolean block_quote = false;      // inside \Q...\E section
        boolean escape_next = false;      // after \ char
        boolean char_class = false;       // inside [...] section
        boolean char_class_start = false; // after [ char
        boolean char_class_range = false; // after [x-
        boolean inverse = false;          // inside [^...] section
        boolean next_char = false;
        char last_char = '\0';
        // loop over string starting from a given position
    main_loop:
        for (; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            charRangeCheck(c);
            boolean process_regular_char;
            if (block_quote) {
                // check for block-quote end
                if (c == '\\' && i + 1 < pattern.length() && pattern.charAt(i + 1) == 'E') {
                    block_quote = false;
                    i++;
                    process_regular_char = false;
                } else
                    process_regular_char = true;
            } else if (escape_next) {
                escape_next = false;
                switch (c) {
                case 'Q':
                    block_quote = true;
                    process_regular_char = false;
                    break;
                default:
                    process_regular_char = true;
                }
            } else {
                process_regular_char = false;
                switch (c) {
                // char class start
                case '[':
                    if (char_class)
                        throw new FilterSyntaxException("Illegal character inside char class '" + c + "'");
                    char_class = true;
                    char_class_start = true;
                    break;
                // char class end
                case ']':
                    if (!char_class)
                        throw new FilterSyntaxException("Illegal character outside char class '" + c + "'");
                    if (char_class_range)
                        throw new FilterSyntaxException("Not terminated char class range");
                    char_class = false;
                    next_char = true;
                    break;
                // char class negation
                case '^':
                    if (char_class_start) {
                        char_class_start = false;
                        inverse = true;
                    } else
                        process_regular_char = true;
                    break;
                // char class range
                case '-':
                    if (char_class) {
                        if (char_class_range || last_char == '\0')
                            throw new FilterSyntaxException("Illegal usage of range '" + c + "'");
                        char_class_range = true;
                    } else
                        process_regular_char = true;
                    break;
                // back-quote escaping
                case '\\':
                    escape_next = true;
                    break;
                // reserved characters (for composite symbols and for future extensions) are not allowed outside char class,
                // unless they are escaped by back-quote
                case '*':
                    if (!char_class)
                        break main_loop; // early break on wild-card
                    // FALLS-THROUGH !
                case '!': case '|': case '&': case '?': case '+':
                case '(': case ')': case '<': case '>':
                case '~':  case '"': case '\'': case '`':
                    if (!char_class)
                        throw new FilterSyntaxException("Reserved character in pattern '" + c + "'");
                    process_regular_char = true;
                    break;
                // regular single character
                default:
                    process_regular_char = true;
                }
            }
            if (process_regular_char) {
                if (char_class_range) {
                    for (int d = last_char; d <= c; d++)
                        setChar(bits, length, d);
                    last_char = '\0';
                } else {
                    setChar(bits, length, c);
                    last_char = c;
                }
                char_class_start = false;
                char_class_range = false;
                if (!char_class)
                    next_char = true;
            }
            if (next_char) {
                if (inverse) {
                    // Note that '\0' should be never accepted.
                    for (int d = 1; d <= MAX_CHAR; d++)
                        invertChar(bits, length, d);
                    inverse = false;
                }
                length++;
                next_char = false;
            }
        }
        if (block_quote)
            throw new FilterSyntaxException("Block quote started with '\\Q' shall be terminated with '\\E'");
        if (escape_next)
            throw new FilterSyntaxException("Missing symbol after '\\' escape character");
        if (char_class)
            throw new FilterSyntaxException("Character class is not terminated with ']' character");
        pos[0] = i;
        // return correctly sized array
        if (length == 0)
            return EMPTY_BITS;
        if (bits.length > (length << BITS_LENGTH_SHIFT))
            bits = Arrays.copyOf(bits, length << BITS_LENGTH_SHIFT);
        return bits;
    }

    private static void charRangeCheck(char c) {
        if (c < ' ' || c >= MAX_CHAR)
            throw new FilterSyntaxException("Character is out of range '" + c + "'");
    }

    @Override
    public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
        DataScheme scheme = getScheme();
        if (scheme == null)
            scheme = record.getScheme(); // for backwards compatibility
        if (cipher == scheme.getCodec().getWildcardCipher())
            return true;
        if (symbol != null) // String symbol is specified
            return negated ^ acceptString(symbol);
        // Only cipher is specified
        return negated ^ acceptCode(scheme.getCodec().decodeToLong(cipher));
    }

    private boolean acceptCode(long code) {
        for (int i = 0; i < prefixLength; i++) {
            int c = charAtCode(code, i);
            if (c == 0 || !hasChar(prefixBits, i, c))
                return false;
        }
        if (fixedLength && charAtCode(code, prefixLength) != 0)
            return false;
        if (suffixLength + midLength > 0) {
            int sl = prefixLength; // compute symbol length
            while (sl < 8 && charAtCode(code, sl) != 0)
                sl++;
            if (sl < prefixLength + midLength + suffixLength)
                return false;
            for (int i = 0; i < suffixLength; i++) {
                int c = charAtCode(code, sl - 1 - i);
                if (!hasChar(suffixBits, suffixLength - 1 - i, c))
                    return false;
            }
            //2 asterisks part
            if (midLength > 0)
                return matchMid(code, sl);
        }
        return true;
    }

    boolean acceptString(String symbol) {
        int sl = symbol.length();
        if (fixedLength && sl != prefixLength)
            return false;
        if (sl < prefixLength + midLength + suffixLength)
            return false;
        for (int i = 0; i < prefixLength; i++) {
            int c = Math.min(MAX_CHAR, symbol.charAt(i));
            if (!hasChar(prefixBits, i, c))
                return false;
        }
        for (int i = 0; i < suffixLength; i++) {
            int c = Math.min(MAX_CHAR, symbol.charAt(sl - 1 - i));
            if (!hasChar(suffixBits, suffixLength - 1 - i, c))
                return false;
        }

        //2 asterisks part
        if (midLength > 0)
            return matchMid(symbol, sl);

        return true;
    }

    private boolean matchMid(long code, int sl) {
        if (midLength == 1)
            return matchSingleChar(code, sl);

        if (bmMidPattern != null)
            return matchBoyerMoore(code, sl);

        return matchRabinCarp(code, sl);
    }

    private boolean matchMid(String symbol, int sl) {
        if (midLength == 1)
            return matchSingleChar(symbol, sl);

        if (bmMidPattern != null)
            return matchBoyerMoore(symbol, sl);

        return matchRabinKarp(symbol, sl);
    }

    private boolean matchSingleChar(long code, int sl) {
        for (int i = prefixLength; i < sl - suffixLength; i++) {
            int c = charAtCode(code, i);
            if (hasChar(midBits, 0, c))
                return true;
        }
        return false;
    }

    private boolean matchSingleChar(String symbol, int sl) {
        for (int i = prefixLength; i < sl - suffixLength; i++) {
            int c = Math.min(MAX_CHAR, symbol.charAt(i));
            if (hasChar(midBits, 0, c))
                return true;
        }
        return false;
    }

    private boolean matchBoyerMoore(long code, int sl) {
        outer_search_loop:
        for (int i = prefixLength; i <= sl - suffixLength - midLength; ) {
            for (int j = midLength- 1; j >= 0; j--) {
                if (bmMidPattern[j] != charAtCode(code, i + j)) {
                    i += Math.max(1, j - bmSkipArray[charAtCode(code, i + j)]);
                    continue outer_search_loop;
                }
            }
            return true;
        }
        return false;
    }

    private boolean matchBoyerMoore(String symbol, int sl) {
        outer_search_loop:
        for (int i = prefixLength; i <= sl - suffixLength - midLength; ) {
            for (int j = midLength - 1; j >= 0; j--) {
                int c = Math.min(MAX_CHAR, symbol.charAt(i + j));
                if (bmMidPattern[j] != c) {
                    i += Math.max(1, j - bmSkipArray[c]);
                    continue outer_search_loop;
                }
            }
            return true;
        }
        return false;
    }

    private boolean matchRabinCarp(long code, int sl) {
        int hs = hashCode(code, prefixLength, prefixLength + midLength);
        outer_search_loop:
        for (int i = prefixLength; i <= sl - suffixLength - midLength; i++) {
            if (i != prefixLength)
                hs = rollingHash(hs, code, i + midLength - 1, i - 1, rollingHashOutMultiplier);
            if (midPatternHashes.contains(hs)) {
                for (int j = 0; j < midLength; j++) {
                    if (!hasChar(midBits, j, charAtCode(code, i + j)))
                        continue outer_search_loop;
                }
                return true;
            }
        }
        return false;
    }

    private boolean matchRabinKarp(String symbol, int sl) {
        int hs = hashCode(symbol, prefixLength, prefixLength + midLength);
        outer_search_loop:
        for (int i = prefixLength; i <= sl - suffixLength - midLength; i++) {
            if (i != prefixLength)
                hs = rollingHash(hs, symbol, i + midLength - 1, i - 1, rollingHashOutMultiplier);
            if (midPatternHashes.contains(hs)) {
                for (int j = 0; j < midLength; j++) {
                    int c = Math.min(MAX_CHAR, symbol.charAt(i + j));
                    if (!hasChar(midBits, j, c))
                        continue outer_search_loop;
                }
                return true;
            }
        }
        return false;
    }

    private List<char[]> generateMidPatterns() {
        if (midLength <= 1)
            return Collections.emptyList();

        List<char[]> patterns = new ArrayList<>();
        for (char c = 0; c < MAX_CHAR; c++)
            if (hasChar(midBits, 0, c)) {
                char[] p = new char[midLength];
                p[0] = c;
                patterns.add(p);
            }

        for (int i = 1; i < midLength; i++) {
            int sbLength = patterns.size();
            int updates = 0;
            for (char c = 0; c < MAX_CHAR; c++) {
                if (hasChar(midBits, i, c)) {
                    if (updates == 0) {
                        for (char[] p : patterns)
                            p[i] = c;
                        updates++;
                    } else {
                        for (int j = 0; j < sbLength; j++) {
                            char[] p = Arrays.copyOf(patterns.get(j), midLength);
                            p[i] = c;
                            patterns.add(p);
                            if (patterns.size() > MAX_MID_PATTERNS)
                                throw new FilterSyntaxException("Too many middle patterns, max=" + MAX_MID_PATTERNS);
                        }
                    }
                }
            }
        }
        return patterns;
    }

    private LongHashSet computeMidHashes(List<char[]> patterns) {
        if (patterns.isEmpty())
            return null;

        LongHashSet hashes = new LongHashSet(patterns.size());
        for (char[] p : patterns)
            hashes.add(hashCode(p));
        return hashes;
    }

    private char[] computeBoyerMoorePattern(List<char[]> patterns) {
        if (patterns.size() != 1)
            return null;

        return Arrays.copyOf(patterns.get(0), midLength);
    }

    private int[] computeBoyerMooreSkipArray() {
        if (bmMidPattern == null)
            return null;

        int skipArray[] = new int[MAX_CHAR + 1];
        for (int i = 0; i < skipArray.length; i++)
            skipArray[i] = -1;
        for (int j = 0; j < bmMidPattern.length; j++)
            skipArray[bmMidPattern[j]] = j;
        return skipArray;
    }

    // result will be 0 when i >= decode(cipher).length()
    private static int charAtCode(long code, int i) {
        return (int) ((code >>> ((7 - i) << 3)) & 0xff);
    }

    private static boolean hasChar(int[] bits, int i, int c) {
        return (bits[(i << BITS_LENGTH_SHIFT) + (c >> BITS_CHAR_SHIFT)] & (1 << (c & BITS_CHAR_MASK))) != 0;
    }

    private static void setChar(int[] bits, int i, int c) {
        bits[(i << BITS_LENGTH_SHIFT) + (c >> BITS_CHAR_SHIFT)] |= 1 << (c & BITS_CHAR_MASK);
    }

    private static void invertChar(int[] bits, int i, int c) {
        bits[(i << BITS_LENGTH_SHIFT) + (c >> BITS_CHAR_SHIFT)] ^= 1 << (c & BITS_CHAR_MASK);
    }

    private static int hashCode(char[] arr) {
        int result = 0;
        for (char c : arr )
            result = 31 * result + c;

        return result;
    }

    private static int hashCode(String s, int beginIndex, int endIndex) {
        int result = 0;
        for (int i = beginIndex; i < endIndex; i++)
            result = 31 * result + s.charAt(i);

        return result;
    }

    private static int hashCode(long code, int beginIndex, int endIndex) {
        int result = 0;
        for (int i = beginIndex; i < endIndex; i++)
            result = 31 * result + charAtCode(code, i);

        return result;
    }

    private static int rollingHash(int hs, String s, int inIndex, int outIndex, int outMultiplier) {
        return 31 * hs + s.charAt(inIndex) - outMultiplier * s.charAt(outIndex);
    }

    private static int rollingHash(int hs, long code, int inIndex, int outIndex, int outMultiplier) {
        return 31 * hs + charAtCode(code, inIndex) - outMultiplier * charAtCode(code, outIndex);
    }
}
