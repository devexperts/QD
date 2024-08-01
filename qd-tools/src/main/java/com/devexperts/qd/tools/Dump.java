/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools;

import com.devexperts.io.ByteArrayInput;
import com.devexperts.qd.DataIterator;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SubscriptionIterator;
import com.devexperts.qd.ng.AbstractRecordProvider;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.qtp.AbstractQTPParser;
import com.devexperts.qd.qtp.HeartbeatPayload;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.MessageConsumerAdapter;
import com.devexperts.qd.qtp.MessageListener;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.MessageVisitor;
import com.devexperts.qd.qtp.OutputStreamMessageVisitor;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.qd.qtp.ProtocolOption;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.qtp.RawDataConsumer;
import com.devexperts.qd.qtp.file.FileFormat;
import com.devexperts.qd.qtp.file.FileWriterImpl;
import com.devexperts.qd.qtp.text.TextQTPComposer;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.LogUtil;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Connects to specified address(es) and dumps all received data and subscription information.
 */
@ToolSummary(
    info = "Dumps all data and subscription information received from address.",
    argString = {"<address>", "<address> <subscription>"},
    arguments = {
        "<address> -- address to dump (see @link{address})",
        "<subscription> -- optional file to read ticker subscription from"
    }
)
@ServiceProvider
public class Dump extends AbstractTool {
    private final OptionLog logfile = OptionLog.getInstance();
    private final Option quiet = new Option('q', "quiet", "Be quiet (do not dump every incoming data record).");
    private final OptionString tape = new OptionString('t', "tape", "<file>[<opts>]",
        "Tape incoming data into the specified file. See @link{tape} for more details. Implies " + quiet + ".");
    private final Option stamp = new Option('S', "stamp", "Print timestamp for every incoming data record.");
    private final OptionName name = new OptionName("dump");
    private final OptionStat stat = new OptionStat();
    private final OptionManagementHtml html = OptionManagementHtml.getInstance();
    private final OptionManagementRmi rmi = OptionManagementRmi.getInstance();

    private List<MessageConnector> connectors;
    private List<Closeable> closeOnExit = new ArrayList<>();

    @Override
    protected Option[] getOptions() {
        return new Option[] { logfile, quiet, tape, stamp, name, stat, html, rmi };
    }

    @Override
    protected void executeImpl(String[] args) throws BadToolParametersException {
        if (args.length == 0) {
            noArguments();
        }
        if (args.length < 1 || args.length > 2) {
            wrongNumberOfArguments();
        }

        String address = args[0];
        RecordBuffer subscription = readSubscription(args.length > 1 ? args[1] : null);

        QDEndpoint endpoint = getEndpointBuilder().withName(name.getName()).build();
        closeOnExit.add(endpoint);
        DataScheme scheme = endpoint.getScheme();

        MessageVisitor writer = null;
        if (tape.isSet()) {
            FileWriterImpl fileWriter = FileWriterImpl.open(tape.getValue(), scheme);
            closeOnExit.add(fileWriter);
            writer = fileWriter;
        }
        if (writer == null && !quiet.isSet()) {
            TextQTPComposer composer;
            if (stamp.isSet())
                composer = new StampComposer(scheme, null);
            else {
                composer = new TextQTPComposer(scheme);
                composer.setWriteHeartbeat(true);
            }
            composer.setOptSet(ProtocolOption.SUPPORTED_SET); // use available protocol options
            writer = new OutputStreamMessageVisitor(System.out, composer, true);
        }

        connectors = MessageConnectors.createMessageConnectors(
            new DumperFactory(endpoint, subscription, writer, QDFilter.ANYTHING),
            address, endpoint.getRootStats());
        endpoint.addConnectors(connectors).startConnectors();
    }

    private RecordBuffer readSubscription(String filename) {
        final RecordBuffer subscription = new RecordBuffer(RecordMode.HISTORY_SUBSCRIPTION);
        if (filename == null || filename.trim().isEmpty())
            return subscription;
        File file = new File(filename);
        if (!file.exists() || !file.isFile())
            return subscription;
        log.info("Reading subscription from " + LogUtil.hideCredentials(filename));
        byte[] buffer = new byte[(int) file.length()];
        int length = 0;
        try (FileInputStream in = new FileInputStream(file)) {
            length = in.read(buffer);
        } catch (IOException e) {
            log.error("Error reading subscription", e);
        }
        AbstractQTPParser parser = FileFormat.detectFormat(buffer).createQTPParser(QDFactory.getDefaultScheme());
        parser.setInput(new ByteArrayInput(buffer, 0, length));
        parser.parse(new MessageConsumerAdapter() {
            @Override
            public void processTickerAddSubscription(SubscriptionIterator iterator) {
                subscription.processSubscription(iterator);
            }
        });
        log.info("Done reading subscription from " + LogUtil.hideCredentials(filename) + ": read " + subscription.size() + " elements");
        return subscription;
    }

    private static class DumperFactory extends MessageAdapter.AbstractFactory {
        private final RecordBuffer subscription;
        private final MessageVisitor writer;

        DumperFactory(QDEndpoint endpoint, RecordBuffer subscription, MessageVisitor writer, QDFilter filter) {
            super(endpoint, filter);
            this.subscription = subscription;
            this.writer = writer;
        }

