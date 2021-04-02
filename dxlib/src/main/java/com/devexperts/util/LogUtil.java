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
package com.devexperts.util;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

public class LogUtil {

    private static final int MAX_LOG_STRING_LENGTH = SystemProperties.getIntProperty(
        LogUtil.class, "maxLogStringLength", 120);
    private static final int MIN_ARRAY_LENGTH = SystemProperties.getIntProperty(
        LogUtil.class, "minArrayLength", 10);
    private static final String NULL = "null";

    private static final Pattern CREDENTIALS_PROPERTIES = Pattern.compile("([\\[\\(\\?,;&](user|password)=)([^\\]\\),;&]+)");
    private static final Pattern CREDENTIALS_USER_INFO = Pattern.compile("(://)([^/\\?\\[\\]\\(\\),;&]+)(@)");

    /**
     * Hides credentials by replacing them with asterisks. Hides both <b>user</b> and <b>password</b> credentials.
     * Hides credentials specified as properties, as URL query parameters, or as URL user info.
     *
     * @param o the object with credentials in string representation
     * @return the string representation with hidden credentials
     */
    public static String hideCredentials(Object o) {
        if (o == null)
            return null;
        String s = o.toString();
        if (s == null)
            return null;
        if (s.contains("user=") || s.contains("password="))
            s = CREDENTIALS_PROPERTIES.matcher(s).replaceAll("$1****");
        if (s.contains("://") && s.lastIndexOf('@') > s.indexOf("://"))
            s = CREDENTIALS_USER_INFO.matcher(s).replaceAll("$1****$3");
        return s;
    }

    /**
     * Returns a compact string representation of the "deep contents" of the specified
     * object.  If the object is array or collection or map and it contains other arrays as elements, the string
     * representation contains their contents and so on.  This method is
     * designed for converting multidimensional arrays to strings.
     *
     * <p> This method always prints at least 10 elements in a collection, map, or array and truncates the
     * string representation of object at 120 characters.
     *
     * @param object the object whose string representation to return
     * @return a compact string representation of the "deep contents" of the specified
     * object.
     */
    public static String deepToString(Object object) {
        if (object == null)
            return NULL;
        if (object instanceof Object[])
            return printArray((Object[]) object);
        if (object instanceof boolean[])
            return printArray((boolean[]) object);
        if (object instanceof byte[])
            return printArray((byte[]) object);
        if (object instanceof short[])
            return printArray((short[]) object);
        if (object instanceof int[])
            return printArray((int[]) object);
        if (object instanceof char[])
            return printArray((char[]) object);
        if (object instanceof long[])
            return printArray((long[]) object);
        if (object instanceof float[])
            return printArray((float[]) object);
        if (object instanceof double[])
            return printArray((double[]) object);
        if (object instanceof Collection)
            return printCollection((Collection<?>) object);
        if (object instanceof Map)
            return printMap((Map<?,?>) object);
        return printObject(object);
    }

