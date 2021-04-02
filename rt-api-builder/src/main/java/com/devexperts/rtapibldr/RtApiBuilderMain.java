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
package com.devexperts.rtapibldr;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class RtApiBuilderMain {

    private static final String CLASS_SUFFIX = ".class";

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: java " + RtApiBuilderMain.class.getName() + " <src-dir> <dest-dir>");
            return;
        }
        File srcDir = new File(args[0]);
        File destDir = new File(args[1]);
        new RtApiBuilderMain(srcDir, destDir).scan("");
    }

    // --------------------- instance ---------------------

    private final File srcDir;
    private final File destDir;

    private RtApiBuilderMain(File srcDir, File destDir) {
        this.srcDir = srcDir;
        this.destDir = destDir;
    }

    private void scan(String path) throws IOException {
        File scanDir = new File(srcDir, path);
        System.out.println("Scanning " + scanDir);
        File[] files = scanDir.listFiles();
        if (files == null)
            return;
        for (File file : files) {
            String name = file.getName();
            if (file.isDirectory()) {
                scan(path + name + File.separator);
                continue;
            }
            if (name.endsWith(CLASS_SUFFIX))
                processFile(file, path + name);
        }
    }

    private void processFile(File srcFile, String path) throws IOException {
        System.out.println("Processing " + srcFile);
        ClassWriter cv = new ClassWriter(0);
        SnipClassVisitor visitor = new SnipClassVisitor(cv);
        FileInputStream in = new FileInputStream(srcFile);
        try {
            ClassReader cr = new ClassReader(in);
            cr.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        } finally {
            in.close();
        }
        if (visitor.writeClass) {
            File destFile = new File(destDir, path);
            destFile.getParentFile().mkdirs();
            System.out.println("Writing " + destFile);
            OutputStream out = new FileOutputStream(destFile);
            try {
                out.write(cv.toByteArray());
            } finally {
                out.close();
            }
        }
    }

    private static class SnipClassVisitor extends ClassAdapter {
        boolean writeClass;

        SnipClassVisitor(ClassVisitor cv) {
            super(cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces)
        {
            writeClass = (access & Opcodes.ACC_PUBLIC) != 0 || (access & Opcodes.ACC_FINAL) == 0;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            if ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0)
                return null;
            return super.visitField(access, name, desc, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0)
                return null;
            if ((access & Opcodes.ACC_ABSTRACT) == 0)
                access |= Opcodes.ACC_NATIVE;
            return super.visitMethod(access, name, desc, signature, exceptions);
        }
    }
}
