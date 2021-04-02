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
package com.dxfeed.ipf.transform;

/**
 * Basic class for executable statements.
 */
abstract class Statement {
    enum ControlFlow { NORMAL, BREAK, RETURN, DELETE }

    private final int tokenLine;

    // Structure statements organize other statements.
    Statement() {
        tokenLine = -1;
    }

    // Payload statements perform useful things.
    Statement(Compiler compiler) {
        tokenLine = compiler.getTokenLine();
    }

    void incModificationCounter(TransformContext ctx) {
        ctx.incModificationCounter(tokenLine);
    }

    /* Executes statement on the current instrument profile (use currentProfile() method).
     * If profile is modified within the statement then copyProfile() must be called to work on copy.
     * Returns flag whether the control flow should continue normally or be interrupted.
     */
    abstract ControlFlow execute(TransformContext ctx);
}
