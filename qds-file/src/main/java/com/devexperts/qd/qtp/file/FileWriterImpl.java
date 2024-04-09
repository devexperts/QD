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
package com.devexperts.qd.qtp.file;

import com.devexperts.io.BufferedOutput;
import com.devexperts.io.ChunkList;
import com.devexperts.io.ChunkedOutput;
import com.devexperts.io.StreamCompression;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.ng.AbstractRecordProvider;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.qtp.AbstractMessageVisitor;
import com.devexperts.qd.qtp.AbstractQTPComposer;
import com.devexperts.qd.qtp.FileConstants;
import com.devexperts.qd.qtp.HeartbeatPayload;
import com.devexperts.qd.qtp.MessageDescriptor;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.qd.qtp.ProtocolOption;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.qd.util.TimeSequenceUtil;
import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.LogUtil;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.annotation.concurrent.GuardedBy;

/**
 * Writes QTP messages to a tape file.
 * This class is thread-safe. It contains its own synchronization and can be used by multiple threads.
 */
public class FileWriterImpl extends AbstractMessageVisitor implements Closeable {
    private static final Logging log = Logging.getLogging(FileWriterImpl.class);

    private static final boolean NO_HEADER_AND_FOOTER =
        SystemProperties.getBooleanProperty(FileWriterImpl.class, "noHeaderAndFooter", false);
    private static final int TASK_QUEUE_SIZE =
        SystemProperties.getIntProperty(FileWriterImpl.class, "taskQueueSize", 4);

