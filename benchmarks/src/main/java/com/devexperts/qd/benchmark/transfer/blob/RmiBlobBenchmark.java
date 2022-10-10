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

import org.openjdk.jmh.Main;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A benchmark for measuring speed of transferring a blob over RMI as a single byte-array response.
 * Initially created during QD-1386 investigation.
 */
@BenchmarkMode(value = Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class RmiBlobBenchmark {
    private static final int DEFAULT_CHUNK_SIZE = 4096;
    private static final int DEFAULT_POOL_SIZE = 1024;
    private static final int RESERVED_BYTES = 20;

    private static final int ITERATIONS = 1;
    private static final int THREADS = 1;
    private static final String EXTERNAL_SERVER = null; // use an external server address if needed

    private RmiBlobServer server;
    private RmiBlobClient client;
    private ExecutorService executor;

    // a number of concurrent client threads, each will perform ITERATIONS requests per benchmark iteration.
    @Param("" + THREADS)
    public int threads;

    // the factor of used chunks size, with base = DEFAULT_CHUNK_SIZE.
    // 0 stands for the framework default settings that used to be asymmetric in some implementations
    @Param({"0", "1", "2", "4", "8", "16"})
    public int scale;

    @Param({"100", "1000", "10000", "100000", "1000000", "10000000", "100000000", "500000000"})
    public int payloadSize;

    @Setup
    public void setup() throws InterruptedException {
        executor = Executors.newFixedThreadPool(THREADS);

        if (scale > 0) {
            int chunkSize = DEFAULT_CHUNK_SIZE * scale;
            int poolSize = DEFAULT_POOL_SIZE / scale;
            int poolListSize = DEFAULT_POOL_SIZE / scale;
            int recyclableListSize = DEFAULT_POOL_SIZE / scale;
            int messagePartSize = chunkSize - RESERVED_BYTES;

            System.setProperty("com.devexperts.qd.qtp.readAggregationSize", String.valueOf(chunkSize / 1000 * 1000));
            System.setProperty("com.devexperts.qd.qtp.composerThreshold", String.valueOf(chunkSize / 1000 * 1000));
            System.setProperty("com.devexperts.io.chunkSize", String.valueOf(chunkSize));
            System.setProperty("com.devexperts.rmi.chunkSize", String.valueOf(chunkSize));
            System.setProperty("com.devexperts.rmi.messagePartMaxSize", String.valueOf(messagePartSize));

            System.setProperty("com.devexperts.rmi.Chunk.poolCapacity", String.valueOf(poolSize));
            System.setProperty("com.devexperts.rmi.ChunkList.poolCapacity", String.valueOf(poolListSize));
            System.setProperty("com.devexperts.rmi.recyclableChunkListCapacity", String.valueOf(recyclableListSize));
        }

        client = new RmiBlobClient();
        if (EXTERNAL_SERVER == null) {
            server = new RmiBlobServer();
            int port = server.init(":0");
            client.init("localhost:" + port);
        } else {
            client.init(EXTERNAL_SERVER);
        }

    }

    @TearDown
    public void stop() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(1, TimeUnit.MINUTES);
        client.disconnect();
        if (EXTERNAL_SERVER == null) {
            server.disconnect();
        }
    }

    @Benchmark
    @OperationsPerInvocation(THREADS * ITERATIONS)
    public void receiveBlob(Blackhole blackhole) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(THREADS);
        for (int k = 0; k < THREADS; k++) {
            executor.execute(() -> {
                for (int i = 0; i < ITERATIONS; i++) {
                    blackhole.consume(client.receiveBlob(payloadSize));
                }
                latch.countDown();
            });
        }
        latch.await();
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0)
            args = new String[]{ RmiBlobBenchmark.class.getName() };
        Main.main(args);
    }
}
