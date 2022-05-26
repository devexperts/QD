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
package com.dxfeed.ipf;

import com.devexperts.io.UncloseableOutputStream;
import com.dxfeed.ipf.impl.InstrumentProfileComposer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes instrument profiles to the stream.
 * Please see <b>Instrument Profile Format</b> documentation for complete description.
 *
 * <p>This writer automatically derives data formats needed to write all meaningful fields.
 */
public class InstrumentProfileWriter {
    /**
     * Creates instrument profile writer.
     */
    public InstrumentProfileWriter() {}

    /**
     * Writes specified instrument profiles into specified file.
     * This method recognizes popular data compression formats "zip" and "gzip" by analysing file name.
     * If file name ends with ".zip" then profiles will be written as a single compressed entry in a "zip" format.
     * If file name ends with ".gz" then profiles will be compressed and written using "gzip" format.
     * In other cases file will be considered uncompressed and profiles will be written as is.
     *
     * @throws IOException  If an I/O error occurs
     */
    public void writeToFile(String file, List<InstrumentProfile> profiles) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            write(out, file, profiles);
        }
    }

    /**
     * Writes specified instrument profiles into specified stream using specified name to select data compression format.
     * This method recognizes popular data compression formats "zip" and "gzip" by analysing file name.
     * If file name ends with ".zip" then profiles will be written as a single compressed entry in a "zip" format.
     * If file name ends with ".gz" then profiles will be compressed and written using "gzip" format.
     * In other cases file will be considered uncompressed and profiles will be written as is.
     *
     * @throws IOException  If an I/O error occurs
     */
    public void write(OutputStream out, String name, List<InstrumentProfile> profiles) throws IOException {
        // NOTE: compression streams (zip and gzip) require explicit call to "close()" method to properly
        // finish writing of compressed file format and to release native Deflater resources.
        // However we shall not close underlying stream here to allow proper nesting of data streams.
        if (name.toLowerCase().endsWith(".zip")) {
            name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1, name.length() - ".zip".length());
            try (ZipOutputStream zip = new ZipOutputStream(new UncloseableOutputStream(out))) {
                zip.putNextEntry(new ZipEntry(name));
                write(zip, name, profiles);
                zip.closeEntry();
            }
            return;
        }
        if (name.toLowerCase().endsWith(".gz")) {
            try (GZIPOutputStream gzip = new GZIPOutputStream(new UncloseableOutputStream(out))) {
                write(gzip, profiles);
            }
            return;
        }
        write(out, profiles);
    }

    /**
     * Writes specified instrument profiles into specified stream with the {@code "##COMPLETE"} end tag at the end.
     *
     * @throws IOException  If an I/O error occurs
     */
    public void write(OutputStream out, List<InstrumentProfile> profiles) throws IOException {
        InstrumentProfileComposer composer = new InstrumentProfileComposer(out);
        composer.compose(profiles, false);
        composer.composeComplete();
    }
}
