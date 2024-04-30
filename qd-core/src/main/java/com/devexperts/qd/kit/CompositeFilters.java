/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.kit;

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.StableSubscriptionFilter;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.SubscriptionFilterFactory;
import com.devexperts.qd.spi.QDFilterContext;
import com.devexperts.qd.spi.QDFilterFactory;
import com.devexperts.qd.util.SymbolSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nonnull;

/**
 * {@code SubscriptionFilter} utilities that combine other filters with ',' (or) operations,
 * '&amp;' (and) operations, and '!' (not) operations. Grouping with opening '(' and closing ')' parenthesis
 * is also supported.
 */
public class CompositeFilters {
    private static final char EOLN = 0;
    private static final char SPACE = ' ';
    private static final char NOT = '!';
    private static final char AND = '&';
    private static final char OR = ',';
    private static final char OPEN_PAREN = '(';
    private static final char CLOSE_PAREN = ')';
    private static final char OPEN_BRACE = '[';
    private static final char CLOSE_BRACE = ']';
    private static final char OPEN_CURL = '{';
    private static final char CLOSE_CURL = '}';
    private static final char BACKSPACE = '\\';
    private static final char RECORD = ':';
    private static final char ANY = '*';

    private static final Comparator<QDFilter> FILTERS_BY_KIND = (f1, f2) -> f1.getKind().compareTo(f2.getKind());

    private static final Logging log = Logging.getLogging(CompositeFilters.class);

    private CompositeFilters() {}

    /**
     * Parses filter specification and combines them with project-specific filters defined in the
     * {@code scheme} with ',' (or) operations, '&amp;' (and) operations, and '!' (not) operations.
     * Grouping with opening '(' and closing ')' parenthesis is also supported.
     * This method shows warning if it suspects that there is a typo in record name.
     *
     * @throws NullPointerException if spec is null.
     * @throws FilterSyntaxException if spec is invalid.
     */
    public static QDFilter valueOf(String spec, DataScheme scheme) {
        QDFilter result = parse(spec, scheme, scheme.getService(QDFilterFactory.class), QDFilterContext.DEFAULT);
        result.setNameOrDefault(spec);
        return result;
    }

    /**
     * Parses filter specification and attaches project-specific name string to it.
     * This method <b>does not</b> show any warnings of typos.
     * Complex filter transformations (logical operations) try to retain the specified name.
     *
     * @see #valueOf(String, DataScheme)
     * @throws IllegalArgumentException if name is invalid.
     */
    public static QDFilter valueOf(String spec, String name, DataScheme scheme) {
        QDFilter result = parse(spec, scheme, scheme.getService(QDFilterFactory.class), QDFilterContext.NAMED);
        result.setShortName(name);
        return result;
    }

    /**
     * Returns a factory that will parse a filter specification into a filter with project-specific
     * filters defined in the {@code scheme} as with {@link #valueOf(String, DataScheme)} method.
     */
    public static QDFilterFactory getFactory(DataScheme scheme) {
        QDFilterFactory baseFactory = scheme.getService(QDFilterFactory.class);
        if (baseFactory == null)
            return new Factory(scheme);
        return baseFactory instanceof Factory ? baseFactory : new Factory(baseFactory);
    }

    /**
     * Parses filter specification and combines filters defined by {@code baseFactory}
     * with ',' (or) operations, '&amp;' (and) operations, and '!' (not) operations.
     * Grouping with opening '(' and closing ')' parenthesis is also supported.
     * Anything that is not supported by {@code baseFactory}
     * (when {@link QDFilterFactory#createFilter(String)} returns null)
     * is run through {@link PatternFilter}.
     *
     * @throws NullPointerException if spec is null.
     * @throws FilterSyntaxException if spec is invalid.
     */
    public static SubscriptionFilter valueOf(String spec, SubscriptionFilterFactory baseFactory) {
        QDFilterFactory factory = QDFilterFactory.fromFilterFactory(baseFactory);
        return nullForAnything(parse(spec, factory.getScheme(), factory, QDFilterContext.DEFAULT));
    }