    /**
     * Returns a compact string representation of the "deep contents" of the specified
     * Map.  If the map contains other arrays or maps or collections as elements, the string
     * representation contains their contents and so on.  This method is
     * designed for converting multidimensional map to strings.
     *
     * <p> This method always prints at least 10 elements in a map and truncates the
     * string representation of object at 120 characters.
     *
     * @param map the map whose string representation to return
     * @return a compact string representation of the "deep contents" of the specified
     * map.
     */
    public static String printMap(Map<?, ?> map) {
        if (map == null)
            return NULL;
        StringBuilder result = new StringBuilder();
        result.append("[");
        int i = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (i > 0)
                result.append(", ");
            result.append(deepToString(entry.getKey())).append("=");
            result.append(deepToString(entry.getValue()));
            if (i > MIN_ARRAY_LENGTH && result.length() >= MAX_LOG_STRING_LENGTH)
                break;
            i++;
        }
        if (i < map.size())
            result.append(" ... ").append(map.size() - i).append(" more");
        result.append("]");
        return result.toString();
    }

    /**
     * Returns a compact string representation of the "deep contents" of the specified
     * collection.  If the collection contains other arrays or maps or collections as elements, the string
     * representation contains their contents and so on.  This method is
     * designed for converting multidimensional collection to strings.
     *
     * <p> This method always prints at least 10 elements in a collection and truncates the
     * string representation of object at 120 characters.
     *
     * @param collection the collection whose string representation to return
     * @return a compact string representation of the "deep contents" of the specified
     * collection.
     */
    public static String printCollection(Collection<?> collection) {
        if (collection == null)
            return NULL;
        StringBuilder result = new StringBuilder();
        result.append("[");
        int i = 0;
        for (Object o : collection) {
            if (i > 0)
                result.append(", ");
            result.append(deepToString(o));
            if (i > MIN_ARRAY_LENGTH && result.length() >= MAX_LOG_STRING_LENGTH)
                break;
            i++;
        }
        if (i < collection.size())
            result.append(" ... ").append(collection.size() - i).append(" more");
        result.append("]");
        return result.toString();
    }

    /**
     * Returns a compact string representation of the "deep contents" of the specified
     * array.  If the array contains other arrays as elements, the string
     * representation contains their contents and so on.  This method is
     * designed for converting multidimensional arrays to strings.
     *
     * <p> This method always prints at least 10 elements in an array and truncates the
     * string representation of object at 120 characters.
     *
     * @param objects the array whose string representation to return
     * @return a compact string representation of the "deep contents" of the specified array.
     */
    public static String printArray(Object[] objects) {
        if (objects == null)
            return NULL;
        StringBuilder result = new StringBuilder();
        result.append("[");
        int count = Math.min(MIN_ARRAY_LENGTH, objects.length);
        int i;
        for (i = 0; i < count; i++) {
            if (i > 0)
                result.append(", ");
            result.append(deepToString(objects[i]));
            if (i > MIN_ARRAY_LENGTH && result.length() >= MAX_LOG_STRING_LENGTH)
                break;
        }
        if (i  < objects.length)
            result.append(", ... ").append(objects.length - i ).append(" more");
        result.append("]");
        return result.toString();
    }

    /**
     * Returns a compact string representation of the "deep contents" of the specified
     * array.  If the array contains other arrays as elements, the string
     * representation contains their contents and so on.  This method is
     * designed for converting multidimensional arrays to strings.
     *
     * <p> This method always prints at least 10 elements in an array and truncates the
     * string representation of object at 120 characters.
     *
     * @param objects the array whose string representation to return
     * @return a compact string representation of the "deep contents" of the specified array.
     */
    public static String printArray(boolean[] objects) {
        if (objects == null)
            return NULL;
        StringBuilder result = new StringBuilder();
        result.append("[");
        int count = Math.min(MIN_ARRAY_LENGTH, objects.length);
        int i;
        for (i = 0; i < count; i++) {
            if (i > 0)
                result.append(", ");
            result.append(deepToString(objects[i]));
            if (i > MIN_ARRAY_LENGTH && result.length() >= MAX_LOG_STRING_LENGTH)
                break;
        }
        if (i  < objects.length)
            result.append(", ... ").append(objects.length - i ).append(" more");
        result.append("]");
        return result.toString();
    }

    /**
     * Returns a compact string representation of the "deep contents" of the specified
     * array.  If the array contains other arrays as elements, the string
     * representation contains their contents and so on.  This method is
     * designed for converting multidimensional arrays to strings.
     *
     * <p> This method always prints at least 10 elements in an array and truncates the
     * string representation of object at 120 characters.
     *
     * @param objects the array whose string representation to return
     * @return a compact string representation of the "deep contents" of the specified array.
     */
    public static String printArray(byte[] objects) {
        if (objects == null)
            return NULL;
        StringBuilder result = new StringBuilder();
        result.append("[");
        int count = Math.min(MIN_ARRAY_LENGTH, objects.length);
        int i;
        for (i = 0; i < count; i++) {
            if (i > 0)
                result.append(", ");
            result.append(deepToString(objects[i]));
            if (i > MIN_ARRAY_LENGTH && result.length() >= MAX_LOG_STRING_LENGTH)
                break;
        }
        if (i  < objects.length)
            result.append(", ... ").append(objects.length - i ).append(" more");
        result.append("]");
        return result.toString();
    }

    /**
     * Returns a compact string representation of the "deep contents" of the specified
     * array.  If the array contains other arrays as elements, the string
     * representation contains their contents and so on.  This method is
     * designed for converting multidimensional arrays to strings.
     *
     * <p> This method always prints at least 10 elements in an array and truncates the
     * string representation of object at 120 characters.
     *
     * @param objects the array whose string representation to return
     * @return a compact string representation of the "deep contents" of the specified array.
     */
    public static String printArray(short[] objects) {
        if (objects == null)
            return NULL;
        StringBuilder result = new StringBuilder();
        result.append("[");
        int count = Math.min(MIN_ARRAY_LENGTH, objects.length);
        int i;
        for (i = 0; i < count; i++) {
            if (i > 0)
                result.append(", ");
            result.append(deepToString(objects[i]));
            if (i > MIN_ARRAY_LENGTH && result.length() >= MAX_LOG_STRING_LENGTH)
                break;
        }
        if (i  < objects.length)
            result.append(", ... ").append(objects.length - i ).append(" more");
        result.append("]");
        return result.toString();
    }

    /**
     * Returns a compact string representation of the "deep contents" of the specified
     * array.  If the array contains other arrays as elements, the string
     * representation contains their contents and so on.  This method is
     * designed for converting multidimensional arrays to strings.
     *
     * <p> This method always prints at least 10 elements in an array and truncates the
     * string representation of object at 120 characters.
     *
     * @param objects the array whose string representation to return
     * @return a compact string representation of the "deep contents" of the specified array.
     */
    public static String printArray(int[] objects) {
        if (objects == null)
            return NULL;
        StringBuilder result = new StringBuilder();
        result.append("[");
        int count = Math.min(MIN_ARRAY_LENGTH, objects.length);
        int i;
        for (i = 0; i < count; i++) {
            if (i > 0)
                result.append(", ");
            result.append(deepToString(objects[i]));
            if (i > MIN_ARRAY_LENGTH && result.length() >= MAX_LOG_STRING_LENGTH)
                break;
        }
        if (i  < objects.length)
            result.append(", ... ").append(objects.length - i ).append(" more");
        result.append("]");
        return result.toString();
    }

    /**
     * Returns a compact string representation of the "deep contents" of the specified
     * array.  If the array contains other arrays as elements, the string
     * representation contains their contents and so on.  This method is
     * designed for converting multidimensional arrays to strings.
     *
     * <p> This method always prints at least 10 elements in an array and truncates the
     * string representation of object at 120 characters.
     *
     * @param objects the array whose string representation to return
     * @return a compact string representation of the "deep contents" of the specified array.
     */
    public static String printArray(char[] objects) {
        if (objects == null)
            return NULL;
        StringBuilder result = new StringBuilder();
        result.append("[");
        int count = Math.min(MIN_ARRAY_LENGTH, objects.length);
        int i;
        for (i = 0; i < count; i++) {
            if (i > 0)
                result.append(", ");
            result.append(deepToString(objects[i]));
            if (i > MIN_ARRAY_LENGTH && result.length() >= MAX_LOG_STRING_LENGTH)
                break;
        }
        if (i  < objects.length)
            result.append(", ... ").append(objects.length - i ).append(" more");
        result.append("]");
        return result.toString();
    }

    /**
     * Returns a compact string representation of the "deep contents" of the specified
     * array.  If the array contains other arrays as elements, the string
     * representation contains their contents and so on.  This method is
     * designed for converting multidimensional arrays to strings.
     *
     * <p> This method always prints at least 10 elements in an array and truncates the
     * string representation of object at 120 characters.
     *
     * @param objects the array whose string representation to return
     * @return a compact string representation of the "deep contents" of the specified array.
     */
    public static String printArray(long[] objects) {
        if (objects == null)
            return NULL;
        StringBuilder result = new StringBuilder();
        result.append("[");
        int count = Math.min(MIN_ARRAY_LENGTH, objects.length);
        int i;
        for (i = 0; i < count; i++) {
            if (i > 0)
                result.append(", ");
            result.append(deepToString(objects[i]));
            if (i > MIN_ARRAY_LENGTH && result.length() >= MAX_LOG_STRING_LENGTH)
                break;
        }
        if (i  < objects.length)
            result.append(", ... ").append(objects.length - i ).append(" more");
        result.append("]");
        return result.toString();
    }

    /**
     * Returns a compact string representation of the "deep contents" of the specified
     * array.  If the array contains other arrays as elements, the string
     * representation contains their contents and so on.  This method is
     * designed for converting multidimensional arrays to strings.
     *
     * <p> This method always prints at least 10 elements in an array and truncates the
     * string representation of object at 120 characters.
     *
     * @param objects the array whose string representation to return
     * @return a compact string representation of the "deep contents" of the specified array.
     */
    public static String printArray(float[] objects) {
        if (objects == null)
            return NULL;
        StringBuilder result = new StringBuilder();
        result.append("[");
        int count = Math.min(MIN_ARRAY_LENGTH, objects.length);
        int i;
        for (i = 0; i < count; i++) {
            if (i > 0)
                result.append(", ");
            result.append(deepToString(objects[i]));
            if (i > MIN_ARRAY_LENGTH && result.length() >= MAX_LOG_STRING_LENGTH)
                break;
        }
        if (i  < objects.length)
            result.append(", ... ").append(objects.length - i ).append(" more");
        result.append("]");
        return result.toString();
    }

    /**
     * Returns a compact string representation of the "deep contents" of the specified
     * array.  If the array contains other arrays as elements, the string
     * representation contains their contents and so on.  This method is
     * designed for converting multidimensional arrays to strings.
     *
     * <p> This method always prints at least 10 elements in an array and truncates the
     * string representation of object at 120 characters.
     *
     * @param objects the array whose string representation to return
     * @return a compact string representation of the "deep contents" of the specified array.
     */
    public static String printArray(double[] objects) {
        if (objects == null)
            return NULL;
        StringBuilder result = new StringBuilder();
        result.append("[");
        int count = Math.min(MIN_ARRAY_LENGTH, objects.length);
        int i;
        for (i = 0; i < count; i++) {
            if (i > 0)
                result.append(", ");
            result.append(deepToString(objects[i]));
            if (i > MIN_ARRAY_LENGTH && result.length() >= MAX_LOG_STRING_LENGTH)
                break;
        }
        if (i  < objects.length)
            result.append(", ... ").append(objects.length - i ).append(" more");
        result.append("]");
        return result.toString();
    }

    //---------------------------- private static methods ----------------------------

    private static String printObject(Object object) {
        String s = object.toString();
        return s.length() <= MAX_LOG_STRING_LENGTH ? s :
            s.substring(0, MAX_LOG_STRING_LENGTH) + " ... " + (s.length() - MAX_LOG_STRING_LENGTH) + " chars more";
    }
}
