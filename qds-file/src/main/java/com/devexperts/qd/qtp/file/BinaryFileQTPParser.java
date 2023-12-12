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
package com.devexperts.qd.qtp.file;

import com.devexperts.io.BufferedInput;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.qtp.AbstractQTPParser;
import com.devexperts.qd.qtp.BinaryQTPComposer;
import com.devexperts.qd.qtp.BinaryQTPParser;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.QTPConstants;
import com.devexperts.util.SystemProperties;

import java.io.EOFException;
import java.io.IOException;

/**
 * Parses QTP messages in binary format from byte stream supporting additional file-specific features.
 * The input for this parser must be configured with {@link #setInput(BufferedInput)} method
 * immediately after construction.
 *
 * @see AbstractQTPParser
 * @see BinaryQTPComposer
 */
public class BinaryFileQTPParser extends BinaryQTPParser {
    private static final int MAX_RESYNC_MESSAGE_LENGTH =
        SystemProperties.getIntProperty(BinaryFileQTPParser.class, "maxResyncMessageLength",
            QTPConstants.COMPOSER_THRESHOLD + 100);

    private static final Logging log = Logging.getLogging(BinaryFileQTPParser.class);

    // ======================== private instance fields ========================

    private MessageType resyncOn;
    private boolean inSync;
    private int skippedToResync = 0;
    private boolean schemeKnown;

    // ======================== constructor and instance methods ========================

    public BinaryFileQTPParser(DataScheme scheme) {
        super(scheme);
    }

    // ------------------------ configuration methods ------------------------

    /**
     * Sets "resyncOn" message type while enables heuristic recovery when reading broken/partial files.
     */
    public void setResyncOn(MessageType resyncOn) {
        this.resyncOn = resyncOn;
    }

    @Override
    public boolean isSchemeKnown() {
        return schemeKnown;
    }

    /**
     * Sets "schemeKnown" flag which enables parsing with optional record description messages.
     * By default it is set to false - when description are being parsed, it is a QTP protocol error to refer to
     * the record that was not previously described.
     */
    public void setSchemeKnown(boolean schemeKnown) {
        this.schemeKnown = schemeKnown;
    }

    // ------------------------ resync support ------------------------

    @Override
    protected boolean resyncOnParse(BufferedInput in) throws IOException {
        if (inSync || resyncOn == null)
            return true; // already in sync or no need to sync -- nothing more to do
        return resyncImpl(in);
    }

    private boolean resyncImpl(BufferedInput in) throws IOException {
        while (in.hasAvailable()) {
            in.mark();
            boolean ok = false;
            try {
                int length = in.readCompactInt();
                if (length > 0 && length < MAX_RESYNC_MESSAGE_LENGTH)
                    // length heuristically looks Ok, now check message type
                    ok = MessageType.findById(in.readCompactInt()) == resyncOn;
            } catch (EOFException e) {
                // end of know bytes reached. will continue to resync from this point when more more bytes are available
                in.unmark();
                return false; // no sync so far
            }
            in.reset();
            if (ok) { // stream in sync
                if (skippedToResync > 0) {
                    log.info("Skipped " + skippedToResync + " bytes to resync stream on " + resyncOn +
                        " message at most " + MAX_RESYNC_MESSAGE_LENGTH + " bytes long");
                    skippedToResync = 0;
                }
                inSync = true;
                return true;
            }
            // skip one byte and resync again
            in.read();
            skippedToResync++;
        }
        return false; // no more bytes -- failed to sync
    }

    @Override
    protected boolean resyncOnCorrupted(BufferedInput in) throws IOException {
        if (resyncOn == null)
            return false; // no syncing -- handle error
        in.reset();
        // skip one byte and resync again
        in.read();
        skippedToResync++;
        inSync = false;
        return true;
    }
}
