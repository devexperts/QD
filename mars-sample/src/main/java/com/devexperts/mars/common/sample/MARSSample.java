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
package com.devexperts.mars.common.sample;

import com.devexperts.mars.common.MARSAgent;
import com.devexperts.mars.common.MARSEvent;
import com.devexperts.mars.common.MARSListener;
import com.devexperts.mars.common.MARSNode;
import com.devexperts.mars.common.net.MARSConnector;

/**
 * Contains sample code of how MARS library may be used.
 */
public class MARSSample {

    /**
     * Executes specified commands and sleeps indefinitely if long-running command was found.
     */
    public static void main(String[] args) throws InterruptedException {
        if (executeCommands("MARSSample", args))
            Thread.sleep(Long.MAX_VALUE);
    }

    /**
     * Executes specified commands. Returns true if long-running command was found.
     */
    public static boolean executeCommands(String program_name, String[] args) {
        if (args.length == 0)
            help(program_name);
        boolean result = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-help")) {
                help(program_name);
            } else if (args[i].equalsIgnoreCase("-dump")) {
                dump();
            } else if (args[i].equalsIgnoreCase("-generate") && i + 1 < args.length) {
                generate(args[++i]);
                result = true;
            } else if (args[i].equalsIgnoreCase("-receive") && i + 1 < args.length) {
                receive(args[++i]);
                result = true;
            } else if (args[i].equalsIgnoreCase("-send") && i + 1 < args.length) {
                send(args[++i]);
                result = true;
            }
        }
        return result;
    }

    /**
     * Prints help to standard output.
     */
    public static void help(String program_name) {
        System.out.println("Usage: " + program_name + " [command] [command]...");
        System.out.println("Where commands are:");
        System.out.println("    -help                   - print this help info");
        System.out.println("    -dump                   - dump MARS data to standard output");
        System.out.println("    -generate <name>        - generate system time data using specified name");
        System.out.println("    -receive <address>      - receive MARS data using specified address");
        System.out.println("    -send <address>         - send MARS data using specified address");
    }

    // ========== Listening and Dumping Section ==========

    /**
     * Retrieves accumulated events and dumps them to standard output. Implemented as:
     * <pre>
     * public void marsChanged(MARSAgent agent) {
     *     for (MARSEvent event : agent.retrieveEvents())
     *         System.out.println(event);
     * }
     * </pre>
     */
    public static class DumpingListener implements MARSListener {
        public void marsChanged(MARSAgent agent) {
            for (MARSEvent event : agent.retrieveEvents())
                System.out.println(event);
        }
    }

    /**
     * Dumps all {@link MARSEvent} events to standard output. Implemented as:
     * <pre>
     * MARSAgent agent = new MARSAgent(MARSNode.getRoot().getMars());
     * agent.setListener(new DumpingListener());
     * </pre>
     */
    public static void dump() {
        MARSAgent agent = new MARSAgent(MARSNode.getRoot().getMars());
        agent.setListener(new DumpingListener());
    }

    // ========== Data Generation Section ==========

    /**
     * Generates system time events for specified name each second. Implemented as:
     * <pre>
     * private final MARSNode node = MARSNode.getRoot().subNode(name, "System time.");
     * <p/>
     * public void run() {
     *     while (true) {
     *         node.setTimeValue(System.currentTimeMillis());
     *         try {
     *             Thread.sleep(1000);
     *         } catch (InterruptedException e) {}
     *     }
     * }
     * </pre>
     */
    public static class Generator implements Runnable {

        private final MARSNode node;

        public Generator(String name) {
            node = MARSNode.getRoot().subNode(name, "System time.");
        }

        public void run() {
            while (true) {
                node.setTimeValue(System.currentTimeMillis());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Generates {@link MARSEvent} events for specified name with system time value each second. Implemented as:
     * <pre>
     * new Thread(new Generator(name), "Generator").start();
     * </pre>
     */
    public static void generate(String name) {
        new Thread(new Generator(name), "Generator").start();
    }

    // ========== Network Communication Section ==========

    /**
     * Creates and starts receiving network connector for specified address. Implemented as:
     * <pre>
     * MARSConnector connector = new MARSConnector(MARSNode.getRoot().getMars(), true, false);
     * connector.setAddress(address);
     * connector.start();
     * </pre>
     */
    public static void receive(String address) {
        MARSConnector connector = new MARSConnector(MARSNode.getRoot().getMars(), true, false);
        connector.setAddress(address);
        connector.start();
    }

    /**
     * Creates and starts sending network connector for specified address. Implemented as:
     * <pre>
     * MARSConnector connector = new MARSConnector(MARSNode.getRoot().getMars(), false, true);
     * connector.setAddress(address);
     * connector.start();
     * </pre>
     */
    public static void send(String address) {
        MARSConnector connector = new MARSConnector(MARSNode.getRoot().getMars(), false, true);
        connector.setAddress(address);
        connector.start();
    }
}
