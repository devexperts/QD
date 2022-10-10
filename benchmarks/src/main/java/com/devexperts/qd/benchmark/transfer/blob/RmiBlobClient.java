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

import com.devexperts.rmi.RMIEndpoint;

import java.util.concurrent.CountDownLatch;

public class RmiBlobClient {

    private RMIEndpoint endpoint;
    private BlobService service;

    public void init(String address) throws InterruptedException {
        endpoint = createEndpoint();
        endpoint.connect(address);

        CountDownLatch latch = new CountDownLatch(1);
        endpoint.addEndpointListener(e -> {
            if (e.isConnected()) {
                latch.countDown();
            }
        });
        latch.await();

        service = endpoint.getClient().getProxy(BlobService.class);
    }

    public void disconnect() {
        endpoint.disconnect();
    }

    public byte[] receiveBlob(int blobSize) {
        return service.getBlob(blobSize);
    }

    private static RMIEndpoint createEndpoint() {
        return RMIEndpoint.newBuilder()
            .withProperties(System.getProperties())
            .withName("client")
            .withSide(RMIEndpoint.Side.CLIENT)
            .build();
    }
}
