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
package com.devexperts.rmi.impl;

import com.devexperts.auth.AuthSession;
import com.devexperts.io.BufferedInput;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataIterator;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SubscriptionIterator;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.qtp.MasterMessageAdapter;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageDescriptor;
import com.devexperts.qd.qtp.MessageProvider;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.MessageVisitor;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.qd.qtp.auth.QDAuthRealm;
import com.devexperts.qd.qtp.auth.QDLoginHandler;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.util.LogUtil;
import com.devexperts.util.TypedMap;

import java.io.IOException;
import java.util.EnumSet;
import javax.annotation.Nonnull;

/**
 * <code>RMIMessageAdapter</code> adapts {@link RMIConnection} to message API.
 */
class RMIMessageAdapter extends MessageAdapter implements MasterMessageAdapter {

    // ==================== private static fields ====================

    /**
     * How much time to wait before assuming that "legacy" (pre QDS 3.69) implementation,
     * that is not sending {@link MessageType#DESCRIBE_PROTOCOL DESCRIBE_PROTOCOL} message,
     * supports RMI.
     */
    private static final long LEGACY_WAIT_INTERVAL = getLong(RMIMessageAdapter.class.getName() + ".LegacyWaitInterval", 3000); // 3 sec

    private static final DataScheme EMPTY_SCHEME = new DefaultScheme(new PentaCodec());

    // NOTE: Retrieval from attached adapter must always come first, because it composes DESCRIBE PROTOCOL
    // message that must be sent first.
    private static final int RETRIEVE_ATTACH = 0;
    private static final int RETRIEVE_RMI_REQ = 1;
    private static final int RETRIEVE_RMI_RES = 2;
    private static final int RETRIEVE_RMI_ADS = 3;
    private static final int RETRIEVE_COUNT = 4;

    private static final MessageType ATTACHED_ADAPTER_MASK_TYPE = MessageType.RAW_DATA; // just for flag

    private static final Logging log = Logging.getLogging(RMIMessageAdapter.class);

    // ==================== private static helper methods ====================

    private static long getLong(String key, long defVal) {
        try {
            defVal = Long.getLong(key, defVal);
        } catch (SecurityException ex) {
            // ignored
        }
        return defVal;
    }

    // ==================== private instance fields ====================

    private final RMIConnection connection;
    private final MessageAdapter attachedAdapter;

    private volatile boolean remoteSupportsComboResponseMessage = false;

    // time when we need to start sending all subscription Long.MAX_VALUE -> never, by default(0) -- send immediately
    private volatile long legacyWaitTillTimeMillis;

    // next time when [aggregating] attached adapter has to be retrieved
    private volatile long nextRetrieveAttachedAdapter = Long.MAX_VALUE;

    // NOTE: Retrieval from attached adapter must always come first, because it composes DESCRIBE PROTOCOL
    // message that must be sent first.
    private int retrieve = RETRIEVE_ATTACH; // see RETRIEVE_XXX constants

    // ==================== constructor ====================

    RMIMessageAdapter(RMIConnection connection, QDStats stats, MessageAdapter attachedAdapter) {
        super(connection.endpoint.getQdEndpoint(), stats);
        this.connection = connection;
        this.attachedAdapter = attachedAdapter;
        if (attachedAdapter != null) {
            attachedAdapter.setCloseListener(adapter -> {
                if (adapter.isMarkedForImmediateRestart())
                    markForImmediateRestart();
                close();
            });
            attachedAdapter.setMessageListener(this::attachedAdapterMessagesAvailable);
        }
    }

    // ==================== methods ====================

    @Override
    public DataScheme getScheme() {
        return attachedAdapter != null ? attachedAdapter.getScheme() : EMPTY_SCHEME;
    }

    @Override
    public void setAuthRealm(QDAuthRealm authRealm) {
        if (attachedAdapter != null)
            attachedAdapter.setAuthRealm(authRealm);
        else
            super.setAuthRealm(authRealm);
    }

    @Override
    public void setLoginHandler(QDLoginHandler loginHandler) {
        if (attachedAdapter != null)
            attachedAdapter.setLoginHandler(loginHandler);
        else
            super.setLoginHandler(loginHandler);
    }

    @Override
    public void setConnectionVariables(@Nonnull TypedMap connectionVariables) {
        super.setConnectionVariables(connectionVariables);
        if (attachedAdapter != null)
            attachedAdapter.setConnectionVariables(connectionVariables);
    }

    @Override
    protected void startImpl(MasterMessageAdapter master) {
        if (master != null)
            throw new IllegalArgumentException(); // It is always a master by itself and does not support other masters
        legacyWaitTillTimeMillis = System.currentTimeMillis() + LEGACY_WAIT_INTERVAL;
        connection.start();
        if (attachedAdapter != null) {
            attachedAdapter.start(this); // attached adapter is always slave (RMI is its master that sends protocol descriptor)
        } else
            super.startImpl(this); // otherwise, we start ourselves
    }

