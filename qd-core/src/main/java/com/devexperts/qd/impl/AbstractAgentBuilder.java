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
package com.devexperts.qd.impl;

import com.devexperts.qd.QDAgent;
import com.devexperts.qd.qtp.ProtocolOption;

public abstract class AbstractAgentBuilder extends AbstractBuilder<QDAgent.Builder, AbstractAgentBuilder> implements QDAgent.Builder {
    protected QDAgent.AttachmentStrategy<?> attachmentStrategy;
    protected boolean useHistorySnapshot;
    protected boolean hasEventTimeSequence;
    protected boolean hasVoidRecordListener;

    @Override
    public QDAgent.AttachmentStrategy<?> getAttachmentStrategy() {
        return attachmentStrategy;
    }

    @Override
    public boolean useHistorySnapshot() {
        return useHistorySnapshot;
    }

    @Override
    public boolean hasEventTimeSequence() {
        return hasEventTimeSequence;
    }

    @Override
    public boolean hasVoidRecordListener() {
        return hasVoidRecordListener;
    }

    @Override
    public QDAgent.Builder withAttachmentStrategy(QDAgent.AttachmentStrategy<?> attachmentStrategy) {
        if (attachmentStrategy == this.attachmentStrategy)
            return this;
        AbstractAgentBuilder result = clone();
        result.attachmentStrategy = attachmentStrategy;
        return result;
    }

    @Override
    public QDAgent.Builder withHistorySnapshot(boolean useHistorySnapshot) {
        if (useHistorySnapshot == this.useHistorySnapshot)
            return this;
        AbstractAgentBuilder result = clone();
        result.useHistorySnapshot = useHistorySnapshot;
        return result;
    }

    @Override
    public QDAgent.Builder withOptSet(ProtocolOption.Set optSet) {
        return withHistorySnapshot(optSet.contains(ProtocolOption.HISTORY_SNAPSHOT));
    }

    @Override
    public QDAgent.Builder withEventTimeSequence(boolean hasEventTimeSequence) {
        if (this.hasEventTimeSequence == hasEventTimeSequence)
            return this;
        AbstractAgentBuilder result = clone();
        result.hasEventTimeSequence = hasEventTimeSequence;
        return result;
    }

    @Override
    public QDAgent.Builder withVoidRecordListener(boolean hasVoidRecordListener) {
        if (this.hasVoidRecordListener == hasVoidRecordListener)
            return this;
        AbstractAgentBuilder result = clone();
        result.hasVoidRecordListener = hasVoidRecordListener;
        return result;
    }

    @Override
    public String toString() {
        return "AgentBuilder{" + super.toString() +
            ", attachmentStrategy=" + attachmentStrategy +
            ", useHistorySnapshot=" + useHistorySnapshot +
            ", hasEventTimeSequence=" + hasEventTimeSequence +
            ", hasVoidRecordListener=" + hasVoidRecordListener +
            '}';
    }
}
