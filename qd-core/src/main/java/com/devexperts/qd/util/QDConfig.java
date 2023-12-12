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
package com.devexperts.qd.util;

import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.AddressSyntaxException;
import com.devexperts.util.ConfigUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Configuration utility methods.
 */
public class QDConfig {
    /**
     * Backslash.
     */
    public static final char ESCAPE_CHAR = '\\';

    private static final Logging log = Logging.getLogging(QDConfig.class);

    private QDConfig() {}

    /**
     * Removes backslash escapes from the string.
     * @see #ESCAPE_CHAR
     * @see #escape(String)
     */
    public static String unescape(String s) {
        int n = s.length();
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == ESCAPE_CHAR && i < n - 1) // treat next char literally
                c = s.charAt(++i);
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Adds backslash escaped to all characters in the string that are consider special in QD configuration
     * strings: '(', ')', '[', ']', ',', '+', '@', '\'.
     * @see #ESCAPE_CHAR
     * @see #unescape(String)
     */
    public static String escape(String s) {
        int n = s.length();
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            switch (c) {
            case '(': case ')':
            case '[': case ']':
            case ',': case '+':
            case '@': case '\\':
                sb.append(ESCAPE_CHAR);
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Splits a parenthesis-separated collection like {@code '('<item>')'('<item>')...('<item>')}.
     * @param s the original string.
     * @return a list of items.
     * @throws AddressSyntaxException if braces in string are unbalanced.
     */
    public static List<String> splitParenthesisSeparatedString(String s) {
        if (!s.startsWith("("))
            return Collections.singletonList(s); // just one item
        List<String> result = new ArrayList<>();
        int cnt = 0;
        int startIndex = -1;
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            switch (c) {
            case '(':
                if (cnt++ == 0)
                    startIndex = i + 1;
                break;
            case ')':
                if (cnt == 0)
                    throw new AddressSyntaxException("Extra closing parenthesis ')' in a list");
                if (--cnt == 0)
                    result.add(s.substring(startIndex, i));
                break;
            default:
                if (cnt == 0 && c > ' ')
                    throw new AddressSyntaxException("Unexpected character '" + c + "' outside parenthesis in a list");
                if (c == ESCAPE_CHAR) // escapes next char (skip it)
                    i++;
            }
        }
        if (cnt > 0)
            throw new AddressSyntaxException("Missing closing parenthesis ')' in a list");
        return result;
    }

    /**
     * Finds first occurrence of {@code atChar} while honoring quotation by '('..')' and '['...']' pairs.
     * @param s the string.
     * @param atChar the char to find.
     * @return array of one or two items.
     */
    public static String[] splitParenthesisedStringAt(String s, char atChar) {
        List<Character> stack = new ArrayList<>();
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            switch (c) {
            case '(':
                stack.add(')');
                break;
            case '[':
                stack.add(']');
                break;
            case ')':
            case ']':
                if (stack.isEmpty())
                    throw new AddressSyntaxException("Extra closing parenthesis: " + c);
                if (stack.remove(stack.size() - 1) != c)
                    throw new AddressSyntaxException("Wrong closing parenthesis: " + c);
                break;
            case ESCAPE_CHAR:  // escapes next char (skip it)
                i++;
                break;
            default:
                if (stack.isEmpty() && c == atChar)
                    return new String[] { s.substring(0, i).trim(), s.substring(i + 1).trim() };
            }
        }
        return new String[] { s }; // at chart is not found
    }

    /**
     * Parses additional properties at the end of the given description string.
     * Properties can be enclosed in pairs of matching '[...]' or '(...)' (the latter is deprecated).
     * Multiple properties can be specified with multiple pair of braces or be comma-separated inside
     * a single pair, so both "something[prop1,prop2]" and "something[prop1][prop2]" are
     * valid properties specifications. All braces must go in matching pairs.
     * Original string and all properties string are trimmed from extra space, so
     * extra spaces around or inside braces are ignored.
     * Special characters can be escaped with a backslash ({@link #ESCAPE_CHAR}).
     *
     * @param desc Description string to parse.
     * @param kvList Collection of strings where parsed properties are added to.
     * @return The resulting description string without properties.
     * @throws InvalidFormatException when description string is malformed.
     */
    public static String parseProperties(String desc, Collection<String> kvList) throws InvalidFormatException {
        desc = desc.trim();
        List<String> result = new ArrayList<>();
        List<Character> stack = new ArrayList<>();
        while ((desc.endsWith(")") || desc.endsWith("]")) && !isEscapedCharAt(desc, desc.length() - 1)) {
            int prop_end = desc.length() - 1;
            int i;
            // going backwards
        scan_loop:
            for (i = desc.length(); --i >= 0;) {
                char c = desc.charAt(i);
                if (c == ESCAPE_CHAR || isEscapedCharAt(desc, i))
                    continue;
                switch (c) {
                case ']':
                    stack.add('[');
                    break;
                case ')':
                    stack.add('(');
                    break;
                case ',':
                    if (stack.size() == 1) {
                        // this is a top-level comma
                        result.add(desc.substring(i + 1, prop_end).trim());
                        prop_end = i;
                    }
                    break;
                case '[':
                case '(':
                    assert !stack.isEmpty(); // we should have quit loop on empty stack!
                    char expect = stack.remove(stack.size() - 1);
                    if (c != expect)
                        throw new InvalidFormatException("Unmatched '" + c + "' in a list of properties");
                    if (stack.isEmpty()) {
                        result.add(desc.substring(i + 1, prop_end).trim());
                        break scan_loop;
                    }
                }
            }
            if (i < 0)
                throw new InvalidFormatException("Extra '" + stack.get(0) + "' in a list of properties");
            desc = desc.substring(0, i).trim();
        }
        Collections.reverse(result); // reverse properties into original order
        kvList.addAll(result);
        return desc;
    }

