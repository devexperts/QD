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

import java.lang.ref.WeakReference;

/**
 * <p>A chunk of binary data.
 * Each {@code Chunk} holds a fixed-sized {@link #getBytes() byte array}
 * that may be used to store arbitrary binary data; {@link #getOffset() offset}
 * and {@link #getLength() length} properties are used to define the range
 * of significant data within the array.
 *
 * <h3>Pooling</h3>
 *
 * <p>Chunks are usually reused within the system; in such cases each
 * {@code Chunk} corresponds to some {@link ChunkPool pool} instance.
 * A chunk may be acquired from the specified pool by invocation of
 * {@link ChunkPool#getChunk(Object) getChunk(owner)} method and after being used
 * it may be returned back to the pool by {@link #recycle(Object) recycle(owner)} method.
 * (The <i>owner</i> argument of these methods will be described below.)
 *
 * <p>It is is also possible to create a chunk without binding it with any pool,
 * simply by {@link #wrap(byte[], Object) wrapping} a given byte array.
 *
 * <h3>Linear type &amp; chunk owners</h3>
 *
 * <p>The concept of a chunk is concerned with the concept of a
 * <a href="http://en.wikipedia.org/wiki/Linear_type">linear type</a>.
 * The main idea is that there may always exist at most one reference
 * for every chunk within the system.
 * Each time a reference to some chunk instance is passed from one part
 * of the system to another (e.g. as some method argument or return value,
 * by assignment to another variable or in any other way) it may
 * no longer be used in its previous location.
 *
 * <p>In order to ensure that every chunk may be used from a single place at any
 * moment of time, the <b>owner</b> for every chunk is introduced.
 * Each chunk always corresponds to exactly one owner, representing the current
 * part of the system which this chunk currently belongs to.
 * A reference to any arbitrary object may be used as an owner for a chunk.
 * Each time a chunk is passed to another part of the system, its owner
 * is validated and changed to a new value.
 * This operation is called <b>{@link #handOver(Object, Object) handover}</b>.
 *
 * <p>Each handover or modification operation requires to be provided with
 * the reference to the current owner of the chunk, and it fails with
 * {@link IllegalStateException} in case this reference is incorrect.
 * Thus, only the true owner of the chunk may modify it or hand it over
 * to someone else (and it may do this at most once).
 *
 * <p>Note, that {@link #getBytes()} method, however, does not validate
 * the owner, in spite of the fact that it may be used to modify the contents
 * of the chunk. Moreover, this method potentially allows to store the
 * reference for the underlying byte array of the chunk even after it was
 * recycled or handed over to someone else <b>(which is strictly forbidden)</b>.
 * Therefore it must be used with great care (see {@link #getBytes() method
 * documentation} for more details on its usage).
 *
 * <h3>Recycling</h3>
 *
 * <p>When a chunk is no longer needed and is not passed anywhere else,
 * it should be returned into its pool via {@link #recycle(Object) recycle(owner)} method.
 * Recycling a chunk implies its handover from the current owner to the pool,
 * so a chunk may no longer be used after being recycled, as well as it
 * may not be recycled if is passed somewhere else (in the latter case
 * it is recipient's responsibility to recycle the chunk after it is
 * no longer needed).
 *
 * <h3>Modifiable and read-only chunks</h3>
 *
 * <p>In case it is required to pass the same chunk into several different places
 * or to store its underlying binary data somewhere for a long time without
 * knowing exactly whether it is still used or not,
 * {@link #markReadOnly(Object) markReadOnly(owner)} method may be used. This method marks
 * the chunk as <b>read-only</b> and since this moment it is excluded
 * from pooling turnover. In order to check whether a chunk is read-only
 * or not {@link #isReadOnly()} method can be used.
 *
 * <p>All read-only chunks are owned by special <b>read-only owner</b>, which
 * means that they are actually owned by everybody except for the fact that
 * no one may modify them.
 * {@link #handOver(Object, Object) Handing over} read-only chunk is, however,
 * still allowed: it just does not actually change the owner.
 * In particular, invocation of {@link #recycle(Object) recycle(owner)} method would have
 * no effect on a read-only chunk.
 *
 * <p>All modifying methods (such as {@link #setLength(int, Object) setLength(length, owner)} and
 * {@link #setRange(int, int, Object) setRange(offset, length, owner)}) would throw an {@link IllegalStateException}
 * on a read-only chunk. The underlying byte array, however, may still be accessed and, thus, modified but this is
 * <b>strictly forbidden</b> by convention.
 *
 * <p>All chunks are modifiable (not read-only) by default. A chunk once marked as
 * read-only can never become modifiable again.
 * References to read-only chunks may be spawned and stored infinitely because
 * they would never be modified or recycled and reused again by anyone.
 *
 * <h3>Hand-over convention</h3>
 *
 * By convention, every method that accepts chunk and owner in parameters
 * takes ownership of the chunk via {@link #handOver(Object, Object) handOver} method or
 * recycles it via {@link #recycle(Object) recyle} method, so an original owner of the
 * chunk can no longer use it after invocation of such a method, unless the chunk is read-only, in which
 * case it can be still used (both methods do nothing when chunk is read-only).
 *
 * <h3>Threads and locks</h3>
 *
 * <b>This class is not thread-safe</b>. It cannot be safely updated from multiple threads at the same time.
 * Code that is using chunks shall include its own synchronization, especially to guarantee correct
 * operation of hand-over protocol.
 *
 * <p>The only only exception are {@link #isReadOnly() read-only} chunks that can be used (for read only)
 * concurrently from multiple threads.
 *
 * @see ChunkList
 * @see ChunkPool
 * @see ChunkedInput
 * @see ChunkedOutput
 */
