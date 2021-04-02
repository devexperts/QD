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
package com.devexperts.qd.qtp.file;

import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.qtp.HeartbeatPayload;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.MessageVisitor;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.util.InvalidFormatException;

import java.io.Closeable;

/**
 * Simple wrapper on top of {@link FileWriterImpl} supporting direct setXXX methods for configuration.
 */
public class FileWriter extends FileWriterParams.Default implements MessageVisitor, Closeable {
    private FileWriterImpl impl;

    public FileWriter() {}

    public FileWriter open(String dataFilePath, DataScheme scheme) throws InvalidFormatException {
        impl = new FileWriterImpl(dataFilePath, scheme, this);
        impl.open();
        return this;
    }

    @Override
    public void close() {
        impl.close();
    }

    @Override
    public void visitHeartbeat(HeartbeatPayload heartbeatPayload) {
        impl.visitHeartbeat(heartbeatPayload);
    }

    @Override
    public boolean visitData(DataProvider provider, MessageType message) {
        return impl.visitData(provider, message);
    }

    @Override
    public boolean visitSubscription(SubscriptionProvider provider, MessageType message) {
        return impl.visitSubscription(provider, message);
    }

    @Override
    public boolean visitOtherMessage(int messageType, byte[] messageBytes, int offset, int length) {
        return impl.visitOtherMessage(messageType, messageBytes, offset, length);
    }

    @Override
    @Deprecated
    public boolean visitTickerData(DataProvider provider) {
        return impl.visitTickerData(provider);
    }

    @Override
    @Deprecated
    public boolean visitTickerAddSubscription(SubscriptionProvider provider) {
        return impl.visitTickerAddSubscription(provider);
    }

    @Override
    @Deprecated
    public boolean visitTickerRemoveSubscription(SubscriptionProvider provider) {
        return impl.visitTickerRemoveSubscription(provider);
    }

    @Override
    @Deprecated
    public boolean visitStreamData(DataProvider provider) {
        return impl.visitStreamData(provider);
    }

    @Override
    @Deprecated
    public boolean visitStreamAddSubscription(SubscriptionProvider provider) {
        return impl.visitStreamAddSubscription(provider);
    }

    @Override
    @Deprecated
    public boolean visitStreamRemoveSubscription(SubscriptionProvider provider) {
        return impl.visitStreamRemoveSubscription(provider);
    }

    @Override
    @Deprecated
    public boolean visitHistoryData(DataProvider provider) {
        return impl.visitHistoryData(provider);
    }

    @Override
    @Deprecated
    public boolean visitHistoryAddSubscription(SubscriptionProvider provider) {
        return impl.visitHistoryAddSubscription(provider);
    }

    @Override
    public void visitDescribeProtocol(ProtocolDescriptor descriptor) {
        impl.visitDescribeProtocol(descriptor);
    }

    @Override
    @Deprecated
    public boolean visitHistoryRemoveSubscription(SubscriptionProvider provider) {
        return impl.visitHistoryRemoveSubscription(provider);
    }
}