    private static QDFilter parse(String spec, DataScheme scheme, QDFilterFactory factory, QDFilterContext context) {
        if (spec.isEmpty())
            return QDFilter.ANYTHING;
        if (factory instanceof Factory)
            factory = ((Factory) factory).baseFactory;
        Parser parser = new Parser(spec, scheme, factory, context);
        QDFilter result = parser.parseOr();
        if (parser.cur != EOLN)
            throw new FilterSyntaxException("Unexpected end of complex filter specification");
        return result;
    }

    /**
     * Returns filter that accepts only a specified collection of records.
     */
    public static RecordOnlyFilter forRecords(Collection<DataRecord> records) {
        // RecordOnlyFilter formally requires a scheme although it probably could work without one.
        if (records.isEmpty())
            throw new IllegalArgumentException("At least one record must be specified to derive scheme");
        DataScheme scheme = records.iterator().next().getScheme();
        boolean[] accepts = new boolean[scheme.getRecordCount()];
        for (DataRecord record : records) {
            if (record.getScheme() != scheme)
                throw new IllegalArgumentException("Records from different schemes");
            accepts[record.getId()] = true;
        }
        return new FastRecordFilter(scheme, accepts);
    }

    /**
     * Returns named token filter that accepts only records accepted by specified predicate filter.
     */
    public static RecordOnlyFilter forRecords(DataScheme scheme, String name, Predicate<DataRecord> filter) {
        return new FastRecordFilter(scheme, name, filter);
    }

    /**
     * Returns a stable filter that is the same or more encompassing as this filter.
     * This method never returns null.
     * @see QDFilter#toStableFilter()
     */
    public static QDFilter toStableFilter(SubscriptionFilter filter) {
        if (filter instanceof StableSubscriptionFilter) {
            QDFilter result = QDFilter.fromFilter(((StableSubscriptionFilter) filter).toStableFilter(), null);
            if (result.toStableFilter() != result)
                throw new IllegalArgumentException(filter + ": .toStableFilter returns filter that is not itself stable");
            return result;
        }
        return QDFilter.ANYTHING;
    }

    /**
     * Returns new filter that performs logical-or operation on filters.
     * This method never returns null, but accepts null arguments for backwards compatibility
     * instead of {@link QDFilter#ANYTHING}.
     */
    public static QDFilter makeOr(QDFilter one, QDFilter two) {
        OrBuilder builder = new OrBuilder();
        builder.add(one == null ? QDFilter.ANYTHING : one);
        builder.add(two == null ? QDFilter.ANYTHING : two);
        return builder.build(null);
    }

    /**
     * Returns new filter that performs logical-or operation on filters.
     * @deprecated Use {@link #makeOr(QDFilter, QDFilter)} instead and use {@link QDFilter#ANYTHING}
     *             instead of null arguments.
     */
    public static SubscriptionFilter makeOr(SubscriptionFilter one, SubscriptionFilter two) {
        return one == null || two == null ? null :
            nullForAnything(makeOr(QDFilter.fromFilter(one, null), QDFilter.fromFilter(two, null)));
    }

    /**
     * Returns new filter that performs logical-and operation on filters.
     * This method never returns null, but accepts null arguments for backwards compatibility
     * instead of {@link QDFilter#ANYTHING}.
     */
    public static QDFilter makeAnd(QDFilter one, QDFilter two) {
        AndBuilder builder = new AndBuilder();
        builder.add(one == null ? QDFilter.ANYTHING : one);
        builder.add(two == null ? QDFilter.ANYTHING : two);
        return builder.build(null);
    }

    /**
     * Returns new filter that performs logical-and operation on filters.
     * @deprecated Use {@link #makeAnd(QDFilter, QDFilter)} instead and use {@link QDFilter#ANYTHING}
     *             instead of null arguments.
     */
    public static SubscriptionFilter makeAnd(SubscriptionFilter one, SubscriptionFilter two) {
        if (one == null)
            return two;
        if (two == null)
            return one;
        return nullForAnything(makeAnd(QDFilter.fromFilter(one, null), QDFilter.fromFilter(two, null)));
    }

