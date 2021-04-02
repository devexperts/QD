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

import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.impl.RMIEndpointImpl;
import com.devexperts.rmi.security.SecurityController;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.test.NTU;

class ServerRoutingSide {

    static void close(RMIEndpointImpl[] endpoints) {
        for (RMIEndpointImpl endpoint : endpoints)
            endpoint.close();
    }

    static void connect(RMIEndpointImpl[] endpoints, String[] addresses) {
        if (addresses.length != endpoints.length)
            throw new IllegalArgumentException();
        for (int i = 0; i < endpoints.length; i++)
            NTU.connect(endpoints[i], addresses[i]);
    }


    static void disconnect(RMIEndpointImpl[] endpoints) {
        for (RMIEndpointImpl endpoint : endpoints)
            endpoint.disconnect();
    }

    final RMIEndpointImpl[] servers;

    ServerRoutingSide(int count) {
        servers = new RMIEndpointImpl[count];
        for (int i = 0; i < count; i++) {
            servers[i] = (RMIEndpointImpl) RMIEndpoint.newBuilder()
                .withName("Server-" + i)
                .withSide(RMIEndpoint.Side.SERVER)
                .build();
        }
    }

    void connect(String... addresses) {
        connect(servers, addresses);
    }

    int[] connectAuto(String... opts) {
        RMIEndpointImpl[] servers = this.servers;
        return connectAuto(servers, opts);
    }

    int[] connectAuto() {
        return connectAuto(new String[servers.length]);
    }

    static int[] connectAuto(RMIEndpointImpl[] servers, String[] opts) {
        if (opts.length != servers.length)
            throw new IllegalArgumentException();
        int[] ports = new int[servers.length];
        for (int i = 0; i < servers.length; i++)
            ports[i] = NTU.connectServer(servers[i], null, opts[i]);
        return ports;
    }

    void disconnect() {
        disconnect(servers);
    }

    void export(int number, RMIService<?>... services) {
        for (RMIService<?> rmiService : services)
            servers[number].getServer().export(rmiService);
    }

    void export(RMIService<?>... services) {
        for (RMIEndpointImpl server : servers) {
            for (RMIService<?> service : services)
                server.getServer().export(service);
        }
    }

    void unexport(int number, RMIService<?>... services) {
        for (RMIService<?> rmiService : services)
            servers[number].getServer().unexport(rmiService);
    }

    void setSecurityController(SecurityController controller) {
        for (RMIEndpointImpl server : servers)
            server.setSecurityController(controller);
    }

    void close() {
        close(servers);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Servers: ");
        for (RMIEndpointImpl server : servers)
            sb.append(server.getEndpointId()).append(", ");
        return sb.toString();
    }
}
