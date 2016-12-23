/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.tools;

import java.util.ArrayList;
import java.util.List;

import com.devexperts.qd.*;
import com.devexperts.qd.ng.*;
import com.devexperts.qd.qtp.QDEndpoint;

/**
 * This thread connects to collector via {@link com.devexperts.qd.QDAgent agent}
 * and counts number of received records.
 *
 * @see NetTestConsumerSide
 * @see NetTestWorkingThread
 */
class NetTestConsumerAgentThread extends NetTestWorkingThread {

	private final List<QDAgent> agents;
	private boolean signalled;

	NetTestConsumerAgentThread(int index, NetTestConsumerSide side, QDEndpoint endpoint) {
		super("ConsumerAgentThread", index, side, endpoint);
		agents = new ArrayList<QDAgent>();
		createAgent(endpoint.getTicker());
		createAgent(endpoint.getStream());
		createAgent(endpoint.getHistory());
	}

	private void createAgent(QDCollector collector) {
		if (collector != null) {
			agents.add(collector.agentBuilder().build());
		}
	}

	@Override
	public void run() {
		subscribe();
		DataVisitor countingVisitor = new AbstractRecordSink() {
			@Override
			public void append(RecordCursor cursor) {
				processedRecords++;
			}
		};

		RecordListener listener = new RecordListener() {
			public synchronized void recordsAvailable(RecordProvider provider) {
				signalled = true;
				notifyAll();
			}
		};
		for (QDAgent agent : agents) {
			agent.setRecordListener(listener);
		}
		try {
			while (true) {
				for (QDAgent agent : agents) {
					while (agent.retrieveData(countingVisitor)) {}
				}
				synchronized (listener) {
					while (!signalled)
						listener.wait();
					signalled = false;
				}
			}
		} catch (InterruptedException e) {
			// do nothing
		}
	}

	private void subscribe() {
		RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
		int num = 0;
		if (side.config.wildcard) {
			sub.add(NetTestSide.RECORD, NetTestSide.SCHEME.getCodec().getWildcardCipher(), null);
		} else {
			SymbolList subList = side.symbols.generateRandomSublist(side.config.symbolsPerEntity);
			for (int i = 0; i < subList.size(); i++) {
				sub.add(NetTestSide.RECORD, side.symbols.getCipher(i), side.symbols.getSymbol(i));
			}
			num = subList.size();
		}
		for (QDAgent agent : agents) {
			agent.setSubscription(sub);
			sub.rewind();
		}
		sub.release();
		QDLog.log.info("Consumer #" + index + " subscribed to " +
			(side.config.wildcard ? "wildcard" : num + " symbols"));
	}

}