    @Override
    protected void closeImpl() {
        connection.close();
        if (attachedAdapter != null)
            attachedAdapter.close();
    }

    @Override
    public boolean supportsMixedSubscription() {
        return attachedAdapter != null && attachedAdapter.supportsMixedSubscription();
    }

    @Override
    public void processOtherMessage(int messageTypeId, BufferedInput data, int len) {
        if (!isAlive()) {
            return;
        }
        try {
            switch (messageTypeId) {
            case MESSAGE_RMI_RESULT:
                connection.messageProcessor.processOldResultMessage(data);
                break;
            case MESSAGE_RMI_ERROR:
                connection.messageProcessor.processOldErrorMessage(data);
                break;
            case MESSAGE_RMI_RESPONSE:
                connection.messageProcessor.processComboResponseMessage(data);
                break;
            case MESSAGE_RMI_REQUEST:
                if (remoteSupportsComboResponseMessage) {
                    connection.messageProcessor.processComboRequestMessage(data);
                } else {
                    connection.messageProcessor.processOldRequestMessage(data);
                }
                break;
            case MESSAGE_RMI_ADVERTISE_SERVICES:
                connection.messageProcessor.processAdvertiseServicesMessage(data);
                break;
            case MESSAGE_RMI_DESCRIBE_OPERATION:
                connection.messageProcessor.processDescribeOperationMessage(data);
                break;
            case MESSAGE_RMI_DESCRIBE_SUBJECT:
                connection.messageProcessor.processDescribeSubjectMessage(data);
                break;
            case MESSAGE_RMI_CANCEL:
                connection.messageProcessor.processOldCancelMessage(data);
                break;
            default:
                if (attachedAdapter == null)
                    handleUnknownMessage(messageTypeId);
                else
                    attachedAdapter.processOtherMessage(messageTypeId, data, len);
            }
        } catch (IOException e) {
            handleCorruptedMessage(messageTypeId);
        }
    }

    @Override
    protected void processData(DataIterator iterator, MessageType message) {
        if (attachedAdapter == null)
            super.processData(iterator, message);
        else
            switch (message) {
            case TICKER_DATA:
                attachedAdapter.processTickerData(iterator);
                break;
            case STREAM_DATA:
                attachedAdapter.processStreamData(iterator);
                break;
            case HISTORY_DATA:
                attachedAdapter.processHistoryData(iterator);
                break;
            default:
                throw new IllegalArgumentException("non-data message type");
            }
    }

    @Override
    protected void processSubscription(SubscriptionIterator iterator, MessageType message) {
        if (attachedAdapter == null)
            super.processSubscription(iterator, message);
        else
            switch (message) {
            case TICKER_ADD_SUBSCRIPTION:
                attachedAdapter.processTickerAddSubscription(iterator);
                break;
            case TICKER_REMOVE_SUBSCRIPTION:
                attachedAdapter.processTickerRemoveSubscription(iterator);
                break;
            case STREAM_ADD_SUBSCRIPTION:
                attachedAdapter.processStreamAddSubscription(iterator);
                break;
            case STREAM_REMOVE_SUBSCRIPTION:
                attachedAdapter.processStreamRemoveSubscription(iterator);
                break;
            case HISTORY_ADD_SUBSCRIPTION:
                attachedAdapter.processHistoryAddSubscription(iterator);
                break;
            case HISTORY_REMOVE_SUBSCRIPTION:
                attachedAdapter.processHistoryRemoveSubscription(iterator);
                break;
            default:
                throw new IllegalArgumentException("not-subscription message type");
            }
    }

