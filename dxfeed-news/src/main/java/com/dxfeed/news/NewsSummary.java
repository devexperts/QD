/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.news;

import java.io.*;
import java.util.*;

import com.devexperts.io.IOUtil;
import com.devexperts.util.TimeFormat;

/**
 * Contains news details including time, title, news source, etc.
 */
public class NewsSummary implements Serializable, Comparable<NewsSummary> {
    private static final long serialVersionUID = 0L;

    // Symbol used for stream subscription
    public static final String MESSAGE_SYMBOL = "NEWS";

    /**
     * Maximum length of title string.
     */
    public static final int MAX_TITLE_LENGTH = 32700;

    private NewsKey key;
    private String sourceId;
    private long time;
    private String title;
    private String source;

    private transient Map<String, Collection<String>> tags;

    public NewsSummary(NewsKey key, String sourceId, long time, String title, String source,
        Map<String, Collection<String>> tags)
    {
        this.key = key;
        this.sourceId = sourceId;
        this.time = time;

        if (title != null) {
            this.title = title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
        }
        this.source = source;
        this.tags = tags;
    }

    /**
     * Returns news key for identifying news.
     * @return news key.
     */
    public NewsKey getKey() {
        return key;
    }

    public void assignKey(NewsKey key) {
        if (!this.key.equals(NewsKey.FIRST_KEY))
            throw new IllegalStateException("key is already assigned");
        this.key = key;
    }

    /**
     * Returns external ID assigned by news provider.
     * @return external ID.
     */
    public String getSourceId() {
        return sourceId;
    }

    /**
     * Returns news time.
     * @return news time.
     */
    public long getTime() {
        return time;
    }

    /**
     * Returns news title.
     * @return news title.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns news source provider.
     * @return news provider
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns set of tags available for the news
     * @return set of tag names.
     */
    public Set<String> getTagNames() {
        return tags.keySet();
    }

    /**
     * Returns values for the specified tag, or null if not found.
     * @param tag tag name.
     * @return values for the specified tag, or null if not found.
     */
    public Collection<String> getTagValues(String tag) {
        return tags.get(tag);
    }

    /**
     * Returns set of instrument symbols for this news.
     * @return set of instrument symbols.
     */
    public Collection<String> getSymbols() {
        return getTagValues(NewsTags.SYMBOLS);
    }

    // Serializable interface methods

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        // write tags
        IOUtil.writeCompactInt(out, tags.size());
        for (String tag : getTagNames()) {
            IOUtil.writeUTFString(out, tag);
            Collection<String> values = getTagValues(tag);
            IOUtil.writeCompactInt(out, values.size());
            for (String value : values) {
                IOUtil.writeUTFString(out, value);
            }
        }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        // read tags
        int size = IOUtil.readCompactInt(in);
        if (size == 0) {
            tags = Collections.emptyMap();
        } else {
            tags = new HashMap<>(size);
            for (int i = 0; i < size; i++) {
                String tag = IOUtil.readUTFString(in);
                int tagSize = IOUtil.readCompactInt(in);
                if (tagSize == 0) {
                    tags.put(tag, Collections.emptySet());
                } else if (tagSize == 1) {
                    String value = IOUtil.readUTFString(in);
                    tags.put(tag, Collections.singleton(value));
                } else {
                    Collection<String> values = new ArrayList<>(tagSize);
                    for (int j = 0; j < tagSize; j++) {
                        String value = IOUtil.readUTFString(in);
                        values.add(value);
                    }
                    tags.put(tag, values);
                }
            }
        }
    }

    @Override
    public int compareTo(NewsSummary o) {
        return getKey().getCode().compareTo(o.getKey().getCode());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o instanceof NewsSummary) {
            NewsSummary that = (NewsSummary) o;
            return this.getKey().equals(that.getKey());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getKey().hashCode();
    }

    public String toString() {
        return "NewsSummary{" + key.getCode() + "/" + getSourceId() +
            ", time=" + TimeFormat.DEFAULT.format(getTime()) +
            ", title=" + title +
            ", source=" + source +
            "}";
    }
}
