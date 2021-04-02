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
package com.dxfeed.ipf.transform;

import com.devexperts.io.CSVReader;
import com.devexperts.io.URLInputStream;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class FixOPOLStatement extends Statement {
    private final Set<String> symbols = new HashSet<>();
    private final Object opol;

    FixOPOLStatement(Compiler compiler) throws IOException {
        super(compiler);
        compiler.skipToken('(');
        String file = Compiler.getString(null, compiler.readExpression());
        compiler.skipToken(',');
        opol = compiler.readExpression();
        Compiler.getString(Compiler.newTestContext(), opol); // Early check of expression constraints (data types)
        compiler.skipToken(')');
        compiler.skipToken(';');
        readSymbols(file);
    }

    private void readSymbols(String file) throws IOException {
        try (CSVReader reader =
                new CSVReader(new InputStreamReader(new URLInputStream(file), StandardCharsets.UTF_8), '|', '\0'))
        {
            String[] header = reader.readRecord();
            if (header == null)
                return;
            int symbol = -1;
            for (int i = 0; i < header.length; i++)
                if (header[i].equalsIgnoreCase("Symbol") || header[i].equalsIgnoreCase("Issue_Sym_Id"))
                    symbol = i;
            if (symbol < 0)
                return;
            for (String[] record; (record = reader.readRecord()) != null;) {
                if (symbol < record.length && record[symbol].trim().length() != 0)
                    symbols.add(record[symbol].trim());
            }
        }
    }

    @Override
    ControlFlow execute(TransformContext ctx) {
        if (!symbols.contains(ctx.currentProfile().getSymbol()))
            return ControlFlow.NORMAL;
        String oldOpol = ctx.currentProfile().getOPOL();
        String newOpol = Compiler.getString(ctx, opol);
        if (newOpol.equals(oldOpol))
            return ControlFlow.NORMAL;

        String oldExchanges = ctx.currentProfile().getExchanges();
        if (oldExchanges.equals(oldOpol)) {
            ctx.copyProfile().setExchanges(newOpol);
        } else {
            List<String> exchanges = new ArrayList<>();
            for (int i = 0; i < oldExchanges.length();) {
                int j = oldExchanges.indexOf(';', i);
                if (j < 0)
                    j = oldExchanges.length();
                String exchange = oldExchanges.substring(i, j);
                if (!exchange.equals(oldOpol))
                    exchanges.add(exchange);
                i = j + 1;
            }
            if (newOpol.length() != 0)
                exchanges.add(newOpol);
            Collections.sort(exchanges);
            StringBuilder sb = new StringBuilder();
            for (String s : exchanges)
                sb.append(sb.length() == 0 ? "" : ";").append(s);
            ctx.copyProfile().setExchanges(sb.toString());
        }
        ctx.copyProfile().setOPOL(newOpol);
        incModificationCounter(ctx);
        return ControlFlow.NORMAL;
    }
}