    @Override
    public boolean retrieveMessages(MessageVisitor visitor) {
        super.retrieveMessages(visitor);
        long currentTime = System.currentTimeMillis();
        if (currentTime >= legacyWaitTillTimeMillis)
            processLegacyConnection();
        long mask = retrieveMask();
        if (attachedAdapter == null) {
            // only retrieve our own protocol description if not attached. If attached, then slave constructs protocol descriptor
            mask = retrieveDescribeProtocolMessage(visitor, mask);
        }
        // This loop is designed to find first message type that is available and retrieve it
        // Subsequent messages will be retrieve on the next invocation of this retrieveMessages method
        // NOTE: Retrieval from attached adapter must always come first, because it composes DESCRIBE PROTOCOL
        // message that must be sent first.
        for (int i = 0; i < RETRIEVE_COUNT; i++, retrieve = ++retrieve % RETRIEVE_COUNT) {
            switch (retrieve) {
            case RETRIEVE_ATTACH:
                // proceed to retrieve when nextRetrieveAttachedAdapter time was reached or the flag is set
                if (currentTime < nextRetrieveAttachedAdapter && !hasMessageMask(mask, ATTACHED_ADAPTER_MASK_TYPE))
                    continue;
                mask = retrieveAttachedMessages(visitor, mask);
                nextRetrieveAttachedAdapter = attachedAdapter.nextRetrieveTime(currentTime);
                break;
            case RETRIEVE_RMI_ADS:
                if (!hasMessageMask(mask, RMIQueueType.ADVERTISE.maskType()))
                    continue;
                mask = retrieveRMIMessages(visitor, mask, RMIQueueType.ADVERTISE);
                break;
            case RETRIEVE_RMI_REQ:
                if (!hasMessageMask(mask, RMIQueueType.REQUEST.maskType()))
                    continue;
                mask = retrieveRMIMessages(visitor, mask, RMIQueueType.REQUEST);
                break;
            case RETRIEVE_RMI_RES:
                if (!hasMessageMask(mask, RMIQueueType.RESPONSE.maskType()))
                    continue;
                mask = retrieveRMIMessages(visitor, mask, RMIQueueType.RESPONSE);
                break;
            default:
                throw new AssertionError();
            }
            break;
        }
        // start looking from a different message type next time
        retrieve = ++retrieve % RETRIEVE_COUNT;
        // note: addMask was previously enclosed into finally block. This could lead to StackOverflow and
        // offers no real protection, since any exception should terminate ongoing connection anyway.
        addMask(mask);
        // note that currentTime is checked against nextRetrieveAttachedAdapter to make sure that it is compatible
        // with aggregating QD AgetnAdapter so that if we don't make RETRIEVE_ATTACH during this invocation, but
        // the time has come to, then we return true to signal that retieveMessages must be invoked again.
        return mask != 0 || currentTime >= nextRetrieveAttachedAdapter;
    }

    private void attachedAdapterMessagesAvailable(MessageProvider provider) {
        assert provider == attachedAdapter;
        addMask(getMessageMask(ATTACHED_ADAPTER_MASK_TYPE));
    }

    private void processLegacyConnection() {
        log.warn("Legacy connection (pre QDS 3.69) is detected. Assuming RMI is supported at " +
            LogUtil.hideCredentials(getRemoteHostAddress()));
        setRemoteReceiveSet(EnumSet.of(
            MessageType.RMI_DESCRIBE_SUBJECT, MessageType.RMI_DESCRIBE_OPERATION,
            MessageType.RMI_REQUEST, MessageType.RMI_CANCEL,
            MessageType.RMI_RESULT, MessageType.RMI_ERROR));
    }

    @Override
    public long nextRetrieveTime(long currentTime) {
        return Math.min(Math.min(super.nextRetrieveTime(currentTime), legacyWaitTillTimeMillis),
            nextRetrieveAttachedAdapter);
    }

    @Override
    public void reinitConfiguration(AuthSession session) {
        if (attachedAdapter != null)
            attachedAdapter.reinitConfiguration(session);
        else
            super.reinitConfiguration(session);
    }

    @Override
    public void prepareProtocolDescriptor(ProtocolDescriptor desc) {
        super.prepareProtocolDescriptor(desc);
        if (attachedAdapter != null)
            attachedAdapter.prepareProtocolDescriptor(desc);
    }

    @Override
    public void augmentProtocolDescriptor(ProtocolDescriptor desc) {
        for (MessageType message : MessageType.values()) {
            // NOTE: Channel messages are sent and services by all RMI sides
            if (message.isRMIChan() ||
                message.isRMIReq() && connection.side.hasServer() ||
                message.isRMIRes() && connection.side.hasClient())
            {
                desc.addReceive(desc.newMessageDescriptor(message));
            }
            if (message.isRMIChan() ||
                message.isRMIReq() && connection.side.hasClient() ||
                message.isRMIRes() && connection.side.hasServer())
            {
                desc.addSend(desc.newMessageDescriptor(message));
            }
            if (message.isRMIAds()) {
                if (connection.side.hasClient()) {
                    MessageDescriptor rmiAdsDesc = desc.newMessageDescriptor(message);
                    rmiAdsDesc.setProperty(ProtocolDescriptor.SERVICES_PROPERTY, connection.configuredServices.toString());
                    desc.addReceive(rmiAdsDesc);
                }
                if (connection.side.hasServer() && connection.configuredServices != ServiceFilter.NOTHING &&
                    connection.adFilter.isSendAdvertisement()) {
                    desc.addSend(desc.newMessageDescriptor(message));
                }
            }
        }
        desc.setProperty(ProtocolDescriptor.RMI_PROPERTY, String.valueOf(connection.endpoint.side.toString()));
    }