        @Override
        public MessageAdapter createAdapter(QDStats stats) {
            return new Dumper(stats, endpoint, subscription, writer, getFilter());
        }
    }

    private static class Dumper extends MessageAdapter implements RawDataConsumer {
        private static final MessageType SUBSCRIPTION_TYPE = MessageType.TICKER_ADD_SUBSCRIPTION;

        private final DataScheme scheme;
        private final RecordBuffer subscription;
        private final MessageVisitor writer;
        private final QDFilter filter;

        // shared between processData/Subscription invocations, protected by sync(this)
        private final FilteredSink filteredSink;
        private final FilteredProvider filteredProvider;

        Dumper(QDStats stats, QDEndpoint endpoint, RecordBuffer subscription, MessageVisitor writer, QDFilter filter) {
            super(endpoint, stats);
            this.doNotCloseOnErrors = true; // special mode to decode even bad stuff
            this.scheme = endpoint.getScheme();
            this.subscription = new RecordBuffer();
            this.subscription.process(subscription);
            this.writer = writer;
            this.filter = filter;
            this.filteredSink = new FilteredSink(filter);
            this.filteredProvider = new FilteredProvider(filteredSink);
            useDescribeProtocol();
            addMask(getMessageMask(SUBSCRIPTION_TYPE));
        }

        @Override
        public DataScheme getScheme() {
            return scheme;
        }

        @Override
        public void prepareProtocolDescriptor(ProtocolDescriptor desc) {
            super.prepareProtocolDescriptor(desc);
            QDFilter stableFilter = filter.toStableFilter();
            if (stableFilter != QDFilter.ANYTHING)
                desc.setProperty(ProtocolDescriptor.FILTER_PROPERTY, stableFilter.toString());
            desc.addReceive(desc.newMessageDescriptor(MessageType.RAW_DATA));
            for (QDContract contract : QDContract.values()) {
                desc.addReceive(desc.newMessageDescriptor(MessageType.forData(contract)));
                desc.addReceive(desc.newMessageDescriptor(MessageType.forAddSubscription(contract)));
                desc.addReceive(desc.newMessageDescriptor(MessageType.forRemoveSubscription(contract)));
            }
        }

        @Override
        public void processDescribeProtocol(ProtocolDescriptor desc, boolean logDescriptor) {
            super.processDescribeProtocol(desc, logDescriptor);
            if (writer != null)
                writer.visitDescribeProtocol(desc);
        }

        @Override
        public void processHeartbeat(HeartbeatPayload heartbeatPayload) {
            if (writer != null)
                writer.visitHeartbeat(heartbeatPayload);
        }

        @Override
        public synchronized void processData(DataIterator iterator, MessageType message) {
            if (writer != null) {
                // we know that all connectors/parsers use RecordBuffers, so we just cast it to RecordSource
                filteredProvider.source = (RecordSource) iterator;
                filteredSink.contract = message.getContract();
                //noinspection StatementWithEmptyBody
                while (writer.visitData(filteredProvider, message)) {} // write all
            }
        }

        @Override
        protected synchronized void processSubscription(SubscriptionIterator iterator, MessageType message) {
            if (writer != null) {
                // we know that all connectors/parsers use RecordBuffers, so we just cast it to RecordSource
                filteredProvider.source = (RecordSource) iterator;
                filteredSink.contract = message.getContract();
                //noinspection StatementWithEmptyBody
                while (writer.visitSubscription(filteredProvider, message)) {} // write all
            }
        }

        @Override
        public boolean retrieveMessages(MessageVisitor visitor) {
            super.retrieveMessages(visitor);
            long mask = retrieveMask();
            if (hasMessageMask(mask, MessageType.DESCRIBE_PROTOCOL))
                mask = retrieveDescribeProtocolMessage(visitor, mask);
            if (hasMessageMask(mask, SUBSCRIPTION_TYPE) && !visitor.visitSubscription(subscription, SUBSCRIPTION_TYPE))
                mask = clearMessageMask(mask, SUBSCRIPTION_TYPE);
            return addMask(mask);
        }

        @Override
        public void setMessageListener(MessageListener listener) {
            super.setMessageListener(listener);
            notifyListener();
        }
    }

    private static class FilteredSink extends AbstractRecordSink {
        final QDFilter filter;
        QDContract contract;
        RecordSink sink;

        FilteredSink(QDFilter filter) {
            this.filter = filter;
        }

        @Override
        public void append(RecordCursor cur) {
            if (filter.accept(contract, cur.getRecord(), cur.getCipher(), cur.getSymbol()))
                sink.append(cur);
        }
    }

    private static class FilteredProvider extends AbstractRecordProvider {
        final FilteredSink filteredSink;
        RecordSource source;

        private FilteredProvider(FilteredSink filteredSink) {
            this.filteredSink = filteredSink;
        }

        @Override
        public RecordMode getMode() {
            return source.getMode();
        }

        @Override
        public boolean retrieve(RecordSink sink) {
            filteredSink.sink = sink;
            return source.retrieve(filteredSink);
        }
    }

    @Override
    public List<MessageConnector> mustWaitWhileActive() {
        return connectors;
    }

    @Override
    public List<Closeable> closeOnExit() {
        return closeOnExit;
    }

    public static void main(String[] args) {
        Tools.executeSingleTool(Dump.class, args);
    }
}
