/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.dxlink.websocket.application;

import com.devexperts.qd.qtp.QTPConstants;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

class DxLinkJsonMessageFactory {
    public static final String FIELD_NAME_TYPE = "type";
    public static final String FIELD_NAME_CHANNEL = "channel";
    private static final int INITIAL_BUFFER_BYTES = 256;
    // Streaming messages (FEED_SUBSCRIPTION / FEED_SETUP-with-fields) accumulate up to
    // COMPOSER_THRESHOLD bytes before end(); pre-size to avoid power-of-two grow/memcpy events
    // on the writer thread under per-append gen.flush().
    private static final int STREAMING_BUFFER_BYTES = QTPConstants.COMPOSER_THRESHOLD;

    private final ByteBufAllocator allocator;
    private final JsonFactory jsonFactory;

    public DxLinkJsonMessageFactory() {
        this(PooledByteBufAllocator.DEFAULT);
    }

    DxLinkJsonMessageFactory(ByteBufAllocator allocator) {
        this.allocator = allocator;
        this.jsonFactory = JsonFactory.builder().build();
    }

    ByteBuf createSetup(int channel, String version, Long keepaliveTimeout, Long acceptKeepaliveTimeout,
        Map<String, String> agent) throws IOException
    {
        ByteBuf buf = allocator.buffer(INITIAL_BUFFER_BYTES);
        try (ByteBufOutputStream out = new ByteBufOutputStream(buf);
             JsonGenerator gen = jsonFactory.createGenerator((OutputStream) out))
        {
            gen.writeStartObject();
            gen.writeStringField(FIELD_NAME_TYPE, "SETUP");
            gen.writeNumberField(FIELD_NAME_CHANNEL, channel);
            gen.writeStringField("version", version);
            if (keepaliveTimeout != null)
                gen.writeNumberField("keepaliveTimeout", keepaliveTimeout / 1000.0);
            if (acceptKeepaliveTimeout != null)
                gen.writeNumberField("acceptKeepaliveTimeout", acceptKeepaliveTimeout / 1000.0);
            if (agent != null && !agent.isEmpty()) {
                gen.writeObjectFieldStart("agent");
                for (Entry<String, String> entry : agent.entrySet()) {
                    gen.writeStringField(entry.getKey(), entry.getValue());
                }
                gen.writeEndObject();
            }
            gen.writeEndObject();
        } catch (Throwable t) {
            buf.release();
            throw t;
        }
        return buf;
    }

    ByteBuf createAuth(int channel, String token) throws IOException {
        ByteBuf buf = allocator.buffer(INITIAL_BUFFER_BYTES);
        try (ByteBufOutputStream out = new ByteBufOutputStream(buf);
             JsonGenerator gen = jsonFactory.createGenerator((OutputStream) out))
        {
            gen.writeStartObject();
            gen.writeStringField(FIELD_NAME_TYPE, "AUTH");
            gen.writeNumberField(FIELD_NAME_CHANNEL, channel);
            gen.writeStringField("token", token);
            gen.writeEndObject();
        } catch (Throwable t) {
            buf.release();
            throw t;
        }
        return buf;
    }

    ByteBuf createKeepalive(int channel) throws IOException {
        ByteBuf buf = allocator.buffer(INITIAL_BUFFER_BYTES);
        try (ByteBufOutputStream out = new ByteBufOutputStream(buf);
             JsonGenerator gen = jsonFactory.createGenerator((OutputStream) out))
        {
            gen.writeStartObject();
            gen.writeStringField(FIELD_NAME_TYPE, "KEEPALIVE");
            gen.writeNumberField(FIELD_NAME_CHANNEL, channel);
            gen.writeEndObject();
        } catch (Throwable t) {
            buf.release();
            throw t;
        }
        return buf;
    }

    ByteBuf createChannelRequest(int channel, String service, String contract) throws IOException {
        ByteBuf buf = allocator.buffer(INITIAL_BUFFER_BYTES);
        try (ByteBufOutputStream out = new ByteBufOutputStream(buf);
             JsonGenerator gen = jsonFactory.createGenerator((OutputStream) out))
        {
            gen.writeStartObject();
            gen.writeStringField(FIELD_NAME_TYPE, "CHANNEL_REQUEST");
            gen.writeNumberField(FIELD_NAME_CHANNEL, channel);
            gen.writeStringField("service", service);
            gen.writeObjectFieldStart("parameters");
            gen.writeStringField("contract", contract);
            gen.writeEndObject();
            gen.writeEndObject();
        } catch (Throwable t) {
            buf.release();
            throw t;
        }
        return buf;
    }

