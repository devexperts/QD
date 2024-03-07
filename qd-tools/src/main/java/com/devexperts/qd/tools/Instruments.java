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
package com.devexperts.qd.tools;

import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.URLInputStream;
import com.devexperts.mars.common.MARSEndpoint;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.LogUtil;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimeUtil;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileField;
import com.dxfeed.ipf.InstrumentProfileReader;
import com.dxfeed.ipf.InstrumentProfileWriter;
import com.dxfeed.ipf.live.InstrumentProfileCollector;
import com.dxfeed.ipf.live.InstrumentProfileConnection;
import com.dxfeed.ipf.services.InstrumentProfileServer;
import com.dxfeed.ipf.tools.InstrumentProfileUtil;
import com.dxfeed.ipf.tools.OCCParser;
import com.dxfeed.ipf.transform.InstrumentProfileTransform;
import com.dxfeed.ipf.transform.TransformCompilationException;
import com.dxfeed.ipf.transform.TransformContext;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Reads, transforms and writes instrument profiles.
 */
@ToolSummary(
    info = "Reads, transforms and writes instrument profiles.",
    argString = "",
    arguments = {}
)
@ServiceProvider
public class Instruments extends AbstractTool {

    private static final boolean WRITE_EMPTY_RESULT_FILE =
        SystemProperties.getBooleanProperty("com.devexperts.qd.tools.instruments.writeEmptyResults", false);

    private final OptionString bizdate = new OptionString('b', "bizdate", "<date>",
        "Business date for filtering of active options, format YYYY-MM-DD, applicable only to OCC.xml file.");
    private final Option osi = new Option('o', "osi",
        "Use OSI symbology, implicit after OSI conversion, applicable only to OCC.xml file.");
    private final OptionMultiString read = new OptionMultiString('r', "read", "<source>",
        "Source of instruments - network address, IPF file, or OCC FIXML file. " +
        "Option can be specified several times to concatenate several sources.");
    private final Option products = new Option('p', "products",
        "Create products for futures and add them to the end. " +
        "Ignores existing products. " +
        "Use 'merge' command to merge new products with existing products.");
    private final OptionMultiString transform = new OptionMultiString('t', "transform", "<transform>",
        "Direct transform, URL or file with transform to be applied to instruments. " +
        "Option can be specified several times to apply several transforms.");
    private final Option merge = new Option('m', "merge",
        "Merge data using symbol as a unique identifier.");
    private final OptionMultiString exclude = new OptionMultiString('e', "exclude", "<source>",
        "Exclude instruments by symbol from the source - network address, IPF file, or OCC FIXML file. " +
        "Option can be specified several times to exclude several sources.");
    private final Option check = new Option('c', "check",
        "Perform data consistency check.");
    private final Option sort = new Option('s', "sort",
        "Sort profiles in natural order.");
    private final OptionString write = new OptionString('w', "write", "<write>",
        "Output to IPF file or start server at \":<port>[<opts>]\". " +
        "In the later server mode case, <source> can be IPF file in simple format only or a live service URL. " +
        "In server mode, merge is implied, and bizdate, osi, check, sort, and script are not supported.");
    private final OptionMultiString script = new OptionMultiString('i', "script", "<script>",
        "Execute command script. Option can be specified several times to execute several scripts.");
    private final OptionInteger performance = new OptionInteger('n', "performance", "<n>",
        "Run read/transform performance test n times.");

    InstrumentProfileServer server;

