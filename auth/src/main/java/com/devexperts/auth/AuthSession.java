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
package com.devexperts.auth;

import com.devexperts.logging.Logging;
import com.devexperts.util.TypedMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The unique session, which determines the permissions of the user.
 * The session may be revoked.
 *
 * <p><b>Note</b> that AuhSession is closed when the underlying connection is closed,
 * therefore unique session must be created for each connection.
 *
 * <p><b>This class is thread-safe.</b>
 **/
public class AuthSession {

    private static final Logging log = Logging.getLogging(AuthSession.class);

    private Object subject;
    private final List<SessionCloseListener> listeners = new ArrayList<>();
    private volatile String closeReason;
    private final TypedMap sessionVariables = new TypedMap();

    /**
     * Creates session.
     * @param subject user subject for RMI.
     */
    public AuthSession(Object subject) {
        this.subject = subject;
    }

    public boolean isClosed() {
        return closeReason != null;
    }

    /**
     * Adds the close listener. Listener is notified immediately if session is already closed.
     * @param listener the close listener.
     */
    public void addCloseListener(SessionCloseListener listener) {
        Objects.requireNonNull(listener);
        String closeReason;
        synchronized (this) {
            closeReason = this.closeReason;
            if (closeReason == null)
                listeners.add(listener);
        }
        if (closeReason != null)
            listener.close(this, closeReason);
    }

    /**
     * Removes the close listener.
     * @param listener the close listener.
     */
    public synchronized void removeCloseListener(SessionCloseListener listener) {
        listeners.remove(listener);
    }

    /**
     * Returns user subject for RMI.
     * @return user subject for RMI.
     */
    public Object getSubject() {
        return subject;
    }

    /**
     * Sets specific user subject for RMI.
     * @param subject specific user subject.
     */
    public void setSubject(Object subject) {
        this.subject = subject;
    }

    /**
     * Close this session for this closeReason.
     * @param closeReason the closing reason.
     */
    public final void close(String closeReason) {
        Objects.requireNonNull(closeReason);
        SessionCloseListener[] listeners;
        synchronized (this) {
            if (this.closeReason != null)
                return; // nothing to do -- already closed
            this.closeReason = closeReason;
            listeners = this.listeners.toArray(new SessionCloseListener[this.listeners.size()]);
        }
        try {
            closeImpl(closeReason);
        } catch (Throwable t) {
            log.error("Unexpected error in closeImpl()", t);
        }
        for (SessionCloseListener listener : listeners)
            try {
                listener.close(this, closeReason);
            } catch (Throwable t) {
                log.error("Failed to notify session listener", t);
            }
    }

    protected void closeImpl(String closeReason) {}

    /**
     * Returns special session variables.
     * @return session variables.
     */
    public TypedMap variables() {
        return sessionVariables;
    }
}
