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
package com.devexperts.io;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Writes data to the stream using Comma-Separated Values (CSV) format.
 * See <a href="http://www.rfc-editor.org/rfc/rfc4180.txt">RFC 4180</a> for CSV format specification.
 * <p>
 * This writer supports records with arbitrary (variable) number of fields, multiline fields,
 * custom separator and quote characters. It uses <b>CRLF</b> sequence to separate records.
 * <p>
 * This writer does not provide buffering of any sort and does not perform encoding.
 * The correct way to efficiently write CSV file with UTF-8 encoding is as follows:
 * <pre>
 * CSVWriter writer = new CSVWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)));
 * writer.writeRecord(header);
 * writer.writeAll(records);
 * writer.close();
 * </pre>
 */
public class CSVWriter implements Closeable, Flushable{

    private static final char CR = '\r';
    private static final char LF = '\n';
    private static final char[] CRLF = {CR, LF};

    private final Writer writer;
    private final char separator;
    private final char quote;

    private boolean needCRLF;
    private boolean insideRecord;
    private int lineNumber = 1;
    private int recordNumber = 1;

    private char[] quoteBuf; // Used for quoting fields; lazy initialized.

    /**
     * Creates new CSVWriter with default separator and quote characters.
     *
     * @throws NullPointerException if writer is null
     */
    public CSVWriter(Writer writer) {
        this(writer, ',', '"');
    }

    /**
     * Creates new CSVWriter with specified separator and quote characters.
     *
     * @throws NullPointerException if writer is null
     * @throws IllegalArgumentException if separator or quote characters are invalid
     */
    public CSVWriter(Writer writer, char separator, char quote) {
        if (writer == null)
            throw new NullPointerException("writer is null");
        if (separator == CR || separator == LF)
            throw new IllegalArgumentException("separator is CR or LF");
        if (quote == CR || quote == LF || quote == separator)
            throw new IllegalArgumentException("quote is CR, LF or same as separator");
        this.writer = writer;
        this.separator = separator;
        this.quote = quote;
    }

    /**
     * Returns current line number.
     * Line numeration starts with 1 and counts new lines within record fields.
     * Both <b>CR</b> and <b>LF</b> are counted as new lines, although <b>CRLF</b> sequence is counted only once.
     * Line number points to new line after completion of the current record.
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Returns current record number.
     * Record numeration starts with 1 and does not count new lines within record fields.
     * Record number points to new record after completion of the current record.
     */
    public int getRecordNumber() {
        return recordNumber;
    }

    /**
     * Writes specified field to the end of current record.
     * Empty and <b>null</b> strings are written as empty fields.
     * This method does not advance to the next record - it only adds field to the current record.
     *
     * @throws IOException  If an I/O error occurs
     */
    public void writeField(String field) throws IOException {
        if (needCRLF) {
            writer.write(CRLF);
            needCRLF = false;
            lineNumber++;
        }
        if (insideRecord)
            writer.write(separator);
        insideRecord = true;
        if (field == null || field.isEmpty())
            return;
        if (field.indexOf(separator) < 0 && field.indexOf(quote) < 0 && field.indexOf(CR) < 0 && field.indexOf(LF) < 0) {
            writer.write(field);
            return;
        }
        int capacity = field.length() * 2 + 2;
        char[] buf = capacity > 512 ? new char[capacity] : quoteBuf != null ? quoteBuf : (quoteBuf = new char[512]);
        int n = 0;
        buf[n++] = quote;
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            // below: buf[n-1] exists as buf holds at least quote char
            if (c == CR || c == LF && buf[n - 1] != CR) // count CRLF only once
                lineNumber++;
            if (c == quote)
                buf[n++] = quote;
            buf[n++] = c;
        }
        buf[n++] = quote;
        writer.write(buf, 0, n);
    }

    /**
     * Writes specified record and advances to the next record upon completion.
     * Empty and <b>null</b> arrays are normally prohibited because records must
     * contain at least one field, but they can be used to complete current record.
     * If there is incomplete record (written by {@link #writeField} method)
     * then specified fields will be added to the end of current record,
     * the record will be completed and writer will advance to the next record.
     *
     * @throws IllegalArgumentException if attempt to write record without fields was made
     * @throws IOException  If an I/O error occurs
     */
    public void writeRecord(String[] record) throws IOException {
        if (record != null)
            for (String field : record)
                writeField(field);
        if (!insideRecord)
            throw new IllegalArgumentException("records without fields are not allowed");
        needCRLF = true;
        insideRecord = false;
        recordNumber++;
    }

    /**
     * Writes specified records to the output. Does nothing if specified list is empty.
     * Empty and <b>null</b> arrays are normally prohibited because records must
     * contain at least one field, but they can be used to complete current record.
     * If there is incomplete record (written by {@link #writeField} method)
     * then fields from first specified record will be added to the end of current record,
     * the record will be completed and writer will advance to the next record.
     *
     * @throws IllegalArgumentException if attempt to write record without fields was made
     * @throws IOException  If an I/O error occurs
     */
    public void writeAll(List<String[]> records) throws IOException {
        for (String[] record : records)
            writeRecord(record);
    }

    /**
     * Flushes the stream.
     *
     * @throws IOException  If an I/O error occurs
     */
    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    /**
     * Closes the stream.
     *
     * @throws IOException  If an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        writer.close();
    }
}
