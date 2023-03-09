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
package com.dxfeed.ondemand.impl;

import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.ByteArrayOutput;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This internal class is public for implementation purposes only.
 */
public class ReplayResponse {
    private final ByteArrayOutput responseBlocksOutput = new ByteArrayOutput();
    private ByteArrayInput responseBlocksInput;

    @SuppressWarnings("unused")
    public ByteArrayOutput getResponseBlocksOutput() {
        return responseBlocksOutput;
    }

    public ByteArrayInput getResponseBlocksInput() {
        return responseBlocksInput;
    }

    public ByteArrayOutput write() throws IOException {
        Map<String, Object> elements = new LinkedHashMap<String, Object>();
        ReplayUtil.addGZippedElement(elements, "blocks", responseBlocksOutput);
        return ReplayUtil.writeElements(elements);
    }

    public void read(ByteArrayInput in) throws IOException {
        Map<String, byte[]> elements = ReplayUtil.readElements(in);
        responseBlocksInput = ReplayUtil.getGZippedElement(elements, "blocks");
    }
}
