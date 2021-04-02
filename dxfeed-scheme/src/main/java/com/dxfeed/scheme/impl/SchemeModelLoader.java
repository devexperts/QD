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
package com.dxfeed.scheme.impl;

import com.devexperts.logging.Logging;
import com.dxfeed.scheme.EmbeddedTypes;
import com.dxfeed.scheme.SchemeException;
import com.dxfeed.scheme.SchemeLoadingOptions;
import com.dxfeed.scheme.impl.xml.XmlSchemeModelReader;
import com.dxfeed.scheme.model.SchemeModel;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Loader of full {@link SchemeModel scheme model}.
 * <p>
 * This class contains methods to parse {@link com.dxfeed.scheme.DXScheme DXScheme} specification strings
 * and load and merge complex, multi-file scheme models.
 * <p>
 * Don't use this class directly, use {@link com.dxfeed.scheme.DXScheme.Loader DXScheme.Loader} or
 * {@link SchemeModel.Loader SchemeModel.Loader}.
 */
public final class SchemeModelLoader {
    private final static Logging log = Logging.getLogging(SchemeModelLoader.class);

    private final static String DXFEED_KEYWORD = "dxfeed";
    private final static String RESOURCE_SCHEME = "resource:";
    private final static String DEFAULT_URL = RESOURCE_SCHEME + "dxfeed.xml";

    private final static Map<String, Supplier<SchemeModelReader>> FORMATS = new HashMap<>();

    static {
        FORMATS.put("xml", XmlSchemeModelReader::new);
    }

    public static final EmbeddedTypes DEFAULT_TYPES = new DefaultEmbeddedTypes();

    private SchemeModelLoader() {
    }

    public static List<URL> parseSpecification(String specification) throws IOException {
        return parseSpecification(specification, new SchemeLoadingOptions());
    }

    public static List<URL> parseSpecification(String specification, SchemeLoadingOptions options)
        throws IOException
    {
        URL root = new File("").toURI().toURL();
        List<URL> urls = new ArrayList<>();

        for (String s : specification.split("\\s*,\\s*")) {
            if (s.equals(DXFEED_KEYWORD)) {
                s = DEFAULT_URL;
                options.setDXFeedDefaults();
            }

            if (s.startsWith(RESOURCE_SCHEME)) {
                s = s.substring(RESOURCE_SCHEME.length());
                ClassLoader cl = resolveLoader();
                if (cl == null) {
                    throw new IOException(
                        "Cannot load scheme \"" + s + "\" from resources, class loader problem");
                }
                List<URL> resUrls = Collections.list(cl.getResources(s));
                if (resUrls.isEmpty()) {
                    throw new IOException("Cannot load scheme \"" + s + "\" from resources, not found");
                }
                if (resUrls.size() > 1) {
                    throw new IOException(
                        "Cannot load scheme \"" + s + "\" from resources, found several candidates: " +
                            resUrls.stream().map(a -> '"' + a.toString() + '"')
                                .collect(Collectors.joining(", "))
                    );
                }

                urls.add(resUrls.get(0));
            } else if (s.startsWith(SchemeLoadingOptions.OPTIONS_SCHEME)) {
                options.applyOptions(s);
            } else {
                urls.add(new URL(root, s));
            }
        }
        return urls;
    }

    public static SchemeModel loadSchemeModel(String spec) throws SchemeException, IOException {
        return loadSchemeModel(spec, DEFAULT_TYPES);
    }

    public static SchemeModel loadSchemeModel(String spec, EmbeddedTypes embeddedTypes)
        throws IOException, SchemeException
    {
        SchemeLoadingOptions options = new SchemeLoadingOptions();
        return loadSchemeModel(parseSpecification(spec, options), options, embeddedTypes);
    }

    public static SchemeModel loadSchemeModel(List<URL> urls, SchemeLoadingOptions options,
        EmbeddedTypes embeddedTypes) throws IOException, SchemeException
    {

        SchemeModel model = SchemeModel.newBuilder().
            withName("<scheme>")
            .withTypes(embeddedTypes)
            .withOptions(options)
            .build();
        Set<URL> seenFiles = new HashSet<>();
        for (URL url : urls) {
            // Resolve reader
            SchemeModelReader reader = getSchemeModelReader(url);
            loadFileAndImports(model, reader, url, options, null, seenFiles);
        }
        if (model.mergedInCount() == 0) {
            throw new SchemeException("Cannot load anything from given files.",
                urls.stream().map(URL::toString).collect(Collectors.toList())
            );
        }
        List<SchemeException> errors = model.validateState();
        // Check errors and bail out
        if (!errors.isEmpty()) {
            // Throw wrapping exception
            throw new SchemeException(errors, urls.stream().map(URL::toString).collect(Collectors.toList()));
        }
        return model;
    }


    private static void loadFileAndImports(SchemeModel model, SchemeModelReader reader, URL url,
        SchemeLoadingOptions options, String parent, Set<URL> seenFiles) throws IOException, SchemeException
    {
        // Don't load twice
        if (!seenFiles.add(url)) {
            return;
        }

        // Root for file is its onw URL, to resolve imports
        if (options.isDebugMode()) {
            if (parent == null) {
                log.debug("Loading external scheme file \"" + url + "\"...");
            } else {
                log.debug("Loading external scheme file \"" + url + "\" imported by \"" + parent + "\"...");
            }
        }

        reader.readModel(model, parent, url.toExternalForm(), url.openStream(),
            (parentName, imp) ->
                loadFileAndImports(model, reader, new URL(url, imp.getUrl()), options, parentName, seenFiles)
        );
        model.finishMergeIn(url.toExternalForm());

        if (options.isDebugMode()) {
            log.debug("Loaded external scheme file \"" + url + "\"");
        }
    }

    private static SchemeModelReader getSchemeModelReader(URL url) throws SchemeException {
        Supplier<SchemeModelReader> readerFabric = null;
        String fileName = url.getFile();
        int dot = fileName.lastIndexOf(".");
        if (dot >= 0 && dot < fileName.length() - 1) {
            String ext = fileName.substring(dot + 1).toLowerCase();
            readerFabric = FORMATS.get(ext);
        }
        if (readerFabric == null) {
            throw new SchemeException("Unknown scheme model file format \"" + url + "\"");
        }
        return readerFabric.get();
    }

    private static ClassLoader resolveLoader() {
        ClassLoader loader = SchemeModelLoader.class.getClassLoader();
        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }
        return loader;
    }
}
