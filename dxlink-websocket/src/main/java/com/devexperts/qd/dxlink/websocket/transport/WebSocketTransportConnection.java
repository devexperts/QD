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
package com.devexperts.qd.dxlink.websocket.transport;

import com.devexperts.connector.proto.AbstractTransportConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.logging.Logging;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.dxlink.websocket.application.DxLinkWebSocketApplicationConnection;
import com.devexperts.qd.qtp.AbstractMessageConnector;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectorState;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.MessageProvider;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.transport.stats.ConnectionStats;
import com.devexperts.util.JMXNameBuilder;
import com.devexperts.util.LogUtil;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;
import com.dxfeed.api.DXEndpoint;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketScheme;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;

/**
 * WebSocket Transport-layer of a single two-way text-oriented connection.
 * </p>
 * Normally, when starting a MessageConnector, a new TransportConnection is created that reads bytes from the socket to the
 * chunks and writes bytes from the chunks to the socket. For this purpose TransportConnection has its own thread model. So
 * for a normal socket, one thread is created for reading, one thread for writing. For NIO there will be more threads. In
 * our case web socket always has serial messages and only one connection to the server and we can use the model of one
 * thread per read, one thread per write, but we use the ready java WebSocketClient implementation.
 * The TransportConnection should be able to close,
 * for example in case the {@link DXEndpoint#disconnect} method (eventually {@link MessageConnector#stop}) is called, or for example if
 * we don't receive a keepalive message from the server, all transports will be closed in the
 * {@link TransportConnection#connectionClosed} method. The mechanism of stopping transport means that connections should be closed
 * and transport threads should be stopped. It is also worth noting that {@link DXEndpoint#closeAndAwaitTermination} support
 * blocking transport stop and {@link DXEndpoint#reconnect} (actually recreating the transport).
 * </p>
 * And so, {@link WebSocketWriter} - is a separate thread that, as in the standard implementation
 * of com.devexperts.qd.qtp.socket. SocketWriter sleeps until the next time a message is sent (e.g. keepAlive),
 * and can also be woken up, e.g. if the reader has received, parsed and processed a message from the server
 * (created a response for the server), it will notify {@link WebSocketWriter}
 * (see how to use {@link DxLinkWebSocketApplicationConnection#messagesAvailable(MessageProvider)}) to wake up
 * and send a message.
 */
class WebSocketTransportConnection extends AbstractTransportConnection implements AbstractMessageConnector.Joinable {
    private static final String VERBOSE = SystemProperties.getProperty("com.devexperts.qd.qtp.socket.verbose", null);
    private static final int MAX_TEXT_MESSAGE_BUFFER_SIZE =
        SystemProperties.getIntProperty("com.devexperts.qd.dxlink.websocket.maxTextMessageBufferSize", 65536);
    public static final int MAX_FRAME_PAYLOAD_LENGTH =
        SystemProperties.getIntProperty("com.devexperts.qd.dxlink.websocket.maxFramePayloadLength", 65536);

    private static final long CONNECT_TIMEOUT = TimePeriod.valueOf(
        SystemProperties.getProperty("com.devexperts.qd.dxlink.websocket.connectTimeout", "5m")).getTime();
    public static final long HANDSHAKE_TIMEOUT = TimePeriod.valueOf(
        SystemProperties.getProperty("com.devexperts.qd.dxlink.websocket.handshakeTimeout", "10s")).getTime();

    /**
     * The <code>CloseListener</code> interface allows tracking of handler death.
     */
    public interface CloseListener {
        void handlerClosed(AbstractTransportConnection transportConnection);
    }

    private final Logging log;

    private final DxLinkClientWebSocketConnector connector;

    final boolean verbose;

    private final String address;
    private WebSocketWriter writer;
    private EventLoopGroup socketThread;

    private volatile Session session; // only defined when state == CONNECTED
    private volatile CloseListener closeListener;

    private volatile SocketState state = SocketState.NEW;

    WebSocketTransportConnection(DxLinkClientWebSocketConnector connector, String address) {
        this.connector = connector;
        this.log = connector.getLogging();
        this.address = address;
        this.verbose = VERBOSE != null && connector.getName().contains(VERBOSE);
    }

    public MessageConnectorState getHandlerState() { return state.state; }

    public boolean isConnected() { return state == SocketState.CONNECTED; }

    ConnectionStats getActiveConnectionStats() {
        Session threadData = this.session; // Atomic read.
        return threadData == null ? null : threadData.connectionStats;
    }

    public void setCloseListener(CloseListener listener) { this.closeListener = listener; }

