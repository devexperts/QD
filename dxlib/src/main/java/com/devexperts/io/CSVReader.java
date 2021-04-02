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

import com.devexperts.util.StringCache;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads data from the stream using Comma-Separated Values (CSV) format.
 * See <a href="http://www.rfc-editor.org/rfc/rfc4180.txt">RFC 4180</a> for CSV format specification.
 * <p>
 * This reader supports records with arbitrary (variable) number of fields, multiline fields,
 * custom separator and quote characters. It accepts <b>CR</b>, <b>LF</b> and <b>CRLF</b> sequence
 * as record separators.
 * <p>
 * This reader provides its own buffering but does not perform decoding.
 * The correct way to efficiently read CSV file with UTF-8 encoding is as follows:
 * <pre>
 * CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
 * String[] header = reader.readRecord();
 * List&lt;String[]&gt; records = reader.readAll();
 * reader.close();
 * </pre>
 */
public class CSVReader implements Closeable {

    private static final char CR = '\r';
    private static final char LF = '\n';

    private static final int INITIAL_CAPACITY = 8192;
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 100;

    private final Reader reader;
    private final char separator;
    private final char quote;

    private final StringCache strings = new StringCache(); // LRU cache to reduce memory footprint and garbage.

    private char[] buf = new char[INITIAL_CAPACITY];
    private int position;
    private int limit;
    private boolean eol;
    private boolean eof;
    private int lineNumber = 1;
    private int recordNumber = 1;

    /**
     * Creates new CSVReader with default separator and quote characters.
     *
     * @throws NullPointerException if reader is null
     */
    public CSVReader(Reader reader) {
        this(reader, ',', '"');
    }

