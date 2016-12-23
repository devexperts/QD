/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.monitoring;

import java.util.Arrays;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.stats.QDStats;

/**
 * Collects total and per-record delta values for specific stat values.
 */
class Cur {
	private static final long[] EMPTY = new long[0];

	final QDStats.SValue statValue;

	long totalDelta; // total delta
	long[] recordDelta = EMPTY; // per-record delta bytes, only used when statValue.isRid()

	Cur(QDStats.SValue statValue) {
		this.statValue = statValue;
	}

	void clear(int nRecords) {
		totalDelta = 0;
		if (statValue.isRid()) {
			// reserve space for record deltas
			if (nRecords == 0)
				recordDelta = EMPTY;
			else if (recordDelta.length != nRecords)
				recordDelta = new long[nRecords];
			else
				Arrays.fill(recordDelta, 0);
		}
	}

	int findMax() {
		// don't report if less than 1% of total bytes
		long max = totalDelta / 100;
		int index = -1;
		for (int i = 0; i < recordDelta.length; i++)
			if (recordDelta[i] > max) {
				max = recordDelta[i];
				index = i;
			}
		return index;
	}

	String fmtMax(DataScheme scheme, StringBuilder sb, String name, int index, String sep) {
		if (index < 0 || totalDelta <= 0) // Note: curTot may be zero even when curVal[index] > 0, because of async reading
			return sep;
		// Note: because of async reading v may be over 100%, so we clamp it to 100% to avoid surprising log results
		int v = Math.min(100, (int)(recordDelta[index] * 100 / totalDelta));
		sb.append(sep).append(name).append(' ').
			append(scheme.getRecord(index).getName()).append(": ").append(v).append("%");
		return "; ";
	}
}
