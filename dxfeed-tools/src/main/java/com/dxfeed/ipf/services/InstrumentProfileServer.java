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

import com.devexperts.connector.proto.ApplicationConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedOutput;
import com.devexperts.io.ChunkList;
import com.devexperts.io.ChunkedInput;
import com.devexperts.io.ChunkedOutput;
import com.devexperts.io.StreamCompression;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimePeriod;
import com.devexperts.util.TimeUtil;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.impl.InstrumentProfileComposer;
import com.dxfeed.ipf.live.InstrumentProfileCollector;
import com.dxfeed.ipf.live.InstrumentProfileUpdateListener;
import com.dxfeed.ipf.transform.InstrumentProfileTransform;
import com.dxfeed.ipf.transform.TransformContext;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InstrumentProfileServer implements Closeable {
    // ===================== private static constants =====================

    private static final String USER_AGENT_PREFIX = "User-Agent: ";
    private static final String IF_MODIFIED_SINCE_PREFIX = "If-Modified-Since: ";
    private static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";

    private static final String LIVE_PROP_PREFIX = "X-Live: ";
    private static final String LIVE_PROP_REQUEST_YES = "yes";
    private static final String LIVE_PROP_REQUEST_NO = "no";
    private static final String LIVE_PROP_RESPONSE = "provided";

    private static final String IPF_PATH = "/ipf/";

    private static final Pattern COMMAND_PATTERN = Pattern.compile("(GET|HEAD) ([^ ?]+)(\\?[^ ]*)? HTTP/1\\.[01]");
    private static final Pattern LIVE_QUERY_PATTERN = Pattern.compile("\\?(.*&)?live(=[^&]*)?(&.*)?");
    private static final String TEXT_PLAIN = "text/plain; charset=UTF-8";

    private static final int UPDATE_BATCH_SIZE = SystemProperties.getIntProperty(
        InstrumentProfileServer.class, "updateBatchSize", 1000, 1, Integer.MAX_VALUE / 2);
    private static final long REQUEST_TIMEOUT = TimePeriod.valueOf(SystemProperties.getProperty(
        InstrumentProfileServer.class, "requestTimeout", "30s")).getTime();
    private static final long HEARTBEAT_PERIOD = TimePeriod.valueOf(SystemProperties.getProperty(
        InstrumentProfileServer.class, "heartbeatPeriod", "10s")).getTime();

    // =====================  public static factory methods =====================

    public static InstrumentProfileServer createServer(String address, InstrumentProfileCollector collector) {
        return new InstrumentProfileServer(address, collector);
    }

    // =====================  private instance fields =====================

    private final String address;
    private InstrumentProfileCollector collector;
    private List<MessageConnector> connectors;
    private boolean closed;
    private volatile List<InstrumentProfileTransform> transforms;

    // =====================  package-private constructor =====================

    InstrumentProfileServer(String address, InstrumentProfileCollector collector) {
        this.address = address;
        this.collector = collector;
    }

    // =====================  public instance methods =====================

    public List<InstrumentProfileTransform> getTransforms() {
        return transforms;
    }

    public void setTransforms(List<InstrumentProfileTransform> transforms) {
        this.transforms = transforms;
    }

    public synchronized void start() {
        if (connectors != null || closed)
            return; // already started or closed
        connectors = MessageConnectors.createMessageConnectors(new ConnectionFactory(), address);
        MessageConnectors.startMessageConnectors(connectors);
    }

    @Override
    public synchronized void close() {
        if (closed)
            return; // already closed
        closed = true;
        MessageConnectors.stopMessageConnectors(connectors);
        connectors = null;
    }

    // =====================  package-private hook =====================

    // hook for InstrumentProfileService to fill in a list of profiles
    void onRequest() {}

    // hook for InstrumentProfileService to turn off live support
    boolean supportsLive() {
        return true;
    }

    // =====================  private inner classes =====================

    private class ConnectionFactory extends ApplicationConnectionFactory {
        @Override
        public ApplicationConnection<?> createConnection(TransportConnection transportConnection) throws IOException {
            return new Connection(this, transportConnection);
        }

        @Override
        public String toString() {
            return "InstrumentProfileServer";
        }
    }

    private class Connection extends ApplicationConnection<ConnectionFactory>
        implements InstrumentProfileUpdateListener
    {
        private final long connectionTime = System.currentTimeMillis();
        private final SimpleDateFormat dateFormat;
        private final ChunkedInput in = new ChunkedInput();
        private final ChunkedOutput out = new ChunkedOutput();
        private final ChunkedOutput bodyOut = new ChunkedOutput();
        private final List<InstrumentProfile> batch = new ArrayList<>();
        private Request request;
        private boolean liveGetRequest;
        private InstrumentProfileComposer composer;
        private Iterator<InstrumentProfile> instruments;
        private long nextHeartbeatTime;
        private boolean sendHeartbeat;
        private boolean complete;
        private TransformContext ctx;

        Connection(ConnectionFactory factory, TransportConnection transportConnection) {
            super(factory, transportConnection);
            in.mark(); // mark at the beginning
            dateFormat = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
            dateFormat.setTimeZone(TimeUtil.getTimeZoneGmt());
        }

        @Override
        public synchronized boolean processChunks(ChunkList chunks, Object owner) {
            if (request != null)
                return false; // we already have request !!! No parsing anymore
            in.addAllToInput(chunks, owner);
            in.reset(); // try parse from tbe beginning
            try {
                request = parseRequest(in);
            } catch (EOFException e) {
                // not enough data -- do nothing -- wait more
                return true; // give us more data
            } catch (IOException e) {
                request = new Request(); // dummy bad quest
                log.error("Bad request", e);
                makeResponseHeader(HttpURLConnection.HTTP_BAD_REQUEST, "Bad request", TEXT_PLAIN);
                return false; // parsing no more
            }
            log.info(request.toString());
            if (request.name == null) {
                makeResponseHeader(HttpURLConnection.HTTP_NOT_FOUND, "Not found", TEXT_PLAIN);
                return false; // parsing no more
            }
            onRequest(); // hook for InstrumentProfileService to fill in a list of profiles
            if (request.ifModifiedSince != 0 && collector.getLastUpdateTime() <= request.ifModifiedSince) {
                // not modified response
                makeResponseHeader(HttpURLConnection.HTTP_NOT_MODIFIED, "Not modified", TEXT_PLAIN);
                return false; // parsing no more
            }
            makeResponseHeader(HttpURLConnection.HTTP_OK, "OK", request.compression == StreamCompression.NONE ?
                TEXT_PLAIN : request.compression.getMimeType());
            if (request.method.equals("GET")) {
                try {
                    composer = new InstrumentProfileComposer(
                        request.compression.compress(bodyOut, request.compression.stripExtension(request.name)));
                } catch (IOException e) {
                    throw new AssertionError(e); // cannot happen (out is a chunked output)
                }
                // add listener for live requests or just initialize snapshot view
                if (request.live) {
                    liveGetRequest = true; // need to removeListener on close
                    // will assign to instruments from inside of instrumentProfilesUpdated
                    collector.addUpdateListener(this);
                } else {
                    instruments = collector.view().iterator();
                    // makeResponseHeader already called notifyChunksAvailable method
                }
            }
            return false; // does not support next request
        }

        private Request parseRequest(BufferedInput in) throws IOException {
            String command = nextLine(in);
            Matcher m = COMMAND_PATTERN.matcher(command);
            if (!m.matches())
                throw new InvalidFormatException("Unsupported command '" + command + "'");
            Request request = new Request(m.group(1), m.group(2), m.group(3));
            while (true) {
                // reader headers up to empty line
                String line = nextLine(in);
                if (line.isEmpty())
                    break;
                // "X-Live: yes|no" request property overrides "?live" query
                if (line.startsWith(LIVE_PROP_PREFIX)) {
                    String val = line.substring(LIVE_PROP_PREFIX.length());
                    switch (val) {
                    case LIVE_PROP_REQUEST_YES:
                        request.live = true;
                        break;
                    case LIVE_PROP_REQUEST_NO:
                        request.live = false;
                        break;
                    }
                }
                // capture user agent for logs
                if (line.startsWith(USER_AGENT_PREFIX))
                    request.userAgent = line.substring(USER_AGENT_PREFIX.length());
                // capture If-Modified-Since
                if (line.startsWith(IF_MODIFIED_SINCE_PREFIX))
                    try {
                        request.ifModifiedSince = dateFormat.parse(line.substring(IF_MODIFIED_SINCE_PREFIX.length())).getTime();
                    } catch (ParseException e) {
                        // just ignore if cannot parse
                    }
            }
            // if this instance does not support live or compression is wrong, then don't do live
            if (!supportsLive() || !request.compression.hasSyncFlush())
                request.live = false;
            return request;
        }

        private String nextLine(BufferedInput in) throws IOException {
            StringBuilder sb = new StringBuilder();
            while (true) {
                sb.appendCodePoint(in.readUTFChar());
                int len = sb.length();
                if (len >= 2 && sb.charAt(len - 2) == 0x0d && sb.charAt(len - 1) == 0x0a)
                    break; // CRLF seen
            }
            sb.setLength(sb.length() - 2);
            return sb.toString();
        }

        // require SYNC(this)
        private void makeResponseHeader(int code, String reason, String mimeType) {
            try {
                long lastModified = collector.getLastUpdateTime();
                writeLine(out, "HTTP/1.1 " + code + " " + reason);
                writeLine(out, "Content-Type: " + mimeType);
                writeLine(out, "Transfer-Encoding: chunked");
                writeLine(out, "Date: " + dateFormat.format(new Date()));
                if (lastModified != 0)
                    writeLine(out, "Last-Modified: " + dateFormat.format(new Date(lastModified)));
                if (request != null && request.live)
                    writeLine(out, LIVE_PROP_PREFIX + LIVE_PROP_RESPONSE);
                writeLine(out, "Server: IPS/1.0 (" + QDFactory.getVersion() + ")");
                writeLine(out, "Connection: close");
                writeLine(out, "");
            } catch (IOException e) {
                throw new AssertionError(e); // cannot happen
            }
            notifyChunksAvailable();
        }

        private void writeLine(BufferedOutput out, String s) throws IOException {
            for (int i = 0; i < s.length(); i++)
                out.writeUTFChar(s.charAt(i));
            writeCRLF(out);
        }

        private void writeCRLF(BufferedOutput out) throws IOException {
            out.write(0x0d);
            out.write(0x0a);
        }

        @Override
        public synchronized long examine(long currentTime) {
            if (currentTime >= nextHeartbeatTime) {
                sendHeartbeat = true;
                nextHeartbeatTime = currentTime + HEARTBEAT_PERIOD;
                notifyChunksAvailable();
            }
            if (request == null && currentTime >= connectionTime + REQUEST_TIMEOUT)
                close();
            return nextHeartbeatTime;
        }

        @Override
        public synchronized ChunkList retrieveChunks(Object owner) {
            boolean hasMore = composeBatch();
            // reserve space for chunk size
            ChunkList result = getTransferChunk(owner, false);
            if (hasMore)
                notifyChunksAvailable();
            else if (request != null && !liveGetRequest) {
                // snapshot request ("HEAD" or not-live "GET"), finish it
                if (result == null) {
                    // let's closer composer to write compression footer
                    if (composer != null) {
                        try {
                            composer.close();
                        } catch (IOException e) {
                            throw new AssertionError(e); // cannot happen, as the underlying is chunked stream
                        }
                        composer = null;
                        result = getTransferChunk(owner, true);
                        notifyChunksAvailable(); // will close on next retrieve chunks
                    } else
                        close(); // that's it, all chunks were sent
                } else
                    notifyChunksAvailable(); // close on next retrieve chunks call
            }
            return result;
        }

        private ChunkList getTransferChunk(Object owner, boolean lastChunk) {
            ChunkList bodyData = bodyOut.getOutput(this);
            try {
                if (bodyData != null) {
                    writeLine(out, Long.toHexString(bodyData.getTotalLength()));
                    out.writeAllFromChunkList(bodyData, this);
                    writeCRLF(out);
                }
                if (lastChunk) {
                    writeLine(out, "0");
                    writeCRLF(out);
                }
            } catch (IOException e) {
                throw new AssertionError(e); // cannot happen
            }
            return out.getOutput(owner);
        }

        // require SYNC(this)
        private boolean composeBatch() {
            if (instruments == null || composer == null)
                return false; // nothing to see here
            boolean hasMore = true;
            while (true) {
                // check hasNext first
                if (!instruments.hasNext()) {
                    hasMore = false;
                    break;
                }
                // full batch means that we have more for next batch
                if (batch.size() >= UPDATE_BATCH_SIZE)
                    break;
                batch.add(instruments.next());
            }
            if (sendHeartbeat || !batch.isEmpty())
                try {
                    transformBatch(transforms); // atomic read current transform
                    composer.compose(batch, !liveGetRequest); // only send REMOVED in live connection
                    if (!hasMore && !complete) {
                        // send complete marker once per connection
                        composer.composeComplete();
                        complete = true;
                    } else
                        composer.composeFlush();
                    sendHeartbeat = false;
                    nextHeartbeatTime = System.currentTimeMillis() + HEARTBEAT_PERIOD;
                } catch (IOException e) {
                    throw new AssertionError(e); // cannot happen, as the underlying is chunked stream
                } finally {
                    batch.clear();
                }
            return hasMore;
        }

        // require SYNC(this)
        private void transformBatch(List<InstrumentProfileTransform> transforms) {
            if (transforms == null)
                return;
            if (ctx == null)
                ctx = new TransformContext();
            for (InstrumentProfileTransform transform : transforms) {
                List<InstrumentProfile> transformed = transform.transform(ctx, batch);
                batch.clear();
                batch.addAll(transformed);
            }
        }

        @Override
        public synchronized void instrumentProfilesUpdated(Iterator<InstrumentProfile> instruments) {
            // this is invoked for live request, for snapshot instrument is initialized with a snapshot view
            this.instruments = instruments;
            notifyChunksAvailable();
        }

        @Override
        protected synchronized void closeImpl() {
            if (liveGetRequest)
                collector.removeUpdateListener(this);
            try {
                if (composer != null) {
                    composer.close();
                    composer = null;
                }
            } catch (IOException e) {
                throw new AssertionError(e); // cannot happen, as the underlying is chunked stream
            }
        }
    }

    private static class Request {
        final String method;
        final String path;
        final String name; // null for invalid/unsupported path
        boolean live;
        String userAgent;
        long ifModifiedSince;
        StreamCompression compression;

        Request() { // dummy bad quest
            method = "BAD";
            path = "";
            name = null;
        }

        Request(String method, String path, String query) {
            this.method = method;
            this.path = path;
            String name = null;
            if (path.startsWith(IPF_PATH)) {
                name = path.substring(IPF_PATH.length());
                if (name.indexOf('/') >= 0)
                    name = null;
            }
            this.name = name;
            live = query != null && LIVE_QUERY_PATTERN.matcher(query).matches();
            compression = name == null ? StreamCompression.NONE : StreamCompression.detectCompressionByExtension(name);
        }

        @Override
        public String toString() {
            return method + " request for " + path +
                (live ? " with live streaming" : "") +
                (ifModifiedSince != 0 ? " if-modified-since " + TimeFormat.DEFAULT.format(ifModifiedSince) : "") +
                (userAgent != null ? " from " + userAgent : "");
        }
    }
}
