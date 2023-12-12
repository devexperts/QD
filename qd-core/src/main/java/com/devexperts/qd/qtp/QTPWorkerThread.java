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
package com.devexperts.qd.qtp;

import com.devexperts.logging.Logging;

/**
 * Daemon worker thread that distinguished between being gracefully {@link #close closed} by
 * its parent, or being forcefully interrupted/stopped by someone else.
 */
public abstract class QTPWorkerThread extends Thread implements AbstractMessageConnector.Joinable {

    protected final Logging log = Logging.getLogging(getClass());

    /**
     * This flag is true when this worker thread was {@link #close() closed}. We cannot fully rely
     * on {@link #interrupt() interruption} flag of the tread, because there is a chance that this
     * flag is going to be lost by some external code running from inside worker thread. This closed
     * flag serves as a guaranteed way to bail out the thread when it was closed. The following
     * pattern of code is consistently used to check the closed condition:
     * <pre><tt>
     *     if (isClosed())
     *         return; // bail out if closed
     * </tt></pre>
     */
    private volatile boolean closed;

    protected QTPWorkerThread(String name) {
        super(name);
        setDaemon(true);
    }

    /**
     * Marks thread as "closed" and interrupts it (if needed).
     */
    public final void close() {
        closed = true;
        // We must interrupt even if called from the current thread, which might happen inside Reader or Writer loop.
        // It makes sure that any subsequent call to LockSupport.park() immediately bails out.
        // See QD-531 for details.
        interrupt();
    }

    /**
     * Returns <code>true</code> if thread is closed and clears interrupt flag.
     * @throws InterruptedException if thread is interrupted without call to {@link #close}.
     * @return <code>true</code> if thread is closed and clears interrupt flag
     */
    public final boolean isClosed() throws InterruptedException {
        if (Thread.interrupted()) {
            if (closed)
                return true;
            throw new InterruptedException();
        }
        // Note: Some 3rd party code might have lost interrupting flag, so we double-check closed variable anymore
        return closed;
    }

    @Override
    public final void run() {
        Throwable reason = null;
        try {
            try {
                doWork();
            } catch (ThreadDeath tde) {
                // Should never happen, so we log it as an error.
                log.error("External Thread.stop() -- will shut down" , tde);
                handleShutdown();
                reason = tde;
            } catch (InterruptedException ie) {
                // InterruptedException may be normally generated when we close
                if (!closed) {
                    handleShutdown();
                    reason = ie;
                }
            } catch (Throwable t) {
                reason = t;
                if (t instanceof Error || t instanceof RuntimeException)
                    log.error("Unchecked exception in QTP", t);
            }
        } finally {
            handleClose(reason);
        }
    }

    protected abstract void doWork() throws Throwable;

    protected abstract void handleShutdown();

    protected abstract void handleClose(Throwable reason);
}