    private static final boolean IS_POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    private static final FileAttribute<?>[] POSIX_FS_FILE_ATTRIBUTES =
        new FileAttribute[] {PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-rw-rw-"))};
    private static final FileAttribute<?>[] OTHER_FS_FILE_ATTRIBUTES = new FileAttribute[0];

    // Parameters
    private final DataScheme scheme;
    private final TimePeriod split;
    private final StreamCompression compression;
    private final FileFormat format;
    private final TimestampsType time;
    private final MessageType saveAs;
    private final long storageTime;
    private final long storageSize;
    private final ProtocolOption.Set optSet;

    // Computed from parameters
    private final String containerExtension;
    private final String dataFileExtension;
    private final boolean storageLimited;
    private final TimestampedFilenameFilter fileFilter; // not-null in split mode

    // Changes from parameters computing and #addSendMessageType method
    private final ProtocolDescriptor protocolDescriptor = ProtocolDescriptor.newSelfProtocolDescriptor("tape");

    // Reusable write threads
    private FlushThread flushThread;
    private ParallelWriter dataWriter;
    private ParallelWriter timeWriter;

    // Streams are non-null only while writing
    private volatile BufferedOutput dataOut;
    private PrintWriter timeOut;

    private final ChunkedOutput output = new ChunkedOutput(FileConstants.CHUNK_POOL); // output for composer
    private AbstractQTPComposer composer;

    private long position;
    private long lastTime;
    private long curTime;
    private long lastIncomingTimeMillis;
    private volatile long nextSplitTime; // == Long.MAX_VALUE if not in split mode

    private String dataFilePath;        // destination data file path
    private String currentDataFilePath; // current data file path
    private String currentTimeFilePath; // current time file path
    private Path tmpDirPath;            // tmp dir path

    private final HeartbeatPayload heartbeatPayload = new HeartbeatPayload();
    private final TimestampedSink timestampedSink = new TimestampedSink();
    private final TimestampedProvider timestampedProvider = new TimestampedProvider();

    /**
     * Creates and opens file writer with parameters specified in the specified string of
     * {@code dataFilePathWithParams}.
     *
     * @throws InvalidFormatException if some parameters are invalid.
     */
    public static FileWriterImpl open(String dataFilePathWithParams, DataScheme scheme) {
        FileWriterParams params = new FileWriterParams.Default();
        String dataFilePath = parseParameters(dataFilePathWithParams, params);
        return new FileWriterImpl(dataFilePath, scheme, params).open();
    }

    /**
     * Utility method to parse additional parameters after
     * file path and returns file path itself (already without parameters).
     *
     * @param dataFilePathWithParams String with file path of tape data, which can be followed by list of parameters
     *                 <tt>[key1=value1, key2=value2, ..., keyN=valueN]</tt>.
     * @param params   params to which parameters from filename are set via reflection.
     * @return file path without parameters.
     * @throws InvalidFormatException if couldn't parse parameters.
     */
    private static String parseParameters(String dataFilePathWithParams, FileWriterParams params) {
        List<String> props = new ArrayList<>();
        dataFilePathWithParams = QDConfig.parseProperties(dataFilePathWithParams, props);
        QDConfig.setProperties(params, props);
        return dataFilePathWithParams;
    }

    /**
     * Creates file writer with parameters specified in a separate object.
     * It must be {@link #open()} before use.
     *
     * @throws InvalidFormatException if some parameters are invalid.
     */
    public FileWriterImpl(String dataFilePath, DataScheme scheme, FileWriterParams params) {
        // Set parameters
        this.scheme = scheme;
        this.dataFilePath = dataFilePath;
        if (params.getTmpDir() != null) {
            String trimTmpPath = params.getTmpDir().trim();
            if (!trimTmpPath.isEmpty()) {
                this.tmpDirPath = Paths.get(trimTmpPath);
            }
        }
        this.split = params.getSplit();
        this.format = fromNullable(params.getFormat(), FileFormat.BINARY);
        this.time = fromNullable(params.getTime(), format.getTimestampsType());
        if (time != TimestampsType.NONE)
            protocolDescriptor.setProperty(ProtocolDescriptor.TIME_PROPERTY, time.toString().toLowerCase(Locale.US));
        this.saveAs = params.getSaveAs();
        this.storageSize = params.getStorageSize();
        this.storageTime = params.getStorageTime().getTime();
        this.optSet = fromNullable(ProtocolOption.parseProtocolOptions(params.getOpt()), ProtocolOption.SUPPORTED_SET);
        this.protocolDescriptor.setProperty(ProtocolDescriptor.OPT_PROPERTY, optSet.isEmpty() ? null : params.getOpt());
        MessageType saveAs = params.getSaveAs();
        if (saveAs != null)
            this.protocolDescriptor.addSend(protocolDescriptor.newMessageDescriptor(saveAs));
        // Determine if storage is limited
        storageLimited =
            (storageTime != FileWriterParams.UNLIMITED_TIME) || (storageSize != FileWriterParams.UNLIMITED_SIZE);
        if (storageLimited && split == null)
            throw new InvalidFormatException("Storage time or size can be limited only in split mode");
        // Detect compression type if needed (no compression by default)
        compression =
            fromNullable(params.getCompression(), StreamCompression.detectCompressionByExtension(dataFilePath));
        // Retrieve data file suffix
        if (dataFilePath.endsWith(compression.getExtension())) {
            containerExtension = compression.getExtension();
        } else {
            containerExtension = "";
        }
        dataFileExtension = FileUtils.retrieveExtension(compression.stripExtension(dataFilePath));
        if (time.isUsingTimeFile() && dataFileExtension.equals(FileUtils.TIME_FILE_EXTENSION))
            throw new InvalidFormatException("File extension must differ from \".time\" when timestamps are written");
        // Create fileFilter if split mode is enabled.
        if (split != null) {
            fileFilter = TimestampedFilenameFilter.create(new File(dataFilePath), containerExtension);
            if (fileFilter == null) {
                throw new InvalidFormatException("There must be timestamp marker " +
                    "'" + FileUtils.TIMESTAMP_MARKER + "' in file name when using \"split\" option");
            }
            if (time.isUsingTimeFile()) {
                fileFilter.requireTimeFile();
            }
            log.info("Create FileWriter which writes tape to " + LogUtil.hideCredentials(dataFilePath) +
                (time.isUsingTimeFile() ? "/" + FileUtils.TIME_FILE_EXTENSION : "") +
                ", where \"" + FileUtils.TIMESTAMP_MARKER + "\" is replaced by current date and time. " +
                "Splitting files with interval " + split +
                (Objects.nonNull(params.getTmpDir()) ? ". Use tmp dir: " +  params.getTmpDir() : ""));
        } else {
            nextSplitTime = Long.MAX_VALUE;
            fileFilter = null;
            if (new File(dataFilePath).getName().contains(FileUtils.TIMESTAMP_MARKER)) {
                throw new InvalidFormatException("\"split\" option must be used when file named contains " +
                    "timestamp marker '" + FileUtils.TIMESTAMP_MARKER + "'");
            }
        }
    }

    /**
     * Returns default value if specified value is {@code null}.
     */
    private static <T> T fromNullable(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    /**
     * Adds description of sent message type to protocol descriptor.
     * This method does nothing if {@link FileWriterParams#getSaveAs() saveAs} is set.
     */
    public synchronized void addSendMessageType(MessageType messageType) {
        if (saveAs != null)
            return;
        protocolDescriptor.addSend(protocolDescriptor.newMessageDescriptor(messageType));
    }

    @Override
    public synchronized void visitDescribeProtocol(ProtocolDescriptor descriptor) {
        if (saveAs != null)
            return;
        for (MessageDescriptor md : descriptor.getSendMessages())
            protocolDescriptor.addSend(protocolDescriptor.newMessageDescriptor(md.getMessageType()));
    }

    public synchronized FileWriterImpl open() throws InvalidFormatException {
        // Just in case -- close everything we've worked on before
        close();
        // Create composer for the specified data format
        composer = format.createQTPComposer(scheme);
        composer.setOutput(output);
        composer.setWriteEventTimeSequence(time == TimestampsType.FIELD);
        composer.setWriteHeartbeat(time == TimestampsType.MESSAGE);
        composer.setOptSet(optSet);
        // Create and start periodic flushing thread
        flushThread = new FlushThread(LogUtil.hideCredentials(currentDataFilePath));
        flushThread.start();
        // user composer's buffer size
        dataWriter = new ParallelWriter("Write-Data-" + LogUtil.hideCredentials(currentDataFilePath), TASK_QUEUE_SIZE);
        dataWriter.start();
        if (time.isUsingTimeFile()) {
            timeWriter =
                new ParallelWriter("Write-Time-" + LogUtil.hideCredentials(currentDataFilePath), TASK_QUEUE_SIZE);
            timeWriter.start();
        }
        return this;
    }

    private String getTimeFilePath(String dataFilePath) {
        return FileUtils.getTimeFilePath(dataFilePath, dataFileExtension, containerExtension);
    }

    private boolean needNewSplitFile(long curTime) {
        return curTime >= nextSplitTime;
    }

    private void reopenFilesIfNeeded() throws IOException {
        if (dataOut == null) { // Open first or only file
            if (split != null) {
                // Open first file in a splitted tape
                nextSplitTime = (curTime / split.getTime() + 1) * split.getTime();
                reopenFiles(fileFilter.getPathForTime(curTime));
            } else {
                // Open the only file (no splitted tape)
                reopenFiles(dataFilePath);
            }
        } else if (needNewSplitFile(curTime)) { // Open next split file in a splitted tape
            nextSplitTime = (curTime / split.getTime() + 1) * split.getTime();
            reopenFiles(fileFilter.getPathForTime(nextSplitTime - split.getTime()));
        }
    }

    /**
     * It is synchronized to make sure that flush thread does not concurrently close current file
     * while writeData is in progress.
     */
    private synchronized void reopenFiles(String dataFilePath) throws IOException {
        closeCurrentFiles(nextSplitTime); // close all previously open files
        position = 0;
        lastTime = 0; // have not written timestamp to the new file yet

        Runnable dataCloseHandler;
        this.dataFilePath = dataFilePath;
        if (tmpDirPath != null) {
            FileUtils.checkOrCreateDirectory(tmpDirPath);
            currentDataFilePath = createTmpFile(tmpDirPath, dataFilePath);
            dataCloseHandler = getCloseHandler(currentDataFilePath, dataFilePath, storageLimited);
        } else {
            currentDataFilePath = dataFilePath;
            dataCloseHandler = storageLimited ? this::deleteOldFiles : null;
        }

        log.info("Writing tape data to " + LogUtil.hideCredentials(currentDataFilePath) +
            (compression == StreamCompression.NONE ? "" : " with " + compression));

        dataOut = reuseOutputStream(
            currentDataFilePath, dataWriter, Paths.get(dataFilePath).getFileName().toString(), dataCloseHandler);
        if (time.isUsingTimeFile()) {
            // timeCloseHandler doesn't have to delete files because of dataCloseHandler will do it at the same time
            // It is acceptable that in the worst case, not all suitable files will be deleted at this iteration
            Runnable timeCloseHandler;
            String timeFilePath = getTimeFilePath(dataFilePath);
            if (tmpDirPath != null) {
                currentTimeFilePath = createTmpFile(tmpDirPath, timeFilePath);
                timeCloseHandler = getCloseHandler(currentTimeFilePath, timeFilePath, false);
            } else {
                currentTimeFilePath = timeFilePath;
                timeCloseHandler = null;
            }

            log.info("Writing tape time to " + LogUtil.hideCredentials(currentTimeFilePath) +
                (compression == StreamCompression.NONE ? "" : " with " + compression));

            timeOut = new PrintWriter(reuseOutputStream(
                currentTimeFilePath, timeWriter, Paths.get(timeFilePath).getFileName().toString(), timeCloseHandler));
        } else {
            timeOut = null;
        }
        writeHeader();
    }

    private Runnable getCloseHandler(String sourcePath, String destinationPath, boolean deleteOldFiles) {
        return () -> {
            moveFile(sourcePath, destinationPath);
            if (deleteOldFiles) {
                deleteOldFiles();
            }
        };
    }

    private String createTmpFile(Path dir, String originFile) throws IOException {
        String filName = Paths.get(originFile).getFileName().toString();
        FileAttribute<?>[] fileAttributes = IS_POSIX && (dir.getFileSystem() == FileSystems.getDefault()) ?
            POSIX_FS_FILE_ATTRIBUTES : OTHER_FS_FILE_ATTRIBUTES;
        Path path = Files.createTempFile(dir, filName, null, fileAttributes);
        return path.toString();
    }

    private BufferedOutput reuseOutputStream(
        final String path, ParallelWriter writeThread, String compressedName, Runnable closeHandler)
    {
        return writeThread.open(() -> {
            Path currentFilePath = Paths.get(path);
            Path parentDirectory = currentFilePath.getParent();
            if (parentDirectory != null) {
                FileUtils.checkOrCreateDirectory(parentDirectory);
            }
            return compression.compress(
                Files.newOutputStream(currentFilePath), compression.stripExtension(compressedName));
        }, closeHandler);
    }

    private void deleteOldFiles() {
        TimestampedFile[] files = fileFilter.listTimestampedFiles();
        long allowedTime = storageTime ==
            FileWriterParams.UNLIMITED_TIME ? Long.MIN_VALUE : System.currentTimeMillis() - storageTime;
        long totalSize = 0;
        int k = files.length - 1;
        while (k > 0) {
            if (files[k].time < allowedTime)
                break;
            File dataFile = files[k].file;
            totalSize += dataFile.length();
            if (time.isUsingTimeFile())
                totalSize += new File(getTimeFilePath(dataFile.getPath())).length();
            if (totalSize > storageSize)
                break;
            k--;
        }
        for (int i = 0; i < k; i++) {
            File dataFile = files[i].file;
            // delete time file first to make sure that we don't leave only time file on disk,
            // which cannot be deleted w/o data file on the next call to deleteOldFiles
            if (time.isUsingTimeFile())
                tryDelete(new File(getTimeFilePath(dataFile.getPath())));
            tryDelete(dataFile);
        }
    }

    private void tryDelete(File file) {
        if (!file.exists())
            return;
        log.info("Deleting file " + LogUtil.hideCredentials(file));
        if (!file.delete())
            log.warn("Failed to delete " + LogUtil.hideCredentials(file));
    }

    @Override
    public synchronized void close() {
        // need to ignore close operation from open
        boolean initialized = flushThread != null;
        try {
            closeCurrentFiles(nextSplitTime);
        } finally {
            FileUtils.tryClose(flushThread, null);
            flushThread = null;
            FileUtils.tryClose(dataWriter, currentDataFilePath);
            dataWriter = null;
            FileUtils.tryClose(timeWriter, currentTimeFilePath);
            timeWriter = null;

            if (initialized && storageLimited) {
                deleteOldFiles();
            }
        }
    }

    /**
     * It can be invoked from FlushThread when data is not coming too long and from close when done.
     */
    private synchronized void closeCurrentFiles(long expectedNextSplitTime) {
        if (dataOut == null)
            return; // already closed
        //check to ensure that expected file will be closed
        //done due to thread-safety reasons, FlushThread may try to close 'new' files
        if (expectedNextSplitTime != nextSplitTime)
            return;
        try {
            writeFooter();
        } catch (IOException e) {
            log.error("Failed to write tape file", e);
        } finally {
            FileUtils.tryClose(dataOut, currentDataFilePath);
            dataOut = null;
            FileUtils.tryClose(timeOut, currentTimeFilePath);
            timeOut = null;
        }
    }

    private void moveFile(String sourceFile, String destinationFile) {
        if (sourceFile == null || destinationFile == null) {
            return; // no file for move
        }
        Path sourcePath = Paths.get(sourceFile);
        Path destinationPath = Paths.get(destinationFile);
        if (sourcePath.equals(destinationPath)) {
            return; // no tmp dir
        }
        log.info("Move source file: " + sourcePath + " to destination file: " + destinationPath);
        try {
            Path parentFolder = destinationPath.getParent();
            if (parentFolder != null) {
                FileUtils.checkOrCreateDirectory(parentFolder);
            }
            FileUtils.tryAtomicFileMove(sourcePath, destinationPath);
        } catch (IOException e) {
            log.error("Failed to create directory or to move file", e);
        }
    }

    @GuardedBy("this")
    private void writeHeader() throws IOException {
        if (format.isBareBones() || NO_HEADER_AND_FOOTER)
            return;
        composer.composeDescribeProtocol(protocolDescriptor);
        writeDataWithoutTimestamp();
    }

    @GuardedBy("this")
    private void writeFooter() throws IOException {
        if (format.isBareBones() || NO_HEADER_AND_FOOTER)
            return;
        composer.composeEmptyHeartbeat();
        writeDataWithoutTimestamp();
    }

    /**
     * It is used for {@link #writeHeader} and {@link #writeFooter}
     * only -- messages that don't need to be timestamped.
     */
    @GuardedBy("this")
    private void writeDataWithoutTimestamp() throws IOException {
        ChunkList chunks = output.getOutput(this);
        if (chunks != null) {
            position += chunks.getTotalLength();
            dataOut.writeAllFromChunkList(chunks, this);
        }
    }

    /**
     * Takes care of writing data to file and writing ".time" file (if needed)
     * It copies chunks from composer's output to dataOut.
     * It is synchronized to make sure that flush thread does not concurrently close current file
     * while writeData is in progress.
     */
    private synchronized void writeData() {
        // take chunks that were composed by composer so far
        // NOTE: we take chunks first, because "reopenFilesIfNeed" will need to reuse composer & output
        // to write file header
        ChunkList chunks = output.getOutput(this);
        if (chunks == null)
            return; // nothing to do
        try {
            // open new set of files if needed (and write file header if needed)
            reopenFilesIfNeeded();
            // write time to separate ".time" file if needed
            if (time.isUsingTimeFile() && curTime != lastTime) {
                // submit timestamp to parallel writer thread via "timeOut"
                new TimestampedPosition(curTime, position).writeTo(timeOut, time);
                lastTime = curTime;
            }
            // increase position
            position += chunks.getTotalLength();
            // submit chunks to parallel writer thread via "dataOut"
            dataOut.writeAllFromChunkList(chunks, this);
        } catch (IOException e) {
            log.error("Failed to write tape file", e);
        }
    }

    /**
     * It is invoked periodically from FlushThread to avoid data loss.
     * This method does not actually blocks in IO wait, but flushes data to {@link ParallelWriter}.
     */
    synchronized void flush() {
        if (dataOut == null)
            return; // file is not open yet
        try {
            dataOut.flush();
            if (timeOut != null)
                timeOut.flush();
        } catch (IOException e) {
            log.error("Failed to write tape file", e);
        }
    }

    @Override
    public synchronized void visitHeartbeat(HeartbeatPayload heartbeatPayload) {
        if (heartbeatPayload.hasTimeMillis())
            lastIncomingTimeMillis = heartbeatPayload.getTimeMillis();
        // deliver curTime to to composer via heartbeat message.
        // it will take care of embedding them in the the way it is configured to
        if (time.isUsingEmbeddedTime()) {
            this.heartbeatPayload.updateFrom(heartbeatPayload);
            updateCurTime();
            if (!this.heartbeatPayload.isEmpty())
                composer.visitHeartbeat(this.heartbeatPayload);
        }
    }

    @Override
    public synchronized boolean visitData(DataProvider provider, MessageType message) {
        MessageType saveMessage = saveAs != null && saveAs.isData() ? saveAs : message;
        if (isTimestampedDataProvider(provider))
            return visitTimestampedData((RecordProvider) provider, saveMessage);
        else
            return visitRegularData(provider, saveMessage);
    }

    private boolean isTimestampedDataProvider(DataProvider provider) {
        return (provider instanceof RecordProvider) && ((RecordProvider) provider).getMode().hasEventTimeSequence();
    }

    private void updateCurTime() {
        curTime = lastIncomingTimeMillis;
        if (curTime == 0)
            curTime = System.currentTimeMillis();
        // special case to suspend message processing if we need new file or a separate timestamp message
        if (needNewSplitFile(curTime))
            composer.resetSession();
        if (time.isUsingEmbeddedTime() && curTime != lastTime) {
            // deliver curTime to to composer via heartbeat message.
            // it will take care of embedding them in the the way it is configured to
            // will also deliver previously stored data in heartbeat payload
            heartbeatPayload.setTimeMillis(curTime);
            composer.visitHeartbeat(heartbeatPayload);
            heartbeatPayload.clear();
            lastTime = curTime;
        }
    }

    private boolean visitTimestampedData(RecordProvider provider, MessageType saveMessage) {
        // defer updateCurTime until the first record is appended to TimestampedSink
        timestampedProvider.provider = provider;
        timestampedSink.saveMessage = saveMessage;
        timestampedSink.first = true;
        boolean result = composer.visitData(timestampedProvider, saveMessage);
        writeData();
        return result;
    }

    private boolean visitRegularData(DataProvider provider, MessageType saveMessage) {
        updateCurTime();
        boolean result = composer.visitData(provider, saveMessage);
        writeData();
        return result;
    }

    @Override
    public synchronized boolean visitSubscription(SubscriptionProvider provider, MessageType message) {
        updateCurTime();
        MessageType saveMessage = saveAs != null && saveAs.isSubscription() ? saveAs : message;
        boolean result = composer.visitSubscription(provider, saveMessage);
        writeData();
        return result;
    }

    @Override
    public synchronized String toString() {
        return "tape data file " + LogUtil.hideCredentials(dataFilePath);
    }

    // it is used from visitTimestampedData
    private class TimestampedSink extends AbstractRecordSink {
        MessageType saveMessage;
        RecordSink sink;
        private long timeSequenceCache;
        private long timeMillisCache;
        boolean first;

        @Override
        public boolean hasCapacity() {
            return sink.hasCapacity();
        }

        @Override
        public void append(RecordCursor cursor) {
            // convert time sequence to time millis
            long curEventTimeSequence = cursor.getEventTimeSequence();
            if (timeSequenceCache != curEventTimeSequence) {
                timeSequenceCache = curEventTimeSequence;
                timeMillisCache = TimeSequenceUtil.getTimeMillisFromTimeSequence(curEventTimeSequence);
            }
            if (curEventTimeSequence != 0 && lastIncomingTimeMillis != timeMillisCache) {
                lastIncomingTimeMillis = timeMillisCache;
                // suspend current message if new split file or is needed for time
                if (needNewSplitFile(lastIncomingTimeMillis) || time.isSlipMessageOnTime() && curTime != lastIncomingTimeMillis) {
                    composer.endMessage();
                    // TODO Oops, we are making I/O under QD lock.
                    writeData();
                    updateCurTime();
                    composer.beginMessage(saveMessage);
                }
            } else if (first) {
                // always update cur time before first record anyway (!)
                composer.endMessage(); // rollback message header (will collapse empty message)
                updateCurTime();
                composer.beginMessage(saveMessage);
            }
            first = false;
            sink.append(cursor);
        }
    }

    // it is used from visitTimestampedData
    private class TimestampedProvider extends AbstractRecordProvider {
        RecordProvider provider;

        @Override
        public RecordMode getMode() {
            return provider.getMode();
        }

        @Override
        public boolean retrieve(RecordSink sink) {
            timestampedSink.sink = sink;
            return provider.retrieve(timestampedSink);
        }
    }

    private class FlushThread extends Thread implements Closeable {
        private volatile boolean closed;

        FlushThread(String dataFilePath) {
            super("FileWriter-Flush-" + dataFilePath);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            interrupt();
        }

        @Override
        public void run() {
            try {
                long lastNextSplitTime = Long.MAX_VALUE;
                int rounds = 0;
                while (!closed) {
                    flush();
                    if (split != null && dataOut != null) {
                        // Check that new file was opened.
                        long curNextSplitTime = nextSplitTime; // volatile read
                        if (lastNextSplitTime != curNextSplitTime) {
                            // Reset rounds to zero.
                            lastNextSplitTime = curNextSplitTime;
                            rounds = 0;
                        } else {
                            rounds++;
                        }
                        // Check that file should be closed by timeout and close if needed.
                        if (rounds * FileConstants.MAX_BUFFER_TIME >= FileConstants.MAX_OPEN_FACTOR * split.getTime()) {
                            closeCurrentFiles(curNextSplitTime);
                        }
                    }
                    Thread.sleep(FileConstants.MAX_BUFFER_TIME);
                }
            } catch (InterruptedException e) {
                // Done, quit.
            }
        }
    }
}