    /**
     * One-shot FEED_SETUP carrying only the aggregation period (or nothing if it's null).
     * JSON field name retained for DxLink wire compatibility; rename to {@code requestedAggregationPeriod}
     * is pending DxLink protocol coordination.
     */
    ByteBuf createFeedSetup(int channel, Long acceptAggregationPeriod) throws IOException {
        ByteBuf buf = allocator.buffer(INITIAL_BUFFER_BYTES);
        try (ByteBufOutputStream out = new ByteBufOutputStream(buf);
             JsonGenerator gen = jsonFactory.createGenerator((OutputStream) out))
        {
            gen.writeStartObject();
            gen.writeStringField(FIELD_NAME_TYPE, "FEED_SETUP");
            gen.writeNumberField(FIELD_NAME_CHANNEL, channel);
            if (acceptAggregationPeriod != null) {
                gen.writeNumberField("acceptAggregationPeriod", acceptAggregationPeriod / 1000.0);
                //todo  gen.writeNumberField("requestedAggregationPeriod", acceptAggregationPeriod / 1000.0);
            } else {
                gen.writeNumberField("acceptAggregationPeriod", Double.NaN);
                //todo  gen.writeNumberField("requestedAggregationPeriod", Double.NaN);
            }
            gen.writeEndObject();
        } catch (Throwable t) {
            buf.release();
            throw t;
        }
        return buf;
    }

    /**
     * Opens a FEED_SETUP message that carries {@code acceptEventFields} entries. The prologue
     * — {@code type}, {@code channel}, optional {@code acceptAggregationPeriod}/{@code acceptDataFormat}
     * — is written and flushed here; {@code acceptEventFields} is opened lazily by the first
     * {@link FeedSetupJsonMessage#appendAcceptEventFields} call. Caller closes via {@link JsonMessage#close()}.
     */
    FeedSetupJsonMessage openFeedSetupWithFields(int channel, Long acceptAggregationPeriod,
        String acceptDataFormat) throws IOException
    {
        ByteBuf buf = allocator.buffer(STREAMING_BUFFER_BYTES);
        ByteBufOutputStream out = null;
        JsonGenerator gen = null;
        try {
            out = new ByteBufOutputStream(buf);
            gen = jsonFactory.createGenerator((OutputStream) out);
            gen.writeStartObject();
            gen.writeStringField(FIELD_NAME_TYPE, "FEED_SETUP");
            gen.writeNumberField(FIELD_NAME_CHANNEL, channel);
            if (acceptAggregationPeriod != null) {
                gen.writeNumberField("acceptAggregationPeriod", acceptAggregationPeriod / 1000.0);
                //todo  gen.writeNumberField("requestedAggregationPeriod", acceptAggregationPeriod / 1000.0);
            } else {
                gen.writeNumberField("acceptAggregationPeriod", Double.NaN);
                //todo  gen.writeNumberField("requestedAggregationPeriod", Double.NaN);
            }
            if (acceptDataFormat != null)
                gen.writeStringField("acceptDataFormat", acceptDataFormat);
            gen.flush();
            return new FeedSetupJsonMessage(buf, gen);
        } catch (Throwable t) {
            closeQuietly(gen, out);
            buf.release();
            throw t;
        }
    }

    /**
     * Opens a FEED_SUBSCRIPTION message and writes its prologue including the (always-false)
     * {@code reset} flag and the opening bracket of either the {@code add} or {@code remove} array,
     * selected by {@code isRemove}. Subsequent {@link FeedSubscriptionJsonMessage#appendSubscription}
     * calls write items into that array. Caller closes via {@link JsonMessage#close()}.
     */
    FeedSubscriptionJsonMessage openFeedSubscription(int channel, boolean isRemove) throws IOException {
        ByteBuf buf = allocator.buffer(STREAMING_BUFFER_BYTES);
        ByteBufOutputStream out = null;
        JsonGenerator gen = null;
        try {
            out = new ByteBufOutputStream(buf);
            gen = jsonFactory.createGenerator((OutputStream) out);
            gen.writeStartObject();
            gen.writeStringField(FIELD_NAME_TYPE, "FEED_SUBSCRIPTION");
            gen.writeNumberField(FIELD_NAME_CHANNEL, channel);
            gen.writeBooleanField("reset", false);
            gen.writeArrayFieldStart(isRemove ? "remove" : "add");
            gen.flush();
            return new FeedSubscriptionJsonMessage(buf, gen);
        } catch (Throwable t) {
            closeQuietly(gen, out);
            buf.release();
            throw t;
        }
    }

    private static void closeQuietly(JsonGenerator gen, ByteBufOutputStream out) {
        if (gen != null) {
            try {
                gen.close();
            } catch (IOException ignore) {
                // fall through to buf.release()
            }
        } else if (out != null) {
            try {
                out.close();
            } catch (IOException ignore) {
                // fall through to buf.release()
            }
        }
    }

