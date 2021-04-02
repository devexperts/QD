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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

/**
 * Reusable list of {@link Chunk chunks} with data.
 * This class is used for containment and transfer of variable-sized binary data
 * within the system. It uses a list of limited-capacity {@link Chunk chunks}
 * to accommodate data of any size.
 *
 * <p>This class does not implement {@link Collection} interface but some of
 * its methods conform with methods of standard collections (such as
 * {@link ArrayList} and {@link Queue}) from Java Collections Framework.
 *
 * <p>Chunk lists are designed to be reusable in much the same way as individual
 * {@link Chunk chunks}; they have similar reuse, sharing and owning API
 * methods.
 * Chunk lists may be acquired from pools via {@link ChunkPool#getChunkList(Object) getChunkList(owner)}
 * method and recycled via {@link #recycle(Object) recycle(owner)} method.
 * They also have <b>owners</b>, supports {@link #handOver(Object, Object)
 * handover} and may be {@link #markReadOnly(Object) marked as read-only}.
 * A {@link #isReadOnly() read-only} chunk list may contain only
 * {@link Chunk#isReadOnly() read-only} chunks.
 * All chunks within the chunk list (except for the read-only ones) are
 * owned by the chunk list. Thus, adding and removing
 * chunks into/from the list implies {@link Chunk#handOver(Object, Object)
 * handover} of these chunks from external owner to the list and vice versa.
 * Only the owner of the list may add and remove chunks to/from the list.
 *
 * <p>The {@code ChunkList} cannot contain {@code null} chunks.
 *
 * <h3>Iterable</h3>
 *
 * <p>{@code ChunkList} implements {@link Iterable} interface. The particular
 * feature of its {@link Iterator} is that it is reusable: after the invocation
 * of {@link Iterator#hasNext()} returns {@code false} the iterator
 * becomes invalid and may no longer be used, however several iterator
 * instances may still be acquired and used concurrently. This feature provides
 * garbage-free "foreach" iteration over chunk lists.
 *
 * <h3>Threads and locks</h3>
 *
 * <b>This class is not thread-safe</b>. It cannot be safely updated from multiple threads at the same time.
 * Code that is using chunks shall include its own synchronization, especially to guarantee correct
 * operation of hand-over protocol.
 *
 * <p>The only only exception are {@link #isReadOnly() read-only} chunk lists that can be used (for read only)
 * concurrently from multiple threads.
 *
 * @see Chunk
 * @see ChunkPool
 * @see ChunkedInput
 * @see ChunkedOutput
 */
public class ChunkList implements Iterable<Chunk> {
    /**
     * Read-only empty chunk list.
     */
    public static final ChunkList EMPTY = new ChunkList(null);

    static {
        EMPTY.markReadOnly(null);
    }

    private static final Iterator<Chunk> EMPTY_ITERATOR = Collections.<Chunk>emptySet().iterator();

    /**
     * Constructs a new chunk list of specified owner that wraps specified byte array
     * and does not correspond to any {@link ChunkPool pool}.
     *
     * <p><b>Note:</b> wrapping shall be used only with 'throw-away' arrays
     * that will not be used by source code after wrapping.
     *
     * @param bytes the byte array to wrap
     * @param owner owner of new chunk list
     * @return new chunks
     * @throws NullPointerException if {@code bytes} is {@code null}.
     */
    public static ChunkList wrap(byte[] bytes, Object owner) {
        return wrap(bytes, 0, bytes.length, owner);
    }

    /**
     * Constructs a new chunk list of specified owner that wraps specified byte array
     * with specified range of data, and does not correspond to any {@link ChunkPool pool}.
     *
     * <p><b>Note:</b> wrapping shall be used only with 'throw-away' arrays
     * that will not be used by source code after wrapping.
     *
     * @param bytes the byte array to wrap
     * @param offset position of the first meaningful byte
     * @param length total number of meaningful bytes
     * @param owner owner of new chunk list
     * @return new chunks
     * @throws NullPointerException if {@code bytes} is {@code null}.
     * @throws IndexOutOfBoundsException if the region defined by {@code offset} and {@code length}
     *                                   is out of underlying byte array range
     */
    public static ChunkList wrap(byte[] bytes, int offset, int length, Object owner) {
        ChunkList chunks = new ChunkList(owner);
        chunks.add(Chunk.wrap(bytes, offset, length, owner), owner);
        return chunks;
    }

    // ==================== Instance members ====================

    private final Object internalOwner = new Object(); // owner for contained chunks
    private Object owner;

    protected final WeakReference<ChunkPool> poolReference;

    protected Chunk[] chunkArray;
    protected int head;
    protected int tail;

