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

import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.tools.fs.FilteredStream;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.util.TimePeriod;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class OptionCollector extends OptionString implements EndpointOption {

    private final String defaultValue;
    private QDEndpoint.Builder endpointBuilder;

    public OptionCollector(String defaultValue) {
        this('c', "collector", "COLLECTOR may be one of \"ticker\", \"stream\", \"history\", \"all\" " +
                "and OPTION may be one of \"se\" (enables storeEverything mode, not applied for filtered stream), " +
                "\"nwc\" (disables wildcards), \"ts\" (enables timestamps), \"filtered=<value>\" (sets filter for stream)",
            defaultValue);
    }

    public OptionCollector(char shortName, String fullName, String description, String defaultValue) {
        super(shortName, fullName, "COLLECTOR[OPTION ...] ...", description);
        this.defaultValue = defaultValue;
    }

    public String getValueOrDefault() {
        return isSet() ? getValue() : defaultValue;
    }

    @Override
    public boolean isSupportedEndpointOption(QDEndpoint.Builder endpointBuilder) {
        return true;
    }

    @Override
    public void applyEndpointOption(QDEndpoint.Builder endpointBuilder) {
        this.endpointBuilder = endpointBuilder;
    }

    public QDEndpoint createEndpoint(String name) {
        return newEndpointBuilder(name).build();
    }

    public QDEndpoint.Builder newEndpointBuilder(String name) {
        Parser parser = new Parser();
        List<QDCollector.Factory> factories = parser.parse(getValueOrDefault());
        return endpointBuilder.clone().withName(name).withCollectors(factories);
    }

    public List<QDCollector.Factory> getCollectorFactories() {
        return new Parser().parse(getValueOrDefault());
    }

    public static class Parser {
        // Collector names
        private static final String ALL_SE = "all-se";
        private static final String TICKER_SE = "ticker-se";
        private static final String HISTORY_SE = "history-se";
        private static final String ALL_NWC = "all-nwc";
        private static final String STREAM_NWC = "stream-nwc";
        private static final String FILTERED_STREAM = "filtered-stream";
        private static final String ALL = "all";
        private static final String TICKER = "ticker";
        private static final String STREAM = "stream";
        private static final String HISTORY = "history";
        // Factories
        private List<QDCollector.Factory> factories;
        // Options
        private boolean se;
        private boolean ts;
        private boolean nwc;
        private String filteredValue;
        // Marks for options validation. They are used via reflection.
        private boolean seValid;
        private boolean tsValid;
        private boolean nwcValid;
        private boolean filteredValid;
        // Current collector name
        private String collector;

        public void setSe(boolean se) {
            checkOptionIsValid("se");
            this.se = se;
        }

        public void setTs(boolean ts) {
            checkOptionIsValid("ts");
            this.ts = ts;
        }

        public void setNwc(boolean nwc) {
            checkOptionIsValid("nwc");
            this.nwc = nwc;
        }

        public void setFiltered(String filteredValue) {
            checkOptionIsValid("filtered");
            this.filteredValue = filteredValue;
        }

        List<QDCollector.Factory> parse(String input) {
            factories = new ArrayList<>(3);
            List<String> collectors = splitCollectors(input);
            for (String c : collectors) {
                resetOptions();
                // Parse options and get collector name
                List<String> options = new ArrayList<>();
                collector = QDConfig.parseProperties(c, options);
                // FOR BACKWARD COMPATIBILITY: SUPPORT filtered-stream[XXX] format
                if (collector.equals(FILTERED_STREAM)) {
                    if (options.size() > 1)
                        throw new IllegalArgumentException("filtered-stream collector can have only filtered value in brackets");
                    if (options.size() == 1)
                        filteredValue = options.get(0);
                    factories.add(createStreamFactory(false, false, false, filteredValue));
                    continue;
                }
                // Mark valid options
                switch (collector) {
                case ALL_SE:
                    setValidOptions(false, false, false, false);
                    break;
                case TICKER_SE:
                    setValidOptions(false, false, false, false);
                    break;
                case HISTORY_SE:
                    setValidOptions(false, false, false, false);
                    break;
                case ALL_NWC:
                    setValidOptions(false, false, false, false);
                    break;
                case STREAM_NWC:
                    setValidOptions(false, false, false, false);
                    break;
                case ALL:
                    setValidOptions(true, true, true, true);
                    break;
                case TICKER:
                    setValidOptions(true, true, false, false);
                    break;
                case STREAM:
                    setValidOptions(true, true, true, true);
                    break;
                case HISTORY:
                    setValidOptions(true, true, false, false);
                    break;
                }
                // Set properties ...
                QDConfig.setProperties(this, options);
                // And create factory
                switch (collector) {
                case ALL_SE:
                    factories.add(createHistoryFactory(true, false));
                    factories.add(createStreamFactory(true, false, false, null));
                    factories.add(createTickerFactory(true, false));
                    break;
                case TICKER_SE:
                    factories.add(createTickerFactory(true, false));
                    break;
                case HISTORY_SE:
                    factories.add(createHistoryFactory(true, false));
                    break;
                case ALL_NWC:
                    factories.add(createHistoryFactory(false, false));
                    factories.add(createStreamFactory(false, false, true, null));
                    factories.add(createTickerFactory(false, false));
                    break;
                case STREAM_NWC:
                    factories.add(createStreamFactory(false, false, true, null));
                    break;
                case ALL:
                    factories.add(createHistoryFactory(se, ts));
                    factories.add(createStreamFactory(se, ts, nwc, filteredValue));
                    factories.add(createTickerFactory(se, ts));
                    break;
                case TICKER:
                    factories.add(createTickerFactory(se, ts));
                    break;
                case STREAM:
                    factories.add(createStreamFactory(se, ts, nwc, filteredValue));
                    break;
                case HISTORY:
                    factories.add(createHistoryFactory(se, ts));
                    break;
                default:
                    throw new IllegalArgumentException("Invalid collector name: " + c);
                }
            }
            return factories;
        }

        List<String> splitCollectors(String s) {
            s = s.replaceAll("\\s", "");
            List<String> collectors = new ArrayList<>();
            int curStart = 0;
            int deep = 0;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                case '[':
                    deep++;
                    break;
                case ']':
                    deep--;
                    break;
                case ',':
                    if (deep == 0) {
                        collectors.add(s.substring(curStart, i));
                        curStart = i + 1;
                    }
                }
                if (i == s.length() - 1)
                    collectors.add(s.substring(curStart));
            }
            return collectors;
        }

        private void setValidOptions(boolean seValid, boolean tsValid, boolean nwcValid, boolean filteredValid) {
            this.seValid = seValid;
            this.tsValid = tsValid;
            this.nwcValid = nwcValid;
            this.filteredValid = filteredValid;
        }

        private void checkOptionIsValid(String option) {
            try {
                String fieldName = option + "Valid";
                Field field = Parser.class.getDeclaredField(fieldName);
                boolean valid = field.getBoolean(this);
                if (!valid)
                    throw new IllegalArgumentException("\"" + option + "\" option is not valid for \"" + collector + "\" collector");
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        private void resetOptions() {
            se = false;
            ts = false;
            nwc = false;
            filteredValue = null;
        }

        private QDCollector.Factory createStreamFactory(boolean se, boolean ts, boolean nwc, String filtered) {
            return new QDCollector.Factory() {
                @Override
                public QDContract getContract() {
                    return QDContract.STREAM;
                }

                @Override
                public QDCollector createCollector(QDFactory factory, QDCollector.Builder<?> builder) {
                    QDCollector.Builder<QDStream> baseBuilder = factory.streamBuilder()
                        .copyFrom(builder)
                        .withEventTimeSequence(ts)
                        .withStoreEverything(se);
                    QDStream stream = (filtered == null) ? baseBuilder.build()
                        : new FilteredStream(factory, builder, TimePeriod.valueOf(filtered).getTime());
                    stream.setEnableWildcards(!nwc);
                    return stream;
                }

                @Override
                public QDStats.SType getStatsType() {
                    return QDContract.STREAM.getStatsType();
                }

                @Override
                public String toString() {
                    StringBuilder options = new StringBuilder();
                    appendOption("se", se, options);
                    appendOption("ts", ts, options);
                    appendOption("nwc", nwc, options);
                    appendOption("filtered", filtered, options);
                    return "Stream" + wrapOptions(options);
                }
            };
        }

        private QDCollector.Factory createTickerFactory(boolean se, boolean ts) {
            return new QDCollector.Factory() {
                @Override
                public QDContract getContract() {
                    return QDContract.TICKER;
                }

                @Override
                public QDCollector createCollector(QDFactory factory, QDCollector.Builder<?> builder) {
                    QDTicker ticker = factory.tickerBuilder()
                        .copyFrom(builder)
                        .withEventTimeSequence(ts)
                        .withStoreEverything(se)
                        .build();
                    return ticker;
                }

                @Override
                public QDStats.SType getStatsType() {
                    return QDContract.TICKER.getStatsType();
                }

                @Override
                public String toString() {
                    StringBuilder options = new StringBuilder();
                    appendOption("se", se, options);
                    appendOption("ts", ts, options);
                    return "Ticker" + wrapOptions(options);
                }
            };
        }

        private QDCollector.Factory createHistoryFactory(final boolean se, final boolean ts) {
            return new QDCollector.Factory() {
                @Override
                public QDContract getContract() {
                    return QDContract.HISTORY;
                }

                @Override
                public QDCollector createCollector(QDFactory factory, QDCollector.Builder<?> builder) {
                    QDHistory history = factory.historyBuilder()
                        .copyFrom(builder)
                        .withEventTimeSequence(ts)
                        .withStoreEverything(se)
                        .build();
                    return history;
                }

                @Override
                public QDStats.SType getStatsType() {
                    return QDContract.HISTORY.getStatsType();
                }

                @Override
                public String toString() {
                    StringBuilder options = new StringBuilder();
                    appendOption("se", se, options);
                    appendOption("ts", ts, options);
                    return "History" + wrapOptions(options);
                }
            };
        }

        private void appendOption(String optionName, Object optionValue, StringBuilder options) {
            boolean hasValue = optionValue != null && !(optionValue instanceof Boolean);
            boolean append = hasValue || (optionValue instanceof Boolean && (boolean) optionValue);
            if (append) {
                if (options.length() > 0)
                    options.append(",");
                options.append(optionName);
                if (hasValue)
                    options.append("=").append(optionValue);
            }
        }

        private String wrapOptions(StringBuilder options) {
            if (options.length() == 0)
                return "";
            return "[" + options + "]";
        }
    }
}
