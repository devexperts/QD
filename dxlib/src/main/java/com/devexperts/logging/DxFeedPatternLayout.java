/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.logging;

import java.nio.charset.Charset;
import java.util.*;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.*;
import org.apache.logging.log4j.core.config.plugins.*;
import org.apache.logging.log4j.core.layout.*;
import org.apache.logging.log4j.core.pattern.*;

/**
 * Custom pattern layout with particular conversion pattern: {@link  #PATTERN} for log4j2.
 */
@SuppressWarnings("unused") //used by Log4j2
@Plugin(name = "dxFeedPatternLayout", category = Node.CATEGORY, elementType = Layout.ELEMENT_TYPE, printObject = true)
public class DxFeedPatternLayout extends AbstractStringLayout {
    // Outputs the first 1M lines of the stack trace as a workaround to make Log4j2 stacktrace format as Log4j
    private static final String PATTERN = "%p{length=1} %d{yyMMdd HHmmss.SSS} [%t] %c{1} - %dxm%n{%ex{1000000}%n}";
    private static final String SHORT_PATTERN = "%p{length=1} %d{yyMMdd HHmmss.SSS} %dxm%n";
    private static final String PATTERN_KEY = "Converter";

    private final PatternFormatter[] patternFormatters;
    private final PatternFormatter[] shortPatternFormatters;

    private DxFeedPatternLayout(Configuration configuration) {
        super(configuration, Charset.defaultCharset(), null, null);
        patternFormatters = createFormatters(configuration, PATTERN);
        shortPatternFormatters = createFormatters(configuration, SHORT_PATTERN);
    }

    @Override
    public String toSerializable(LogEvent event) {
        StringBuilder sb = getStringBuilder();
        format(event, sb);
        String text = sb.toString();
        trimToMaxSize(sb);
        return text;
    }

    @Override
    public void encode(LogEvent event, ByteBufferDestination destination) {
        StringBuilder text = getStringBuilder();
        format(event, text);
        Encoder<StringBuilder> encoder = getStringBuilderEncoder();
        encoder.encode(text, destination);
        trimToMaxSize(text);
    }

    private void format(LogEvent event, StringBuilder sb) {
        // First backspace is a signal to skip thread name and logger name output
        PatternFormatter[] formatters = event.getMessage().getFormattedMessage().charAt(0) == '\b'
            ? shortPatternFormatters
            : patternFormatters;
        for (PatternFormatter formatter : formatters)
            formatter.format(event, sb);
    }

    private static PatternFormatter[] createFormatters(Configuration configuration, String pattern) {
        PatternParser parser = configuration.getComponent(PATTERN_KEY);
        if (parser == null) {
            parser = new PatternParser(configuration, PATTERN_KEY, LogEventPatternConverter.class);
            configuration.addComponent(PATTERN_KEY, parser);
            parser = configuration.getComponent(PATTERN_KEY);
        }
        List<PatternFormatter> list = parser.parse(pattern, true, false);
        return list.toArray(new PatternFormatter[list.size()]);
    }

    @Override
    public Map<String, String> getContentFormat() {
        Map<String, String> result = new HashMap<>();
        result.put("structured", "false");
        result.put("formatType", "conversion");
        result.put("format", PATTERN);
        result.put("shortFormat", SHORT_PATTERN);
        return result;
    }

    @Override
    public String toString() {
        return getContentFormat().toString();
    }

    public static DxFeedPatternLayout createDefaultLayout() {
        return createDefaultLayout(null);
    }

    /**
     * Creates a DxFeedPatternLayout using the default options and the given configuration. Options include using UTF-8,
     * the default conversion pattern, exceptions being written.
     *
     * @see #PATTERN Default conversion pattern
     */
    @PluginFactory
    public static DxFeedPatternLayout createDefaultLayout(@PluginConfiguration Configuration configuration) {
        if (configuration == null)
            configuration = new DefaultConfiguration();
        return new DxFeedPatternLayout(configuration);
    }
}