    private ChunkIterator pooledIterator;

    /**
     * Constructs empty chunk list of specified owner not corresponding
     * to any {@link ChunkPool pool}.
     *
     * @param owner owner of new chunk list
     */
    public ChunkList(Object owner) {
        this(null, owner);
    }

    /**
     * Constructs new chunk list of specified owner corresponding
     * to a specified {@link ChunkPool pool}.
     *
     * @param pool pool to use for pooling
     * @param owner owner of new chunk list
     */
    protected ChunkList(ChunkPool pool, Object owner) {
        poolReference = pool != null ? pool.reference : ChunkPool.EMPTY_REFERENCE;
        chunkArray = new Chunk[ChunkUtil.INITIAL_CHUNK_LIST_CAPACITY];
        this.owner = owner;
    }


    /**
     * Returns chunk pool that this chunk list was allocated from or {@code null} if none.
     * @return chunk pool that this chunk list was allocated from or {@code null} if none.
     */
    public ChunkPool getPool() {
        return poolReference.get();
    }

    /**
     * Returns the <b>number of chunks</b> in this list.
     * In order to get the total number of bytes stored in chunks
     * {@link #getTotalLength()} method should be used.
     *
     * @return number of chunks in this chunk list
     */
    public int size() {
        return tail - head;
    }

    /**
     * Checks whether this chunk list is empty, i.e. contains no chunks.
     * Note, that this method will return {@code false} if there are any chunks
     * even if they are all empty.
     *
     * @return {@code true} if there is no chunks in this chunk list
     */
    public boolean isEmpty() {
        return head == tail;
    }

    /**
     * Returns the chunk located at the specified position of the list.
     * This method does not extract the chunk from the list and does
     * not change its owner.
     *
     * @param index position of a chunk to get
     * @return {@link Chunk} located at the specified position
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    public Chunk get(int index) {
        checkRange(index);
        return chunkArray[head + index];
    }

    /**
     * Returns the total number of bytes stored in chunks, i.e. &sum; {@link Chunk#getLength() chunk.getLength()}.
     *
     * @return the total number of bytes stored in chunks
     */
    public long getTotalLength() {
        long result = 0;
        for (int i = head; i < tail; i++)
            result += chunkArray[i].getLength();
        return result;
    }

    /**
     * Returns an {@link Iterator iterator} over the chunks.
     * The returned iterator does not support {@link java.util.Iterator#remove() remove} operation.
     *
     * <p>Iterator returned by this method may be reused again by someone else
     * after it have traversed all the elements in this {@link ChunkList},
     * therefore it is not allowed to use the iterator after its
     * {@link Iterator#hasNext() hasNext()} method has returned {@code false}.
     * However it is still allowed to acquire and use several instances
     * of iterator (it will generate garbage).
     *
     * <p>Read-only chunk lists <b>can</b> be iterated concurrently from multiple threads.
     * Each call to this method creates its own instance of iterator for read-only chunk lists.
     *
     * @return {@link Iterator iterator} over chunks as specified by {@link Iterable} interface.
     */
    public Iterator<Chunk> iterator() {
        if (isEmpty())
            return EMPTY_ITERATOR;
        ChunkIterator it = pooledIterator;
        if (it == null || isReadOnly())
            return new ChunkIterator();
        if (it.index != -1)
            throw new IllegalStateException();
        pooledIterator = null;
        it.index = head;
        return it;
    }

    /**
     * Adds given chunk to the end of this chunk list. The specified chunk
     * must either be {@link Chunk#isReadOnly() read-only} or have the same
     * owner as this chunk list. The chunk will be handed over to this
     * chunk list's internal owner object after invocation of this method.
     *
     * @param chunk a chunk to add
     * @param owner current owner of this chunk list and given chunk
     * @throws NullPointerException if {@code chunk} is {@code null}
     * @throws IllegalStateException if this chunk list's owner differs from
     *         the one specified (in particular, if it is read-only) or if the given
     *         chunk is not read-only and its owner differs from the one specified
     */
    public void add(Chunk chunk, Object owner) {
        checkOwner(owner);
        if (chunk == null)
            throw new NullPointerException("chunk is null");
        ensureTailSpace(1);
        chunk.handOver(owner, internalOwner);
        chunkArray[tail++] = chunk;
    }

    private void ensureTailSpace(int space) {
        if (space <= chunkArray.length - tail)
            return;
        int cap = tail - head + space;
        if (cap < 0)
            throw new IllegalArgumentException("Capacity overflow");
        Chunk[] tmp = (cap < chunkArray.length >> 1) ? chunkArray :
            new Chunk[Math.max((int) Math.min((long) chunkArray.length * 2 + 1, Integer.MAX_VALUE), cap)];
        System.arraycopy(chunkArray, head, tmp, 0, tail - head);
        chunkArray = tmp;
        tail = tail - head;
        head = 0;
    }