    @Override
    protected Option[] getOptions() {
        return new Option[] { bizdate, osi, read, products, transform, merge, exclude, check, sort, write, script,
            performance, OptionLog.getInstance()
        };
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected void executeImpl(String[] args) {
        if (args.length != 0)
            wrongNumberOfArguments();
        if (!read.isSet() && !transform.isSet() && !script.isSet())
            noArguments();
        if (write.isSet() && write.getValue().startsWith(":")) {
            executeServerImpl();
            return;
        }
        List<InstrumentProfile> profiles = new ArrayList<>();
        for (String source : read.getValues())
            profiles = read(profiles, source);
        if (products.isSet())
            profiles = products(profiles);
        for (String source : transform.getValues())
            profiles = transform(profiles, null, source, null);
        if (merge.isSet())
            profiles = merge(profiles);
        for (String source : exclude.getValues())
            profiles = exclude(profiles, source);
        if (check.isSet())
            profiles = check(profiles);
        if (sort.isSet())
            profiles = sort(profiles);
        if (write.isSet())
            profiles = write(profiles, write.getValue());
        for (String source : script.getValues())
            profiles = script(profiles, source);
    }

    private void executeServerImpl() {
        // parse properties
        if (bizdate.isSet() || osi.isSet() || check.isSet() || sort.isSet() || script.isSet()) {
            throw new BadToolParametersException(
                "In server mode bizdate, osi, check, sort, and script are not supported");
        }
        if (!read.isSet()) {
            throw new BadToolParametersException("At least one " + read + " option must be set in server mode");
        }
        List<InstrumentProfileTransform> transforms = new ArrayList<>();
        for (String source : transform.getValues()) {
            transforms.add(compileTransform(null, source, null));
        }

        // start MARS monitoring
        MARSEndpoint.newBuilder().acquire();

        // start connections and server
        InstrumentProfileCollector collector = new InstrumentProfileCollector();
        for (String url : read.getValues()) {
            InstrumentProfileConnection connection = InstrumentProfileConnection.createConnection(url, collector);
            connection.start();
        }
        server = InstrumentProfileServer.createServer(write.getValue(), collector);
        server.setTransforms(transforms);
        server.start();
    }

    @Override
    public Thread mustWaitForThread() {
        return server != null ? Thread.currentThread() : null;
    }

    @Override
    public List<Closeable> closeOnExit() {
        return server == null ? null : Collections.singletonList(server);
    }

    private List<InstrumentProfile> script(List<InstrumentProfile> profiles, String source) {
        long time = System.currentTimeMillis();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new URLInputStream(source), UTF_8))) {
            String header = "";
            for (String command; (command = reader.readLine()) != null;) {
                command = command.trim();
                if (command.isEmpty() || command.startsWith("#"))
                    continue;

                log.info("Executing " + command);
                String cmd = command.split(" ")[0];
                String arg = command.substring(cmd.length()).trim();
                switch (cmd) {
                    case "read":
                        profiles = read(profiles, arg);
                        break;
                    case "products":
                        profiles = products(profiles);
                        break;
                    case "transheader":
                        header = header + arg + "\r\n";
                        break;
                    case "transform":
                        profiles = transform(profiles, header, arg, reader);
                        break;
                    case "merge":
                        profiles = merge(profiles);
                        break;
                    case "exclude":
                        profiles = exclude(profiles, arg);
                        break;
                    case "check":
                        profiles = check(profiles);
                        break;
                    case "sort":
                        profiles = sort(profiles);
                        break;
                    case "write":
                        profiles = write(profiles, arg);
                        break;
                    case "script":
                        profiles = script(profiles, arg);
                        break;
                    case "clear":
                        profiles.clear();
                        break;
                    default:
                        log.error("Unknown command " + command);
                        throw new IllegalArgumentException("Unknown command " + command);
                }
            }
            log.info("Executed script " + source + " in " + secondsSince(time) + "s");
        } catch (IOException e) {
            log.error("Error reading script " + source, e);
            throw new IllegalArgumentException(e);
        }
        return profiles;
    }

    private List<InstrumentProfile> read(List<InstrumentProfile> profiles, String source) {
        InstrumentProfileReader reader;
        int fileNameIndex = source.lastIndexOf('/');
        // Select proper reader class based on source file name
        if (source.toLowerCase().indexOf(".xml") > fileNameIndex) {
            long biz = System.currentTimeMillis();
            if (bizdate.isSet()) {
                try {
                    DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
                    df.setTimeZone(TimeUtil.getTimeZoneGmt());
                    biz = df.parse(bizdate.getValue()).getTime();
                } catch (ParseException e) {
                    throw new OptionParseException(e.getMessage());
                }
            }
            reader = new OCCParser(biz, osi.isSet());
        } else {
            reader = new InstrumentProfileReader();
        }
        try {
            if (performance.isSet() && !transform.isSet() && !script.isSet()) {
                byte[] bytes = URLInputStream.readBytes(InstrumentProfileReader.resolveSourceURL(source));
                for (int i = 0; i < performance.getValue(); i++) {
                    long nanos = System.nanoTime();
                    int result = new InstrumentProfileReader().read(new ByteArrayInput(bytes), source).size();
                    nanos = System.nanoTime() - nanos;
                    log.info("Read " + result + " in " + ((nanos + 500) / 1000 / 1000.0) + " ms");
                }
            }
            long time = System.currentTimeMillis();
            List<InstrumentProfile> readProfiles = reader.readFromFile(source);
            profiles.addAll(readProfiles);
            log.info("Read " + readProfiles.size() + " profiles from " + LogUtil.hideCredentials(source) +
                " in " + secondsSince(time) + "s");
            return profiles;
        } catch (IOException e) {
            log.error("Error reading source " + LogUtil.hideCredentials(source), e);
            throw new IllegalArgumentException(e);
        }
    }

    private List<InstrumentProfile> products(List<InstrumentProfile> profiles) {
        long time = System.currentTimeMillis();
        List<InstrumentProfile> products = InstrumentProfileUtil.createProducts(profiles);
        profiles.addAll(products);
        log.info("Created " + products.size() + " profiles in " + secondsSince(time) + "s");
        return profiles;
    }

    private List<InstrumentProfile> transform(
        List<InstrumentProfile> profiles, String header, String source, Reader reader)
    {
        InstrumentProfileTransform transform = compileTransform(header, source, reader);
        if (performance.isSet())
            for (int i = 0; i < performance.getValue(); i++) {
                long nanos = System.nanoTime();
                int result = transform.transform(profiles).size();
                nanos = System.nanoTime() - nanos;
                log.info("Transformed " + profiles.size() + " -> " + result +
                    " in " + ((nanos + 500) / 1000 / 1000.0) + " ms");
            }
        long time = System.currentTimeMillis();
        TransformContext ctx = new TransformContext();
        profiles = transform.transform(ctx, profiles);
        log.info("Transformed " + profiles.size() + " profiles in " + secondsSince(time) + "s");
        for (String s : transform.getStatistics(ctx)) {
            log.info(s);
        }
        return profiles;
    }

    private InstrumentProfileTransform compileTransform(String header, String source, Reader reader) {
        long time = System.currentTimeMillis();
        InstrumentProfileTransform transform;
        try {
            if (source.isEmpty()) {
                log.error("Empty transform");
                throw new IllegalArgumentException("Empty transform");
            } else if (source.equals("{")) {
                transform = InstrumentProfileTransform.compileSingleStatement(new MergeReader("{\r\n", header, reader));
            } else if (source.contains(";")) {
                transform = InstrumentProfileTransform.compile(new MergeReader(header, source, null));
            } else {
                try (InputStream in = new URLInputStream(source)) {
                    transform = InstrumentProfileTransform.compile(in);
                }
            }
        } catch (IOException e) {
            log.error("Error reading transform " + source, e);
            throw new IllegalArgumentException(e);
        } catch (TransformCompilationException e) {
            log.error(e.getMessage());
            throw new IllegalArgumentException(e);
        }
        log.info("Compiled transform in " + secondsSince(time) + "s");
        return transform;
    }

    private static class MergeReader extends Reader {
        private final List<Reader> readers = new ArrayList<>();

        MergeReader(String header, String body, Reader footer) {
            if (header != null && !header.isEmpty())
                readers.add(new StringReader(header));
            if (body != null && !body.isEmpty())
                readers.add(new StringReader(body));
            if (footer != null)
                readers.add(footer);
        }

        @Override
        public int read() throws IOException {
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < readers.size(); i++) {
                int c = readers.get(i).read();
                if (c >= 0)
                    return c;
            }
            return -1;
        }

        @Override
        public int read(char[] chars, int off, int len) throws IOException {
            for (int i = 0; i < len; i++) {
                int c = read();
                if (c < 0)
                    return i == 0 ? -1 : i;
                chars[off + i] = (char) c;
            }
            return len;
        }

        @Override
        public void close() throws IOException {
        }
    }

    private List<InstrumentProfile> merge(List<InstrumentProfile> profiles) {
        long time = System.currentTimeMillis();
        Map<String, Integer> map = new HashMap<>();
        List<InstrumentProfile> merged = new ArrayList<>();
        List<EnumSet<InstrumentProfileField>> conflicts = new ArrayList<>();
        InstrumentProfileField[] fields = InstrumentProfileField.values();
        List<Set<String>> customConflicts = new ArrayList<>();
        List<String> customFields = new ArrayList<>();

        for (InstrumentProfile ip : profiles) {
            Integer index = map.get(ip.getSymbol());
            if (index == null) {
                map.put(ip.getSymbol(), merged.size());
                merged.add(ip);
                conflicts.add(null);
                customConflicts.add(null);
                continue;
            }
            InstrumentProfile old = merged.get(index);
            old = new InstrumentProfile(old);
            merged.set(index, old);
            EnumSet<InstrumentProfileField> c = conflicts.get(index);
            if (c == null) {
                conflicts.set(index, c = EnumSet.noneOf(InstrumentProfileField.class));
            }
            for (InstrumentProfileField field : fields) {
                String s = field.getField(ip);
                if (s.isEmpty())
                    continue;

                String sOld = field.getField(old);
                if (sOld.length() != 0 && !s.equals(sOld))
                    c.add(field);
                field.setField(old, s);
            }
            customFields.clear();
            ip.addNonEmptyCustomFieldNames(customFields);

            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < customFields.size(); i++) {
                String field = customFields.get(i);
                String s = ip.getField(field);
                if (s == null || s.isEmpty())
                    continue;

                String sOld = old.getField(field);
                if (sOld.length() != 0 && !s.equals(sOld)) {
                    Set<String> cc = customConflicts.get(index);
                    if (cc == null)
                        customConflicts.set(index, cc = new TreeSet<>());
                    cc.add(field);
                }
                old.setField(field, s);
            }
        }
        for (int i = 0; i < conflicts.size(); i++) {
            EnumSet<InstrumentProfileField> c = conflicts.get(i);
            if (c == null)
                continue;

            Set<String> cc = customConflicts.get(i);
            if (cc == null) {
                cc = Collections.emptySet();
            }
            log.info("MERGED  " + merged.get(i).getSymbol() +
                (c.isEmpty() && cc.isEmpty() ? "" : "  conflicts:") +
                (c.isEmpty() ? "" : " " + c) + (cc.isEmpty() ? "" : " " + cc));
        }
        log.info("Merged " + merged.size() + " profiles in " + secondsSince(time) + "s");
        return merged;
    }

    private List<InstrumentProfile> exclude(List<InstrumentProfile> profiles, String source) {
        HashSet<String> symbols = new HashSet<>();
        for (InstrumentProfile ip : read(new ArrayList<>(), source)) {
            symbols.add(ip.getSymbol());
        }
        long time = System.currentTimeMillis();
        List<InstrumentProfile> filtered = new ArrayList<>();
        for (InstrumentProfile ip : profiles) {
            if (!symbols.contains(ip.getSymbol()))
                filtered.add(ip);
        }
        log.info("Excluded " + (profiles.size() - filtered.size()) + " profiles in " + secondsSince(time) + "s");
        return filtered;
    }

    private List<InstrumentProfile> check(List<InstrumentProfile> profiles) {
        long time = System.currentTimeMillis();
        HashSet<String> symbols = new HashSet<>();
        HashSet<String> reportedSymbols = new HashSet<>();
        HashMap<String, Character> exercises = new HashMap<>();
        HashSet<String> reportedExercises = new HashSet<>();
        HashMap<String, InstrumentProfile> uniqueness = new HashMap<>();
        for (InstrumentProfile ip : profiles) {
            if (!symbols.add(ip.getSymbol()) && reportedSymbols.add(ip.getSymbol())) {
                log.info("DUPLICATE SYMBOL: " + ip.getSymbol());
            }
            if (!ip.getType().equals("OPTION") || "FLEX".equals(ip.getOptionType())) {
                continue;
            }

            char exercise = ip.getCFI().charAt(2);
            Character oldExercise = exercises.put(ip.getUnderlying(), exercise);
            if (oldExercise != null && oldExercise != exercise && reportedExercises.add(ip.getUnderlying())) {
                log.warn("WARNING: underlying " + ip.getUnderlying() +
                    " has options with different exercise styles.");
            }

            String key = ip.getUnderlying() + " " + ip.getSPC() + " (" + ip.getAdditionalUnderlyings() + ") " +
                ip.getMMY() + " " + ip.getLastTrade() + " " + ip.getExpirationStyle() + " " +
                ip.getStrike() + " " + ip.getCFI().charAt(1);
            InstrumentProfile oldProfile = uniqueness.put(key, ip);
            if (oldProfile != null) {
                log.info("CONFLICT: options " + oldProfile.getSymbol() + " and " + ip.getSymbol() +
                    " have same parameters: " + key);
            }
        }
        log.info("Checked " + profiles.size() + " profiles in " + secondsSince(time) + "s");
        return profiles;
    }

    private List<InstrumentProfile> sort(List<InstrumentProfile> profiles) {
        long time = System.currentTimeMillis();
        Collections.sort(profiles);
        log.info("Sorted " + profiles.size() + " profiles in " + secondsSince(time) + "s");
        return profiles;
    }

    private List<InstrumentProfile> write(List<InstrumentProfile> profiles, String file) {
        try {
            long time = System.currentTimeMillis();
            if (!profiles.isEmpty() || WRITE_EMPTY_RESULT_FILE) {
                new InstrumentProfileWriter().writeToFile(file, profiles);
            }
            log.info("Wrote " + profiles.size() + " profiles to " + LogUtil.hideCredentials(file) +
                " in " + secondsSince(time) + "s");
            return profiles;
        } catch (IOException e) {
            log.error("Error writing file " + LogUtil.hideCredentials(file), e);
            throw new IllegalArgumentException(e);
        }
    }

    private static double secondsSince(long time) {
        return (double) ((System.currentTimeMillis() - time) / 100) / 10.0;
    }

    public static void main(String[] args) {
        Tools.executeSingleTool(Instruments.class, args);
    }
}
