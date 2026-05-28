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
package com.devexperts.qd.dxlink.websocket.scripted;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Single-connection scripted WebSocket server that drives the dxLink client over a fixed sequence
 * of "expect this inbound JSON" / "send this outbound JSON" steps. Used to exercise the dxLink
 * client's wire-level state machine without a real upstream feed.
 *
 * <p>The script is a strictly ordered list of {@link Step}s. The runner advances eagerly through
 * any leading {@link SendStep}s as soon as the WebSocket handshake completes, then blocks on the
 * next {@link ExpectStep} until a matching inbound frame arrives, then advances eagerly again.
 * Inbound frames whose {@code type} is in {@link Builder#ignoreTypes(String...)} are recorded but
 * do not advance the cursor.
 */
public final class ScriptedDxLinkServer implements AutoCloseable {
    private static final JsonFactory JSON = JsonFactory.builder().build();

    private final List<Step> script;
    private final Set<String> ignoreTypes;
    private final EventLoopGroup boss;
    private final EventLoopGroup workers;
    private final Channel serverChannel;
    private final int port;
    private final CountDownLatch scriptDone = new CountDownLatch(1);
    private final List<String> received = new CopyOnWriteArrayList<>();
    private volatile Throwable failure;
    private volatile int cursor;

    private ScriptedDxLinkServer(List<Step> script, Set<String> ignoreTypes) throws InterruptedException {
        this.script = script;
        this.ignoreTypes = ignoreTypes;
        this.boss = new NioEventLoopGroup(1);
        this.workers = new NioEventLoopGroup(1);
        ChannelFuture bound = new ServerBootstrap()
            .group(boss, workers)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(
                        new HttpServerCodec(),
                        new HttpObjectAggregator(65536),
                        new WebSocketServerCompressionHandler(),
                        new WebSocketServerProtocolHandler("/", null, true, 65536),
                        new ScriptedFrameHandler());
                }
            })
            .bind(new InetSocketAddress("127.0.0.1", 0))
            .sync();
        this.serverChannel = bound.channel();
        this.port = ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    public int port() {
        return port;
    }

    public String url() {
        return "ws://127.0.0.1:" + port + "/";
    }

    /** Every inbound text frame, in arrival order (including those whose {@code type} was ignored). */
    public List<String> received() {
        return Collections.unmodifiableList(new ArrayList<>(received));
    }

    /**
     * Blocks until the script has executed every step. Throws {@link AssertionError} on script
     * failure (mismatched inbound frame, matcher exception, channel exception) or if the timeout
     * elapses before the script finishes. The error message names the step that did not complete.
     */
    public void awaitScriptCompleted(long timeoutMs) throws InterruptedException {
        boolean done = scriptDone.await(timeoutMs, TimeUnit.MILLISECONDS);
        if (failure != null)
            throw new AssertionError("scripted server failure at step " + cursor + ": " + failure.getMessage(), failure);
        if (!done) {
            String pending = cursor < script.size() && script.get(cursor) instanceof ExpectStep
                ? ((ExpectStep) script.get(cursor)).description
                : "step " + cursor;
            throw new AssertionError("scripted server timed out after " + timeoutMs
                + "ms waiting for [" + pending + "] (step " + cursor + " of " + script.size() + ")");
        }
    }

    @Override
    public void close() {
        try {
            serverChannel.close().sync();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            boss.shutdownGracefully(0, 1, TimeUnit.SECONDS);
            workers.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    // ---- builder

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Step> steps = new ArrayList<>();
        private final Set<String> ignoreTypes = new HashSet<>();

        private Builder() {}

        public Builder ignoreTypes(String... types) {
            ignoreTypes.addAll(Arrays.asList(types));
            return this;
        }

        public Builder expectType(String type) {
            steps.add(new ExpectStep("type=" + type, json -> type.equals(extractType(json))));
            return this;
        }

        /**
         * Expects an inbound frame whose {@code type} matches and whose raw JSON contains every
         * substring in {@code mustContain}. The substrings are matched verbatim — wrap field names
         * and values exactly as they appear on the wire, e.g. {@code "\"channel\":1"}.
         */
        public Builder expectType(String type, String... mustContain) {
            StringBuilder desc = new StringBuilder("type=").append(type);
            for (String s : mustContain)
                desc.append(" containing ").append(s);
            steps.add(new ExpectStep(desc.toString(), json -> {
                if (!type.equals(extractType(json)))
                    return false;
                for (String s : mustContain) {
                    if (!json.contains(s))
                        return false;
                }
                return true;
            }));
            return this;
        }

        public Builder expect(String description, Predicate<String> matcher) {
            steps.add(new ExpectStep(description, matcher));
            return this;
        }

        public Builder send(String json) {
            steps.add(new SendStep(json));
            return this;
        }

        public ScriptedDxLinkServer start() throws InterruptedException {
            return new ScriptedDxLinkServer(new ArrayList<>(steps), new HashSet<>(ignoreTypes));
        }
    }

    // ---- steps

    private interface Step {}

    private static final class ExpectStep implements Step {
        final String description;
        final Predicate<String> matcher;

        ExpectStep(String description, Predicate<String> matcher) {
            this.description = description;
            this.matcher = matcher;
        }
    }

    private static final class SendStep implements Step {
        final String json;

        SendStep(String json) {
            this.json = json;
        }
    }

    // ---- runner

    private final class ScriptedFrameHandler extends SimpleChannelInboundHandler<Object> {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete)
                drainSends(ctx);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof TextWebSocketFrame))
                return;
            String json = ((TextWebSocketFrame) msg).text();
            received.add(json);
            if (failure != null || cursor >= script.size())
                return;
            String type = extractType(json);
            if (type != null && ignoreTypes.contains(type))
                return;
            Step step = script.get(cursor);
            if (!(step instanceof ExpectStep)) {
                fail("expected " + step + " but client sent " + json);
                return;
            }
            ExpectStep expect = (ExpectStep) step;
            try {
                if (!expect.matcher.test(json)) {
                    fail("inbound frame does not match [" + expect.description + "]: " + json);
                    return;
                }
            } catch (Throwable t) {
                fail("matcher [" + expect.description + "] threw on " + json + ": " + t);
                return;
            }
            cursor++;
            drainSends(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            fail("netty channel exception: " + cause);
            ctx.close();
        }
    }

    private void drainSends(ChannelHandlerContext ctx) {
        while (cursor < script.size() && script.get(cursor) instanceof SendStep) {
            SendStep send = (SendStep) script.get(cursor);
            ctx.channel().writeAndFlush(new TextWebSocketFrame(
                Unpooled.copiedBuffer(send.json, StandardCharsets.UTF_8)));
            cursor++;
        }
        if (cursor >= script.size())
            scriptDone.countDown();
    }

    private void fail(String message) {
        if (failure == null)
            failure = new AssertionError(message);
        scriptDone.countDown();
    }

    static String extractType(String json) {
        try (JsonParser p = JSON.createParser(json)) {
            if (p.nextToken() != JsonToken.START_OBJECT)
                return null;
            while (p.nextToken() == JsonToken.FIELD_NAME) {
                String name = p.currentName();
                p.nextToken();
                if ("type".equals(name))
                    return p.getValueAsString();
                p.skipChildren();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