    @Override
    protected void prepareAuthenticateProtocolDescriptor(ProtocolDescriptor desc) {
        super.prepareAuthenticateProtocolDescriptor(desc);
    }

    @Override
    public void processDescribeProtocol(ProtocolDescriptor desc, boolean logDescriptor) {
        super.processDescribeProtocol(desc, logDescriptor);
        if (attachedAdapter != null)
            attachedAdapter.processDescribeProtocol(desc, false);
        connection.messageComposer.setSupportTargetRouteProtocol(desc.getEndpointId() != null);
        setRemoteReceiveSet(desc.getReceiveSet());
        MessageDescriptor rmiAdsDesc = desc.getReceive(MessageType.RMI_ADVERTISE_SERVICES);
        ServiceFilter filter = rmiAdsDesc != null ?
            ServiceFilter.valueOf(rmiAdsDesc.getProperty(ProtocolDescriptor.SERVICES_PROPERTY)) :
            ServiceFilter.NOTHING; // does not receive any RMI advertisements
        connection.serverDescriptorsManager.setServicesOnDescribeProtocolAndSendAllDescriptors(
            connection.configuredServices.intersection(filter));
        remoteSupportsComboResponseMessage = desc.canSend(MessageType.RMI_RESPONSE);
        if (getRemoteRMISide(desc).hasServer())
            connection.requestsManager.setAnonymousOnDescribeProtocol(!desc.canSend(MessageType.RMI_ADVERTISE_SERVICES));
    }

    private RMIEndpoint.Side getRemoteRMISide(ProtocolDescriptor desc) {
        return desc.canSend(MessageType.RMI_RESPONSE)
            ? getRemoteRMISideFromProp(desc)
            : getRemoteRMISideByMessages(desc);
    }

    private RMIEndpoint.Side getRemoteRMISideFromProp(ProtocolDescriptor desc) {
        String s = desc.getProperty(ProtocolDescriptor.RMI_PROPERTY);
        if (s == null)
            return RMIEndpoint.Side.NONE;
        try {
            return RMIEndpoint.Side.valueOf(s);
        } catch (IllegalArgumentException e) {
            return RMIEndpoint.Side.NONE;
        }
    }

    private RMIEndpoint.Side getRemoteRMISideByMessages(ProtocolDescriptor desc) {
        RMIEndpoint.Side side = RMIEndpoint.Side.NONE;
        if (desc.canSend(MessageType.RMI_REQUEST)) {
            side = side.withClient();
        }
        if (desc.canSend(MessageType.RMI_RESULT) || desc.canSend(MessageType.RMI_ERROR)) {
            side = side.withServer();
        }
        return side;
    }

    private void setRemoteReceiveSet(EnumSet<MessageType> enumSet) {
        legacyWaitTillTimeMillis = Long.MAX_VALUE;
        connection.messageComposer.setRemoteReceiveSet(enumSet);
        if (connection.side.hasClient())
            rmiMessageAvailable(RMIQueueType.REQUEST);
    }

    private long retrieveRMIMessages(MessageVisitor visitor, long mask, RMIQueueType type) {
        if (!connection.messageComposer.retrieveRMIMessages(visitor, type))
            mask = clearMessageMask(mask, type.maskType());
        return mask;
    }

    private long retrieveAttachedMessages(MessageVisitor visitor, long mask) {
        if (!attachedAdapter.retrieveMessages(visitor))
            mask = clearMessageMask(mask, ATTACHED_ADAPTER_MASK_TYPE);
        return mask;
    }

    @Override
    public boolean isProtocolDescriptorCompatible(ProtocolDescriptor desc) {
        RMIEndpoint.Side remoteRMISide = getRemoteRMISide(desc);
        boolean ok =
            connection.side.hasClient() && remoteRMISide.hasServer() ||
            connection.side.hasServer() && remoteRMISide.hasClient();
        if (ok || attachedAdapter == null)
            return ok;
        return attachedAdapter.isProtocolDescriptorCompatible(desc);
    }

    @Override
    public void useDescribeProtocol() {
        super.useDescribeProtocol();
        if (attachedAdapter != null)
            attachedAdapter.useDescribeProtocol();
    }

    // Returns true, if the RMIMessageAdapter wasn't sending any RMI messages when this method
    // was called (so it is supposed that it would wake up and process new request).
    boolean rmiMessageAvailable(RMIQueueType queueType) {
        return addMask(getMessageMask(queueType.maskType()));
    }

    @Override
    public String toString() {
        return attachedAdapter == null ? "RMI" : attachedAdapter.toString();
    }
}
