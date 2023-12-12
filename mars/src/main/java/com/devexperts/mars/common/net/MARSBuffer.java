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
package com.devexperts.mars.common.net;

import com.devexperts.logging.Logging;
import com.devexperts.mars.common.MARSEvent;
import com.devexperts.mars.common.MARSEventFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * Buffer to read and write {@link MARSEvent} events.
 *
 * <p>The buffer is built atop character array which is used both for reading and writing. Partial reads and writes are
 * supported with state stored in the buffer between operations. All operations are not thread-safe, it is assumed that
 * each thread and/or data stream will use it's own instance of the buffer or provide thread-safety by other means.
 */
public class MARSBuffer {

    private static final Logging log = Logging.getLogging(MARSBuffer.class);

    private char[] buffer;
    private int size;
    private final MARSEventFactory marsEventFactory = MARSEventFactory.getInstance();
    /**
     * Returns character array currently used by this buffer or null if none is allocated yet. <b>NOTE:</b> the array
     * instance may change over time because of reallocation.
     */
    public char[] getBuffer() {
        return buffer;
    }

    /**
     * Returns number of payload characters in the buffer.
     */
    public int getSize() {
        return size;
    }

    /**
     * Sets new number of payload characters in the buffer.
     *
     * @throws IllegalArgumentException if specified size is outside of current buffer length.
     */
    public void setSize(int size) {
        if (size < 0 || size > (buffer == null ? 0 : buffer.length))
            throw new IllegalArgumentException("Size is out of limits.");
        this.size = size;
    }

    /**
     * Reallocates buffer to ensure that it has required total capacity.
     */
    public void ensureCapacity(int required_capacity) {
        int capacity = buffer == null ? 0 : buffer.length;
        if (required_capacity <= capacity)
            return;
        int new_capacity = Math.max(Math.max(1024, capacity << 1), required_capacity);
        char[] new_buffer = new char[new_capacity];
        if (buffer != null && capacity > 0)
            System.arraycopy(buffer, 0, new_buffer, 0, capacity);
        buffer = new_buffer;
    }

    /**
     * Clears buffer by setting it's size to 0.
     */
    public void clear() {
        size = 0;
    }

    /**
     * Removes first <code>n</code> characters from the buffer.
     *
     * @throws IndexOutOfBoundsException if specified number is outside current size.
     */
    public void removeChars(int n) {
        if (n < 0 || n > size)
            throw new IndexOutOfBoundsException();
        if (n == 0)
            return;
        System.arraycopy(buffer, n, buffer, 0, size - n);
        size -= n;
    }

    /**
     * Writes specified string to the end of the buffer.
     */
    public void writeString(String s) {
        int length = s.length();
        ensureCapacity(size + length);
        s.getChars(0, length, buffer, size);
        size += length;
    }

    /**
     * Writes specified characters to the end of the buffer.
     */
    public void writeChars(char[] chars, int offset, int length) {
        if (offset < 0 || offset > chars.length || length < 0 || length > chars.length - offset)
            throw new IndexOutOfBoundsException();
        ensureCapacity(size + length);
        System.arraycopy(chars, offset, buffer, size, length);
        size += length;
    }

    /**
     * Reads and removes all complete {@link MARSEvent} events from the buffer.
     */
    public Collection<MARSEvent> readEvents() {
        Collection<MARSEvent> events = null;
        int parsed = 0;
        for (int crlf = indexOfCRLF(parsed); crlf >= parsed; parsed = crlf + 1, crlf = indexOfCRLF(parsed)) {
            try {
                if (crlf - parsed <= 1) {
                    continue;
                }
                int assignment = indexOfSign(parsed, '=');
                if (assignment < parsed || assignment >= crlf) {
                    continue;
                }

                int timestampSeparator = indexOfSign(parsed, '|');
                String name = trimString(parsed, assignment);
                long timestamp = -1;
                String value = "";

                if (timestampSeparator >= 0 && timestampSeparator < crlf) {
                    int count = (timestampSeparator - 1) - assignment;
                    if (count >= 0) {
                        try {
                            String timestampString = new String(buffer, assignment + 1, count);
                            timestamp = Long.parseLong(timestampString);
                            value = trimString(timestampSeparator + 1, crlf);
                        } catch (IndexOutOfBoundsException e) {
                            timestamp = -1;
                        } catch (NumberFormatException e) {
                            timestamp = -1;
                        }
                    }
                }

                if (name.isEmpty())
                    continue;
                if (events == null)
                    events = new ArrayList<MARSEvent>();
                if (timestamp < 0) {
                    value = trimString(assignment + 1, crlf);
                    events.add(marsEventFactory.createMARSEvent(name, value));
                } else {
                    events.add(marsEventFactory.createMARSEvent(name, value, timestamp));
                }
            } catch (Exception e) {
                log.error("readEvents() crlf=" + crlf + " parsed=" + parsed, e);
            }
        }
        removeChars(parsed);
        return events == null ? Collections.EMPTY_LIST : events;
    }

    /**
     * Writes specified {@link MARSEvent} events to the end of the buffer.
     */
    public void writeEvents(Collection<MARSEvent> events) {
        if (events.isEmpty())
            return;
        for (MARSEvent event : events) {
            writeString(event.getName());
            writeString("=");
            if (event.getTimestamp() != 0) {
                writeString(event.getTimestamp() + "|");
            }
            writeString(event.getValue());
            writeString("\r\n");
        }
    }

    // ========== Utility Methods ==========

    private int indexOfCRLF(int from_index) {
        while (from_index < size) {
            if (buffer[from_index] == '\n' || buffer[from_index] == '\r') {
                return from_index;
            }
            from_index++;
        }
        return -1;
    }

    private int indexOfSign(int from_index, char sign) {
        while (from_index < size) {
            if (buffer[from_index] == sign) {
                return from_index;
            }
            from_index++;
        }
        return -1;
    }

    private String trimString(int from_index, int to_index) {
        while (from_index < to_index && buffer[from_index] <= ' ') {
            from_index++;
        }
        while (from_index < to_index && buffer[to_index - 1] <= ' ') {
            to_index--;
        }
        return from_index == to_index ? "" : new String(buffer, from_index, to_index - from_index);
    }
}