    /**
     * Returns new filter that performs logical-not operation on filters.
     * Resulting filter always accepts wildcard character for any record that is accepted
     * by the underlying filter.
     * This method never returns null, but accepts null argument for backwards compatibility
     * instead of {@link QDFilter#ANYTHING}.
     *
     * <p>Application of this method to {@link QDFilter#ANYTHING} will result in
     * {@link QDFilter#NOTHING} and vice versa.</p>
     */
    public static QDFilter makeNot(QDFilter filter) {
        return filter == null ? QDFilter.NOTHING : filter.negate();
    }

    /**
     * Returns new filter that performs logical-not operation on filters.
     * Resulting filter always accepts wildcard character for any record that is accepted
     * by the underlying filter.
     * @deprecated Use {@link #makeNot(QDFilter)} instead and use {@link QDFilter#ANYTHING}
     *             instead of null arguments.
     */
    public static SubscriptionFilter makeNot(SubscriptionFilter filter) {
        return nullForAnything(makeNot(QDFilter.fromFilter(filter, null)));
    }

    //-------------------- general static helper methods --------------------

    private static SubscriptionFilter nullForAnything(QDFilter filter) {
        return filter == QDFilter.ANYTHING ? null : filter;
    }

    private static QDFilter wrapFastFilter(QDFilter f, boolean warnOnPotentialTypo) {
        if (f.isDynamic())
            return f; // do not support wrapping of dynamic filters
        switch (f.getKind()) {
        case RECORD_ONLY:
            return f instanceof FastRecordFilter ? f : new FastRecordFilter(f).warnOnPotentialTypo(warnOnPotentialTypo);
        case SYMBOL_SET:
            return f instanceof SymbolSetFilter ? f : new SymbolSetFilter(f);
        default:
            return f;
        }
    }

    private static QDFilter.Kind getListFilterKind(List<QDFilter> list) {
        int ro = 0;
        int so = 0;
        int n = list.size();
        for (int i = 0; i < n; i++) {
            QDFilter.Kind kind = list.get(i).getKind();
            if (kind.isRecordOnly())
                ro++;
            else if (kind.isSymbolOnly())
                so++;
        }
        if (ro == n)
            return QDFilter.Kind.RECORD_ONLY;
        if (so == n)
            return QDFilter.Kind.OTHER_SYMBOL_ONLY;
        return QDFilter.Kind.OTHER;
    }

    private static List<QDFilter> negateList(QDFilter[] list) {
        List<QDFilter> result = new ArrayList<>(list.length);
        for (QDFilter filter : list)
            result.add(filter.negate());
        return result;
    }

    //-------------------- private nested classes --------------------

    private static class Parser {
        static final int N_CHAR_KINDS = 128;
        static final boolean[] SPECIAL = new boolean[N_CHAR_KINDS];
        static final boolean[] DELIMITER = new boolean[N_CHAR_KINDS];
        static final Map<String,Character> KEYWORDS = new HashMap<>();

        static {
            // characters with special meaning to composite filters
            SPECIAL[EOLN] = true;
            SPECIAL[NOT] = true;
            SPECIAL[AND] = true;
            SPECIAL[OR] = true;
            SPECIAL[OPEN_PAREN] = true;
            SPECIAL[CLOSE_PAREN] = true;

            // additional characters that delimit keywords
            System.arraycopy(SPECIAL, 0, DELIMITER, 0, N_CHAR_KINDS);
            DELIMITER[SPACE] = true;
            DELIMITER[RECORD] = true;
            DELIMITER[ANY] = true;
            DELIMITER[OPEN_CURL] = true;
            DELIMITER[CLOSE_CURL] = true;

            KEYWORDS.put("not", NOT);
            KEYWORDS.put("and", AND);
            KEYWORDS.put("or", OR);
            KEYWORDS.put("record", RECORD);
            KEYWORDS.put("any", ANY);
        }

        final String spec;
        final DataScheme scheme;
        final QDFilterFactory baseFactory;
        final QDFilterContext context;

        int pos;
        int next;
        char cur;
        boolean keyword;

        Parser(String spec, DataScheme scheme, QDFilterFactory baseFactory, QDFilterContext context) {
            this.spec = spec;
            this.scheme = scheme;
            this.baseFactory = baseFactory;
            this.context = context;
            /*
             * Always skip leading spaces, but everything else is significant.
             * All other spaces are significant. For example "\ \ " is a pattern with two escaped space characters
             * that matches a string of two space characters.
             */
            this.pos = skipSpaces(0);
            initCur();
        }

