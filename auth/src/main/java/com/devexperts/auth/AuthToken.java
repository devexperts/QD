/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.auth;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import javax.annotation.Nonnull;

import com.devexperts.util.Base64;
import com.devexperts.util.InvalidFormatException;


/**
 * AuthToken for security controller in RMI framework, which encapsulates information about the authorization.
 * This AuthToken contains
 */

/*
 *
 * [Scheme] -- authorization scheme. (for example: "Basic" or "Bearer")
 * [Value] -- encoded value (scheme-dependent). (for example: "accessToken" per RFC6750 or "Base64(user:password)" per RFC2617)
 * [String] -- it is pair: [scheme + " " + value]. (for example: "Basic Base64(user:password)" per RFC2617)
 * [Capability] -- it is not encoded object authentication and allowed actions. (for example: "user:password:actions")
 *
 */
public class AuthToken implements Serializable {
    public static final String BASIC_SCHEME = "Basic";
    public static final String BEARER_SCHEME = "Bearer";
    private static final long serialVersionUID = 0L;

    /**
     * Constructs a {@link AuthToken} by specified string.
     * @see #toString()
     * @param string specified string with space separated scheme and value.
     * @return constructed {@link AuthToken}.
     * @throws NullPointerException if credentials is null.
     * @throws InvalidFormatException if string is malformed, or scheme is Basic, but wrong format per RFC2617.
     */
    public static AuthToken valueOf(String string) {
        Objects.requireNonNull(string);
        AuthToken at = new AuthToken(string);
        at.decode();
        return at;
    }

    /**
     * Constructs a {@link AuthToken} with specified user and password per RFC2617.
     * @param userPassword user name and password ("user:password)".
     * @return constructed {@link AuthToken}.
     * @throws NullPointerException if userPassword is null.
     * @throws InvalidFormatException if authorization is not contain user or password.
     */
    public static AuthToken createBasicToken(String userPassword) {
        Objects.requireNonNull(userPassword);
        int i = userPassword.indexOf(':');
        if (i < 1 || i == userPassword.length() - 1)
            throw new InvalidFormatException("Does not contain user:password");
        AuthToken at = new AuthToken(BASIC_SCHEME, userPassword.substring(0, i), userPassword.substring(i + 1));
        return at;
    }

    /**
     * Constructs a {@link AuthToken} with specified user and password per RFC2617.
     * @param user user name.
     * @param password password.
     * @return constructed {@link AuthToken}.
     * @throws NullPointerException if user or password is null.
     */
    public static AuthToken createBasicToken(String user, String password) {
        Objects.requireNonNull(user);
        Objects.requireNonNull(password);
        AuthToken at = new AuthToken(BASIC_SCHEME, user, password);
        return at;
    }

    /**
     * Constructs a {@link AuthToken} with specified bearer accessToken per RFC6750.
     * @param accessToken access accessToken.
     * @return constructed {@link AuthToken}.
     * @throws NullPointerException if accessToken is null.
     */
    public static AuthToken createBearerToken(String accessToken) {
        Objects.requireNonNull(accessToken);
        AuthToken at = new AuthToken(BEARER_SCHEME, accessToken);
        return at;
    }

    /**
     * Constructs a {@link AuthToken} with custom custom scheme and value.
     * @param scheme custom scheme.
     * @param value custom value.
     * @return constructed {@link AuthToken}.
     * @throws NullPointerException if scheme or value is null.
     * @throws IllegalArgumentException if scheme is empty.
     */
    public static AuthToken createCustomToken(String scheme, String value) {
        if (scheme.isEmpty())
            throw new IllegalArgumentException();
        Objects.requireNonNull(value);
        return new AuthToken(scheme, value);
    }

    private transient String scheme;
    private transient String user;
    private transient String password;
    private transient String value;
    private String string;

    private AuthToken(@Nonnull String string) {
        this.string = string;
    }

    private AuthToken(@Nonnull String scheme, @Nonnull String user, @Nonnull String password) {
        this.scheme = scheme;
        this.user = user;
        this.password = password;
        this.value = Base64.DEFAULT.encode((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.string = scheme + " " + value;
    }

    private AuthToken(@Nonnull String scheme, @Nonnull String value) {
        this.scheme = scheme;
        this.value = value;
        this.string = scheme + " " + value;
    }

    /**
     * Returns user name or {@code null} if it is not known/applicable.
     * @return user name.
     */
    public String getUser() {
        return user;
    }

    /**
     * Returns password or {@code null} if it is not known/applicable.
     * @return password.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Returns scheme.
     * @return scheme.
     */
    public String getScheme() {
        return scheme;
    }

    /**
     * Returns accessToken for RFC6750 or Base64(user:password) for RFC2617.
     * @return accessToken.
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || !(o instanceof AuthToken))
            return false;
        return string.equals(((AuthToken) o).string);
    }

    @Override
    public int hashCode() {
        return string.hashCode();
    }

    @Override
    public String toString() {
        return string;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        decode();
    }

    private void decode() {
        int j = string.indexOf(' ');
        if (j <= 0)
            throw new InvalidFormatException("Expected space-separated scheme and value");
        scheme = string.substring(0, j);
        value = string.substring(j + 1);
        if (scheme.equals(BASIC_SCHEME)) {
            String userPassword = new String(Base64.DEFAULT.decode(value), StandardCharsets.UTF_8);
            int i = userPassword.indexOf(':');
            if (i < 0)
                throw new InvalidFormatException("Does not contain user:password");
            user = userPassword.substring(0, i);
            password = userPassword.substring(i + 1);
            return;
        }
    }
}