    public synchronized void start() {
        if (state != SocketState.NEW)
            return; // handler was already concurrently closed -- will not start
        state = SocketState.STARTED;
        writer = new WebSocketWriter(this);
        writer.setPriority(connector.getThreadPriority());
        writer.start();
        socketThread = new OioEventLoopGroup();
        notifyAll();
    }

    public void close() { exitSocket(null); }

    @Override
    public void join() throws InterruptedException {
        writer.join();
        if (!socketThread.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS)) {
            throw new InterruptedException();
        }
    }

    public void exitSocket(Throwable reason) {
        if (!makeClosedAndStopThread())
            return; // was already closed
        Session session = this.session;
        if (session != null) {
            // Closed socket that was already connected
            this.session = null;
            session.close(reason);
        }
        CloseListener listener = closeListener; // Atomic read.
        if (listener != null)
            listener.handlerClosed(this);
        connector.notifyMessageConnectorListeners();
    }

    public void stopConnector() { connector.stop(); }

    @Override
    public String toString() { return address; }

    // ========== Internal API for SocketReader & SocketWriter ==========
    // These methods shall be called only by dedicated reader/writer sockets (threads).

    class Session {
        Channel channel;
        DxLinkWebSocketApplicationConnection application;
        QDStats stats;
        ConnectionStats connectionStats = new ConnectionStats();

        public void writeAndFlush(ByteBuf message) {
            if (WebSocketTransportConnection.this.verbose && log.debugEnabled())
                log.debug("SNT: " + message.toString(StandardCharsets.UTF_8));
            channel.writeAndFlush(new TextWebSocketFrame(message));
            connectionStats.addWrittenBytes(message.readableBytes());
        }

        public void close(Throwable reason) {
            try {
                if (application != null)
                    application.close();
            } catch (Throwable t) {
                log.error("Failed to close connection", t);
            }
            try {
                if (stats != null)
                    stats.close();
            } catch (Throwable t) {
                log.error("Failed to close stats", t);
            }
            connector.addClosedConnectionStats(connectionStats);
            if (channel != null) {
                if (reason != null) {
                    try {
                        String errorJson = String.format(
                            "{\"type\":\"ERROR\",\"channel\":0,\"error\":\"UNKNOWN\",\"message\":\"%s\"}",
                            reason.getMessage());
                        writeAndFlush(Unpooled.copiedBuffer(errorJson.getBytes(StandardCharsets.UTF_8)));
                    } catch (Throwable t) {
                        log.error("Error occurred while sending an error to " + LogUtil.hideCredentials(address), t);
                    }
                }
                try {
                    channel.close();
                    if (reason == null || reason instanceof IOException) {
                        log.info("Disconnected from " + LogUtil.hideCredentials(address) +
                            (reason == null ? "" :
                                " because of " +
                                    (reason.getMessage() == null ? reason.toString() : reason.getMessage())));
                    } else {
                        log.error("Disconnected from " + LogUtil.hideCredentials(address), reason);
                    }
                } catch (Throwable t) {
                    log.error("Error occurred while disconnecting from " + LogUtil.hideCredentials(address), t);
                }
            }
        }
    }

    Session createSession() throws InterruptedException {
        if (!makeConnecting())
            return waitConnected(); // somebody is already connecting -- wait for it
        connector.notifyMessageConnectorListeners();

        // Connect in this thread
        if (this.address == null)
            return null;

        // wait if needed to prevent abuse
        connector.getReconnectHelper().sleepBeforeConnection();
        log.info("Connecting to " + LogUtil.hideCredentials(this.address));

        final Address adr;
        try {
            adr = new Address(this.address);
            variables().set(REMOTE_HOST_ADDRESS_KEY, address);
        } catch (URISyntaxException | SSLException e) {
            throw new RuntimeException("Failed to connect to " + LogUtil.hideCredentials(address), e);
        }

        // Receiving messages from a socket is usually made through chunks. One thread reads all bytes into chunks,
        // which are size-limited byte arrays. This allows to simplify the work of the garbage collector,
        // because it does not have to allocate larger byte arrays, and also does not have to release memory,
        // because the chunks are reusable when the owner of the chunk returns it to the pool.
        // We decided to do without chunks, because in our case libraries that implement a web socket,
        // already read bytes from the socket and save them in some buffer. In addition, the API of such libraries gives
        // for text protocol is already a complete string and it does not makes sense to pack it into chunks
        // and then in the parser from chunks to collect again into a string for parsing json.
        final Session session = new Session();
        try {
            final ChannelPromise[] handshakeFuture = new ChannelPromise[1];
            final ChannelInitializer<SocketChannel> channelInitializer = new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline pipeline = ch.pipeline();
                    if (adr.sslCtx != null)
                        pipeline.addLast(adr.sslCtx.newHandler(ch.alloc(), adr.host, adr.port));
                    pipeline.addLast(
                        new HttpClientCodec(),
                        new HttpObjectAggregator(MAX_TEXT_MESSAGE_BUFFER_SIZE),
                        WebSocketClientCompressionHandler.INSTANCE,
                        new WebSocketChannelInboundHandler(handshakeFuture, session, adr.uri)
                    );
                }
            };
            ChannelFuture connect = new Bootstrap()
                .group(socketThread)
                .channel(OioSocketChannel.class)
                .handler(channelInitializer).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT)
                .connect(adr.host, adr.port);

            // Wait for connection to the server
            wait(connect, CONNECT_TIMEOUT);

            // Wait until Handshake arrives, which means that WebSocket connection is established
            wait(handshakeFuture[0], HANDSHAKE_TIMEOUT);

            session.channel = connect.channel();
        } catch (Throwable e) {
            session.close(e);
            throw new RuntimeException("Failed to connect to " + LogUtil.hideCredentials(address), e);
        }

        // Everything is ready to notify other threads
        boolean connected = makeConnected(session);

        // If we have not connected, then thread data was not stored and we have to clean it up ourselves
        if (!connected) {
            session.close(null);
            return null;
        }
        connector.notifyMessageConnectorListeners();
        return session;
    }

    /**
     * Waits for a connection to be established with the given ChannelFuture, with a specified timeout.
     *
     * @param future the ChannelFuture object representing the connection attempt
     * @param timeoutInMs the timeout duration in milliseconds
     * @throws IOException if the connection attempt is cancelled or fails
     */
    private static void wait(ChannelFuture future, long timeoutInMs) throws IOException {
        future.awaitUninterruptibly(timeoutInMs, TimeUnit.MILLISECONDS);
        if (future.isCancelled()) {
            throw new SocketTimeoutException();
        } else if (!future.isSuccess()) {
            throw new IOException(future.cause().getMessage(), future.cause());
        }
    }

    private DxLinkWebSocketApplicationConnection createApplicationConnection(QDStats stats) {
        DxLinkWebSocketApplicationConnection connection = null;
        Throwable failureReason = null;
        try {
            // create and start adapter
            ApplicationConnectionFactory acf = connector.getFactory();
            connection = (DxLinkWebSocketApplicationConnection) acf.createConnection(this);
        } catch (Throwable t) {
            failureReason = t;
        }
        if (connection == null) {
            log.error("Failed to create connection on socket " +
                LogUtil.hideCredentials(address), failureReason);
            try {
                stats.close();
            } catch (Throwable t) {
                log.error("Failed to close stats", t);
            }
            connector.addClosedConnectionStats(new ConnectionStats());
            throw new RuntimeException(failureReason);
        }
        connection.start();
        return connection;
    }

    private QDStats createStats() {
        try {
            // Create stats in try/catch block, so that we clean up socket if anything happens)
            URI uri = new URI(this.address);
            QDStats stats = connector.getStats()
                .getOrCreate(QDStats.SType.CONNECTIONS)
                .create(QDStats.SType.CONNECTION,
                    "host=" + JMXNameBuilder.quoteKeyPropertyValue(uri.getHost()) + "," +
                        "port=" + uri.getPort() + "," + "localPort=" + -1);
            if (stats == null)
                throw new NullPointerException("Stats were not created");
            return stats;
        } catch (Throwable t) {
            log.error("Failed to configure socket " + LogUtil.hideCredentials(address), t);
            connector.addClosedConnectionStats(new ConnectionStats());
            throw new RuntimeException(t);
        }
    }

    private synchronized boolean makeConnecting() {
        if (state == SocketState.STARTED) {
            state = SocketState.CONNECTING;
            notifyAll();
            return true;
        }
        return false;
    }

    private synchronized boolean makeConnected(Session sessionData) {
        if (state == SocketState.CONNECTING) {
            state = SocketState.CONNECTED;
            this.session = sessionData;
            notifyAll();
            return true;
        }
        return false;
    }

    private synchronized Session waitConnected() throws InterruptedException {
        while (state == SocketState.CONNECTING) {
            wait();
        }
        if (state == SocketState.CONNECTED)
            return session;
        if (state == SocketState.STOPPED)
            return null;
        throw new IllegalStateException();
    }

    private synchronized boolean makeClosedAndStopThread() {
        if (state == SocketState.STOPPED)
            return false;
        if (state != SocketState.NEW) {
            writer.close();
            socketThread.shutdownGracefully();
        }
        state = SocketState.STOPPED;
        notifyAll();
        return true;
    }

    // ========== TransportConnection implementation ==========

    @Override
    public void markForImmediateRestart() { connector.getReconnectHelper().reset(); }

    @Override
    public void connectionClosed() { close(); }

    @Override
    public void chunksAvailable() { writer.chunksAvailable(); }

    @Override
    public void readyToProcessChunks() {}

    private static class Address {
        private final URI uri;
        private final String host;
        private final int port;
        private final SslContext sslCtx;

        private Address(String address) throws URISyntaxException, SSLException {
            this.uri = new URI(address);
            if (!"ws".equalsIgnoreCase(uri.getScheme()) && !"wss".equalsIgnoreCase(uri.getScheme()))
                throw new IllegalArgumentException("Only WS(S) is supported.");
            if (uri.getHost() == null)
                throw new IllegalArgumentException("No host name specified");
            this.host = uri.getHost();
            if (uri.getPort() == -1) {
                if ("ws".equalsIgnoreCase(uri.getScheme())) {
                    this.port = WebSocketScheme.WS.port();
                } else if ("wss".equalsIgnoreCase(uri.getScheme())) {
                    this.port = WebSocketScheme.WSS.port();
                } else {
                    this.port = uri.getPort();
                }
            } else {
                this.port = uri.getPort();
            }
            if ("wss".equalsIgnoreCase(uri.getScheme())) {
                this.sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
            } else {
                this.sslCtx = null;
            }
        }
    }

    private class WebSocketChannelInboundHandler extends SimpleChannelInboundHandler<Object> {
        final WebSocketClientHandshaker handshaker;
        private final ChannelPromise[] handshakeFuture;
        private final Session session;

        private WebSocketChannelInboundHandler(ChannelPromise[] handshakeFuture, Session session, URI webSocketURL) {
            this.handshakeFuture = handshakeFuture;
            this.session = session;
            DefaultHttpHeaders headers = new DefaultHttpHeaders();
            // TODO Map<String, String> agent = ((DxLinkWebSocketApplicationConnectionFactory)
            //  connector.getFactory()).getAgentInfo();
            headers.set(HttpHeaderNames.USER_AGENT, QDFactory.getVersion().replace('-', '/').replace('+', ' '));
            handshaker = WebSocketClientHandshakerFactory.newHandshaker(webSocketURL, WebSocketVersion.V13, null,
                true, headers, MAX_FRAME_PAYLOAD_LENGTH);
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            handshakeFuture[0] = ctx.newPromise();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            handshaker.handshake(ctx.channel());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            // WebSocket Client disconnected
            WebSocketTransportConnection.this.close();
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            Channel ch = ctx.channel();
            if (!handshaker.isHandshakeComplete()) {
                try {
                    // WebSocket Client connected
                    FullHttpResponse response = (FullHttpResponse) msg;
                    String secWebSocketExtensions = response.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS);
                    handshaker.finishHandshake(ch, response);
                    session.stats = createStats();
                    variables().set(MessageConnectors.STATS_KEY, session.stats);
                    session.application = createApplicationConnection(session.stats);
                    handshakeFuture[0].setSuccess();
                    log.info("Connected to " + LogUtil.hideCredentials(address) +
                        ", host=" + ch.remoteAddress().toString() + ", sec extensions=" + secWebSocketExtensions);
                } catch (Throwable e) {
                    // WebSocket Client failed to connect
                    handshakeFuture[0].setFailure(e);
                    throw new IOException(e);
                }
                return;
            }

            if (msg instanceof FullHttpResponse) {
                FullHttpResponse response = (FullHttpResponse) msg;
                throw new IllegalStateException(
                    "Unexpected FullHttpResponse (getStatus=" + response.status() +
                        ", content=" + response.content().toString(CharsetUtil.UTF_8) + ')');
            }

            WebSocketFrame frame = (WebSocketFrame) msg;
            if (frame instanceof TextWebSocketFrame) {
                // WebSocket Client received message
                TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
                if (verbose && log.debugEnabled())
                    log.debug("RCVD: " + textFrame.text());
                session.application.processMessage(textFrame.text());
            } else if (frame instanceof CloseWebSocketFrame) {
                // WebSocket Client received closing
                ch.close();
                WebSocketTransportConnection.this.exitSocket(new EOFException());
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            // WebSocket Client exceptionCaught: + cause.getMessage()
            ctx.close();
            if (!handshakeFuture[0].isDone())
                handshakeFuture[0].setFailure(cause);
            WebSocketTransportConnection.this.exitSocket(cause);
        }
    }
}
