/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools;

import com.devexperts.services.ServiceProvider;
import com.devexperts.util.TimePeriod;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tool that is used to test network throughput. It works either in
 * producer or in consumer mode. It creates one or more connections
 * to specified address and transmit randomly generated 'Quote' records
 * over it and counts number of produced/received quotes.
 *
 * @see NetTestSide
 */
@ToolSummary(
    info = "Tests network throughput.",
    argString = "<side> <address>",
    arguments = {
        "<side>    -- either 'p' (producer) or 'c' (consumer)",
        "<address> -- address to connect (see @link{address})"
    }
)
@ServiceProvider
public class NetTest extends AbstractTool {
    private final OptionCollector collector = new OptionCollector("ticker");
    private final OptionStripe stripe = new OptionStripe();
    private final OptionSticky sticky = new OptionSticky();
    private final OptionSymbols symbols = new OptionSymbols();
    private final OptionInteger connections = new OptionInteger('C', "connections", "<number>", "Number of instances to create.");
    private final Option wildcard = new Option('w', "wildcard", "Enable wildcard subscription (for stream collector).");
    private final OptionLog logfile = OptionLog.getInstance();
    private final OptionStat stat = new OptionStat();
    private final OptionName name = new OptionName("NetTest");

    @Override
    protected Option[] getOptions() {
        return new Option[] { logfile, collector, stripe, sticky, symbols, connections, stat, wildcard, name };
    }

    @Override
    protected void executeImpl(String[] args) {
        if (args.length == 0) {
            noArguments();
        }
        if (args.length != 2) {
            wrongNumberOfArguments();
        }

        if (!stat.isSet()) {
            stat.setValue(TimePeriod.valueOf(10000));
            stat.init();
        }
        NetTestConfig config = new NetTestConfig();
        config.name = name.getValue();
        config.address = args[1];
        if (connections.isSet())
            config.instanceCount = connections.getValue();
        config.optionCollector = collector;
        if (symbols.isSet()) {
            config.totalSymbols = symbols.getTotal();
            config.symbolsPerEntity = symbols.getPerEntity();
            config.sliceSelection = symbols.isSliceSelection();
            config.minLength = symbols.getMinLength();
            config.maxLength = symbols.getMaxLength();
            config.ipfPath = symbols.getIpfPath();
        }
        config.wildcard = wildcard.isSet();
        config.optionStat = stat;

        NetTestSide side;
        if (args[0].equalsIgnoreCase("p")) {
            side = new NetTestProducerSide(config);
        } else if (args[0].equalsIgnoreCase("c")) {
            side = new NetTestConsumerSide(config);
        } else {
            throw new BadToolParametersException("<side> must be either 'p' (producer) or 'c' (consumer)");
        }
        side.start();
    }

    static class OptionSymbols extends OptionString {
        private static final Pattern PATTERN = Pattern.compile(
            "^(?<total>\\d+)" +
                "(?:/(?:(?<mode>r|random|s|slice))?(?<perEntity>\\d+))" +
                "?(?:(?:\\{(?<length>\\d+)(?:,(?<maxLength>\\d+))?\\})" +
                     "|(?:@(?<filePath>.+)))" +
                "?$");
        private int total = 100000;
        private int perEntity = total;
        private boolean sliceSelection = false;
        private int minLength = -1;
        private int maxLength = -1;
        private String ipfPath = null;

        OptionSymbols() {
            super('S', "symbols",
                "<total>[/<split>][<source>]",
                "set of symbols to use (examples: 1000, 1000{8}, 1000/s10@ipf.ipf, 1000/r10{10,15}).\n" +
                    "<total>: total number of symbols in the pool (default: 100,000)\n" +
                    "<split> (opt): Controls pooled symbols distribution per distributor/agent\n" +
                    "  Format: [random|r|slice|s]<per-entity>\n" +
                    "  - random|r: randomly selects <per-entity> symbols (default),\n" +
                    "  - slice|s: sequentially assigns <per-entity> symbols.\n" +
                    "  If the pool's size is insufficient, the symbols will be reused cyclically.\n" +
                    "<source> (opt): Defines symbol source:\n" +
                    "  Format: {<length>}|{<min>,<max>}|@<path-to-ipf-file>\n" +
                    "  - {<length>}: Generate symbols of fixed length\n" +
                    "  - {<min>,<max>}: Generates symbols with lengths in given range\n" +
                    "  - @<path-to-ipf-file>: loads at most <total> first symbols from specified IPF file.\n" +
                    "When <source> is not specified, the symbol pool is filled with random symbols with lengths " +
                    "in range [1,6] characters, with peak probability (50%) at lengths 3 and 4.\n" +
                    "Note: Random generation uses fixed seeds, ensuring identical configurations produce " +
                    "the same symbol sets.\n"
            );
        }

        public int getTotal() {
            return total;
        }

        public int getPerEntity() {
            return perEntity;
        }

        public boolean isSliceSelection() {
            return sliceSelection;
        }

        public int getMinLength() {
            return minLength;
        }

        public int getMaxLength() {
            return maxLength;
        }

        public String getIpfPath() {
            return ipfPath;
        }

        @Override
        public int parse(int i, String[] args) throws OptionParseException {
            i = super.parse(i, args);
            parseValue(value);
            return i;
        }

        void parseValue(String spec) throws OptionParseException {
            if (spec.isEmpty())
                return;
            Matcher matcher = PATTERN.matcher(spec);
            if (matcher.matches()) {
                total = Integer.parseInt(matcher.group("total"));
                if (total <= 0) {
                    throw new OptionParseException("Total number of symbols must be greater than 0");
                }
                if (matcher.group("mode") != null) {
                    sliceSelection = matcher.group("mode").equals("slice") || matcher.group("mode").equals("s");
                }
                if (matcher.group("perEntity") != null) {
                    perEntity = Integer.parseInt(matcher.group("perEntity"));
                    if (perEntity > total) {
                        throw new OptionParseException(
                            "Number of symbols per entity must not be greater than total number of symbols");
                    }
                    if (perEntity <= 0) {
                        throw new OptionParseException("Number of symbols per entity must be greater than 0");
                    }
                } else {
                    perEntity = total;
                }
                if (matcher.group("length") != null) {
                    minLength = Integer.parseInt(matcher.group("length"));
                    maxLength = minLength;
                    if (minLength <= 0) {
                        throw new OptionParseException("Minimum length must be greater than 0");
                    }
                }
                if (matcher.group("maxLength") != null) {
                    maxLength = Integer.parseInt(matcher.group("maxLength"));
                    if (minLength > maxLength) {
                        throw new OptionParseException("Minimum length must not be greater than maximum length");
                    }
                }
                ipfPath = matcher.group("filePath");
            } else {
                throw new OptionParseException("Invalid symbols specification: " + spec);
            }
        }
    }

    @Override
    public Thread mustWaitForThread() {
        return Thread.currentThread();
    }

    public static void main(String[] args) {
        Tools.executeSingleTool(NetTest.class, args);
    }
}

