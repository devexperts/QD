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
package com.dxfeed.ipf.transform;

import com.devexperts.io.StreamInput;
import com.devexperts.io.URLInputStream;
import com.devexperts.util.SystemProperties;
import com.dxfeed.ipf.InstrumentProfile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Reads, compiles and executes instrument profile transform programs.
 * Please see <b>Instrument Profile Format</b> documentation for complete description.
 * <p>
 * The correct way to use InstrumentProfileTransform is as follows:
 * <pre>
 * // Create new instance and read transform:
 * InstrumentProfileTransform transform = InstrumentProfileTransform.compileURL(url);
 * // Execute transform
 * profiles = transform.transform(profiles);
 * </pre>
 *
 * <p><b>This class is thread-safe.</b>
 */
public final class InstrumentProfileTransform {

    static final boolean ALLOW_UNDECLARED_FIELD_ACCESS =
        SystemProperties.getBooleanProperty(InstrumentProfileTransform.class, "allowUndeclaredFieldAccess", false);

    private Compiler compiler; // shall be final after deprecated methods are removed

    // ===================== public static factory methods =====================

    /**
     * Reads and compiles transform from specified URL.
     *
     * @param url the URL.
     * @return Compiled transform.
     * @throws TransformCompilationException if input stream does not conform to the transform syntax
     * @throws IOException  If an I/O error occurs
     */
    public static InstrumentProfileTransform compileURL(String url) throws IOException, TransformCompilationException {
        try (URLInputStream in = new URLInputStream(url)) {
            return compile(new StreamInput(in));
        }
    }

