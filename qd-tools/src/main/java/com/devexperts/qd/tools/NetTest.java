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
package com.devexperts.qd.tools;

import com.devexperts.services.ServiceProvider;
import com.devexperts.util.TimePeriod;

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
    private final OptionSymbols symbols = new OptionSymbols();
    private final OptionInteger connections = new OptionInteger('C', "connections", "<number>", "Number of connections to create.");
    private final Option wildcard = new Option('w', "wildcard", "Enable wildcard subscription (for stream collector).");
    private final OptionLog logfile = OptionLog.getInstance();
    private final OptionStat stat = new OptionStat();

    @Override
    protected Option[] getOptions() {
        return new Option[] { logfile, collector, stripe, symbols, connections, stat, wildcard };
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
        config.address = args[1];
        if (connections.isSet())
            config.connectionsNum = connections.getValue();
        config.optionCollector = collector;
        if (symbols.isSet()) {
            config.totalSymbols = symbols.getTotal();
            config.symbolsPerEntity = symbols.getPerEntity();
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
        private int total;
        private int perEntity;

        OptionSymbols() {
            super('S', "symbols", "<total>[/per-entity>]",
                "Number of symbols. (<total> is total number of used symbols and " +
                "<per-entity> is average number of symbols interesting for one entity " +
                "connection (distributor or agent), same as <total> by default)."
            );
        }

        public int getTotal() {
            return total;
        }

        public int getPerEntity() {
            return perEntity;
        }

        @Override
        public int parse(int i, String[] args) throws OptionParseException {
            i = super.parse(i, args);
            int slashPos = value.indexOf('/');
            String totalStr;
            String entityStr;
            if (slashPos == -1) {
                totalStr = value;
                entityStr = null;
            } else {
                totalStr = value.substring(0, slashPos);
                entityStr = value.substring(slashPos + 1);
            }
            try {
                total = Integer.parseInt(totalStr);
            } catch (NumberFormatException e) {
                throw new OptionParseException("<total> must be a number");
            }
            if (entityStr != null) {
                try {
                    perEntity = Integer.parseInt(entityStr);
                } catch (NumberFormatException e) {
                    throw new OptionParseException("<per-entity> must be a number");
                }
            } else {
                perEntity = total;
            }
            if ((total < 0) || (perEntity < 0)) {
                throw new OptionParseException("<total> and <per-entity> must be non-negative");
            }
            if (perEntity > total)
                throw new OptionParseException("<per-entity> must not be greater than <total>");
            return i;
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

