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
package com.dxfeed.ipf.services;

import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.live.InstrumentProfileCollector;
import com.dxfeed.ipf.transform.InstrumentProfileTransform;
import com.dxfeed.ipf.transform.TransformCompilationException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * A service that publishes instrument profiles over network.
 * @deprecated Use {@link InstrumentProfileServer}.
 */
public class InstrumentProfileService {
    /**
     * Starts new instance of the service for specified provider and server socket port number.
     * @deprecated Use {@link InstrumentProfileServer}.
     */
    public static void startService(InstrumentProfileProvider provider, int port) {
        startService(provider, port, null);
    }

    /**
     * Starts new instance of the service for specified provider, server socket port number and transform file name.
     * The transform file is read once, but applied to instrument profiles each time when new socket is accepted.
     * @deprecated Use {@link InstrumentProfileServer}.
     */
    public static void startService(final InstrumentProfileProvider provider, int port, String transformFile) {
        if (provider == null)
            throw new NullPointerException("provider is null");
        if (port <= 0 || port >= 65536)
            throw new IllegalArgumentException("port number is invalid");
        InstrumentProfileTransform transform = null;
        if (transformFile != null && !transformFile.isEmpty())
            try {
                transform = InstrumentProfileTransform.compileURL(transformFile);
            } catch (TransformCompilationException | IOException e) {
                throw new IllegalArgumentException("Invalid transform", e);
            }
        // We need InstrumentProfileCollector with defensive copies
        final InstrumentProfileCollector collector = new InstrumentProfileCollector() {
            @Override
            protected InstrumentProfile copyInstrumentProfile(InstrumentProfile ip) {
                return new InstrumentProfile(ip);
            }
        };
        InstrumentProfileServer server = new InstrumentProfileServer(":" + port, collector) {
            Object oldGeneration;
            boolean updating; // pseudo-lock -- true when update profiles method is working

            @Override
            void onRequest() {
                if (tryUpdate())
                    try {
                        updateProfiles();
                    } finally {
                        doneUpdate();
                    }
            }

            private synchronized boolean tryUpdate() {
                if (!updating) {
                    // this thread will launch updateProfiles
                    updating = true;
                    return true;
                }
                // is updating now -- wait until it is over
                while (updating)
                    try {
                        wait();
                    } catch (InterruptedException ignored) {
                        return false; // just return when interrupted
                    }
                return false;
            }

            private synchronized void doneUpdate() {
                updating = false;
                notifyAll(); // wake up all threads waiting in tryUpdate
            }

            // This method is invoked in at most one thread at a time due to "updating" flag
            private void updateProfiles() {
                List<InstrumentProfile> profiles = provider.getInstrumentProfiles();
                Object newGeneration = new Object();
                collector.updateInstrumentProfiles(profiles, newGeneration);
                if (oldGeneration != null)
                    collector.removeGenerations(Collections.singleton(oldGeneration));
                oldGeneration = newGeneration;
            }

            @Override
            boolean supportsLive() {
                return false;
            }
        };
        if (transform != null)
            server.setTransforms(Collections.singletonList(transform));
        server.start();
    }

    private InstrumentProfileService() {} // do not create
}