    /**
     * Adds chunks from specified chunk list into the end of this one.
     * The specified chunk list must either be {@link #isReadOnly() read-only}
     * or have the same owner as this chunk list.
     * All chunks added will be handed over to this chunk list's internal
     * owner object and the given chunk list will be recycled after invocation
     * of this method.
     *
     * @param chunks chunk list with chunks to add
     * @param owner current owner of both this and given chunk lists
     *         (if the given chunk list is not read-only).
     * @throws NullPointerException if {@code chunks} is {@code null}
     * @throws IllegalArgumentException if {@code chunks} is {@code this}
     * @throws IllegalStateException if this chunk list's owner differs from
     *         the one specified (in particular, if it is read-only) or if the given
     *         chunk list is not read-only and its owner differs from the one specified
     */
    public void addAll(ChunkList chunks, Object owner) {
        if (chunks == this)
            throw new IllegalArgumentException("can not add itself");
        checkOwner(owner);
        ensureTailSpace(chunks.size());
        if (chunks.isReadOnly()) {
            System.arraycopy(chunks.chunkArray, chunks.head, chunkArray, tail, chunks.size());
            tail += chunks.size();
        } else {
            chunks.checkOwner(owner);
            for (int i = chunks.head; i < chunks.tail; i++) {
                Chunk c = chunks.chunkArray[i];
                c.handOver(chunks.internalOwner, internalOwner);
                chunks.chunkArray[i] = null;
                chunkArray[tail++] = c;
            }
            chunks.head = chunks.tail = 0;
            chunks.recycle(owner);
        }
    }

    /**
     * Adds to this chunk list data from specified byte array.
     * Chunk pool that this chunk list was acquired from must be strongly-reachable
     * (new chunks will be acquired from it).
     *
     * @param bytes byte array to take data from
     * @param offset offset of data within the array
     * @param length length of data
     */
    public void add(byte[] bytes, int offset, int length) {
        if ((offset | length | (offset + length) | (bytes.length - (offset + length))) < 0)
            throw new IndexOutOfBoundsException();
        while (length > 0) {
            Chunk chunk = poolReference.get().getChunk(owner);
            int n = Math.min(length, chunk.getLength());
            System.arraycopy(bytes, offset, chunk.getBytes(), chunk.getOffset(), n);
            offset += n;
            length -= n;
            chunk.setLength(n, owner);
            add(chunk, owner);
        }
    }

    /**
     * Retrieves a chunk from the beginning of the list, hands it over to the
     * owner of this chunk list and returns it.
     * The chunk list must not be read-only.
     *
     * @param owner owner of this chunk list (which will also become the owner
     *               of the taken chunk after invocation of this method), unless
     *               the taken chunk was read-only.
     * @return a chunk from the beginning of this chunk list or
     *        {@code null} if the list is empty
     * @throws IllegalStateException if this chunk list's owner differs from
     *         the one specified or if the chunk list is read-only.
     */
    public Chunk poll(Object owner) {
        checkOwner(owner);
        if (isEmpty())
            return null;
        Chunk c = chunkArray[head];
        chunkArray[head++] = null;
        if (head == tail)
            head = tail = 0;
        c.handOver(internalOwner, owner);
        return c;
    }

    /**
     * Retrieves a chunk from the end of the list, hands it over to the
     * owner of this chunk list and returns it.
     * The chunk list must not be read-only.
     *
     * @param owner owner of this chunk list (which will also become the owner
     *             of the taken chunk after invocation of this method).
     * @return a chunk from the end of this chunk list or
     *         {@code null} if the list is empty
     * @throws IllegalStateException if this chunk list's owner differs from
     *         the one specified (in particular, if the chunk list is read-only)
     */
    public Chunk pollLast(Object owner) {
        checkOwner(owner);
        if (isEmpty())
            return null;
        Chunk c = chunkArray[--tail];
        chunkArray[tail] = null;
        if (head == tail)
            head = tail = 0;
        c.handOver(internalOwner, owner);
        return c;
    }

    /**
     * Sets range for specified chunk.
     *
     * @param index position of a chunk to set range
     * @param offset position of the first meaningful byte
     * @param length total number of meaningful bytes
     * @param owner owner of the chunk list
     * @throws IndexOutOfBoundsException if the region defined by
     * {@code offset} and {@code length} is out of chunk's underlying
     * byte array range
     * @throws IllegalStateException if the chunk list's owner differs from
     * the one specified (in particular, if the chunk list or chunk is read-only).
     * @see Chunk#setRange
     */
    public void setChunkRange(int index, int offset, int length, Object owner) {
        checkOwner(owner);
        checkRange(index);
        chunkArray[head + index].setRange(offset, length, internalOwner);
    }

