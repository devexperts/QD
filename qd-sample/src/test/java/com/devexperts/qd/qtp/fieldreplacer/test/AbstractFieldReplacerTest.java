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
package com.devexperts.qd.qtp.fieldreplacer.test;

import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.EventType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

abstract class AbstractFieldReplacerTest {
    //FIXME Switch to temporary folder when [QD-1436] is fixed
    private static final Path DIRECTORY_WRITE_TO = Paths.get("./target");

    private String replacer;

    protected static Iterable<Object[]> parameters(Object[] fields) {
        return Arrays.stream(fields)
            .map(element -> new Object[] { element})
            .collect(Collectors.toList());
    }

    protected void setReplacer(String replacer) {
        this.replacer = replacer;
    }

    protected String getReplacer() {
        return replacer;
    }

    @SuppressWarnings("unchecked")
    protected <T extends EventType<?>> void testEvent(T initialEvent, Predicate<T>... predicates) {
        try (TempFile tmp = new TempFile()) {
            // 1. Create endpoint with PUBLISHER role and connect to tape file
            DXEndpoint endpoint = DXEndpoint.newBuilder()
                .withRole(DXEndpoint.Role.PUBLISHER)
                .withProperty(DXEndpoint.DXFEED_WILDCARD_ENABLE_PROPERTY, "true")
                .withProperty("dxscheme.enabled.ActionTime", "*")
                .build();
            endpoint.connect("tape:" + tmp.getFile() + "[format=text]");

            // 2. Publish event and close endpoint
            endpoint.getPublisher().publishEvents(Collections.singletonList(initialEvent));
            endpoint.awaitProcessed();
            endpoint.closeAndAwaitTermination();

            // 3. Read published events
            endpoint = DXEndpoint.newBuilder()
                .withRole(DXEndpoint.Role.STREAM_FEED)
                .withProperty("dxscheme.enabled.ActionTime", "*")
                .build();
            DXFeedSubscription<T> sub = endpoint.getFeed().createSubscription((Class<T>) initialEvent.getClass());
            List<T> events = new ArrayList<>();
            sub.addEventListener(events::addAll);
            sub.addSymbols(initialEvent.getEventSymbol());
            String fieldReplacerConfig = "[fieldReplacer=" + getReplacer() + "]";
            endpoint.connect("(file:" + tmp.getFile() + fieldReplacerConfig + ")");
            endpoint.awaitNotConnected();
            endpoint.closeAndAwaitTermination();

            // 4. Check that events are changed as required
            for (T readEvent : events) {
                for (Predicate<T> predicate : predicates) {
                    assertTrue(initialEvent + " -> " + readEvent, predicate.test(readEvent));
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            fail("I/O error: " + e.getMessage());
        } catch (Exception e) {
            fail("Error: " + e.getMessage());
        }
    }

    protected static class TempFile implements AutoCloseable {
        private final File file;

        TempFile() throws IOException {
            file = java.nio.file.Files.createTempFile(DIRECTORY_WRITE_TO, "fieldReplacer", ".qds.tmp").toFile();
            file.deleteOnExit();
        }

        String getFile() {
            return file.getPath();
        }

        @Override
        public void close() throws Exception {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
