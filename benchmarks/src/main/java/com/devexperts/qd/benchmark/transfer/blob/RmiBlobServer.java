/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.benchmark.transfer.blob;

import com.devexperts.qd.qtp.socket.ServerSocketTestHelper;
import com.devexperts.rmi.RMIEndpoint;
import com.dxfeed.promise.Promise;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RmiBlobServer {

    private RMIEndpoint endpoint;
    private ExecutorService executor;

    public static void main(String[] args) throws IOException, InterruptedException {
        RmiBlobServer server = new RmiBlobServer();
        if (args.length > 1) {
            System.err.println(
                "Usage: java ... " + RmiBlobServer.class.getName() + " [<endpoint-address>]\n" +
                "default address is ':0'");
        }

        server.init(args.length > 0 ? args[0] : ":0");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.err.println("Server shutdown initiated...");
                server.disconnect();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    public int init(String address) {
        executor = Executors.newFixedThreadPool(8);
        endpoint = createEndpoint();
        endpoint.getServer().setDefaultExecutor(executor);
        endpoint.getServer().export(new DummyBlobService(), BlobService.class);

        // use ":0" as address to use an automatically allocated port
        if (":0".equals(address)) {
            String name = UUID.randomUUID().toString();
            Promise<Integer> portPromise = ServerSocketTestHelper.createPortPromise(name);
            address = ":0[name=" + name + "]";
            endpoint.connect(address);
            int localPort = portPromise.await(10_000, TimeUnit.MILLISECONDS);
            return localPort;
        } else {
            endpoint.connect(address);
            return -1;
        }
    }

    public void disconnect() throws InterruptedException {
        endpoint.disconnect();
        executor.shutdownNow();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }

    private static RMIEndpoint createEndpoint() {
        return RMIEndpoint.newBuilder()
            .withProperties(System.getProperties())
            .withName("server")
            .withSide(RMIEndpoint.Side.SERVER)
            .build();
    }
}
