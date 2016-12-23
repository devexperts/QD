/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.impl.matrix;

import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.devexperts.logging.Logging;
import com.devexperts.qd.QDLog;
import com.devexperts.qd.impl.matrix.management.DebugDump;
import com.devexperts.services.Services;
import com.devexperts.util.SystemProperties;

/**
 * This class represents a fatal and unrecoverable error in the QDS core implementation.
 */
public class FatalError extends Error {
    private static final Logging log = Logging.getLogging(FatalError.class);

    private static final String SURVIVE_PROPERTY = FatalError.class.getName() + ".survive";
    private static final String DUMP_PROPERTY = FatalError.class.getName() + ".dump";
    private static final String HPROF_PROPERTY = FatalError.class.getName() + ".hprof";

    private static final boolean SURVIVE = SystemProperties.getBooleanProperty(SURVIVE_PROPERTY, false);
    private static final String DUMP = SystemProperties.getProperty(DUMP_PROPERTY, "QDFatalError.dump");
    private static final String HPROF = SystemProperties.getProperty(HPROF_PROPERTY, "");

    private static final String HOTSPOT_DIAGNOSTIC = "com.sun.management:type=HotSpotDiagnostic";

    /**
     * This method returns new {@code FatalError} with the corresponding message when
     * {@link #SURVIVE_PROPERTY} system property is set; by default, this method
     * terminlates JVM with {@link System#exit} method.
     */
    static FatalError fatal(Object owner, String message) {
        FatalError fatal = new FatalError(message);
        if (!SURVIVE)
            dumpAndDie(owner, fatal);
        return fatal;
    }

    private static void dumpAndDie(Object owner, FatalError fatal) {
        QDLog.log.error("FATAL ERROR. Recovery from this error is unlikely. This process will be terminated.\n" +
            "To avoid termination and to continue execution despite fatal errors, use the following JVM argument:\n" +
            "\"-D" + SURVIVE_PROPERTY + "\"", fatal);
        if (DUMP.length() > 0)
            makeDump(DUMP, owner, fatal);
        if (HPROF.length() > 0)
            makeHProf(HPROF);
        QDLog.log.info("EXIT");
        System.exit(1);
    }

    private static void makeDump(String file, Object owner, FatalError fatal) {
        DebugDump dump = Services.createService(DebugDump.class, null, null);
        if (dump != null)
            try {
                dump.makeDump(file, owner, fatal);
            } catch (Throwable t) {
                QDLog.log.error("Failed to dump to " + file, t);
            }
    }

    private static void makeHProf(String file) {
        QDLog.log.info("Dumping all heap memory in HPROF format to " + file + " file...");
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            server.invoke(new ObjectName(HOTSPOT_DIAGNOSTIC), "dumpHeap",
                new Object[] { file, true },
                new String[] { "java.lang.String", "boolean" });
        } catch (Throwable t) {
            QDLog.log.error("Failed to dump to " + file, t);
        }
    }

    private FatalError(String message) {
        super(message);
    }
}