    private static boolean isEscapedCharAt(String s, int i) {
        int escapeCount = 0;
        while (--i >= 0 && s.charAt(i) == ESCAPE_CHAR)
            escapeCount++;
        return (escapeCount & 1) != 0;
    }

    public static List<String> getProperties(Object instance) {
        return getProperties(instance, instance.getClass());
    }

    public static void setProperties(Object instance, List<String> kvList) throws InvalidFormatException {
        setProperties(instance, instance.getClass(), kvList);
    }

    public static List<String> getProperties(Object instance, Class<?> intf) {
        List<String> kvList = new ArrayList<>();
        for (Property prop : getProperties(intf)) {
            Object objValue = getProperty(instance, prop);
            if (objValue == null)
                continue;
            String v = objValue.toString();
            if (v.isEmpty())
                continue;
            kvList.add(prop.getName() + "=" + v);
        }
        return kvList;
    }

    public static void setProperties(Object instance, Class<?> intf, List<String> kvList) throws InvalidFormatException {
        List<Property> props = getProperties(intf);
        for (String kv : kvList) {
            int i = kv.indexOf('=');
            String key = i < 0 ? kv : kv.substring(0, i).trim();
            String value = i < 0 ? "" : kv.substring(i + 1).trim();
            findAndSetProperty(instance, props, key, value);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void setDefaultProperties(Object instance, Class<?> intf, String prefix) {
        Properties sys;
        try {
            sys = System.getProperties();
        } catch (SecurityException e) {
            return; // just do nothing -- no access to defaults
        }
        List<Property> props = getProperties(intf);
        String prefixWithDot = prefix + '.';
        // Properties.stringPropertyNames() is properly synchronized to avoid ConcurrentModificationException.
        for (String key : sys.stringPropertyNames())
            try {
                if (key.startsWith(prefixWithDot))
                    findAndSetProperty(instance, props, key.substring(prefixWithDot.length()), sys.getProperty(key));
            } catch (InvalidFormatException e) {
                log.warn("Failed to set system property " + key + " for " + instance + ": " + e.getMessage());
            }
    }

    public static List<Property> getProperties(Class<?> intf) {
        List<Property> props = new ArrayList<>();
        for (Method m : intf.getMethods()) {
            if (m.getName().startsWith("set") &&
                m.getParameterTypes().length == 1 &&
                m.getReturnType() == Void.TYPE &&
                Modifier.isPublic(m.getModifiers()) &&
                !Modifier.isStatic(m.getModifiers()))
            {
                props.add(new Property(intf, m.getName().substring(3), m.getParameterTypes()[0], m));
            }
        }
        return props;
    }

    private static void findAndSetProperty(Object instance, List<Property> props, String key, String value) {
        for (Property prop : props)
            if (prop.getName().equalsIgnoreCase(key)) {
                setProperty(instance, prop, value);
                return;
            }
        throw new InvalidFormatException("Property is not found: " + key);
    }

    private static Object getProperty(Object instance, Property prop) {
        Method getterMethod = prop.getGetterMethod();
        if (getterMethod == null)
            return null;
        try {
            return getterMethod.invoke(instance);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new InvalidFormatException("Property '" + prop.getName() + "': " + e.getMessage(), e);
        }
    }

    /**
     * Sets the corresponding property in a bean to a given string value.
     * @throws InvalidFormatException if value has wrong format or something wrong with the property
     */
    private static void setProperty(Object instance, Property prop, String value) throws InvalidFormatException {
        try {
            prop.getSetterMethod().invoke(instance, ConfigUtil.convertStringToObject(prop.getPropertyType(), value));
        } catch (IllegalAccessException | InvocationTargetException | InvalidFormatException e) {
            throw new InvalidFormatException("Property '" + prop.getName() + "': " + e.getMessage(), e);
        }
    }

    public static class Property {
        private final Class<?> intf;
        private final String suffix;
        private final String name;
        private final Class<?> propertyType;
        private final Method setterMethod;

        Property(Class<?> intf, String suffix, Class<?> propertyType, Method setterMethod) {
            this.intf = intf;
            this.suffix = suffix;
            this.name = Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
            this.propertyType = propertyType;
            this.setterMethod = setterMethod;
        }

        public String getSuffix() {
            return suffix;
        }

        public String getName() {
            return name;
        }

        public Class<?> getPropertyType() {
            return propertyType;
        }

        public Method getGetterMethod() {
            try {
                return intf.getMethod("get" + suffix);
            } catch (NoSuchMethodException e) {
                try {
                    return intf.getMethod("is" + suffix);
                } catch (NoSuchMethodException e1) {
                    return null;
                }
            }
        }

        public Method getSetterMethod() {
            return setterMethod;
        }
    }
}
