/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.rmi.test.throughput;

import java.util.Arrays;
import java.util.Random;

import com.devexperts.rmi.*;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRequestType;

class RequestingThread extends Thread {
	private final RMIEndpoint endpoint;
	private final int parameterSize;
	private final boolean oneWay;
	private final ClientSideStats stats;

	private static final RMIOperation<byte[]> pingOperation = RMIOperation.valueOf(ITestingService.class.getName(), byte[].class, "ping", byte[].class);

	RequestingThread(RMIEndpoint endpoint, int parameterSize, boolean oneWay, ClientSideStats stats) {
		super("RequestingThread");
		this.setDaemon(true);
		this.endpoint = endpoint;
		this.parameterSize = parameterSize;
		this.oneWay = oneWay;
		this.stats = stats;
	}

	@Override
	public void run() {
		Random rnd = new Random();
		while (!interrupted()) {
			byte[] arg = new byte[parameterSize];
			rnd.nextBytes(arg);
			RMIRequest<byte[]> request;
			if (oneWay) {
				request = endpoint.getClient().createRequest(new RMIRequestMessage<>(RMIRequestType.ONE_WAY,
					pingOperation, endpoint.getSecurityController().getSubject(), new Object[] {arg}));
			} else {
				request = endpoint.createRequest(endpoint.getSecurityController().getSubject(), pingOperation, new Object[]{arg});
			}
			request.send();
			stats.requestSent();
			if (!oneWay) {
				try {
					byte[] res = request.getBlocking();
					if (!Arrays.equals(res, arg)) {
						throw new AssertionError();
					}
				} catch (RMIException e) {
					System.out.println("Request failed with RMIException:");
					System.out.println(e.getMessage());
				}
				stats.requestCompleted(request);
			}
		}
	}
}