    /**
     * Creates new CSVReader with specified separator and quote characters.
     *
     * @throws NullPointerException if reader is null
     * @throws IllegalArgumentException if separator or quote characters are invalid
     */
    public CSVReader(Reader reader, char separator, char quote) {
        if (reader == null)
            throw new NullPointerException("reader is null");
        if (separator == CR || separator == LF)
            throw new IllegalArgumentException("separator is CR or LF");
        if (quote == CR || quote == LF || quote == separator)
            throw new IllegalArgumentException("quote is CR, LF or same as separator");
        this.reader = reader;
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
     * Returns <b>true</b> if record has ended.
     */
    public boolean isRecordEnded() {
        return eol || eof;
    }

    /**
     * Reads and returns a single field of the current record or <b>null</b> if record has ended.
     * Returns empty string if field is empty.
     * <p>
     * This method does not advance to the next record - it keeps to return <b>null</b>.
     *
     * @throws CSVFormatException if input stream does not conform to the CSV format
     * @throws IOException  If an I/O error occurs
     */
    public String readField() throws IOException {
        return readField("");
    }

    /**
     * Reads and returns a single field of the current record or <b>null</b> if record has ended.
     * Returns empty string if field is empty.
     * Returns specified <b>expectedFieldValue</b> string if it is equal to actual field value.
     * <p>
     * This method does not advance to the next record - it keeps to return <b>null</b>.
     *
     * @param expectedFieldValue expected field value
     * @throws NullPointerException if expectedFieldValue is null
     * @throws CSVFormatException if input stream does not conform to the CSV format
     * @throws IOException  If an I/O error occurs
     */
    public String readField(String expectedFieldValue) throws IOException {
        if (eol || eof)
            return null;
        int pos = position;
        int firstLine = lineNumber;
        int quoteCount = 0;
        while (true) {
            if (pos >= limit) {
                pos -= position;
                read();
                pos += position;
                if (pos >= limit)
                    return finishEof(pos, firstLine, quoteCount, expectedFieldValue);
            }
            char c = buf[pos];
            if (c == quote) {
                if (quoteCount == 0 && pos > position)
                    throw fail("unquoted field has quote character", firstLine);
                quoteCount++;
                pos++;
                continue;
            }
            if (c == separator) {
                if ((quoteCount & 1) == 0)
                    return good(pos, quoteCount, 1, expectedFieldValue);
            } else if (c == CR) {
                if ((quoteCount & 1) == 0) {
                    eol = true;
                    return good(pos, quoteCount, 0, expectedFieldValue);
                }
                lineNumber++;
            } else if (c == LF) {
                if ((quoteCount & 1) == 0) {
                    eol = true;
                    return good(pos, quoteCount, 0, expectedFieldValue);
                }
                // NOTE: here quoteCount != 0, thus at least one quote char exists, thus buf[pos - 1] exists
                if (buf[pos - 1] != CR) // count CRLF only once
                    lineNumber++;
            }
            if (quoteCount >= 2 && (quoteCount & 1) == 0)
                throw fail("quoted field has unpaired quote character", firstLine);
            pos++;
        }
    }

    private String finishEof(int pos, int firstLine, int quoteCount, String expectedFieldValue) throws CSVFormatException {
        if ((quoteCount & 1) != 0)
            throw fail("quoted field does not have terminating quote character", firstLine);
        eol = true;
        eof = true;
        return good(pos, quoteCount, 0, expectedFieldValue);
    }

    private String good(int fieldEnd, int quoteCount, int separatorSize, String expectedFieldValue) {
        // NOTE: this method not only unquotes field, but it also updates state machine
        int quoteSize = (-quoteCount) >>> 31; // 0 if unquoted, 1 if quoted
        int pos = position + quoteSize;
        int end = fieldEnd - quoteSize;
        position = fieldEnd + separatorSize;
        if (quoteCount > 2)
            end = unquote(pos, end);
        if (expectedFieldValue.length() == end - pos && contentEquals(expectedFieldValue, pos))
            return expectedFieldValue;
        return strings.get(buf, pos, end - pos);
    }

    private int unquote(int pos, int end) {
        int n = pos;
        while (n < end && buf[n] != quote)
            n++;
        for (int i = n; i < end; i++)
            if ((buf[n++] = buf[i]) == quote)
                i++;
        return n;
    }

    private boolean contentEquals(String expectedFieldValue, int pos) {
        for (int i = expectedFieldValue.length(); --i >= 0;) // compare from the end as it is more likely to be different
            if (expectedFieldValue.charAt(i) != buf[pos + i])
                return false;
        return true;
    }

    private CSVFormatException fail(String message, int firstLine) {
        // NOTE: this method not only formats error, but it also updates state machine
        if (firstLine == lineNumber)
            message = message + " (line " + firstLine + ")";
        else
            message = message + " (lines from " + firstLine + " to " + lineNumber + ")";
        lineNumber = firstLine;
        return new CSVFormatException(message);
    }

    private void read() throws IOException {
        if (limit - position > buf.length >> 1 && buf.length < MAX_ARRAY_SIZE)
            growAndMove();
        else if (position > buf.length - limit)
            move();
        if (limit >= buf.length)
            throw new IllegalStateException("field is too long");
        int n;
        do {
            n = reader.read(buf, limit, buf.length - limit);
        } while (n == 0);
        if (n > 0)
            limit += n;
    }

    private void growAndMove() {
        char[] tmp = new char[(int) Math.min((long) buf.length * 2L, MAX_ARRAY_SIZE)];
        System.arraycopy(buf, position, tmp, 0, limit - position);
        limit -= position;
        position = 0;
        buf = tmp;
    }

    private void move() {
        System.arraycopy(buf, position, buf, 0, limit - position);
        limit -= position;
        position = 0;
    }

    /**
     * Reads and returns a remaining fields of the current record or <b>null</b> if stream has ended.
     * Returns empty array (length 0) if all record fields were already read by {@link #readField} method.
     * Returns array of length 1 with single empty string if record is empty (empty line).
     * Returns empty strings for those fields that are empty.
     * <p>
     * This method advances to the next record upon completion.
     *
     * @throws CSVFormatException if input stream does not conform to the CSV format
     * @throws IOException  If an I/O error occurs
     */
    public String[] readRecord() throws IOException {
        List<String> fields = new ArrayList<>();
        return readRecord(fields) < 0 ? null : fields.toArray(new String[fields.size()]);
    }

    /**
     * Reads and returns the number of remaining fields of the current record or <b>-1</b> if stream has ended.
     * Returns <b>0</b> if all record fields were already read by {@link #readField} method.
     * Returns <b>1</b> (and a single empty string in <b>fields</b> list) if record is empty (empty line).
     * Returns empty strings for those fields that are empty.
     * <p>
     * This method uses specified <b>fields</b> list both as a source of expected fields values and
     * as a destination for actual field values. Specifically, when reading <b>n<sup>th</sup></b> field
     * it uses <b>n<sup>th</sup></b> string from the list as expected field value and stores actual
     * field value to the <b>n<sup>th</sup></b> position in the list. This method grows specified field list
     * as needed but it never shrinks it, thus the list keeps old strings beyond actual returned record size.
     * <p>
     * This method advances to the next record upon completion.
     *
     * @param fields list of expected field values prior invocation and list of actual field values afterwards
     * @return number of actual fields in the record or <b>-1</b> if stream has ended
     * @throws CSVFormatException if input stream does not conform to the CSV format
     * @throws IOException  If an I/O error occurs
     */
    public int readRecord(List<String> fields) throws IOException {
        int n = 0;
        while (true) {
            if (n < fields.size()) {
                String expected = fields.get(n);
                String field = readField(expected);
                if (field == null)
                    break;
                //noinspection StringEquality
                if (field != expected)
                    fields.set(n, field);
            } else {
                String field = readField("");
                if (field == null)
                    break;
                fields.add(field);
            }
            n++;
        }
        if (eol) {
            eol = false;
            boolean skipLF = false;
            while (!eof) {
                if (position >= limit) {
                    read();
                    if (position >= limit) {
                        eof = true;
                        break;
                    }
                }
                if (skipLF) { // second loop after CR
                    if (buf[position] == LF)
                        position++;
                    break;
                }
                // first loop - either CR or LF
                lineNumber++;
                if (buf[position++] == LF)
                    break;
                skipLF = true; // it was CR - to to second loop
            }
            recordNumber++;
            return n;
        }
        return -1;
    }

    /**
     * Reads and returns all records or empty list if stream has ended.
     * Empty records are represented by arrays of length 1 with single empty string.
     * Empty fields are represented by empty strings.
     *
     * @throws CSVFormatException if input stream does not conform to the CSV format
     * @throws IOException  If an I/O error occurs
     */
    public List<String[]> readAll() throws IOException {
        List<String[]> records = new ArrayList<>();
        for (String[] record = readRecord(); record != null; record = readRecord())
            records.add(record);
        return records;
    }

    /**
     * Closes the stream.
     *
     * @throws IOException  If an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        reader.close();
    }
}