    /**
     * Checks whether the chunk list is <i>read-only</i>.
     *
     * @return {@code true} if this chunk list is read-only.
     * @see #markReadOnly(Object)
     * @see Chunk#isReadOnly()
     */
    public boolean isReadOnly() {
        return owner == ChunkUtil.READ_ONLY_OWNER;
    }

    /**
     * Marks this chunk list as {@link #isReadOnly()}. All contained chunks
     * <b>also become read-only</b> after invocation of this method.
     * Does nothing if the list is already read-only.
     *
     * @param owner current owner of this chunk list
     * @throws IllegalStateException if the chunk list is not {@link #isReadOnly() read-only}
     *         and its current owner differs from the one specified
     * @see Chunk#markReadOnly(Object)
     */
    public void markReadOnly(Object owner) {
        for (int i = head; i < tail; i++)
            chunkArray[i].markReadOnly(internalOwner);
        handOver(owner, ChunkUtil.READ_ONLY_OWNER);
    }

    /**
     * Hands over the chunk list to another owner.
     * Does nothing if the list is {@link #isReadOnly() read-only}.
     *
     * @param oldOwner old (current) owner of the chunk list
     * @param newOwner new owner of the chunk list
     * @throws IllegalStateException if the chunk list is not {@link #isReadOnly() read-only}
     *         and its current owner differs from the one specified
     */
    public void handOver(Object oldOwner, Object newOwner) {
        if (this.owner == ChunkUtil.READ_ONLY_OWNER)
            return; // do nothing
        if (this.owner != oldOwner)
            throw new IllegalStateException("invalid owner, expected " + ChunkUtil.ownerString(oldOwner) + ", found " + ChunkUtil.ownerString(this.owner));
        this.owner = newOwner;
    }

    /**
     * Returns this chunk list <b>as well as all the chunks it contains</b> into the pool.
     * This method has no effect if this chunk list is {@link #isReadOnly() read-only}.
     * A reference to this chunk list is considered to become invalid after
     * invocation of this method and may no longer be used, unless the chunk list was read-only.
     *
     * @param owner current owner of the chunk list
     * @throws IllegalStateException if the chunk list is not {@link #isReadOnly() read-only}
     *         and its current owner differs from the one specified
     * @see Chunk#recycle(Object)
     */
    public void recycle(Object owner) {
        if (isReadOnly())
            return;
        checkOwner(owner);
        for (int i = head; i < tail; i++) {
            chunkArray[i].recycle(internalOwner);
            chunkArray[i] = null;
        }
        head = tail = 0;
        ChunkPool pool = poolReference.get();
        if (pool != null)
            pool.recycleChunkList(this, owner);
        else {
            // "destroy" this chunk list so no one will be able use it again
            handOver(owner, ChunkUtil.GARBAGE_OWNER);
            head = tail = -1;
        }
    }

    /**
     * Throws an exception if the chunk list is not owned by specified owner.
     *
     * @param owner expected owner of the chunk list
     * @throws IllegalStateException if the chunk list's current owner differs from the one specified
     */
    protected void checkOwner(Object owner) {
        if (this.owner != owner)
            throw new IllegalStateException(this.owner == ChunkUtil.READ_ONLY_OWNER ? "chunk list is read-only" : "invalid owner");
    }

    protected void checkRange(int index) {
        if (index < 0 || index >= tail - head)
            throw new IndexOutOfBoundsException();
    }

    /**
     * Returns a string representation of the object.
     * @return a string representation of the object.
     */
    @Override
    public String toString() {
        return "ChunkList{" +
            "chunkArray.length=" + chunkArray.length + "," +
            "size=" + size() + "," +
            "totalLength=" + getTotalLength() + "," +
            "pool=" + poolReference.get() +
            "}";
    }

    // ==================== Reusable Iterator implementation ====================

    private class ChunkIterator implements Iterator<Chunk> {
        int index;

        ChunkIterator() {
            index = head;
        }

        public boolean hasNext() {
            if (index < head)
                throw new IllegalStateException();
            if (index < tail)
                return true;
            index = -1;
            if (!isReadOnly())
                pooledIterator = this;
            return false;
        }

        public Chunk next() {
            if (index < head)
                throw new IllegalStateException();
            if (index < tail)
                return chunkArray[index++];
            throw new NoSuchElementException();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
