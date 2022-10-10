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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * A simple benchmark for measuring speed of "raw" blob transfer over TCP socket to get a baseline for RMI transfer.
 */
@BenchmarkMode(value = Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class SocketBlobBenchmark {

    private static final int ITERATIONS = 1;
    private SocketBlobServer server;
    private SocketBlobClient client;

    @Param({"100", "1000", "10000", "100000", "1000000", "10000000", "100000000", "500000000"})
    public String payloadSize;
    private int blobSize;

    @Setup
    public void setup() throws IOException {
        blobSize = Integer.parseInt(payloadSize);

        server = new SocketBlobServer();
        int port = server.init(0); // use fixed port if needed

        client = new SocketBlobClient();
        client.init("localhost", port);
    }

    @TearDown
    public void stop() throws IOException {
        client.disconnect();
        server.disconnect();
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void receiveBlob(Blackhole blackhole) throws IOException {
        for (int i = 0; i < ITERATIONS; i++) {
            blackhole.consume(client.receiveBlob(blobSize));
        }
    }
}
