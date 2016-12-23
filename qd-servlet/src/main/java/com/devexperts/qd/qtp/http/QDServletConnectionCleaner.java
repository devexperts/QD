/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.qtp.http;

import java.util.ArrayList;
import java.util.HashSet;

import com.devexperts.qd.QDLog;

class QDServletConnectionCleaner extends Thread {
	private static final HashSet<QDServletConnection> connections = new HashSet<QDServletConnection>();
	private static final ArrayList<QDServletConnection> cleanupConnections = new ArrayList<QDServletConnection>();
	private static QDServletConnectionCleaner instance;

	public static synchronized void addConnection(QDServletConnection connection) {
		connections.add(connection);
		// make sure that cleaner is started;
		if (instance == null || !instance.isAlive()) {
			instance = new QDServletConnectionCleaner();
			instance.start();
		}
	}

	public static synchronized void removeConnection(QDServletConnection connection) {
		connections.remove(connection);
	}

	private static synchronized void cleanup() {
		long time = System.currentTimeMillis();
		for (QDServletConnection connection : connections)
			if (connection.isTimedOut(time))
				cleanupConnections.add(connection);
		// we have to close in a separate loop to avoid ConcurrentModificationException
		for (QDServletConnection connection : cleanupConnections)
			connection.close("connection timed out");
		cleanupConnections.clear();
	}

	private QDServletConnectionCleaner() {
		super("QDServletConnectionCleaner");
		setDaemon(true);
	}

	public void run() {
		try {
			while (true) {
				Thread.sleep(1000);
				cleanup();
			}
		} catch (Throwable t) {
			QDLog.log.error("QDServletConnectionCleaner fatal error", t);
		}
	}
}
