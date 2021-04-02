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

import com.devexperts.services.SupersedesService;

/**
 * The security controller is used to manage access to executing operations
 * in RMI framework.
 */
@SupersedesService(RMISecurityController.class)
public interface SecurityController {

    /**
     * Returns current subject.
     * <p> This method should be called on the client side RMI endpoint security controller
     * to obtain the subject for sending RMI request.
     * @return current subject.
     */
    public Object getSubject();

    /**
     * Performs a given action on behalf of specified subject.
     * <p> This method will be called on the server side RMI endpoint for each request
     * execution. It will be passed a subject that corresponds to received request.
     * @param subject subject that wants to execute this action.
     * @param action an action to perform.
     * @throws SecurityException if specified subject is not allowed to execute given action.
     */
    public void doAs(Object subject, Runnable action) throws SecurityException;
}
