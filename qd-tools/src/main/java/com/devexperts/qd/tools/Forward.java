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

import com.devexperts.logging.Logging;
import com.devexperts.qd.QDLog;
import com.devexperts.qd.qtp.*;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.impl.RMIEndpointImpl;
import com.devexperts.services.ServiceProvider;
import com.devexperts.tools.*;


/**
 * Forward tool.
 */
@ToolSummary(
	info = "Forwarding a request.",
	argString = "<address>",
	arguments = {
		"<address> --  address to connect to forward (see @link{address})"
	}
)
@ServiceProvider
public class Forward extends AbstractQDTool {
	private final OptionLog logfile = OptionLog.getInstance();
	private final OptionName name = new OptionName("forward");
	private final OptionForward forward = new OptionForward();

	private QDEndpoint qdEndpoint;

	@Override
	protected Option[] getOptions() {
		return new Option[] { logfile, name, forward };
	}

	@Override
	protected void executeImpl(String[] args) {
		if (args.length == 0)
			noArguments();

		if (args.length != 1)
			wrongNumberOfArguments();

		if (!forward.isSet())
			noRequiredOptions();

		qdEndpoint = QDEndpoint.newBuilder().withName(name.getName()).build();

		String address = args[0];

		RMIEndpoint forwardServerEndpoint = new RMIEndpointImpl(RMIEndpoint.Side.SERVER, qdEndpoint, null, null);

		Logging log = QDLog.log;
		log.info("Using address " + MessageConnectors.maskAuthorizationData(address));

		forward.applyForwards(forwardServerEndpoint.getServer(), qdEndpoint);
		log.info("Configured requests forwarding for the following services: " + forward.getServices());

		forwardServerEndpoint.connect(address);
	}

	@Override
	public List<MessageConnector> mustWaitWhileActive() {
		return qdEndpoint.getConnectors();
	}

	public static void main(String[] args) {
		Tools.executeSingleTool(Forward.class, args);
	}

}
