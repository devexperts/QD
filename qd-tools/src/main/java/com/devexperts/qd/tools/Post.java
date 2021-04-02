/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools;

import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.services.ServiceProvider;

import java.util.List;

@ToolSummary(
    info = "Sends records to an agent.",
    argString = "<address>",
    arguments = {
        "<address> -- uplink address to post to (see @link{address})"
    }
)
@ServiceProvider
public class Post extends AbstractTool {
    private final OptionLog logfile = OptionLog.getInstance();
    private final OptionPostCollector collector = new OptionPostCollector(OptionPostCollector.RAW);
    private final OptionName name = new OptionName("post");
    private PostingThread postingThread;

    @Override
    protected Option[] getOptions() {
        return new Option[] { logfile, collector, name };
    }

    @Override
    protected void executeImpl(String[] args) {
        if (args.length == 0) {
            noArguments();
        }
        if (args.length != 1) {
            wrongNumberOfArguments();
        }
        String address = args[0];
        QDEndpoint endpoint = getEndpointBuilder().withName(name.getName()).build();
        PostMessageQueue queue = new PostMessageQueue();
        List<MessageConnector> connectors = MessageConnectors.createMessageConnectors(
            new PostMessageAdapter.Factory(endpoint, queue),
            address, endpoint.getRootStats());
        MessageConnectors.startMessageConnectors(connectors);

        postingThread = new PostingThread(endpoint.getScheme(), queue, collector.getMessageType());
        postingThread.start();
    }

    @Override
    public Thread mustWaitForThread() {
        return postingThread;
    }

    public static void main(String[] args) {
        Tools.executeSingleTool(Post.class, args);
    }
}