public class Chunk {
    /**
     * Constructs new chunk of specified owner that wraps given byte array
     * and does not correspond to any {@link ChunkPool pool}.
     *
     * <p><b>Note:</b> wrapping must be used only with 'throw-away' arrays
     * that will not be used by source code after wrapping.
     *
     * @param bytes the byte array to wrap
     * @param owner owner of the new chunk
     * @return new chunk
     * @throws NullPointerException if {@code bytes} is {@code null}.
     */
    public static Chunk wrap(byte[] bytes, Object owner) {
        return wrap(bytes, 0, bytes.length, owner);
    }

    /**
     * Constructs a new chunk of specified owner that wraps given byte array with
     * specified range of data and does not correspond to any {@link ChunkPool pool}.
     *
     * <p><b>Note:</b> wrapping must be used only with 'throw-away' arrays
     * that will not be used by source code after wrapping.
     *
     * @param bytes the byte array to wrap
     * @param offset position of the first meaningful byte
     * @param length total number of meaningful bytes
     * @param owner owner of the new chunk
     * @return new chunk
     * @throws NullPointerException if {@code bytes} is {@code null}.
     * @throws IndexOutOfBoundsException if the region defined by
     * {@code offset} and {@code length} is out of underlying byte array range
     */
    public static Chunk wrap(byte[] bytes, int offset, int length, Object owner) {
        Chunk c = new Chunk(null, bytes, owner);
        c.setRange(offset, length, owner);
        return c;
    }

    private Object owner;

    protected final WeakReference<ChunkPool> poolReference;

    protected final byte[] bytes;
    protected int offset;
    protected int length;

    /**
     * Constructs a new chunk of specified owner that wraps given byte array.
     *
     * @param pool pool that will be used to recycle this chunk
     * @param bytes byte array that will be wrapped by this chunk
     * @param owner owner of the new chunk
     * @throws NullPointerException if {@code bytes} is {@code null}
     */
    protected Chunk(ChunkPool pool, byte[] bytes, Object owner) {
        this.poolReference = pool != null ? pool.reference : ChunkPool.EMPTY_REFERENCE;
        this.bytes = bytes;
        this.length = bytes.length;
        this.owner = owner;
    }

    /**
     * Returns chunk pool that this chunk was allocated from or {@code null} if none.
     * @return chunk pool that this chunk was allocated from or {@code null} if none.
     */
    public ChunkPool getPool() {
        return poolReference.get();
    }

    /**
     * Returns byte array, wrapped by this chunk.
     *
     * <p><i>Note: although this method can access underlying bytes of a chunk
     * regardless of its owner and "read-only" status, <b>it is strictly forbidden
     * to modify the bytes if the chunk is {@link #isReadOnly() read-only} or
     * is owned by someone else</b>.
     * It is neither allowed to recycle the chunk or hand it over to someone
     * else while you are using its underlying byte array.
     * A caution is required when passing this byte array into various
     * routines in order to be sure, that these routines won't store
     * a reference to the array to access it later.
     * </i>
     * @return byte array, wrapped by this chunk
     */
    public byte[] getBytes() {
        return bytes;
    }

    /**
     * Returns the position of the first meaningful byte in this chunk's
     * {@link #getBytes() underlying byte array}.
     *
     * @return position of the first meaningful byte in this chunk's
     * {@link #getBytes() underlying byte array}
     */
    public int getOffset() {
        return offset;
    }

