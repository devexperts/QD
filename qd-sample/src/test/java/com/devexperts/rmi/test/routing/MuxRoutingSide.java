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

public class MuxRoutingSide {
    final RMIEndpointImpl[] clients;
    final RMIEndpointImpl[] servers;

    public MuxRoutingSide(int count) {
        clients = new RMIEndpointImpl[count];
        servers = new RMIEndpointImpl[count];
        for (int i = 0; i < count; i++) {
            clients[i] = (RMIEndpointImpl) RMIEndpoint.newBuilder()
                .withName("muxClient-" + i)
                .withSide(RMIEndpoint.Side.CLIENT)
                .build();
            clients[i].getClient().setRequestSendingTimeout(20000);
            servers[i] = (RMIEndpointImpl) RMIEndpoint.newBuilder()
                .withName("muxServer-" + i)
                .withSide(RMIEndpoint.Side.SERVER)
                .build();
            servers[i].getServer().export(clients[i].getClient().getService("*"));
        }
    }

    void connectClients(String... addresses) {
        ServerRoutingSide.connect(clients, addresses);
    }

    void connectServers(String... addresses) {
        ServerRoutingSide.connect(servers, addresses);
    }

    int[] connectServersAuto(String... opts) {
        return ServerRoutingSide.connectAuto(servers, opts);
    }

    int[] connectServersAuto() {
        return connectServersAuto(new String[servers.length]);
    }

    void disconnect() {
        ServerRoutingSide.disconnect(servers);
        ServerRoutingSide.disconnect(clients);
    }

    void close() {
        ServerRoutingSide.close(clients);
        ServerRoutingSide.close(servers);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("MuxClients: ");
        for (RMIEndpointImpl client : clients)
            sb.append(client.getEndpointId()).append(", ");
        sb.append('\n');

        sb.append("MuxServers: ");
        for (RMIEndpointImpl server : servers)
            sb.append(server.getEndpointId()).append(", ");
        return sb.toString();
    }
}
