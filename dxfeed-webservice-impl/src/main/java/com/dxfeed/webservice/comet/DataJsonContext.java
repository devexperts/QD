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
package com.dxfeed.webservice.comet;

import com.dxfeed.webservice.DXFeedJson;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.common.JSONContext;
import org.cometd.server.JSONContextServer;
import org.cometd.server.ServerMessageImpl;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.text.ParseException;
import java.util.List;

public class DataJsonContext implements JSONContextServer, JSONContext.Parser, JSONContext.Generator {
    private static final Class<ServerMessageImpl[]> VALUE_TYPE = ServerMessageImpl[].class;

    @Override
    public JSONContext.Parser getParser() {
        return this;
    }

    @Override
    public JSONContext.Generator getGenerator() {
        return this;
    }

    @Override
    public ServerMessage.Mutable[] parse(InputStream stream) throws ParseException {
        try {
            return DXFeedJson.MAPPER.readValue(stream, VALUE_TYPE);
        } catch (IOException e) {
            throw (ParseException) new ParseException("", -1).initCause(e);
        }
    }

    @Override
    public <T> T parse(Reader reader, Class<T> tClass) throws ParseException {
        try {
            return DXFeedJson.MAPPER.readValue(reader, tClass);
        } catch (IOException e) {
            throw (ParseException) new ParseException("", -1).initCause(e);
        }
    }

    @Override
    public ServerMessage.Mutable[] parse(Reader reader) throws ParseException {
        return parse(reader, VALUE_TYPE);
    }

    @Override
    public ServerMessage.Mutable[] parse(String json) throws ParseException {
        try {
            return DXFeedJson.MAPPER.readValue(json, VALUE_TYPE);
        } catch (IOException e) {
            throw (ParseException) new ParseException("", -1).initCause(e);
        }
    }

    @Override
    public String generate(Object message) {
        try {
            return DXFeedJson.MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String generate(ServerMessage.Mutable mutable) {
        return generate((Object) mutable);
    }

    @Override
    public String generate(List<ServerMessage.Mutable> messages) {
        return generate(messages.toArray(new Message.Mutable[messages.size()]));
    }
}
