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

import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.impl.RMIEndpointImpl;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.LogUtil;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

/**
 * Multiplexor tool.
 */
@ToolSummary(
    info = "Multiplexes a connection.",
    argString = "<distributor-address> <agent-address>",
    arguments = {
        "<distributor-address> -- uplink address for incoming data and outgoing subscription (see @link{address})",
        "<agent-address>       -- downlink address for incoming subscription and outgoing data (see @link{address})"
    }
)
@ServiceProvider
public class Multiplexor extends AbstractTool {
    private final OptionLog logfile = OptionLog.getInstance();
    private final OptionCollector collector = new OptionCollector("all");
    private final OptionStripe stripe = new OptionStripe();
    private final OptionFile file = new OptionFile();
    private final OptionWrite write = new OptionWrite(file);
    private final OptionTimePeriod delay = new OptionTimePeriod('d', "delay", "<n>", "Delay data by specified period (in seconds by default).");
    private final OptionDouble drop = new OptionDouble('P', "drop", "<n>", "Drop <n> % of data.", 1, 100);
    private final OptionName name = new OptionName("multiplexor");
    private final OptionForward forward = new OptionForward();
    private final OptionRoute route = new OptionRoute();

    private final OptionStat stat = new OptionStat();
    private final OptionManagementHtml html = OptionManagementHtml.getInstance();
    private final OptionManagementRmi rmi = OptionManagementRmi.getInstance();

    private final List<Closeable> closeOnExit = new ArrayList<>();

    private QDEndpoint endpoint;

    @Override
    protected Option[] getOptions() {
        return new Option[] {
            logfile, collector, stripe, file, write, delay, drop, name, forward, route, stat, html, rmi
        };
    }

    @Override
    protected void executeImpl(String[] args) {
        if (args.length == 0) {
            noArguments();
        }
        if (args.length != 2) {
            wrongNumberOfArguments();
        }

        endpoint = collector.newEndpointBuilder(name.getName())
            .withSubscribeSupport("multiplexor.qd.subscribe.")
            .build();
        FeedFileHandler feedFileHandler = file.initFile(endpoint, true);
        write.initFileWrite(feedFileHandler);

        String distributorAddress = args[0];
        String agentAddress = args[1];

        if (delay.isSet())
            log.info("Configured to delay data for " + delay.getValue() + " secs");
        if (drop.isSet())
            log.info("Configured to drop " + drop.getValue() + "% of data");

        log.info("Using agent address " + LogUtil.hideCredentials(agentAddress));
        AgentAdapter.Factory factory = new AgentAdapter.Factory(endpoint, null);
        RMIEndpoint forwardServerEndpoint = null;
        if (forward.isSet() || route.isSet()) {
            forwardServerEndpoint = new RMIEndpointImpl(RMIEndpoint.Side.SERVER, endpoint, factory, null);
            closeOnExit.add(forwardServerEndpoint);
            endpoint.initializeConnectorsForAddress(agentAddress);
        } else {
            endpoint.addConnectors(
                MessageConnectors.createMessageConnectors(factory, agentAddress, endpoint.getRootStats()));
        }
        if (forward.isSet()) {
            closeOnExit.add(forward);
            forward.applyForwards(forwardServerEndpoint.getServer(), endpoint);
            log.info("Configured requests forwarding for the following services: " + forward.getServices());
        }
        if (route.isSet())
            closeOnExit.add(route);

        log.info("Using distributor address " + LogUtil.hideCredentials(distributorAddress));

        MessageAdapter.AbstractFactory distributorFactory = delay.isSet() || drop.isSet() ?
            new DelayDropAdapter.Factory(endpoint, null, delay.getValue().getTime(), drop.getValue() / 100.0) :
            new DistributorAdapter.Factory(endpoint, null);
        if (route.isSet()) {
            route.applyRouting(forwardServerEndpoint.getServer(), endpoint, distributorFactory, distributorAddress);
            log.info("Configured requests routing for all services");
        } else {
            endpoint.addConnectors(MessageConnectors.createMessageConnectors(distributorFactory,
                distributorAddress, endpoint.getRootStats()));
        }
        endpoint.startConnectors();
        closeOnExit.add(endpoint);

    }

    @Override
    public List<MessageConnector> mustWaitWhileActive() {
        return endpoint.getConnectors();
    }

    @Override
    public List<Closeable> closeOnExit() {
        return closeOnExit;
    }

    public static void main(String[] args) {
        Tools.executeSingleTool(Multiplexor.class, args);
    }
}
