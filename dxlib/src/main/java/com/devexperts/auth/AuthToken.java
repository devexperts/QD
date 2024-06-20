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
package com.devexperts.auth;

import com.devexperts.util.Base64;
import com.devexperts.util.InvalidFormatException;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import javax.annotation.Nonnull;


/**
 * The {@code AuthToken} class represents an authorization token and encapsulates information about the authorization
 * scheme and its associated value.
 *
 * <p>An AuthToken consists of the following components:
 * <ul>
 *   <li>Scheme - The authorization scheme (e.g., "Basic" or "Bearer").</li>
 *   <li>Value - The encoded value, which is scheme-dependent (e.g., an access token per RFC6750 or Base64-encoded "user:password" per RFC2617).</li>
 *   <li>String representation - A string that combines the scheme and value in the format: [scheme + " " + value].</li>
 * </ul>
 */
public class AuthToken implements Serializable {
    public static final String BASIC_SCHEME = "Basic";
    public static final String BEARER_SCHEME = "Bearer";
    private static final long serialVersionUID = 0L;

    /**
     * Constructs an {@link AuthToken} from the specified string.
     *
     * @param string the string with space-separated scheme and value
     * @return the constructed {@link AuthToken}
     * @throws NullPointerException if the specified string is null
     * @throws InvalidFormatException if the string is malformed, or if the scheme is "Basic" but the format does not comply with RFC2617
     * @see #toString()
     */
    public static AuthToken valueOf(String string) {
        Objects.requireNonNull(string, "string");
        AuthToken at = new AuthToken(string);
        at.decode();
        return at;
    }

    /**
     * Constructs an {@link AuthToken} with the specified username and password per RFC2617.
     * Username and password can be empty.
     *
     * @param userPassword the string containing the username and password in the format "username:password"
     * @return the constructed {@link AuthToken}
     * @throws NullPointerException if the userPassword is null
     * @throws InvalidFormatException if the userPassword is malformed
     */
    public static AuthToken createBasicToken(String userPassword) {
        Objects.requireNonNull(userPassword, "userPassword");
        int i = userPassword.indexOf(':');
        if (i < 0)
            throw new InvalidFormatException("Does not contain user:password");
        return new AuthToken(BASIC_SCHEME, userPassword.substring(0, i), userPassword.substring(i + 1));
    }

    /**
     * Constructs an {@link AuthToken} with the specified username and password per RFC2617.
     * Username and password can be empty.
     *
     * @param user the username
     * @param password the password
     * @return the constructed {@link AuthToken}
     * @throws NullPointerException if either the username or password is null
     */
    public static AuthToken createBasicToken(String user, String password) {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(password, "password");
        return new AuthToken(BASIC_SCHEME, user, password);
    }

    /**
     * Constructs an {@link AuthToken} with the specified username and password per RFC2617.
     * If both the username and password are empty or {@code null}, returns {@code null}.
     *
     * @param user the username
     * @param password the password
     * @return the constructed {@link AuthToken} or {@code null}
     */
    public static AuthToken createBasicTokenOrNull(String user, String password) {
        if ((user == null || user.isEmpty()) && (password == null || password.isEmpty()))
            return null;
        return createBasicToken(user == null ? "" : user, password == null ? "" : password);
    }

    /**
     * Constructs an {@link AuthToken} with the specified bearer token per RFC6750.
     *
     * @param token the access token
     * @return the constructed {@link AuthToken}
     * @throws NullPointerException if the token is null
     * @throws InvalidFormatException if the token is empty
     */
    public static AuthToken createBearerToken(String token) {
        Objects.requireNonNull(token, "token");
        if (token.isEmpty())
            throw new InvalidFormatException("Token is empty");
        return new AuthToken(BEARER_SCHEME, token);
    }

    /**
     * Constructs an {@link AuthToken} with the specified bearer token per RFC6750.
     *
     * @param token the access token
     * @return the constructed {@link AuthToken} or {@code null}
     */
    public static AuthToken createBearerTokenOrNull(String token) {
        if (token == null || token.isEmpty())
            return null;
        return createBearerToken(token);
    }

    /**
     * Constructs an {@link AuthToken} with a custom scheme and value.
     *
     * @param scheme the custom scheme
     * @param value the custom value
     * @return the constructed {@link AuthToken}
     * @throws NullPointerException if either the scheme or value is null
     * @throws InvalidFormatException if the scheme or value is empty
     */
    public static AuthToken createCustomToken(String scheme, String value) {
        Objects.requireNonNull(scheme, "scheme");
        Objects.requireNonNull(value, "value");
        if (scheme.isEmpty())
            throw new InvalidFormatException("Scheme is empty");
        if (value.isEmpty())
            throw new InvalidFormatException("Value is empty");
        return new AuthToken(scheme, value);
    }

    private transient String scheme;
    private transient String user;
    private transient String password;
    private transient String value;
    private final String string;

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
     * Returns the HTTP authorization header value.
     *
     * @return the HTTP authorization header value
     */
    public String getHttpAuthorization() {
        return string;
    }

    /**
     * Returns the username or {@code null} if it is not known or applicable.
     *
     * @return the username, or {@code null} if not known or applicable
     */
    public String getUser() {
        return user;
    }

    /**
     * Returns the password or {@code null} if it is not known or applicable.
     *
     * @return the password, or {@code null} if not known or applicable
     */
    public String getPassword() {
        return password;
    }

    /**
     * Returns the authentication scheme.
     *
     * @return the authentication scheme
     */
    public String getScheme() {
        return scheme;
    }

    /**
     * Returns the access token for RFC6750 or the Base64-encoded "username:password" for RFC2617.
     *
     * @return the access token or Base64-encoded "username:password"
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
        }
    }
}
