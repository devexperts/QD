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
package com.devexperts.rmi.test.routing;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class Ports {
    private static final Set<Integer> used = ConcurrentHashMap.newKeySet();

    public static int[] findAvailablePort(int numberOfPorts) {
        int[] res = new int[numberOfPorts];
        List<Integer> alreadySelected = Arrays.stream(res).boxed().collect(Collectors.toList());
        for (int i = 0; i < numberOfPorts; i++) {
            res[i] = findNextPort(alreadySelected);
        }
        return res;
    }

    private static int findNextPort(List<Integer> alreadySelected) {
        while (true) {
            int port = ThreadLocalRandom.current().nextInt(10000, 65000);
            if (alreadySelected.contains(port) || !used.add(port)) continue;
            try (ServerSocket ignored = new ServerSocket(port)) {
                //workaround for linux : calling close() immediately after opening socket
                //may result that socket is not closed
                Thread.sleep(1);
                return port;
            } catch (IOException ignored) {
                // try the next one
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
