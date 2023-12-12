/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools;

import com.devexperts.logging.Logging;
import com.devexperts.qd.QDLog;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.impl.RMIEndpointImpl;
import com.devexperts.rmi.task.RMIServiceDescriptor;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.LogUtil;

import java.util.List;

/**
 * Forward tool.
 */
@ToolSummary(
    info = "Displays information about advertises service instances.",
    argString = "<address>",
    arguments = {
        "<address> --  address for incoming service descriptors (see @link{address})"
    }
)

@ServiceProvider
public class Services extends AbstractTool {

    private final OptionName name = new OptionName("services");
    private final OptionLog logfile = OptionLog.getInstance();

    private QDEndpoint qdEndpoint;

    @Override
    protected Option[] getOptions() {
        return new Option[] { logfile };
    }

    @Override
    protected void executeImpl(String[] args) {
        if (args.length == 0)
            noArguments();

        if (args.length != 1)
            wrongNumberOfArguments();

        String address = args[0];

        qdEndpoint = QDEndpoint.newBuilder().withName(name.getName()).build();
        RMIEndpointImpl endpoint = new RMIEndpointImpl(RMIEndpoint.Side.CLIENT, qdEndpoint, null, null);

        log.info("Using address " + LogUtil.hideCredentials(address));

        endpoint.getClient().getService("*").addServiceDescriptorsListener(descriptors -> {
            for (RMIServiceDescriptor descriptor : descriptors)
                log.info("Received descriptor " + descriptor);
        });
        endpoint.connect(address);
    }

    @Override
    public List<MessageConnector> mustWaitWhileActive() {
        return qdEndpoint.getConnectors();
    }

    public static void main(String[] args) {
        Tools.executeSingleTool(Services.class, args);
    }
}
