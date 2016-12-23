/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.qtp.file;

import java.io.IOException;

import com.devexperts.qd.qtp.MessageConsumerAdapter;

public interface MessageReader {
	public void readInto(MessageConsumerAdapter adapter) throws InterruptedException, IOException;
	public void close();
}