        // returns true for characters with special meaning to composite filters
        private boolean isSpecial(char c) {
            return c < SPECIAL.length && SPECIAL[c];
        }

        // returns true for characters that delimit keywords
        private boolean isDelimiter(char c) {
            return c < DELIMITER.length && DELIMITER[c];
        }

        private char getAtSpaced(int pos) {
            return (pos < 0 || pos >= spec.length()) ? SPACE : spec.charAt(pos);
        }

        private int skipSpaces(int pos) {
            while (pos < spec.length() && spec.charAt(pos) == SPACE)
                pos++;
            return pos;
        }

        private void initCur() {
            // check for potential start of keyword
            int pos = skipSpaces(this.pos);
            if (isDelimiter(getAtSpaced(pos - 1)) && !isDelimiter(getAtSpaced(pos))) {
            next_keyword:
                for (Map.Entry<String, Character> kw : KEYWORDS.entrySet()) {
                    String key = kw.getKey();
                    int n = key.length();
                    for (int i = 0; i < n; i++)
                        if (getAtSpaced(pos + i) != key.charAt(i))
                            continue next_keyword;
                    if (!isDelimiter(getAtSpaced(pos + n)))
                        continue next_keyword;
                    cur = kw.getValue();
                    next = skipSpaces(pos + n);
                    keyword = true;
                    return;
                }
            }
            // look actual pos (not skipping spaces) if keyword is not found
            pos = this.pos;
            keyword = false;
            // check for EOLN
            if (pos >= spec.length()) {
                cur = EOLN;
                next = pos;
                return;
            }
            cur = spec.charAt(pos);
            next = pos + 1;
        }

        private void next() {
            pos = next;
            initCur();
        }

        QDFilter parseOr() {
            QDFilter filter = parseAnd();
            if (cur == OR) {
                OrBuilder builder = new OrBuilder();
                builder.add(filter);
                do {
                    next();
                    builder.add(parseAnd());
                } while (cur == OR);
                filter = builder.build(null);
            }
            return filter;
        }

        private QDFilter parseAnd() {
            QDFilter filter = parseToken();
            if (cur == AND) {
                AndBuilder builder = new AndBuilder();
                builder.add(filter);
                do {
                    next();
                    builder.add(parseToken());
                } while (cur == AND);
                filter = builder.build(null);
            }
            return filter;
        }

        private QDFilter parseToken() {
            switch (cur) {
            case NOT:
                next();
                return makeNot(parseToken());
            case OPEN_PAREN:
                next();
                QDFilter result = parseOr();
                if (cur != CLOSE_PAREN)
                    throw new FilterSyntaxException("Missing ')' in complex filter specification");
                next();
                return result;
            default:
                int brace_level = 0;
                int curl_level = 0;
                StringBuilder ss = new StringBuilder();
                while ((cur != EOLN && (brace_level > 0 || curl_level > 0)) || !isSpecial(cur)) {
                    // brackets shield everything
                    switch (cur) {
                    case OPEN_BRACE:
                        brace_level++;
                        break;
                    case CLOSE_BRACE:
                        brace_level--;
                        if (brace_level < 0)
                            throw new FilterSyntaxException("Extra ']' in complex filter specification");
                    }
                    // backspace completely shields next char from everything
                    if (cur == BACKSPACE && pos + 1 < spec.length()) {
                        char quotedChar = spec.charAt(pos + 1);
                        if (quotedChar == 'Q') {
                            // \Q... starts block quote that goes until \E
                            pos += 2;
                            while (pos + 1 < spec.length() && (spec.charAt(pos) != '\\' || spec.charAt(pos + 1) != 'E'))
                                ss.append(spec.charAt(pos++));
                            if (pos + 1 >= spec.length())
                                throw new FilterSyntaxException("Block quote started with '\\Q' shall be terminated with '\\E'");
                        } else
                            ss.append(BACKSPACE).append(quotedChar);
                        pos += 2;
                        initCur();
                        continue;
                    }
                    // curly braces (outside of brackets) shield everything, too
                    if (brace_level == 0) {
                        switch (cur) {
                        case OPEN_CURL:
                            curl_level++;
                            break;
                        case CLOSE_CURL:
                            curl_level--;
                            if (curl_level < 0)
                                throw new FilterSyntaxException("Extra '}' in complex filter specification");
                        }
                    }
                    // do not expand bracketed keywords into their characters
                    if (brace_level > 0 && keyword)
                        ss.append(spec.substring(pos, next));
                    else
                        ss.append(cur);
                    next();
                }
                if (brace_level > 0)
                    throw new FilterSyntaxException("Missing ']' in complex filter specification");
                if (curl_level > 0)
                    throw new FilterSyntaxException("Missing '}' in complex filter specification");
                if (ss.length() == 0)
                    throw new FilterSyntaxException("Filter pattern missing in complex filter specification");
                String name = ss.toString();
                QDFilter filter = createWithBaseFactory(name);
                filter.setName(name);
                return wrapFastFilter(filter, context == QDFilterContext.DEFAULT || context == QDFilterContext.RECORD_ONLY);
            }
        }

