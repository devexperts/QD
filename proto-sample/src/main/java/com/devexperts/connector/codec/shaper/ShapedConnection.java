/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.connector.codec.shaper;

import java.io.IOException;

import com.devexperts.connector.codec.CodecConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.io.*;

class ShapedConnection extends CodecConnection<ShapedConnectionFactory> {
	private final double throughput; // decimal kilobytes per second (or bytes per millisecond)
	private final Object chunksLock = new Object();

	private volatile long lastTime;
	private volatile boolean delegateHasChunks = true;
	private ChunkList chunksForSending;

	public ShapedConnection(ApplicationConnectionFactory delegateFactory, ShapedConnectionFactory config,
		TransportConnection transportConnection) throws IOException
	{
		super(delegateFactory, config, transportConnection);
		throughput = config.throughput;
	}

	@Override
	protected void startImpl() {
		lastTime = System.currentTimeMillis();
		super.startImpl();
	}

	@Override
	protected void closeImpl() {
		super.closeImpl();
		synchronized (chunksLock) {
			if (chunksForSending != null) {
				chunksForSending.recycle(this);
				chunksForSending = null;
			}
		}
	}

	@Override
	public long examine(long currentTime) {
		boolean hasChunks;
		synchronized (chunksLock) {
			hasChunks = chunksForSending != null;
		}
		long nextTime = super.examine(currentTime);
		if (delegateHasChunks || hasChunks) {
			notifyChunksAvailable();
			nextTime = Math.min(nextTime, currentTime + 10);
		}
		return nextTime;
	}

	@Override
	public ChunkList retrieveChunks(Object owner) {
		synchronized (chunksLock) {
			if (isClosed())
				return null;
			
			long curTime = System.currentTimeMillis();
			long allowedSize = (long)((curTime - lastTime) * throughput);
			long availableSize = chunksForSending == null ? 0 : chunksForSending.getTotalLength();
			if (availableSize < allowedSize && delegateHasChunks) {
				delegateHasChunks = false;
				ChunkList delegateChunks = delegate.retrieveChunks(this);
				if (delegateChunks != null) {
					availableSize += delegateChunks.getTotalLength();
					if (chunksForSending == null)
						chunksForSending = delegateChunks;
					else
						chunksForSending.addAll(delegateChunks, this);
				}
			}

			ChunkList result;
			if (availableSize <= allowedSize) {
				result = chunksForSending;
				chunksForSending = null;
			} else {
				result = ChunkPool.DEFAULT.getChunkList(this);
				while (allowedSize > 0) {
					Chunk chunk = chunksForSending.get(0);
					if (chunk.getLength() > allowedSize) {
						result.addAll(ChunkPool.DEFAULT.copyToChunkList(chunk.getBytes(), chunk.getOffset(), (int)allowedSize, this), this);
						chunksForSending.setChunkRange(0, chunk.getOffset() + (int)allowedSize, chunk.getLength() - (int)allowedSize, this);
						allowedSize = 0;
					} else {
						result.add(chunk = chunksForSending.poll(this), this);
						allowedSize -= chunk.getLength();
					}
				}
			}

			if (result != null)
				result.handOver(this, owner);

			lastTime = curTime;
			return result;
		}
	}

	@Override
	public void chunksAvailable() {
		delegateHasChunks = true;
		super.chunksAvailable();
	}
}