    /**
     * Streaming JSON message builder backed by a single pooled {@link ByteBuf}. After construction,
     * the {@link JsonGenerator} has the message's prologue flushed into the buffer; {@link #readableBytes()}
     * reflects the current on-wire byte count. Caller appends content via subclass-specific methods,
     * then transfers ownership of the buffer to itself via {@link #close()} — or releases everything
     * via {@link #abort()} on error.
     *
     * <p>Lifecycle terminals: after either {@link #close()} or {@link #abort()} the message is
     * consumed and may not be reused. A further {@link #close()} on a consumed message throws
     * {@link IllegalStateException}; a further {@link #abort()} is a no-op.
     */
    abstract static class JsonMessage {
        final ByteBuf buf;
        final JsonGenerator gen;
        private boolean closed;

        JsonMessage(ByteBuf buf, JsonGenerator gen) {
            this.buf = buf;
            this.gen = gen;
        }

        /** Subclasses write any closing tokens (array/object ends) here. */
        protected abstract void writeTail() throws IOException;

        /**
         * Writes the trailing tokens, closes the generator (flushing pending bytes into the buffer
         * and closing the underlying stream), and returns the buffer with ownership transferred
         * to the caller. After return the message is unusable.
         */
        final ByteBuf close() throws IOException {
            if (closed)
                throw new IllegalStateException("already closed");
            try {
                writeTail();
                gen.close();
                closed = true;
                return buf;
            } catch (Throwable t) {
                abort();
                throw t;
            }
        }

        /**
         * Releases the buffer and closes the generator; swallows any I/O failure so the caller's
         * original error is never masked. Idempotent — safe to call after a successful
         * {@link #close()} (which marks the message closed and transfers buffer ownership away).
         */
        final void abort() {
            if (closed)
                return;
            closed = true;
            try {
                gen.close();
            } catch (IOException ignored) {
                // Original cause must surface; never re-throw from abort().
            } finally {
                buf.release();
            }
        }

        final int readableBytes() {
            return closed ? 0 : buf.readableBytes();
        }
    }

    /**
     * FEED_SETUP message with {@code acceptEventFields}. The outer object is opened by
     * {@link DxLinkJsonMessageFactory#openFeedSetupWithFields}; {@code acceptEventFields} is opened
     * lazily on the first {@link #appendAcceptEventFields} call. {@link #close()} closes the
     * {@code acceptEventFields} object (if opened) and the outer object.
     */
    static final class FeedSetupJsonMessage extends JsonMessage {
        private boolean acceptEventFieldsOpen;

        FeedSetupJsonMessage(ByteBuf buf, JsonGenerator gen) {
            super(buf, gen);
        }

        // Writes one acceptEventFields entry into the lazily-opened {@code acceptEventFields:{}} object.
        void appendAcceptEventFields(String eventType, Collection<String> fields) throws IOException {
            if (!acceptEventFieldsOpen) {
                gen.writeFieldName("acceptEventFields");
                gen.writeStartObject();
                acceptEventFieldsOpen = true;
            }
            gen.writeArrayFieldStart(eventType);
            for (String f : fields) {
                gen.writeString(f);
            }
            gen.writeEndArray();
            // Flush per append so payloadSize() / hasCapacity() see the running on-wire size.
            gen.flush();
        }

        @Override
        protected void writeTail() throws IOException {
            if (acceptEventFieldsOpen)
                gen.writeEndObject();
            gen.writeEndObject();
        }
    }

    /**
     * FEED_SUBSCRIPTION message. The prologue (written by
     * {@link DxLinkJsonMessageFactory#openFeedSubscription}) already opened either the
     * {@code add} or {@code remove} array; {@link #appendSubscription} writes items into it,
     * and {@link #close()} writes the closing {@code ]} and {@code }}.
     */
    static final class FeedSubscriptionJsonMessage extends JsonMessage {
        FeedSubscriptionJsonMessage(ByteBuf buf, JsonGenerator gen) {
            super(buf, gen);
        }

        // Writes one subscription entry into the open {@code add} or {@code remove} array.
        void appendSubscription(DxLinkSubscription s) throws IOException {
            gen.writeStartObject();
            gen.writeStringField(FIELD_NAME_TYPE, s.type);
            gen.writeStringField("symbol", s.symbol);
            if (s.source != null) {
                gen.writeStringField("source", s.source);
            } else if (s.fromTime != null) {
                gen.writeNumberField("fromTime", s.fromTime);
            }
            gen.writeEndObject();
            // Flush per append so payloadSize() / hasCapacity() see the running on-wire size.
            gen.flush();
        }

        @Override
        protected void writeTail() throws IOException {
            gen.writeEndArray();
            gen.writeEndObject();
        }
    }
}
