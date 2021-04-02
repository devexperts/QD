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
package com.devexperts.rmi.test;

import com.devexperts.logging.Logging;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.task.RMIChannel;
import com.devexperts.rmi.task.RMIChannelSupport;
import com.devexperts.rmi.task.RMILocalService;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMITask;
import com.devexperts.rmi.task.RMITaskCancelListener;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestService extends RMILocalService<Void> {
    private static final Logging log = Logging.getLogging(TestService.class);

    private static final String NAME = "ChannelService";
    public static final RMIOperation<Void> OPERATION = RMIOperation.valueOf(NAME, Void.class, "method");

    private RMIChannel channel;
    private CountDownLatch latch = new CountDownLatch(1);
    private CountDownLatch start = new CountDownLatch(1);
    final Set<RMIService<?>> handlers = new HashSet<>();

    boolean getStart() throws InterruptedException {
        return start.await(10, TimeUnit.SECONDS);
    }

    protected TestService() {
        super(NAME, null);
    }

    RMIChannel awaitChannel() throws InterruptedException {
        if (latch.await(10, TimeUnit.SECONDS))
            return channel;
        throw new AssertionError("Long wait channel");
    }

    void update() {
        channel = null;
        latch = new CountDownLatch(1);
        start = new CountDownLatch(1);
    }

    void addChannelHandler(RMIService<?> handler) {
        handlers.add(handler);
    }

    @Override
    protected  RMIChannelSupport<Void> channelSupport() {
        return task -> {
            for (RMIService<?> handler : handlers)
                task.getChannel().addChannelHandler(handler);
        };
    }

    @Override
    public Void invoke(RMITask<Void> task) throws Throwable {
        channel = task.getChannel();
        latch.countDown();
        task.setCancelListener(task1 -> {
            log.info("TASK CANCEL");
            task1.cancel();
        });
        RMITask.current().suspend((RMITaskCancelListener) task12 -> {
            log.info("SUSPEND CANCEL");
            task12.cancel();
        });
        start.countDown();
        return null;
    }
}