        private QDFilter createWithBaseFactory(String spec) {
            QDFilter filter = baseFactory != null ? baseFactory.createFilter(spec, context) : null;
            if (filter == null)
                filter = PatternFilter.valueOfImpl(spec, spec, scheme);
            if (context == QDFilterContext.RECORD_ONLY && filter instanceof PatternFilter)
                filter = PatternFilter.valueOfImpl(":" + ((PatternFilter) filter).getPattern(), spec, scheme);
            return filter;
        }
    }

    private static class Factory extends QDFilterFactory {
        final QDFilterFactory baseFactory;

        Factory(QDFilterFactory baseFactory) {
            super(baseFactory.getScheme());
            this.baseFactory = baseFactory;
        }

        Factory(DataScheme scheme) {
            super(scheme);
            this.baseFactory = null;
        }

        @Override
        public QDFilter createFilter(String spec) {
            return createFilter(spec, QDFilterContext.DEFAULT);
        }

        @Override
        public QDFilter createFilter(String spec, QDFilterContext context) {
            return parse(spec, getScheme(), baseFactory, context);
        }
    }

    private abstract static class ListBuilder {
        final List<QDFilter> list = new ArrayList<>();

        abstract void add(QDFilter other);
        abstract QDFilter create(List<QDFilter> list, ListFilter source);
        abstract void symbolSetJoin(SymbolSet set, SymbolSet other);

        ListBuilder addAll(List<QDFilter> list) {
            list.forEach(this::add);
            return this;
        }

