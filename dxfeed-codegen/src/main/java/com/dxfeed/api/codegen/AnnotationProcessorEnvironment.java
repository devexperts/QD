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
package com.dxfeed.api.codegen;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.annotation.processing.Filer;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

class AnnotationProcessorEnvironment implements CodeGenEnvironment {
    private final Filer filer;

    AnnotationProcessorEnvironment(Filer filer) {
        this.filer = filer;
    }

    @Override
    public boolean hasSourceFile(ClassName className) {
        try {
            FileObject object = filer.getResource(StandardLocation.SOURCE_OUTPUT, className.getPackageName(), className.getSimpleName() + ".java");
            return Files.exists(Paths.get(object.toUri()));
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public BufferedReader openSourceFile(ClassName className) throws IOException {
        FileObject object = filer.getResource(StandardLocation.SOURCE_OUTPUT, className.getPackageName(), className.getSimpleName() + ".java");
        return new BufferedReader(object.openReader(false));
    }

    private void writeLines(FileObject file, List<String> lines) throws IOException {
        try (Writer writer = file.openWriter()) {
            for (String line : lines) {
                writer.write(line);
                writer.write('\n');
            }
        }
    }

    @Override
    public void writeSourceFile(ClassName className, List<String> sourceLines) throws IOException {
        JavaFileObject sourceFile = filer.createSourceFile(className.toString());
        Log.info("Writing class source: " + className);
        writeLines(sourceFile, sourceLines);
    }

    @Override
    public void writeTextResourceFile(Path resourcePath, List<String> lines) throws IOException {
        FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "", resourcePath.toString().replace(File.separatorChar, '/'));
        Log.info("Writing text resource: " + resourcePath);
        writeLines(file, lines);
    }
}
