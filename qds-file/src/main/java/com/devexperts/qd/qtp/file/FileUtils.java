/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.file;

import com.devexperts.io.URLInputStream;
import com.devexperts.logging.Logging;
import com.devexperts.util.LogUtil;

import java.io.Closeable;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

class FileUtils {
    private static final Logging log = Logging.getLogging(FileUtils.class);

    public static final String TIME_FILE_EXTENSION = ".time";
    public static final String TIMESTAMP_MARKER = "~";

    private FileUtils() {} // utility class -- do not construct

    /**
     * Retrieves file extension from filePath.
     * @param filePath file name.
     * @return extension of a file (with dot), or empty string for a file that has no extension,
     * or if '~' was met in the extension.
     */
    public static String retrieveExtension(String filePath) {
        int dotPos = filePath.lastIndexOf('.');
        if (dotPos == -1)
            return "";
        String res = filePath.substring(dotPos);
        if (res.contains(File.separator))
            return ""; // extension in directory name -- no file extension
        if (res.contains(TIMESTAMP_MARKER))
            return ""; // assume no extension case, too
        return res;
    }

    public static String getTimeFilePath(String dataFilePath, String dataFileExtension, String containerExtension) {
        String fullExtension = dataFileExtension + containerExtension;
        if (!dataFilePath.endsWith(fullExtension))
            throw new IllegalArgumentException("File path '" + LogUtil.hideCredentials(dataFilePath) + "' is expected to end with extension '" + fullExtension + "'");
        return dataFilePath.substring(0, dataFilePath.length() - fullExtension.length()) + TIME_FILE_EXTENSION + containerExtension;
    }

    public static URL addressToURL(String address) {
        try {
            return URLInputStream.resolveURL(address);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid address URL: " + LogUtil.hideCredentials(address), e);
        }
    }

    public static File urlToFile(URL url) {
        if (!url.getProtocol().equals("file"))
            return null;
        return new File(url.getPath());
    }

    public static void tryClose(Closeable closeable, String address) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Throwable t) {
                log.error("Failed to close " + LogUtil.hideCredentials(address), t);
            }
        }
    }
}