    /**
     * Returns the total number of meaningful bytes stored in this chunk's
     * {@link #getBytes() underlying byte array}.
     *
     * @return total number of meaningful bytes stored in this chunk's
     * {@link #getBytes() underlying byte array}
     */
    public int getLength() {
        return length;
    }

    /**
     * Sets the total number of meaningful bytes stored in this chunk's
     * {@link #getBytes() underlying byte array}.
     *
     * @param length total number of meaningful bytes stored in this chunk's
     * {@link #getBytes() underlying byte array}
     * @param owner owner of the chunk
     * @throws IndexOutOfBoundsException if {@code length} is negative or if
     * {@code offset+length} exceeds underlying array length
     * @throws IllegalStateException if the chunk's owner differs from
     * the one specified (in particular, if the chunk is read-only).
     */
    public void setLength(int length, Object owner) {
        setRange(offset, length, owner);
    }

    /**
     * Sets the range occupied by meaningful data within this chunk's
     * {@link #getBytes() underlying byte array}.
     *
     * @param offset position of the first meaningful byte
     * @param length total number of meaningful bytes
     * @param owner owner of the chunk
     * @throws IndexOutOfBoundsException if the region defined by
     * {@code offset} and {@code length} is out of underlying
     * byte array range
     * @throws IllegalStateException if the chunk's owner differs from
     * the one specified (in particular, if the chunk is read-only).
     */
    public void setRange(int offset, int length, Object owner) {
        checkOwner(owner);
        if ((offset | length | (offset + length) | (bytes.length - (offset + length))) < 0)
            throw new IndexOutOfBoundsException();
        this.offset = offset;
        this.length = length;
    }

    /**
     * Checks whether the chunk is <b>read-only</b>.
     *
     * @return {@code true} if this chunk is read-only.
     * @see #markReadOnly(Object)
     */
    public boolean isReadOnly() {
        return owner == ChunkUtil.READ_ONLY_OWNER;
    }

    /**
     * Marks this chunk as {@link #isReadOnly() read-only}.
     * Does nothing if the chunk is already read-only.
     *
     * @throws IllegalStateException if the chunk is not {@link #isReadOnly()
     * read-only} and its current owner differs from the one specified
     * @param owner current owner of the chunk
     */
    public void markReadOnly(Object owner) {
        handOver(owner, ChunkUtil.READ_ONLY_OWNER);
    }

    /**
     * Hands over the chunk to another owner.
     * Does nothing if the chunk is {@link #isReadOnly() read-only}.
     *
     * @param oldOwner old (current) owner of the chunk
     * @param newOwner new owner of the chunk
     * @throws IllegalStateException if the chunk is not {@link #isReadOnly()
     * read-only} and its current owner differs from the one specified
     */
    public void handOver(Object oldOwner, Object newOwner) {
        if (this.owner == ChunkUtil.READ_ONLY_OWNER)
            return; // do nothing
        if (this.owner != oldOwner)
            throw new IllegalStateException("invalid owner, expected " + ChunkUtil.ownerString(oldOwner) + ", found " + ChunkUtil.ownerString(this.owner));
        this.owner = newOwner;
    }

    /**
     * Returns the chunk into the pool (or invalidates it if it does not correspond to any pool).
     * This method has no effect if this chunk is {@link #isReadOnly() read-only}.
     * A reference to this chunk is considered to become invalid after
     * invocation of this method and may no longer be used, unless the chunk was read-only.
     *
     * @param owner current owner of the chunk
     * @throws IllegalStateException if the chunk is not
     *          {@link #isReadOnly() read-only} and its current owner differs from the one specified
     */
    public void recycle(Object owner) {
        if (isReadOnly())
            return;
        ChunkPool pool = poolReference.get();
        if (pool != null)
            pool.recycleChunk(this, owner);
        else {
            // "destroy" this chunk so no one will be able use it again
            handOver(owner, ChunkUtil.GARBAGE_OWNER);
            offset = length = -1;
        }
    }

    /**
     * Throws an exception if the chunk is not owned by specified owner.
     *
     * @param owner expected owner of the chunk
     * @throws IllegalStateException if the chunk's current owner differs
     * from the one specified
     */
    protected void checkOwner(Object owner) {
        if (this.owner != owner)
            throw new IllegalStateException(this.owner == ChunkUtil.READ_ONLY_OWNER ? "chunk is read-only" : "invalid owner");
    }

    /**
     * Returns a string representation of the object.
     * @return a string representation of the object.
     */
    @Override
    public String toString() {
        return "Chunk{" +
            "bytes.length=" + bytes.length + "," +
            "offset=" + offset + "," +
            "length=" + length +  "," +
            "pool=" + poolReference.get() +
            "}";
    }
}
