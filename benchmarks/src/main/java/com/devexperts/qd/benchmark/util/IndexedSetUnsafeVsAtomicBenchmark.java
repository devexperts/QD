/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.benchmark.util;

import com.devexperts.util.UnsafeHolder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sun.misc.Unsafe;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Stream;

/**
 * The benchmark compares various approaches to the implementation of IndexedSet.Core regarding providing thread safe
 * publication.
 *
 * <p>{@link PlainCore} represents implementation before QD-1320 that fails to provide safe publication on ARM
 * <p>{@link UnsafeCore} implements approach based on accessing elements of plain array using {@link sun.misc.Unsafe}
 * <p>{@link AtomicArrayCore} represents approach based on {@link AtomicReferenceArray} class
 */
@BenchmarkMode(value = Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@State(Scope.Thread)
public class IndexedSetUnsafeVsAtomicBenchmark {

    private static final int SEED = 42;

    // simplified random expecting power of 2 bounds
    static class FastRandom {
        private int r;

        public FastRandom(int seed) {
            r = seed;
        }

        public int nextInt(int bound) {
            r = r * 0xB46394CD + 1;
            return r & (bound - 1);
        }
    }

    // imitates IndexedSet.Core implementation
    public interface Core<T> {
        public void set(int index, T v);

        public T get(int index);
    }

    //<editor-fold desc="Core methods implementation">
    public static class PlainCore<T> implements Core<T> {
        final T[] data;

        public PlainCore(int size) {
            data = (T[]) new Object[size];
        }

        @Override
        public void set(int index, T v) {
            data[index] = v;
        }

        @Override
        public T get(int index) {
            return data[index];
        }
    }

    public static class UnsafeCore<T> implements Core<T> {
        private static final Unsafe unsafe = UnsafeHolder.UNSAFE;
        private static final int base;
        private static final int shift;

        static {
            base = unsafe.arrayBaseOffset(Object[].class);
            int scale = unsafe.arrayIndexScale(Object[].class);
            if ((scale & (scale - 1)) != 0)
                throw new Error("data type scale not a power of two");
            shift = 31 - Integer.numberOfLeadingZeros(scale);
        }

        private static long checkedOffset(Object[] a, int i) {
            if (i < 0 || i >= a.length)
                throw new IndexOutOfBoundsException("index=" + i + ", length=" + a.length);
            return ((long) i << shift) + base;
        }

        final T[] data;

        @SuppressWarnings("unchecked")
        public UnsafeCore(int size) {
            data = (T[]) new Object[size];
        }

        @Override
        public void set(int i, T v) {
            unsafe.putObjectVolatile(data, checkedOffset(data, i), v);
        }

        @SuppressWarnings("unchecked")
        @Override
        public T get(int i) {
            return (T) unsafe.getObjectVolatile(data, checkedOffset(data, i));
        }
    }

    public static class AtomicArrayCore<T> extends AtomicReferenceArray<T> implements Core<T> {

        public AtomicArrayCore(int size) {
            super(size);
        }
    }
    //</editor-fold>

    public enum CoreType {

        PLAIN(PlainCore.class), UNSAFE(UnsafeCore.class), ATOMIC_ARRAY(AtomicArrayCore.class);

        public final Class<? extends Core> core;

        CoreType(Class<? extends Core> coreImpl) {
            core = coreImpl;
        }

        public Core getCore(int size) {
            try {
                return core.getConstructor(Integer.TYPE).newInstance(size);
            } catch (Throwable e) {
                throw new RuntimeException("Failed core creation for " + core.getName(), e);
            }
        }
    }

    // imitates an indexed class that knows its index in table
    public static class Item {
        private final Integer index;

        public Item(int i) {
            //noinspection UnnecessaryBoxing
            index = new Integer(i); // use explicit new to create a unique object
        }

        public int getIndex() {
            return index;
        }
    }

    @Param
    public CoreType coreType;

    @Param({"16", "1024", "65536", "4194304"})
    public int size;

    @Param({"1000000"})
    public int cycles;

    public volatile Core<Item> core;
    public Item[] randomItems;

    @Setup
    public void setup() {
        if ((size & (size - 1)) != 0)
            throw new IllegalArgumentException("Size parameter must be a power of 2");

        core = coreType.getCore(size);
        for (int i = 0; i < size; i++) {
            core.set(i, new Item(i));
        }

        FastRandom r = new FastRandom(SEED);
        randomItems = Stream.generate(() -> new Item(r.nextInt(size))).limit(16).toArray(Item[]::new);
    }

    private Item getRandomItem(FastRandom r) {
        return randomItems[r.nextInt(randomItems.length)];
    }

    @Benchmark
    public void randomGet(Blackhole bh) {
        FastRandom r = new FastRandom(SEED);
        int size = this.size;
        int res = 0;
        for (int i = cycles - 1; i >= 0; i--) {
            res += core.get(r.nextInt(size)).getIndex();
        }
        bh.consume(res);
    }

    @Benchmark
    public void seqGet(Blackhole bh) {
        Core<Item> core = this.core;
        int size = this.size;
        int res = 0;
        for (int i = cycles - 1; i >= 0; i--) {
            res += core.get(i & (size - 1)).getIndex();
        }
        bh.consume(res);
    }

    @Benchmark
    public void seqWrite(Blackhole bh) {
        FastRandom r = new FastRandom(SEED);
        int size = this.size;
        for (int i = cycles - 1; i >= 0; i--) {
            Core<Item> c = this.core;
            c.set(i & (size - 1), getRandomItem(r));
            this.core = c; // updates always do volatile read and write of the core reference
        }
    }

    @Benchmark
    public void randomWrite(Blackhole bh) {
        FastRandom r = new FastRandom(SEED);
        int size = this.size;
        for (int i = cycles - 1; i >= 0; i--) {
            Core<Item> c = this.core;
            int idx = r.nextInt(size);
            c.set(idx, getRandomItem(r));
            this.core = c; // updates always do volatile read and write of the core reference
        }
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(
            new OptionsBuilder()
                .include(IndexedSetUnsafeVsAtomicBenchmark.class.getSimpleName())
                .build()
        ).run();
    }
}
