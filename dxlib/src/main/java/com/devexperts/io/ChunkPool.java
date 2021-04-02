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
package com.devexperts.io;

import com.devexperts.util.SystemProperties;
import com.devexperts.util.ThreadLocalPool;

import java.lang.ref.WeakReference;

/**
 * Pool for {@link Chunk} and {@link ChunkList} instances.
 *
 * @see Chunk
 * @see ChunkList
 */
public class ChunkPool {
    /**
     * Default pool instance with default parameters. Default parameters can be configured with the following
     * JVM system properties:
     * <ul>
     *   <li><b>com.devexperts.io.Chunk.threadLocalCapacity</b>
     *          &mdash; the capacity of thread-local pool of chunks (3 by default);</li>
     *   <li><b>com.devexperts.io.Chunk.poolCapacity</b>
     *          &mdash; the capacity of global pool of chunks (4096 by default);</li>
     *   <li><b>com.devexperts.io.ChunkList.threadLocalCapacity</b>
     *          &mdash; the capacity of thread-local pool of chunk lists (3 by default);</li>
     *   <li><b>com.devexperts.io.ChunkList.poolCapacity</b>
     *          &mdash; the capacity of global pool of chunk lists (1024 by default);</li>
     *   <li><b>com.devexperts.io.chunkSize</b>
     *          &mdash; the size of a chunks (8192 by default);</li>
     *   <li><b>com.devexperts.io.recyclableChunkListCapacity</b>
     *          &mdash; the maximum capacity of {@link ChunkList} that is kept in the pool (1024 by default).</li>
     * </ul>
     */
    public static final ChunkPool DEFAULT = new ChunkPool("com.devexperts.io",
        3, 4096, 1024, 8192, 1024);

    /** Empty {@link WeakReference} to a pool. */
    static final WeakReference<ChunkPool> EMPTY_REFERENCE = new WeakReference<ChunkPool>(null);

    // ---------------------------- instance fields ----------------------------

    /** {@link WeakReference} to this pool. */
    final WeakReference<ChunkPool> reference = new WeakReference<ChunkPool>(this);

    /** Owner for contained chunks and chunk lists */
    private final Object internalOwner = new Object();

    /** Pool for chunks. */
    private final ThreadLocalPool<Chunk> chunkPool;

    /** Pool for chunk lists. */
    private final ThreadLocalPool<ChunkList> chunkListPool;

    /** Number of bytes in producing chunks. */
    private final int chunkSize;

    /** Maximum capacity of a chunk list to be accepted for recycling */
    private final int recyclableChunkListCapacity;

    /**
     * Constructs new pool with specified parameters.
     *
     * @param poolName the base name of the pool.
     * @param threadLocalCapacity the capacity of thread-local pool, e.g. how many objects are pooled per thread.
     * @param chunkPoolCapacity maximum number of chunks to pool.
     * @param chunkListPoolCapacity maximum number of chunk lists to pool.
     * @param chunkSize size of the chunk.
     * @param recyclableChunkListCapacity maximum allowed chunk list capacity.
     */
    public ChunkPool(String poolName, int threadLocalCapacity, int chunkPoolCapacity, int chunkListPoolCapacity,
        int chunkSize, int recyclableChunkListCapacity)
    {
        this.chunkPool =
            new ThreadLocalPool<Chunk>(poolName + ".Chunk", threadLocalCapacity, chunkPoolCapacity);
        this.chunkListPool =
            new ThreadLocalPool<ChunkList>(poolName + ".ChunkList", threadLocalCapacity, chunkListPoolCapacity);
        this.chunkSize =
            SystemProperties.getIntProperty(poolName + ".chunkSize", chunkSize);
        this.recyclableChunkListCapacity =
            SystemProperties.getIntProperty(poolName + ".recyclableChunkListCapacity", recyclableChunkListCapacity);
        if (this.chunkSize < 1)
            throw new IllegalArgumentException("chunk size must be positive");
    }

