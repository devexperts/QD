/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.rmi.impl;

import com.devexperts.io.Marshalled;
import com.devexperts.rmi.message.RMIRequestMessage;

class ServerRequestInfo {
	final long reqId;
	final long channelId;
	final RMIRequestMessage<?> message;
	final Marshalled<?> subject;
	final RMIMessageKind kind;

	ServerRequestInfo(RMIMessageKind kind, long reqId, long channelId, RMIRequestMessage<?> message, Marshalled<?> subject) {
		this.reqId = reqId;
		this.channelId = channelId;
		this.subject = subject;
		this.message = message;
		this.kind = kind;
	}
}