        QDFilter build(ListFilter source) {
            if (list.isEmpty())
                return QDFilter.ANYTHING;
            if (list.size() == 1)
                return list.get(0);
            Collections.sort(list, FILTERS_BY_KIND);
            // collect record only filter
            int iRecordOnly = 0; // index of record filters in list
            while (iRecordOnly < list.size() && list.get(iRecordOnly).getKind() == QDFilter.Kind.RECORD_ONLY)
                iRecordOnly++;
            if (iRecordOnly > 1) { // we have multiple record filters to collect
                for (int i = 0; i < iRecordOnly; i++) // unwrap them all
                    list.set(i, list.get(i).unwrap());
                if (iRecordOnly < list.size()) { // we a mix of record filters and regular filters -- collect them separately
                    List<QDFilter> recordFilters = list.subList(0, iRecordOnly);
                    QDFilter rf = wrapFastFilter(create(recordFilters, null), true);
                    recordFilters.clear();
                    list.add(0, rf);
                    iRecordOnly = 1; // now just one remains
                }
            }
            // collect symbol set filters
            int iSymbolSet = iRecordOnly; // number of symbol set filters in list + iRecordOnly
            while (iSymbolSet < list.size() && list.get(iSymbolSet).getKind() == QDFilter.Kind.SYMBOL_SET)
                iSymbolSet++;
            List<QDFilter> symbolSetCombines = new ArrayList<>(list.subList(iRecordOnly, iSymbolSet));
            if (iSymbolSet - iRecordOnly > 1) { // we have multiple symbol set filters to collect
                QDFilter original = list.get(iRecordOnly);
                SymbolSet set = SymbolSet.copyOf(original.getSymbolSet());
                for (int i = iRecordOnly + 1; i < iSymbolSet; i++)
                    symbolSetJoin(set, list.get(i).getSymbolSet());
                list.subList(iRecordOnly, iSymbolSet).clear();
                list.add(iRecordOnly, createSymbolSetFilter(original, symbolSetCombines, set));
                iSymbolSet = iRecordOnly + 1;
            }
            if (this instanceof AndBuilder && iSymbolSet - iRecordOnly > 0) {
                // special optimization for ANDs -- check other _NON-DYNAMIC_ symbol-only filters
                assert iSymbolSet == iRecordOnly + 1;
                QDFilter original = list.get(iRecordOnly);
                SymbolSet set = SymbolSet.copyOf(original.getSymbolSet());
                int j = iSymbolSet;
                for (int i = iSymbolSet; i < list.size(); i++) {
                    QDFilter filter = list.get(i);
                    if (filter.getKind().isSymbolOnly() && !filter.isDynamic()) {
                        set.retainAll(filter);
                        symbolSetCombines.add(filter);
                    } else
                        list.set(j++, filter);
                }
                list.subList(j, list.size()).clear();
                if (set.isEmpty()) {
                    // it can be only empty if it was AndList. but here is a sanity check just in case...
                    log.warn("WARNING: Filter \"" + new AndFilter(symbolSetCombines, source) + "\" " +
                        "matches no symbol. You must quote '&' character using \"[&]\" to use it in symbol.");
                    return QDFilter.NOTHING;
                }
                list.set(iRecordOnly, createSymbolSetFilter(original, symbolSetCombines, set));
            }
            if (list.size() == 1)
                return list.get(0);
            return wrapFastFilter(create(list, source), true);
        }

        @Nonnull
        private SymbolSetFilter createSymbolSetFilter(QDFilter original, List<QDFilter> symbolSetCombines, SymbolSet set) {
            return new SymbolSetFilter(original.getScheme(), set, create(symbolSetCombines, null));
        }
    }

    private abstract static class ListFilter extends QDFilter implements QDFilter.UpdateListener {
        final QDFilter[] list;
        final Kind kind;
        final boolean stable;
        final boolean dynamic;
        final boolean fast;

        abstract char separator();
        abstract boolean parenthesis(QDFilter f);
        abstract QDFilter rebuild(List<QDFilter> list, ListFilter source);

        ListFilter(List<QDFilter> list, ListFilter source) {
            super(list.get(0).getScheme(), source);
            this.list = list.toArray(new QDFilter[list.size()]);
            kind = getListFilterKind(list);
            if (kind == Kind.RECORD_ONLY && getScheme() == null)
                throw new IllegalArgumentException("Record filter shall have scheme");
            boolean stable = true;
            boolean dynamic = false;
            boolean fast = true;
            for (QDFilter f : list) {
                stable &= f.isStable();
                dynamic |= f.isDynamic();
                fast &= f.isFast();
            }
            this.stable = stable;
            this.dynamic = dynamic;
            this.fast = fast;
        }

        @Override
        public Kind getKind() {
            return kind;
        }

        @Override
        public boolean isStable() {
            return stable;
        }

        @Override
        public boolean isDynamic() {
            return dynamic;
        }

        @Override
        public boolean isFast() {
            return fast;
        }

        @Override
        public QDFilter toStableFilter() {
            if (stable)
                return this;
            List<QDFilter> result = new ArrayList<>();
            for (QDFilter f : list)
                result.add(f.toStableFilter());
            return rebuild(result, null);
        }

        @Override
        protected void dynamicTrackingStart() {
            for (QDFilter f : list)
                f.addUpdateListener(this);
        }

        @Override
        protected void dynamicTrackingStop() {
            for (QDFilter f : list)
                f.removeUpdateListener(this);
        }

        @Override
        protected QDFilter produceUpdatedFilter() {
            List<QDFilter> result = new ArrayList<>();
            for (QDFilter f : list)
                result.add(f.getUpdatedFilter());
            return rebuild(result, this);
        }