    /**
     * Reads and compiles transform from specified input stream.
     *
     * @param in the input stream to read.
     * @return Compiled transform.
     * @throws TransformCompilationException if input stream does not conform to the transform syntax
     * @throws IOException  If an I/O error occurs
     */
    public static InstrumentProfileTransform compile(InputStream in) throws IOException, TransformCompilationException {
        return compile(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /**
     * Reads and compiles transform from specified character stream.
     *
     * @param reader the character stream to read.
     * @return Compiled transform.
     * @throws TransformCompilationException if character stream does not conform to the transform syntax
     * @throws IOException  If an I/O error occurs
     */
    public static InstrumentProfileTransform compile(Reader reader) throws IOException, TransformCompilationException {
        InstrumentProfileTransform transform = new InstrumentProfileTransform();
        transform.compiler = new Compiler(reader, false);
        return transform;
    }

    /**
     * Reads and compiles single statement transform from specified character stream and stops reading after that.
     *
     * @param reader the character stream to read.
     * @return Compiled transform.
     * @throws TransformCompilationException if character stream does not conform to the transform syntax
     * @throws IOException  If an I/O error occurs
     */
    public static InstrumentProfileTransform compileSingleStatement(Reader reader) throws IOException, TransformCompilationException {
        InstrumentProfileTransform transform = new InstrumentProfileTransform();
        transform.compiler = new Compiler(reader, true);
        return transform;
    }

    // ===================== public instance methods =====================

    /**
     * Creates uninitialized instance.
     * @deprecated Use {@link #compileURL(String)}, {@link #compile(InputStream)}, {@link #compile(Reader)} or {@link #compileSingleStatement(Reader)}
     */
    public InstrumentProfileTransform() {}

    /**
     * Reads transform from specified file.
     *
     * @throws TransformCompilationException if input stream does not conform to the transform syntax
     * @throws IOException  If an I/O error occurs
     * @deprecated Use {@link #compileURL(String)}
     */
    public void readFromFile(String file) throws IOException, TransformCompilationException {
        try (InputStream in = new FileInputStream(file)) {
            read(new StreamInput(in));
        }
    }

    /**
     * Reads transform from specified stream.
     *
     * @throws TransformCompilationException if input stream does not conform to the transform syntax
     * @throws IOException  If an I/O error occurs
     * @deprecated Use {@link #compile(InputStream)}
     */
    public void read(InputStream in) throws IOException, TransformCompilationException {
        compiler = new Compiler(new InputStreamReader(in, StandardCharsets.UTF_8), false);
    }

    /**
     * Executes compiled transform on specified instrument profiles and returns transformed profiles.
     * All profiles that require modification are copied beforehand.
     * <p>This is a shortcut for
     * <code>{@link #transform(TransformContext, List) transform}(<b>new</b>
     * {@link TransformContext#TransformContext() TransformContext}(), profiles)</code>.
     *
     * @param profiles the list of instrument profiles.
     * @return list of transformed profiles.
     */
    public List<InstrumentProfile> transform(List<InstrumentProfile> profiles) {
        return compiler.transform(new TransformContext(), profiles);
    }

    /**
     * Executes compiled transform on specified instrument profiles and returns transformed profiles.
     * All profiles that require modification are copied beforehand.
     *
     * @param ctx the context.
     * @param profiles the list of instrument profiles.
     * @return list of transformed profiles.
     */
    public List<InstrumentProfile> transform(TransformContext ctx, List<InstrumentProfile> profiles) {
        ctx.ensureCapacity(compiler.lines.size());
        return compiler.transform(ctx, profiles);
    }

    /**
     * Executes compiled transform on specified instrument profile and returns:
     * <ul>
     * <li>same profile if it was not modified</li>
     * <li>new transformed profile if the given profile was modified</li>
     * <li>null - if the given profile was deleted</li>
     * </ul>
     * <p>This is a shortcut for
     * <code>{@link #transform(TransformContext, InstrumentProfile) transform}(<b>new</b>
     * {@link TransformContext#TransformContext() TransformContext}(), profile)</code>.
     *
     * @param profile the list of instrument profiles.
     * @return same profile, or transformed copy, or null.
     */
    public InstrumentProfile transform(InstrumentProfile profile) {
        return compiler.transform(new TransformContext(), profile);
    }

    /**
     * Executes compiled transform on specified instrument profile and returns:
     * <ul>
     * <li>same profile instance if it was not modified</li>
     * <li>new transformed instance if the given profile was modified</li>
     * <li>null - if the given profile was deleted</li>
     * </ul>
     *
     * @param ctx the context.
     * @param profile the list of instrument profiles.
     * @return same instance, or transformed copied instance, or null.
     */
    public InstrumentProfile transform(TransformContext ctx, InstrumentProfile profile) {
        ctx.ensureCapacity(compiler.lines.size());
        return compiler.transform(ctx, profile);
    }

    /**
     * Executes compiled transform on specified instrument profile and returns whether it was modified or not.
     * The specified instrument profile is transformed <b>In situ</b> - i.e. without copying.
     * Deletion statement is considered a modification although it does not change profile by itself.
     * <p>This is a shortcut for
     * <code>{@link #transformInSitu(TransformContext, InstrumentProfile) transformInSitu}(<b>new</b>
     * {@link TransformContext#TransformContext() TransformContext}(), profile)</code>.
     *
     * @param profile the instrument profile.
     * @return true if profile was modified.
     */
    public boolean transformInSitu(InstrumentProfile profile) {
        return compiler.transformInSitu(new TransformContext(), profile);
    }

    /**
     * Executes compiled transform on specified instrument profile and returns whether it was modified or not.
     * The specified instrument profile is transformed <b>In Situ</b> - i.e. without copying.
     * Deletion statement is considered a modification although it does not change profile by itself.
     *
     * @param ctx the context.
     * @param profile the instrument profile.
     * @return true if profile was modified.
     */
    public boolean transformInSitu(TransformContext ctx, InstrumentProfile profile) {
        ctx.ensureCapacity(compiler.lines.size());
        return compiler.transformInSitu(ctx, profile);
    }

    /**
     * Returns execution statistics of the transform in the given context.
     * The statistics is presented as a list of lines of original transform program
     * each preceded with a counter of how many times modifying statements on that line
     * were applied to instrument profiles.
     *
     * @param ctx the context.
     * @return the list of string lines with statistics.
     */
    public List<String> getStatistics(TransformContext ctx) {
        return compiler.getStatistics(ctx);
    }

    /**
     * This method returns an empty list.
     * @deprecated Use {@link #getStatistics(TransformContext)}
     */
    public List<String> getStatistics() {
        return Collections.emptyList();
    }

    /**
     * This method does nothing.
     * @deprecated No replacement
     */
    public void dropStatistics() {}
}
