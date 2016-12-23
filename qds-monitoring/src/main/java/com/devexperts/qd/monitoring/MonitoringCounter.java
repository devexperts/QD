/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.monitoring;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility class for monitoring counters.   
 */
public class MonitoringCounter extends AtomicLong {
	public void add(long value) {
		addAndGet(value);
	}

	/**
	 * Updates value to the new_value and returns difference
	 * between new_value and old value.
	 */
	public long update(long new_value) {
		long old_value = getAndSet(new_value);
		return new_value - old_value;
	}
}