    /**
     * Returns chunk size of this pool.
     */
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * Extracts a {@link Chunk} from the pool (or creates new one if the pool
     * is empty). The returned chunk is not {@link Chunk#isReadOnly()
     * read-only}, has non-zero {@link Chunk#getLength() length} and belongs
     * to specified owner.
     *
     * <p>The default implementation returns {@link Chunk} with zero
     * {@link Chunk#getOffset() offset} and {@link Chunk#getLength() length}
     * equal to underlying byte array length, however subclasses' behaviour
     * may differ.
     *
     * @param owner owner for the chunk
     * @return a chunk extracted from the pool (or created if the pool was empty)
     */
    public Chunk getChunk(Object owner) {
        Chunk chunk = chunkPool.poll();
        if (chunk == null)
            return createNewChunk(owner);
        chunk.offset = 0;
        chunk.length = chunk.bytes.length;
        chunk.handOver(internalOwner, owner);
        return chunk;
    }

    /**
     * Extracts a {@link ChunkList} from the pool (or creates new one if
     * the pool is empty). The returned chunk list is
     * {@link ChunkList#isEmpty() empty}, not {@link ChunkList#isReadOnly()
     * read-only} and belongs to specified owner.
     *
     * @param owner owner for the chunk list
     * @return a chunk list extracted from the pool (or created if the pool was empty)
     */
    public ChunkList getChunkList(Object owner) {
        ChunkList chunkList = chunkListPool.poll();
        if (chunkList == null)
            return createNewChunkList(owner);
        chunkList.head = chunkList.tail = 0;
        chunkList.handOver(internalOwner, owner);
        return chunkList;
    }

    /**
     * Extracts a {@link ChunkList} from the pool for specified owner
     * and fills it with data from specified byte array.
     *
     * @param bytes byte array to take data from
     * @param offset offset of data within the array
     * @param length length of data
     * @param owner owner for the chunk list
     * @return a chunk list extracted from the pool of specified owner that contains specified data.
     */
    public ChunkList copyToChunkList(byte[] bytes, int offset, int length, Object owner) {
        ChunkList result = getChunkList(owner);
        result.add(bytes, offset, length);
        return result;
    }

    /**
     * Constructs new {@link Chunk} instance for specified owner.
     *
     * @param owner owner for the chunk
     * @return constructed chunk
     */
    protected Chunk createNewChunk(Object owner) {
        return new Chunk(this, new byte[chunkSize], owner);
    }

    /**
     * Constructs a new {@link ChunkList} of specified owner.
     *
     * @param owner owner for the chunk list
     * @return constructed chunk list
     */
    protected ChunkList createNewChunkList(Object owner) {
        return new ChunkList(this, owner);
    }

    /**
     * Returns the specified {@link Chunk} into the pool.
     * This method is invoked by {@link Chunk#recycle(Object)} method.
     *
     * @param chunk a chunk to recycle
     * @param owner current owner of the chunk
     * @throws IllegalStateException if the chunk's owner differs from the one specified
     *                               or if the chunk is read-only
     * @throws IllegalArgumentException if the chunk does not belong to this pool or has invalid length
     */
    protected void recycleChunk(Chunk chunk, Object owner) {
        if (chunk.poolReference.get() != this)
            throw new IllegalArgumentException("chunk belongs to another pool");
        if (chunk.bytes.length != chunkSize)
            throw new IllegalArgumentException("chunk to be pooled has wrong length");
        chunk.checkOwner(owner);
        chunk.handOver(owner, internalOwner);
        chunk.offset = chunk.length = -1;
        chunkPool.offer(chunk);
    }

    /**
     * Returns the specified {@link ChunkList} into the pool.
     * This method is invoked from {@link ChunkList#recycle(Object)} method.
     *
     * @param chunkList a chunk list to recycle
     * @param owner current owner of the chunk list
     * @throws IllegalStateException if the chunk's owner differs from the one specified
     *                               or if the chunk is read-only
     * @throws IllegalArgumentException if the chunk list does not belong to this pool or is not empty
     */
    protected void recycleChunkList(ChunkList chunkList, Object owner) {
        if (chunkList.poolReference.get() != this)
            throw new IllegalArgumentException("chunk list belongs to another pool");
        if (!chunkList.isEmpty())
            throw new IllegalStateException("chunk list to be pooled is not empty");
        chunkList.checkOwner(owner);
        chunkList.handOver(owner, internalOwner);
        chunkList.head = chunkList.tail = -1;
        if (chunkList.chunkArray.length <= recyclableChunkListCapacity)
            chunkListPool.offer(chunkList);
    }

    /**
     * Returns a string representation of the object.
     * @return a string representation of the object.
     */
    @Override
    public String toString() {
        return "ChunkPool{" +
            "chunkSize=" + chunkSize + "," +
            "recyclableChunkListCapacity=" + recyclableChunkListCapacity + "," +
            "chunkPool.size=" + chunkPool.size() + "," +
            "chunkListPool.size=" + chunkListPool.size() + "}";
    }
}
