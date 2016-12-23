/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.api.impl;

import java.util.Iterator;
import java.util.List;

import com.devexperts.qd.qtp.*;
import com.dxfeed.api.DXEndpoint;

class DXConnectorInitializer implements QDEndpoint.ConnectorInitializer {
	private DXEndpoint dxEndpoint;

	DXConnectorInitializer(DXEndpoint dxEndpoint) {
		this.dxEndpoint = dxEndpoint;
	}

	@Override
	public void createAndAddConnector(QDEndpoint qdEndpoint, String address) {
		ConfigurableMessageAdapterFactory factory =
			DXEndpointImpl.getMessageAdapterFactory(qdEndpoint, dxEndpoint.getRole());
		List<MessageConnector> connectors = MessageConnectors.createMessageConnectors(factory, address, qdEndpoint.getRootStats());
			// remove non-demand connectors in ON_DEMAND_FEED role
		if (dxEndpoint.getRole() == DXEndpoint.Role.ON_DEMAND_FEED) {
			for (Iterator<MessageConnector> it = qdEndpoint.getConnectors().iterator(); it.hasNext(); ) {
				MessageConnector connector = it.next();
				if (!(connector instanceof OnDemandConnectorMarker))
					it.remove();
			}
		}
		// add connectors
		qdEndpoint.addConnectors(connectors);
	}
}
