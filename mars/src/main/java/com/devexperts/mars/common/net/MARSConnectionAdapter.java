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
package com.devexperts.mars.common.net;

import com.devexperts.connector.ConnectionAdapter;
import com.devexperts.connector.ConnectionAdapterListener;
import com.devexperts.mars.common.MARS;
import com.devexperts.mars.common.MARSAgent;
import com.devexperts.mars.common.MARSEvent;
import com.devexperts.mars.common.MARSListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Adapts single network connection to {@link MARS} instance.
 */
public class MARSConnectionAdapter implements ConnectionAdapter, MARSListener {

    private final MARS mars;
    private final boolean isMars;
    private final MARSAgent agent;
    private final boolean isAgent;
    private final int read_buffer_limit;

    private final Map<String, MARSEvent> read_events = new HashMap<>();

    private final MARSBuffer read_buffer;
    private final MARSBuffer write_buffer;
    private final InputStreamReader reader;
    private final OutputStreamWriter writer;

    private ConnectionAdapterListener listener;

    /**
     * Creates new MARS connection adapter with specified parameters.
     *
     * @param mars              instance for processing of received events or null if receive is disabled.
     * @param agent             agent for retrieving events for sending or null if send is disabled.
     * @param read_buffer_limit limit of read buffer.
     * @param in                InputStream of the TCP/IP socket.
     * @param out               OutputStream of the TCP/IP socket.
     */
    public MARSConnectionAdapter(MARS mars, MARSAgent agent, int read_buffer_limit, InputStream in, OutputStream out) {
        this.mars = mars;
        this.isMars = mars != null;
        this.agent = agent;
        this.isAgent = agent != null;
        this.read_buffer_limit = read_buffer_limit;

        read_buffer = new MARSBuffer();
        write_buffer = new MARSBuffer();
        reader = new InputStreamReader(in, StandardCharsets.UTF_8);
        writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);

        if (isAgent)
            agent.setListener(this);
    }

    @Override
    public void marsChanged(MARSAgent agent) {
        ConnectionAdapterListener listener = this.listener; // Atomic read.
        if (listener != null)
            listener.dataAvailable(this);
    }

    // ========== ConnectionAdapter Implementation ==========

    @Override
    public void setListener(ConnectionAdapterListener listener) {
        this.listener = listener;
    }

    @Override
    public void start() {}

    @Override
    public void close() {
        if (isAgent)
            agent.close();
        ConnectionAdapterListener listener = this.listener; // Atomic read.
        if (listener != null)
            listener.adapterClosed(this);
        Collection<MARSEvent> events;
        synchronized (read_events) {
            events = new ArrayList<>(read_events.values());
        }
        if (isMars)
            mars.removeAuthenticEvents(events);
    }

    @Override
    public int readData() throws Throwable {
        if (read_buffer.getSize() > read_buffer_limit)
            throw new IOException("Input buffer too large: " + read_buffer.getSize());
        int reserve = Math.max(read_buffer.getSize(), 4096);
        read_buffer.ensureCapacity(read_buffer.getSize() + reserve);
        int chars = reader.read(read_buffer.getBuffer(), read_buffer.getSize(), reserve);
        if (chars > 0) {
            read_buffer.setSize(read_buffer.getSize() + chars);
            Collection<MARSEvent> events = read_buffer.readEvents();
            if (isMars) {
                synchronized (read_events) {
                    for (MARSEvent event : events)
                        read_events.put(event.getName(), event);
                }
                mars.putEvents(events);
            }
        }
        return chars;
    }

    @Override
    public int writeData() throws Throwable {
        if (isAgent)
            write_buffer.writeEvents(agent.retrieveEvents());
        return flushWriteData();
    }

    @Override
    public int writeHeartbeat() throws Throwable {
        write_buffer.writeString("\r\n");
        return flushWriteData();
    }

    private int flushWriteData() throws Throwable {
        int chars = write_buffer.getSize();
        if (chars > 0) {
            writer.write(write_buffer.getBuffer(), 0, write_buffer.getSize());
            writer.flush();
            write_buffer.removeChars(write_buffer.getSize());
        }
        return chars;
    }
}
