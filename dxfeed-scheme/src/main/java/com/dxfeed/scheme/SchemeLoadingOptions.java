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
package com.dxfeed.scheme;

import com.dxfeed.api.DXEndpoint;

/**
 * This is options to regulate scheme files loading process.
 * <p>
 * Defaults are: no support for backward-compatible options, no debug output.
 */
public class SchemeLoadingOptions {
    public static final String OPTIONS_SCHEME = "opt:";

    public static final String NO_PREFIX = "no-";

    private static final String OPT_USE_DXFEED_PROPS = "dxprops";
    private static final String OPT_USE_SYSTEM_PROPS = "sysprops";
    private static final String OPT_DEBUG = "debug";

    private boolean useDXFeedProperties = false;
    private boolean useSystemProperties = false;
    private boolean debugMode = false;

    /**
     * Creates default options.
     */
    public SchemeLoadingOptions() {}

    /**
     * Returns, should {@link DXEndpoint#DXSCHEME_NANO_TIME_PROPERTY} and
     * {@link DXEndpoint#DXSCHEME_ENABLED_PROPERTY_PREFIX} properties be supported.
     *
     * @return option value.
     */
    public boolean shouldUseDXFeedProperties() {
        return useDXFeedProperties;
    }

    /**
     * Sets should {@link DXEndpoint#DXSCHEME_NANO_TIME_PROPERTY} and
     * {@link DXEndpoint#DXSCHEME_ENABLED_PROPERTY_PREFIX} properties be supported.
     *
     * @param val option value.
     */
    public void useDXFeedProperties(boolean val) {
        useDXFeedProperties = val;
    }

    /**
     * Checks whether system properties (such as {@code 'dxscheme.*'}) will be used.
     *
     * @return option value.
     */
    public boolean shouldUseSystemProperties() {
        return useSystemProperties;
    }

    /**
     * Sets whether system properties {@code 'dxscheme.*'} should be supported (or not).
     *
     * @param val option value.
     */
    public void useSystemProperties(boolean val) {
        useSystemProperties = val;
    }

    /**
     * Returns, should debug logging be done or suppressed.
     *
     * @return option value.
     */
    public boolean isDebugMode() {
        return debugMode;
    }

    /**
     * Sets, should debug logging be done or suppressed.
     *
     * @param val option value.
     */
    public void setDebugMode(boolean val) {
        debugMode = val;
    }

    /**
     * Equivalent to {@link #shouldUseDXFeedProperties()} and {@link #shouldUseSystemProperties()} set to {@code true}.
     */
    public void setDXFeedDefaults() {
        useSystemProperties = true;
        useDXFeedProperties = true;
    }

    /**
     * Apply options from string specification.
     * <p>
     * Options specification looks like {@code "opt:<option>[:<option>...]"}. Each {@code "<option>"} is option name
     * with optional {@code "no-"} prefix. Option name sets corresponding option to {@code true} and option name
     * with {@code "no-"} prefix sets corresponding option to {@code false}.
     * <p>
     * Supported option names are:
     * <ul>
     *     <li>{@code dxprops} — equivalent to {@link #useDXFeedProperties(boolean val)}.
     *     <li>{@code sysprops} — equivalent to {@link #useSystemProperties(boolean val)}.
     *     <li>{@code debug} — equivalent to {@link #setDebugMode(boolean val)}.
     * </ul>
     *
     * @param opts options specification.
     */
    public void applyOptions(String opts) {
        if (!opts.startsWith(OPTIONS_SCHEME)) {
            throw new IllegalArgumentException("Wrong external scheme options string: \"" + opts + "\"");
        }
        String trimmedOpts = opts.substring(OPTIONS_SCHEME.length()).trim();
        for (String o : trimmedOpts.split("\\s*:\\s*")) {
            boolean val = true;
            if (o.startsWith(NO_PREFIX)) {
                o = o.substring(NO_PREFIX.length());
                val = false;
            }
            switch (o) {
                case OPT_USE_DXFEED_PROPS:
                    useDXFeedProperties = val;
                    break;
                case OPT_USE_SYSTEM_PROPS:
                    useSystemProperties = val;
                    break;
                case OPT_DEBUG:
                    debugMode = val;
                    break;
                default:
                    throw new IllegalArgumentException(
                        "Wrong external scheme options string: \"" + opts + "\", unknown option \"" +
                            (val ? "" : NO_PREFIX) + o + "\"");
            }
        }
    }
}
