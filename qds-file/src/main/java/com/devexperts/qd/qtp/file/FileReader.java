/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.file;

import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedInputPart;
import com.devexperts.io.Chunk;
import com.devexperts.io.ChunkedInput;
import com.devexperts.io.StreamCompression;
import com.devexperts.io.URLInputStream;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataIterator;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SubscriptionIterator;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.qtp.AbstractQTPParser;
import com.devexperts.qd.qtp.FileConstants;
import com.devexperts.qd.qtp.HeartbeatPayload;
import com.devexperts.qd.qtp.MessageConsumerAdapter;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.qd.qtp.RawDataConsumer;
import com.devexperts.qd.qtp.fieldreplacer.FieldReplacersCache;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.qd.util.TimeSequenceUtil;
import com.devexperts.transport.stats.ConnectionStats;
import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.LogUtil;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FileReader implements MessageReader {
    private static final Logging log = Logging.getLogging(FileReader.class);

    // Sleep time while waiting for new file to appear
    private static final long SLEEP_DURATION = TimePeriod.valueOf(
        SystemProperties.getProperty(FileReader.class, "SleepDuration", "1s")).getTime();

    // --- connection stats ---

    private final ConnectionStats connectionStats;

    // --- initial config parameters copied from connector ---

    private final FileReaderParams params;

    private long startTime = FileConnector.NA_TIME; // will keep an effective start time if delay was set (hasDelay == true)
    private long delayTime = FileConnector.NA_TIME; // will keep an effective delay time based on the current time
    private final long stopTime;                    // is fully determined by params

    // --- computed from params in constructor ---

    private final StreamCompression compression; // from params or from file name
    private final TimestampsType timestampsType; // from params or none if "ignoreTime" is set

    private final boolean hasStart;
    private final boolean hasDelay;
    private final boolean hasStop;
    private final boolean hasSpeed;
    private final boolean hasMaxSpeed;
    private final boolean doNotTryToReadTimeFile; // is set in certain cases
    private final String containerExtension;
    private final String dataFileExtension;
    private final TimestampedFilenameFilter filesFilter; // == null when reading a single file

    // --- state that updates while reading data ---

    private volatile boolean closed; // is set to true by "close" method.

    private TimestampsType currentTimestampsType; // current type of timestamps

    private long virtualTime0; // the first time stamp in file encountered
    private long wallTime0; // time moment when virtualTime0 was encountered by wall clock (System.currentTimeMillis)
    private boolean hasTime0; // true when virtualTime0 and wallTime0 are defined (happens once)

    private TimestampedFile[] filesList;
    private int nextFileId;
    private long lastFileTime = FileConnector.NA_TIME; // last time picked by initNextAddress
    private boolean stopFlag;
    private volatile long delayActual; // for monitoring purposes only -- volatile because read is async from mars monitoring thread!)
    private String dataFileAddress; // current name of data file
    private String timeFileAddress; // current name of time file
    private InputStream dataIn;
    private BufferedReader timeIn;

    // --- is needed only when read(adapter) is called

    private DataScheme scheme; // maybe null for default scheme
    private MessageConsumerAdapter adapter;

    // --- parser, buffer and stats ---

    private long lastTime;

    private FileFormat format; // from params or from first bytes
    private final ChunkedInput input = new ChunkedInput(FileConstants.CHUNK_POOL);
    private boolean hasBytesOnHold; // true when parsing an inputPart
    private BufferedInputPart inputPart; // when file is delimited with ".time" file marks we need to parse parts
    private AbstractQTPParser parser;
    private Chunk chunk;
    private final QDStats stats;

    private final Consumer consumer = new Consumer();
    private final HeartbeatPayload heartbeatPayload = new HeartbeatPayload();

    private class Consumer extends MessageConsumerAdapter implements RawDataConsumer {
        @Override
        public String getSymbol(char[] chars, int offset, int length) {
            return adapter.getSymbol(chars, offset, length);
        }

        @Override
        public void handleCorruptedStream() {
            if (onCorruptedShallContinueWithNextFile()) {
                super.handleCorruptedStream(); // log only
                throw new CorruptedFileException("Corrupted QTP byte stream"); // bail out of parsing
            } else
                adapter.handleCorruptedStream();
        }

        @Override
        public void handleCorruptedMessage(int messageTypeId) {
            if (onCorruptedShallContinueWithNextFile()) {
                super.handleCorruptedMessage(messageTypeId); // log only
                throw new CorruptedFileException("Corrupted QTP message"); // bail out of parsing
            } else
                adapter.handleCorruptedMessage(messageTypeId);
        }

        @Override
        public void handleUnknownMessage(int messageTypeId) {
            if (onCorruptedShallContinueWithNextFile()) {
                super.handleUnknownMessage(messageTypeId); // log only
                throw new CorruptedFileException("Unknown QTP message"); // bail out of parsing
            } else
                adapter.handleUnknownMessage(messageTypeId);
        }

        @Override
        public void processDescribeProtocol(ProtocolDescriptor desc, boolean logDescriptor) {
            processDescribeProtocolMessage(desc);
        }

        @Override
        public void processHeartbeat(HeartbeatPayload heartbeatPayload) {
            processHeartbeatMessage(heartbeatPayload);
        }

        @Override
        public void processData(DataIterator iterator, MessageType message) {
            // we know that parsers parse data into RecordBuffers
            processRecordSourceMessage((RecordSource) iterator, message);
        }

        @Override
        public void processSubscription(SubscriptionIterator iterator, MessageType message) {
            // we know that parsers parse data into RecordBuffers
            processRecordSourceMessage((RecordSource) iterator, message);
        }

        @Override
        public void processOtherMessage(int messageId, BufferedInput data, int len) {
            if (shallProcessMessage())
                adapter.processOtherMessage(messageId, data, len);
        }
    }

    public FileReader(String dataFilePath, ConnectionStats connectionStats, FileReaderParams params) {
        this.connectionStats = connectionStats;
        // copy initial parameters
        this.params = params;
        this.startTime = params.getStartTime();
        this.stopTime = params.getStopTime();
        this.delayTime = params.getDelayTime();
        StreamCompression compression = params.getCompression(); // will try to resolve from name
        this.timestampsType = params.isIgnoreTime() ?
            TimestampsType.NONE : // convert deprecated ignoreTime into time=none
            params.getTime();

        // ignore ".time" file when reading from HTTP-specified file
        boolean doNotTryToReadTimeFile = false;

        this.hasStart = startTime != FileConnector.NA_TIME;
        this.hasDelay = delayTime != FileConnector.NA_TIME;
        this.hasStop = stopTime != FileConnector.NA_TIME;
        this.hasSpeed = params.getSpeed() != 1.0;
        this.hasMaxSpeed = params.getSpeed() == FileReaderParams.MAX_SPEED; // to optimize performance for this case (don't need to wait)
        if (hasStart && hasDelay)  // FileConnector should not let this happen
            throw new IllegalArgumentException("Cannot have both start and delay set");
        if (hasSpeed && hasDelay)  // FileConnector should not let this happen
            throw new IllegalArgumentException("Cannot have both speed and delay set");

        if (timestampsType != null && !timestampsType.isUsingTimeFile())
            doNotTryToReadTimeFile = true; // do not try to read ".time" file when explicitly set to inline timestamp format

        dataFileAddress = dataFilePath;
        if (dataFileAddress.startsWith(FileConnectorFactory.FILE_PREFIX))
            dataFileAddress = dataFileAddress.substring(FileConnectorFactory.FILE_PREFIX.length());

        URL url = FileUtils.addressToURL(dataFilePath);
        if (url.getQuery() != null)
            doNotTryToReadTimeFile = true; // don't try to read ".time" file when url has a query

        // try to autodetect compression by extension if not set explicitly
        // ... will later detect by contents if extension is not telling anything
        String fileNameRest = url.getFile();
        if (compression == null) {
            compression = StreamCompression.detectCompressionByExtension(fileNameRest);
            if (compression == StreamCompression.NONE)
                compression = null; // don't know from extension, will detect from header
        }
        String compressionExtension = "";
        if (compression != null && fileNameRest.endsWith(compression.getExtension())) {
            compressionExtension = compression.getExtension();
            fileNameRest = compression.stripExtension(fileNameRest);
        }
        String dataFileExtension = doNotTryToReadTimeFile ? "" : FileUtils.retrieveExtension(fileNameRest);

        if (dataFileExtension.equals(FileUtils.TIME_FILE_EXTENSION))
            doNotTryToReadTimeFile = true; // implicitly ignore time if data file ends with ".time"
        if (doNotTryToReadTimeFile && timestampsType != null && timestampsType.isUsingTimeFile())
            throw new IllegalArgumentException("Cannot read time file");

        File addressFile = FileUtils.urlToFile(url);
        // can read multiple file only when data is read from an actual file (not http:// or something)
        filesFilter = addressFile == null ? null : TimestampedFilenameFilter.create(addressFile, compressionExtension);

        if (filesFilter != null && timestampsType != null && timestampsType.isUsingTimeFile())
            filesFilter.requireTimeFile(); // only search for data files that have a corresponding time file

        // Parameters resolution done
        this.compression = compression;
        this.containerExtension = compressionExtension;
        this.dataFileExtension = dataFileExtension;
        this.doNotTryToReadTimeFile = doNotTryToReadTimeFile;
        this.stats = params.getStats().getOrCreate(QDStats.SType.CONNECTIONS).create(QDStats.SType.CONNECTION,
            "file=" + LogUtil.hideCredentials(dataFilePath));
    }

    /** Utility method to parse additional parameters after
     * file path and returns file path itself (already without parameters).
     *
     * @param filePath String with file path of tape data, which can be followed by list of parameters
     *                 <tt>[key1=value1, key2=value2, ..., keyN=valueN]</tt>.
     * @param params   params to which parameters from filename is added.
     * @return file path without parameters.
     * @throws InvalidFormatException if couldn't parse parameters.
     */
    public static String parseParameters(String filePath, FileReaderParams params) {
        List<String> props = new ArrayList<>();
        filePath = QDConfig.parseProperties(filePath, props);
        QDConfig.setProperties(params, props);
        return filePath;
    }

    /**
     * Initializes file list.
     * Configures {@link #filesFilter files filter} and reads file list.
     *
     * @return false if files are not found
     */
    private boolean initFileList() throws InterruptedException {
        if (filesFilter == null)
            return true; // not reading multiple files
        // determine effective start time based only delay
        // It assumes non-cycle mode, because delay cannot be combined with cycle
        if (hasDelay)
            startTime = System.currentTimeMillis() - delayTime;
        if (hasStop)
            filesFilter.filterByStopTime(stopTime);
        // configure fileFilter with start time and stop time
        return rescanFileList(startTime, FileConnector.NA_TIME);
    }

    /**
     * Rescans file list after failure.
     * Configures {@link #filesFilter files filter} and reads file list.
     *
     * @return false if files are not found
     */
    private boolean rescanFileList(long rescanStartTime, long lastFileTime) throws InterruptedException {
        if (filesFilter == null)
            return true; // not reading multiple files
        filesFilter.filterByStartAndPreviousFileTime(rescanStartTime, lastFileTime);
        // try to get list of files until succeeded
        if (!waitAngGetFileList())
            // quit if no files found (which could happen only when stop is set, otherwise wait for files)
            return false;
        // start from the first file
        nextFileId = 0;
        initNextAddress();
        return true;
    }

    // returns false when next file is not found
    private boolean waitAngGetFileList() throws InterruptedException {
        boolean logged = false;
        while (true) {
            filesList = filesFilter.listTimestampedFiles();
            if (filesList.length > 0)
                break;
            if (doNotWait()) {
                log.info("No matching files.");
                return false;
            }
            if (!logged) {
                log.info("No matching files. Waiting for files to appear...");
                logged = true;
            }
            Thread.sleep(SLEEP_DURATION);
        }
        if (logged)
            log.info("Found files...");
        return true;
    }

    private void initNextAddress() {
        assert filesFilter != null; // calling this method only when reading multiple files
        TimestampedFile timestampedFile = filesList[nextFileId];
        dataFileAddress = timestampedFile.address;
        lastFileTime = timestampedFile.time;
        filesFilter.filterByPreviousFileTime(timestampedFile.time);
        nextFileId++;
        // report progress to adapter
        adapter.processTimeProgressReport(timestampedFile.time);
    }

    public void setScheme(DataScheme scheme) {
        this.scheme = scheme;
    }

    @Override
    public void readInto(MessageConsumerAdapter adapter) throws InterruptedException {
        // init
        this.adapter = adapter;
        // work
        if (!initFileList())
            return;
        // This loop runs for several times only in case we are reading from timestamped files
        while (!isClosed()) {
            boolean openFilesSuccess = false;
            boolean processSuccess = false;
            try {
                openFilesSuccess = openFiles();
                if (openFilesSuccess) {
                    onConnected();
                    process();
                    processSuccess = true;
                }
            } catch (IOException | CorruptedFileException e) {
                // [QD-418] FileConnector shall skip broken file and continue to the next one
                log.error("Failed to read file", e);
            } finally {
                closeFiles();
            }
            if (isClosed())
                break;
            // perform implicit stop at the end of a single file (or if failed to open a single file)
            if (filesFilter == null)
                stopFlag = true;
            // handle stop depending on cycle mode
            if (stopFlag) {
                if (params.isCycle()) {
                    log.info("End of cycle. Starting from beginning");
                    stopFlag = false;
                    resetParserSessionAndClearInput();
                    resetTime0();
                    if (!initFileList())
                        return;
                    continue; // start reading it
                } else
                    return; // no cycle --- stop reading
            }
            // This point can be reached only if we are reading multiple files -- we go to the next one.
            // Previous file was successfully opened and processed -- good to go to next one.
            if (openFilesSuccess && processSuccess && isNextFileAvailable()) {
                initNextAddress();
            } else {
                // rescan file list on every failure and start from the next
                boolean hasNextFile = rescanFileList(lastFileTime + 1, lastFileTime);
                // no cycle and no next file --- stop reading
                if (!hasNextFile && !params.isCycle())
                    return;
            }
        }
    }

    private boolean isClosed() throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return closed;
    }

    /**
     * Process one file (or pair of files). This methods returns normally only when there are many
     * files and there is next file to read.
     */
    private void process() throws IOException, InterruptedException, CorruptedFileException {
        lastTime = FileConnector.NA_TIME;
        TimestampedPosition next = TimestampedPosition.readFrom(timeIn);
        long position = 0;
        boolean firstBlock = true;

        while (!isClosed()) {
            if (!waitAndReadChunk()) {
                // nothing more in this file, check that it was completely parsed
                if (input.available() > 0) {
                    log.error("File was not completely parsed, " + input.available() + " bytes remaining");
                    consumer.handleCorruptedStream();
                }
                // go on to the next one
                break;
            }
            if (firstBlock) {
                createParserOnFirstChunk();
                firstBlock = false;
            }

            // initialize chunk parsing
            int remaining = chunk.getLength();
            input.addToInput(chunk, this);
            chunk = null;

            // loop until we parse all read bytes
            while (remaining > 0) {
                long nextPosition = next == null ? Long.MAX_VALUE : next.getPosition();
                int sendLen = (int) Math.min(nextPosition - position, remaining);
                if (position < nextPosition) {
                    // send up to next position
                    remaining -= sendLen;
                    processChunkPart(remaining);
                    connectionStats.addReadBytes(sendLen);
                    position += sendLen;
                }
                if (position < nextPosition)
                    break; // have not reached next position yet -- need one more chunk
                if (timeIn != null)
                    parser.setEventTimeSequence(TimeSequenceUtil.getTimeSequenceFromTimeMillis(next.getTime()));
                if (!advanceTime(next.getTime()))
                    return; // don't read anything more at all
                next = TimestampedPosition.readFrom(timeIn);
            }
        }
    }

    private boolean advanceTime(long nextTime) {
        try {
            // define delay between current time and time when data was written (if undefined yet).
            initTime0(nextTime);

            // check if stop time reached will be reached on nextTime
            if (hasStop && nextTime >= stopTime) {
                waitTillVirtualTime(stopTime); // wait until the actual stop time "reached"
                stopFlag = true;
                return false;
            }

            // wait if needed for next time, but no beyond stop time
            waitTillVirtualTime(nextTime);

            // read next time and position
            lastTime = nextTime;
            return true;
        } catch (InterruptedException e) {
            // interrupted
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void waitTillVirtualTime(long nextTime) throws InterruptedException {
        if (!hasTime0 || hasMaxSpeed)
            return; // never wait until start time and at max speed even if time goes backwards
        long waitTillTime = (long) (wallTime0 + (nextTime - virtualTime0) / params.getSpeed());
        long curTime = System.currentTimeMillis();
        long sleepTime;
        while ((sleepTime = waitTillTime - curTime) > 0) {
            Thread.sleep(sleepTime);
            curTime = System.currentTimeMillis();
        }
        delayActual = curTime - nextTime;
    }

    private void initTime0(long nextTime) {
        if (hasTime0)
            return; // already initialized
        wallTime0 = System.currentTimeMillis();
        if (hasDelay) {
            // honor explicitly configured delay
            virtualTime0 = wallTime0 - delayTime;
        } else if (hasStart) {
            if (nextTime < startTime) {
                // do not init yet -- wait until start time is encountered
                return;
            } else
                // replay from the specified start time (see QD-459)
                virtualTime0 = startTime;
        } else
            // replay from the first actually encountered time
            virtualTime0 = nextTime;
        hasTime0 = true;
    }

    private void resetTime0() {
        hasTime0 = false;
        delayActual = delayTime == FileConnector.NA_TIME ? 0 : delayTime;
    }

    private void processChunkPart(int bytesOnHold) {
        if (hasBytesOnHold)
            inputPart.syncInputPosition();
        if (bytesOnHold > 0) {
            // parse only part of an input
            if (inputPart == null)
                inputPart = new BufferedInputPart();
            input.mark();
            inputPart.setInput(input, input.available() - bytesOnHold);
            parser.setInput(inputPart);
            hasBytesOnHold = true;
        } else if (hasBytesOnHold) {
            // switch to parse all remaining
            input.unmark();
            parser.setInput(input);
            hasBytesOnHold = false;
        }
        parser.parse(consumer); // calls to processRecordSource via Consumer
    }

    // Is called by Consumer from handleXXX on all kinds of errors to figure out whether to continue processing
    // on next file (continue despite corruption in multi-file mode and in cycle mode)
    boolean onCorruptedShallContinueWithNextFile() {
        return filesFilter != null || params.isCycle();
    }

    // Is called by Consumer to check if message needs to be processed, to skip messages before start
    boolean shallProcessMessage() {
        // always process messages when time format is auto-detected or time=NONE (not read)
        // ... will skip messages until start when reading timestamps and after stop (when stopFlag was set)
        return (currentTimestampsType == null || currentTimestampsType == TimestampsType.NONE || lastTime >= startTime) && !stopFlag;
    }

    // Is called from Consumer
    public void processDescribeProtocolMessage(ProtocolDescriptor desc) {
        // always process protocol descriptions
        adapter.processDescribeProtocol(desc, true);
        // use time property (if present) to detect current timestamp type
        String timeProperty = desc.getProperty(ProtocolDescriptor.TIME_PROPERTY);
        if (timeProperty != null) {
            try {
                currentTimestampsType = TimestampsType.valueOf(timeProperty.toUpperCase(Locale.US));
            } catch (IllegalArgumentException e) {
                log.error("Unrecognized timestamps type in " + ProtocolDescriptor.TIME_PROPERTY + " property: \"" + timeProperty + "\"");
            }
        }
    }

    // Is called from Consumer
    void processHeartbeatMessage(HeartbeatPayload heartbeatPayload) {
        if ((currentTimestampsType == null || currentTimestampsType == TimestampsType.MESSAGE) && heartbeatPayload.hasTimeMillis()) {
            currentTimestampsType = TimestampsType.MESSAGE; // AUTODETECT TIME FORMAT: detected MESSAGE time format
            long nextTime = heartbeatPayload.getTimeMillis();
            if (!advanceTime(nextTime))
                return;
        }
        if (shallProcessMessage()) {
            if (heartbeatPayload.hasTimeMillis()) {
                // remember that we've processed this heartbeat timestamp
                this.heartbeatPayload.setTimeMillis(heartbeatPayload.getTimeMillis());
                parser.setEventTimeSequence(TimeSequenceUtil.getTimeSequenceFromTimeMillis(heartbeatPayload.getTimeMillis()));
            }
            adapter.processHeartbeat(heartbeatPayload);
        }
    }

    // Is called from Consumer
    void processRecordSourceMessage(RecordSource source, MessageType message) {
        RecordCursor cursor = source.current();
        if (cursor == null)
            return; // nothing to process
        if (cursor.getEventTimeSequence() != 0 && (currentTimestampsType == null || currentTimestampsType == TimestampsType.FIELD)) {
            // source contains event times and we are explicitly configured to use them or in auto-detect mode
            currentTimestampsType = TimestampsType.FIELD; // AUTODETECT TIME FORMAT: always read EventTimeSequence field afterwards
            if (hasMaxSpeed && !hasStop && (!hasStart || lastTime >= startTime)) // optimize for max speed when we don't have to look at time
                adapter.processRecordSource(source, message);
            else
                processRecordSourceMessageByEventTime(source, message);
        } else {
            if (!shallProcessMessage())
                return;
            // timestamp source is not EventTimeSequence or EventTimeSequence is not present -- just process everything
            // report currently timestamp from ".time" file via HeartbeatPayload
            if (lastTime != FileConnector.NA_TIME && lastTime != heartbeatPayload.getTimeMillis()) {
                // only when this timestamp was not reported to adapter yet
                heartbeatPayload.setTimeMillis(lastTime);
                adapter.processHeartbeat(heartbeatPayload);
            }
            adapter.processRecordSource(source, message);
        }
    }

    private void processRecordSourceMessageByEventTime(RecordSource source, MessageType message) {
        // processing loop
        RecordCursor cursor = source.current();
        long position = source.getPosition();
        long endPosition;
        while (true) {
            long nextTimeSequence = cursor.getEventTimeSequence();
            long nextTime = TimeSequenceUtil.getTimeMillisFromTimeSequence(nextTimeSequence);
            if (!advanceTime(nextTime))
                return;
            // find a (position, endPosition] slice that can be processed at "nextTime"
            while (true) {
                endPosition = source.getPosition();
                cursor = source.next();
                if (cursor == null || cursor.getEventTimeSequence() > nextTimeSequence)
                    break;
            }
            // process slice
            if (shallProcessMessage())
                adapter.processRecordSource(source.newSource(position, endPosition), message);
            if (endPosition == source.getLimit())
                break; // all source processed
            // prepare for next slice at next time
            position = endPosition;
        }
    }

    /**
     * Waits for data in file and reads a block of data into chunk.
     * Wait is aborted if there is a next file to read.
     * @return true if something was read into chunk (ensures chunk != null and chunk.getLength() &gt; 0).
     */
    private boolean waitAndReadChunk() throws IOException, InterruptedException {
        if (chunk == null)
            chunk = FileConstants.CHUNK_POOL.getChunk(this);
        int len;
        boolean loggedWaitingMessage = false;
        while (true) {
            len = dataIn.read(chunk.getBytes(), chunk.getOffset(), chunk.getLength());
            if (len > 0)
                break; // read some bytes!
            if (isClosed())
                return false;
            if (isNextFileAvailable())
                break; // break to log "Reading more data as needed"
            // wait and try again, unless
            if (doNotWait()) {
                stopFlag = true;
                return false;
            }
            // log at most once per wait
            if (!loggedWaitingMessage) {
                log.info("Data file is over. Waiting for more data...");
                loggedWaitingMessage = true;
            }
            // sleep actually
            Thread.sleep(SLEEP_DURATION);
        }
        if (loggedWaitingMessage)
            log.info("Reading more data...");
        if (len <= 0)
            return false;
        chunk.setLength(len, this);
        return true;
    }

    private boolean doNotWait() {
        // do not wait for more files or data to appear
        // .. when we are in single-file mode
        // .. and never wait in cycle mode
        // .. and never with stopTime [QD-363] FileReader shall not wait for more data files when running with "stop" property
        // .. and never with speed.
        return filesFilter == null || params.isCycle() || hasStop || hasSpeed;
    }

    private boolean isNextFileAvailable() {
        if (filesFilter != null) {
            // Check for new files
            if (nextFileId < filesList.length)
                return true;
            filesList = filesFilter.listTimestampedFiles();
            if (filesList.length != 0) {
                nextFileId = 0;
                return true;
            }
            // no new files available. have to wait.
        }
        return false;
    }

    private void createParserOnFirstChunk() {
        // always detected format on the first chunk, unless it was explicitly specified
        FileFormat prevFormat = format;
        format = params.getFormat() != null ? params.getFormat() : FileFormat.detectFormat(chunk.getBytes());
        if (parser == null || format != prevFormat) {
            // create new parser
            parser = createParser(format, scheme == null ? QDFactory.getDefaultScheme() : scheme);
            configureParser();
        } else {
            // reuse old parser (but drop any leftovers from previous file and reset total input position)
            // correct input position is important for human-readable error reports that includes file offset
            input.clear();
        }
        // generate fake describe protocol with STREAM_DATA for bare-bones formats
        if (format.isBareBones()) {
            ProtocolDescriptor desc = ProtocolDescriptor.newSelfProtocolDescriptor("tape");
            desc.addSend(desc.newMessageDescriptor(MessageType.STREAM_DATA));
            processDescribeProtocolMessage(desc);
        }
    }

    private void configureParser() {
        parser.setInput(input); // parse everything from input by default
        parser.setReadEventTimeSequence(timestampsType != TimestampsType.NONE);
        parser.setEventTimeSequence(0);
        parser.readAs(params.getReadAs());
        parser.setStats(stats);
        configureFieldReplacers();
        if (params.isSchemeKnown()) {
            if (!(parser instanceof BinaryFileQTPParser))
                throw new InvalidFormatException("schemeKnown is supported only for binary format");
            ((BinaryFileQTPParser) parser).setSchemeKnown(true);
        }
        if (params.getResyncOn() != null) {
            if (!(parser instanceof BinaryFileQTPParser))
                throw new InvalidFormatException("resyncOn is supported only for binary format");
            ((BinaryFileQTPParser) parser).setResyncOn(params.getResyncOn());
        }
    }

    private void configureFieldReplacers() {
        if (params.getFieldReplacer() == null)
            return;
        FieldReplacersCache cache = FieldReplacersCache.valueOf(scheme != null ? scheme : QDFactory.getDefaultScheme(),
            params.getFieldReplacer());
        parser.setFieldReplacers(cache);
    }

    // Is override by file analysis tool
    protected AbstractQTPParser createParser(FileFormat format, DataScheme scheme) {
        return format.createQTPParser(scheme);
    }

    private void resetParserSessionAndClearInput() {
        if (parser != null) {
            parser.resetSession();
            input.clear();
        }
    }

    /**
     * Tries to open next file (or two files .data and .time) with data.
     * @return false if failed to open data or time file (data file could be open, but time failed)
     */
    private boolean openFiles() {
        // QD-417: detect current time format for each file separately
        currentTimestampsType = timestampsType;
        // Try to open next file
        dataIn = tryOpenFile(false, false);
        if (dataIn == null)
            return false;
        if (!doNotTryToReadTimeFile) {
            // don't even try to open time file when explicitly set to read times from "EventTime"
            // ignore FileNotFound only when file=text|long was not explicitly set
            InputStream in = tryOpenFile(true, timestampsType == null);
            if (in == null) {
                timeIn = null;
                if (timestampsType == null) // in timestamp autodetect mode...
                    return true; // opened successfully without time
                return false; // otherwise -- failed to find a required time file
            }
            currentTimestampsType = TimestampsType.TEXT; // AUTODETECT TIME FORMAT: separate time file detected
            timeIn = new BufferedReader(new InputStreamReader(in));
        }
        return true;
    }

    // extension point for FileReaderHandler
    protected void onConnected() {}

    private InputStream tryOpenFile(boolean openTimeFile, boolean ignoreFileNotFound) {
        String address = dataFileAddress;
        try {
            URL url = FileUtils.addressToURL(dataFileAddress);
            if (openTimeFile) {
                String timeFile = FileUtils.getTimeFilePath(url.getFile(), dataFileExtension, containerExtension);
                url = new URL(url.getProtocol(), url.getHost(), url.getPort(), timeFile);
                address = timeFileAddress = url.toString();
            }
            URLConnection con = URLInputStream.openConnection(url, params.getUser(), params.getPassword());
            // detect actual compression of this file if not explicitly specified or implied from extension
            InputStream in = con.getInputStream();
            StreamCompression compression = this.compression;
            if (compression == null) {
                if (!in.markSupported())
                    in = new BufferedInputStream(in);
                compression = StreamCompression.detectCompressionByHeader(in);
            }
            in = compression.decompress(in);
            log.info("Reading " + (openTimeFile ? "time" : "data") + " from " + LogUtil.hideCredentials(address) +
                (compression == StreamCompression.NONE ? "" : " with " + compression));
            return in;
        } catch (IOException e) {
            if (ignoreFileNotFound && e instanceof FileNotFoundException)
                return null;
            log.error("Failed to open " + LogUtil.hideCredentials(address), e);
            return null;
        }
    }

    /**
     * Tries to close files with data.
     */
    private void closeFiles() {
        FileUtils.tryClose(timeIn, timeFileAddress);
        timeIn = null;
        FileUtils.tryClose(dataIn, dataFileAddress);
        dataIn = null;
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed)
                return;
            closed = true;
        }
        // NOTE: This method can be called from the other thread concurrently with "read"
        // Files are closed in "finally" section of "read" method. Cannot close them concurrently
        QDStats stats = this.stats;
        if (stats != null)
            stats.close();
    }

    public long getDelayActual() {
        return delayActual;
    }

    static class CorruptedFileException extends RuntimeException {
        CorruptedFileException(String message) {
            super(message);
        }
    }
}
