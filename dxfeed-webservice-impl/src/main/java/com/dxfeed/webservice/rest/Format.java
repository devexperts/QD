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
package com.dxfeed.webservice.rest;

import com.dxfeed.webservice.DXFeedJson;
import com.dxfeed.webservice.DXFeedXml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.StringTokenizer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public enum Format {
    XML("application/xml", ".xml"),
    JSON("application/json", ".json");

    public static final Format DEFAULT = XML;

    public final String mediaType;
    public final String extension;

    Format(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    public String trimExtension(String path) {
        if (path.endsWith(extension))
            return path.substring(0, path.length() - extension.length());
        return path;
    }

    public void writeTo(Object result, OutputStream out, String indent) throws IOException {
        switch (this) {
        case XML:
            DXFeedXml.writeTo(result, out, indent);
            break;
        case JSON:
            DXFeedJson.writeTo(result, out, indent);
            break;
        default:
            throw new AssertionError();
        }
    }

    public Object readFrom(InputStream in, Class valueType) throws IOException {
        switch (this) {
        case XML:
            return DXFeedXml.readFrom(in);
        case JSON:
            return DXFeedJson.readFrom(in, valueType);
        default:
            throw new AssertionError();
        }
    }

    @Nullable
    public static Format getByExtension(@Nullable String path) {
        if (path == null)
            return null;
        if (path.endsWith(XML.extension))
            return XML;
        if (path.endsWith(JSON.extension))
            return JSON;
        return null;
    }

    @Nonnull
    public static Format getByRequestMediaType(@Nullable String requestMediaType) {
        if (requestMediaType == null || requestMediaType.isEmpty())
            return DEFAULT;
        for (StringTokenizer st = new StringTokenizer(requestMediaType, ","); st.hasMoreTokens();) {
            Format format = getByMediaType(st.nextToken());
            if (format != null)
                return format;
        }
        return DEFAULT;
    }

    @Nullable
    private static Format getByMediaType(String mediaType) {
        if (mediaType.equals(XML.mediaType))
            return XML;
        if (mediaType.equals(JSON.mediaType))
            return JSON;
        return null;
    }
}
