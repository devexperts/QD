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
package com.devexperts.qd.qtp.auth;

import com.devexperts.auth.AuthSession;
import com.devexperts.auth.AuthToken;
import com.devexperts.qd.qtp.ChannelDescription;
import com.devexperts.qd.qtp.MessageAdapterConnectionFactory;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.TypedMap;
import com.dxfeed.promise.Promise;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Basic implementation for {@link QDAuthRealmFactory}. This class used when all other implementations
 * {@link QDAuthRealmFactory} returned {@code null} for the login string.
 *
 * This implementation created {@link BasicAuthRealm}.
 */
public class BasicAuthRealmFactory implements QDAuthRealmFactory {
    public static final BasicAuthRealmFactory INSTANCE = new BasicAuthRealmFactory();
    public static final String CONFIG_SUFFIX = ".config";

    /**
     * Created {@link BasicAuthRealm}.
     * @param auth login string for attribute "auth=login"
     * @param factory factory for connection.
     * @return {@link BasicAuthRealm}.
     */
    @Override
    public QDAuthRealm createAuthRealm(String auth, MessageAdapterConnectionFactory factory) {
        return new BasicAuthRealm(auth);
    }

    public static List<ChannelDescription> parseAgentChannelDescription(String s) {
        if (s == null || s.isEmpty())
            return Collections.emptyList();
        return QDConfig.splitParenthesisSeparatedString(s)
            .stream()
            .map(ChannelDescription::new)
            .collect(Collectors.toList());
    }

    /**
     * Basic implementation for {@link QDAuthRealm}. This class parses the login string as the file name or
     * a list "user1:password1:capabilities1,user1:password1:capabilities1"
     */
    private static class BasicAuthRealm implements QDAuthRealm {

        private static final String AUTHENTICATION_INFO = "Required";
        private final Map<AuthToken, ChannelDescription[]> channelDescriptions = new HashMap<>();

        private final Map<AuthToken, AuthSession> sessions = new HashMap<>();

        BasicAuthRealm(String auth) {
            if (auth.endsWith(CONFIG_SUFFIX)) {
                readFromFile(auth);
                return;
            }
            QDConfig.splitParenthesisSeparatedString(auth).forEach(this::parseOneDescription);
        }

        @Override
        public Promise<AuthSession> authenticate(AuthToken accessToken, TypedMap connectionVariables) {
            try {
                return Promise.completed(generatedSession(accessToken));
            } catch (SecurityException e) {
                return Promise.failed(e);
            }
        }

        @Override
        public String getAuthenticationInfo() {
            return AUTHENTICATION_INFO;
        }

        private void readFromFile(String fileName) {
            try {
                Files.lines(Paths.get(fileName), StandardCharsets.UTF_8).forEach(this::parseOneLine);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void parseOneLine(String line) {
            line = line.trim();
            if (line.startsWith("#") || line.isEmpty())
                return; // comment or empty
            parseOneDescription(line);
        }

        private void parseOneDescription(String desc) {
            String[] ss = desc.split(":");
            if (ss.length > 3 || ss.length < 2)
                throw new InvalidFormatException("Basic auth description format is <user>:<password>[:<channels>]");
            if (ss.length == 2 || ss[2].equals("*")) {
                AuthToken basicToken = AuthToken.createBasicToken(ss[0], ss[1]);
                channelDescriptions.put(basicToken, BasicChannelShaperFactory.ALL_DATA);
            } else {
                List<ChannelDescription> descriptions = parseAgentChannelDescription(ss[2]);
                channelDescriptions.put(
                    AuthToken.createBasicToken(ss[0], ss[1]),
                    descriptions.toArray(new ChannelDescription[descriptions.size()]));
            }
        }

        private synchronized AuthSession generatedSession(AuthToken basicToken) throws SecurityException {
            AuthSession session = sessions.get(basicToken);
            if (session != null)
                return session;
            ChannelDescription[] permissions = channelDescriptions.get(basicToken);
            if (permissions == null)
                throw new SecurityException("Access denied");
            session = new AuthSession(basicToken);
            session.variables().set(BasicChannelShaperFactory.CHANNEL_CONFIGURATION_KEY, permissions);
            sessions.put(basicToken, session);
            return session;
        }
    }
}