        @Override
        public void filterUpdated(QDFilter filter) {
            fireFilterUpdated(null); // will synchronously use produceUpdateFilter
            // NOTE: it uses dynamicTrackingStart mechanics to add new listener (new instance) to the delegate
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            return Arrays.equals(list, ((ListFilter) o).list);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(list);
        }

        @Override
        public String getDefaultName() {
            StringBuilder sb = new StringBuilder();
            for (QDFilter f : list) {
                if (sb.length() > 0)
                    sb.append(separator());
                boolean parenthesis = parenthesis(f);
                if (parenthesis)
                    sb.append(OPEN_PAREN);
                sb.append(f);
                if (parenthesis)
                    sb.append(CLOSE_PAREN);
            }
            return sb.toString();
        }

        protected QDFilter negateShortName(QDFilter result) {
            if (hasShortName()) {
                String name = toString();
                name = name.startsWith("!") ? name.substring(1) : "!" + name;
                result.setName(name);
            }
            return result;
        }
    }

    private static class OrBuilder extends ListBuilder {
        private boolean anything;

        @Override
        QDFilter create(List<QDFilter> list, ListFilter source) {
            return new OrFilter(list, source);
        }

        @Override
        public void add(QDFilter other) {
            if (anything || other == QDFilter.NOTHING)
                return;
            if (other == null || other == QDFilter.ANYTHING) {
                anything = true;
                list.clear();
                return;
            }
            if (other instanceof OrFilter)
                list.addAll(Arrays.asList(((OrFilter) other).list));
            else
                list.add(other);
        }

        @Override
        void symbolSetJoin(SymbolSet set, SymbolSet other) {
            set.addAll(other);
        }
    }

    private static class OrFilter extends ListFilter {
        OrFilter(List<QDFilter> list, ListFilter source) {
            super(list, source);
        }

        @Override
        char separator() {
            return OR;
        }

        @Override
        boolean parenthesis(QDFilter f) {
            return false;
        }

        @Override
        QDFilter rebuild(List<QDFilter> list, ListFilter source) {
            return new OrBuilder().addAll(list).build(source);
        }

        @Override
        public QDFilter negate() {
            return negateShortName(new AndFilter(negateList(list), null));
        }

        @Override
        public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
            for (QDFilter f : list)
                if (f.accept(contract, record, cipher, symbol))
                    return true;
            return false;
        }

        @Override
        public SyntaxPrecedence getSyntaxPrecedence() {
            return hasShortName() ? SyntaxPrecedence.TOKEN : SyntaxPrecedence.OR;
        }
    }

    private static class AndBuilder extends ListBuilder {
        private boolean nothing;

        @Override
        void add(QDFilter other) {
            if (nothing || other == null || other == QDFilter.ANYTHING)
                return;
            if (other == QDFilter.NOTHING) {
                nothing = true;
                list.clear();
                list.add(QDFilter.NOTHING);
                return;
            }
            if (other instanceof AndFilter)
                list.addAll(Arrays.asList(((AndFilter) other).list));
            else
                list.add(other);
        }

        @Override
        QDFilter create(List<QDFilter> list, ListFilter source) {
            return new AndFilter(list, source);
        }

        @Override
        void symbolSetJoin(SymbolSet set, SymbolSet other) {
            set.retainAll(other);
        }
    }

    private static class AndFilter extends ListFilter {
        AndFilter(List<QDFilter> list, ListFilter source) {
            super(list, source);
        }

        @Override
        char separator() {
            return AND;
        }

        @Override
        boolean parenthesis(QDFilter f) {
            return f.getSyntaxPrecedence() == SyntaxPrecedence.OR;
        }

        @Override
        QDFilter rebuild(List<QDFilter> list, ListFilter source) {
            return new AndBuilder().addAll(list).build(source);
        }

        @Override
        public QDFilter negate() {
            return negateShortName(new OrFilter(negateList(list), null));
        }

        @Override
        public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
            for (QDFilter f : list)
                if (!f.accept(contract, record, cipher, symbol))
                    return false;
            return true;
        }

        @Override
        public SyntaxPrecedence getSyntaxPrecedence() {
            return SyntaxPrecedence.AND;
        }
    }

}
