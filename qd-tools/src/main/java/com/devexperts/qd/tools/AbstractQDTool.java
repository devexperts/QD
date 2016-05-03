/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.tools;

import java.util.List;

import com.devexperts.qd.QDFactory;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.tools.*;
import com.devexperts.services.Service;

/**
 * Base class for all QDS tools.
 */
@Service
public abstract class AbstractQDTool extends AbstractTool {
	protected AbstractQDTool() {
		// Force QDS version print to log
		QDFactory.showVersion();
	}

	/**
	 * Parses arguments for this tool.
	 * @param args arguments and options.
	 * @throws BadToolParametersException if couldn't parse tool arguments or options.
	 */
	@Override
	public void parse(String[] args) throws BadToolParametersException {
		options = new QDOptions(getOptions());
		this.args = options.parse(args); // remaining arguments (parameters)
	}

	protected QDEndpoint.Builder getEndpointBuilder() {
		return ((QDOptions) options).getEndpointBuilder();
	}

	/**
	 * Returns a list of connectors that must become inactive before the tool can terminate.
	 */
	public List<MessageConnector> mustWaitWhileActive() {
		return null;
	}
}
