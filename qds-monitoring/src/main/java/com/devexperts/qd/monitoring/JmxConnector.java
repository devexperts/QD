/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.monitoring;

import java.io.IOException;

import com.devexperts.management.Management;

abstract class JmxConnector {
	private final String name;
	private Management.Registration registration;

	JmxConnector(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setRegistration(Management.Registration registration) {
		this.registration = registration;
	}

	public void stop() throws IOException {
		if (registration != null)
			registration.unregister();
		JmxConnectors.removeConnector(this);
	}

	@Override
	public String toString() {
		return name;
	}
}
