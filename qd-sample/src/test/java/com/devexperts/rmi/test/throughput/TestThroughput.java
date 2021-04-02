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
package com.devexperts.rmi.test.throughput;

import java.util.HashSet;
import java.util.Set;

/**
 * A tool for measuring RMI performance.
 */
public class TestThroughput {


    public static void main(String[] args) {
        if (args.length < 2) {
            wrongtUsage();
        }
        String sideStr = args[0].toLowerCase();
        if (!sideStr.equals("c") && !sideStr.equals("s")) {
            wrongtUsage();
        }
        boolean isServer = (sideStr.equals("s"));
        String address = args[1];
        boolean oneWay = false;
        int endpointsNumber = 1;
        int delay = 0;
        int messageSize = 8;
        int loggingInterval = 5;

        Set<Character> setOptions = new HashSet<>();
        for (int i = 2; i < args.length; i++) {
            if ((!args[i].startsWith("-")) || (args[i].length() != 2)) {
                System.out.println("Wrong usage: option expected, but \"" + args[i] + "\" found.");
                wrongtUsage();
            }
            char option = Character.toLowerCase(args[i].charAt(1));
            if (setOptions.contains(option)) {
                System.out.println("Option \"-" + "\" was set twice");
                wrongtUsage();
            }
            setOptions.add(option);
            if (option == 'o') {
                oneWay = true;
                if (isServer) {
                    System.out.println("\"-o\" option can be used only on client side.");
                    wrongtUsage();
                }
            } else {
                i++;
                int value = 0;
                boolean good;
                if (i >= args.length) {
                    good = false;
                } else {
                    try {
                        value = Integer.parseInt(args[i]);
                        good = value >= 0;
                    } catch (NumberFormatException e) {
                        good = false;
                    }
                }
                if (!good) {
                    System.out.println("Non-negative integer value of option \"-" + option + "\" expected.");
                    wrongtUsage();
                }
                switch (option) {
                case 'e':
                    endpointsNumber = value;
                    break;
                case 'l':
                    loggingInterval = value;
                    break;
                case 'm':
                    messageSize = value;
                    if (isServer) {
                        System.out.println("\"-m\" option can be used only on client side.");
                        wrongtUsage();
                    }
                    break;
                case 'd':
                    delay = value;
                    if (!isServer) {
                        System.out.println("\"-d\" option can be used only on server side.");
                        wrongtUsage();
                    }
                    break;
                default:
                    System.out.println("Unknown option: \"-" + option + "\".");
                    wrongtUsage();
                }
            }
        }

        if (isServer) {
            new ServerSide(address, endpointsNumber, loggingInterval, delay).start();
        } else {
            new ClientSide(address, endpointsNumber, loggingInterval, messageSize, oneWay).start();
        }
    }

    private static void wrongtUsage() {
        System.out.print(
            "Usage:\n" +
                "    TestThroughput <side> <address> [<options>]\n" +
                "Where:\n" +
                "    <side>       - is either \"c\" (client) or \"s\" (server)\n" +
                "    <address>    - is an address to connect\n" +
                "    <options>    - list of optional parameters\n" +
                "Optional parameters are:\n" +
                "    -e <number>  - endpoints number (one by default)\n" +
                "    -d <ms>      - time taken to process a message\n" +
                "                   (0 by default, for server side only)\n" +
                "    -m <bytes>   - message size (8 by default, for client side only)\n" +
                "    -o           - use one-way messages (for client side only)\n" +
                "    -l <sec>     - logging interval (0 to disable, 5 sec by default)\n" +
                ""
        );
        System.exit(0);
    }
}
