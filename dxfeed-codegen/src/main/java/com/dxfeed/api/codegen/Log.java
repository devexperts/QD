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

import java.io.PrintWriter;
import java.io.StringWriter;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

class Log {
    private static Logger logger;

    static void setLogger(Logger logger) {
        Log.logger = logger;
    }

    private Log() {
    }

    static void error(String message) {
        logger.error(message, null, null);
    }

    static void error(String message, Object relatedObject) {
        logger.error(message, relatedObject, null);
    }

    static void error(String message, Object relatedObject, Throwable throwable) {
        logger.error(message, relatedObject, throwable);
    }

    static void warn(String message) {
        logger.warn(message, null, null);
    }

    static void warn(String message, Object relatedObject) {
        logger.warn(message, relatedObject, null);
    }

    static void warn(String message, Object relatedObject, Throwable throwable) {
        logger.warn(message, relatedObject, throwable);
    }

    static void info(String message) {
        logger.info(message, null, null);
    }

    static void info(String message, Object relatedObject) {
        logger.info(message, relatedObject, null);
    }

    static void info(String message, Object relatedObject, Throwable throwable) {
        logger.info(message, relatedObject, throwable);
    }

    interface Logger {
        void error(String message, Object relatedObject, Throwable throwable);

        void warn(String message, Object relatedObject, Throwable throwable);

        void info(String message, Object relatedObject, Throwable throwable);
    }

    static class MessagerLogger implements Logger {
        private final Messager messager;

        MessagerLogger(Messager messager) {
            this.messager = messager;
        }

        @Override
        public void error(String message, Object relatedObject, Throwable throwable) {
            log(Diagnostic.Kind.ERROR, message, relatedObject, throwable);
        }

        @Override
        public void warn(String message, Object relatedObject, Throwable throwable) {
            log(Diagnostic.Kind.WARNING, message, relatedObject, throwable);
        }

        @Override
        public void info(String message, Object relatedObject, Throwable throwable) {
            log(Diagnostic.Kind.NOTE, message, relatedObject, throwable);
        }

        private void log(Diagnostic.Kind level, String message, Object relatedObject, Throwable throwable) {
            StringWriter buffer = new StringWriter();
            Element element = null;
            if (relatedObject instanceof Element)
                element = (Element) relatedObject;
            if (relatedObject instanceof CodeGenExecutable) {
                CodeGenExecutable executable = (CodeGenExecutable) relatedObject;
                Object underlying = executable.getUnderlyingExecutable();
                if (underlying instanceof Element)
                    element = (Element) underlying;
            }
            if (relatedObject instanceof CodeGenType) {
                CodeGenType type = (CodeGenType) relatedObject;
                Object underlying = type.getUnderlyingType();
                if (underlying instanceof Element)
                    element = (Element) underlying;
            }
            if (element == null && relatedObject != null) {
                buffer.append(relatedObject.toString());
                buffer.append(": ");
            }
            buffer.append(message);
            if (throwable != null) {
                buffer.append("\nCaused by: ");
                throwable.printStackTrace(new PrintWriter(buffer));
            }
            for (String line : buffer.toString().split("\n"))
                messager.printMessage(level, line, element);
        }
    }
}
