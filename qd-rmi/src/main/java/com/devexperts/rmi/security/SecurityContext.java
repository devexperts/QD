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
package com.devexperts.rmi.security;

import com.devexperts.logging.Logging;

/**
 * Default implementation of {@link SecurityController} that stores subject in its own
 * inheritable thread-local variable.
 */
public class SecurityContext implements SecurityController {
    private static final Logging log = Logging.getLogging(SecurityContext.class);
    protected static final SecurityContext INSTANCE = new SecurityContext();

    /**
     * Returns instance of the SecurityController.
     * @return instance of the SecurityController.
     */
    public static SecurityContext getInstance() {
        return INSTANCE;
    }

    private final ThreadLocal<Object> subject = new InheritableThreadLocal<>();

    private SecurityContext() {}

    /**
     * Returns current subject of the calling thread.
     * @return current subject of the calling thread.
     */
    @Override
    public Object getSubject() {
        return subject.get();
    }

    /**
     * Sets current subject of the calling thread. This method should be used to
     * associate a default subject with the current thread when initial authentication
     * and login in performed (for example, to implement
     * {@link javax.naming.InitialContext}). Threads that are later spawned by the
     * current thread will inherit this subject as their current subject.
     *
     * <p><b>BAD PRACTICE</b>:
     * This method may be used to temporary change the subject and set it back
     * to the old one in {@code finally} block, but this usage is
     * <b>not recommended</b>. For this purpose use {@link #doAs} method.
     *
     * <p>This method logs warning message if not-null subject is being
     * replaced with the other not-null subject to catch potential bugs with
     * improper use of this method.
     *
     * @param subject the subject.
     */
    public void setSubject(Object subject) {
        if (this.subject.get() != null && subject != null) {
            log.warn("Subject " + this.subject.get() + " is replaced with " + subject + ". " +
                "Check for missing call to SecurityContext.setSubject and/or use SecurityContext.doAs instead");
        }
        this.subject.set(subject);
    }

    /**
     * Performs a given action on behalf of specified subject. This method temporarily
     * changes current subject of the calling thread, invokes {@code action.run()}, and
     * reverts current subject when {@code action.run()} returns. Any threads that are
     * spawned by the code in {@code action.run()} will inherit this subject as their
     * current subject and will continue to use this subject even after
     * {@code action.run()} returns.
     *
     * @param subject subject that wants to execute this action.
     * @param action an action to perform.
     */
    @Override
    public void doAs(Object subject, Runnable action) {
        Object old = this.subject.get();
        try {
            this.subject.set(subject);
            action.run();
        } finally {
            this.subject.set(old);
        }
    }
}
