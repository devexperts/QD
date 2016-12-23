/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.monitoring;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import com.devexperts.qd.QDLog;

class JmxConnectors {
	private static final ConcurrentHashMap<Integer, JmxConnector> JMX_CONNECTORS = new ConcurrentHashMap<Integer, JmxConnector>();

	static boolean isPortAvailable(Integer port) {
		return !JMX_CONNECTORS.contains(port);
	}

	static boolean addConnector(int port, JmxConnector connector) {
		return JMX_CONNECTORS.putIfAbsent(port, connector) == null;
	}

	static void removeConnector(JmxConnector connector) {
		JMX_CONNECTORS.remove(connector.getName());
	}

	static void stopConnectors() {
		Collection<JmxConnector> values = JMX_CONNECTORS.values();
		// take snapshot to stop them all
		for (JmxConnector connector : values.toArray(new JmxConnector[values.size()])) {
			try {
				connector.stop();
			} catch (Exception e) {
				QDLog.log.error("Failed to stop JMX connector " + connector, e);
			}
		}
	}
}
