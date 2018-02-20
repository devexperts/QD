/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.test.routing;

import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.impl.RMIEndpointImpl;

class ClientRoutingSide {

    final RMIEndpointImpl[] clients;

    ClientRoutingSide(int count) {
        clients = new RMIEndpointImpl[count];
        for (int i = 0; i < count; i++) {
            clients[i] = (RMIEndpointImpl) RMIEndpoint.newBuilder()
                .withName("Client-" + i)
                .withSide(RMIEndpoint.Side.CLIENT)
                .build();
            clients[i].getClient().setRequestSendingTimeout(20000);
            clients[i].getClient().setRequestRunningTimeout(20000);
        }
    }

    void connect(String... addresses) {
        ServerRoutingSide.connect(clients, addresses);
    }

    void close() {
        ServerRoutingSide.close(clients);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Clients: ");
        for (RMIEndpointImpl client : clients)
            sb.append(client.getEndpointId()).append(", ");
        return sb.toString();
    }

    void disconnect() {
        ServerRoutingSide.disconnect(clients);
    }

}
