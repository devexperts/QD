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

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ReplaceExpression extends Expression<String> {
    private final boolean all;
    private final Object parameter;
    private final Pattern pattern;
    private final Object replacement;

    ReplaceExpression(Compiler compiler, boolean all) throws IOException {
        super(String.class);
        this.all = all;
        compiler.skipToken('(');
        parameter = compiler.readExpression();
        compiler.skipToken(',');
        pattern = Pattern.compile(Compiler.getString(null, compiler.readExpression()));
        compiler.skipToken(',');
        replacement = compiler.readExpression();
        compiler.skipToken(')');
        Compiler.getString(Compiler.newTestContext(), parameter); // Early check of expression constraints (data types)
        Compiler.getString(Compiler.newTestContext(), replacement); // Early check of expression constraints (data types)
    }

    @Override
    String evaluate(TransformContext ctx) {
        Matcher matcher = pattern.matcher(Compiler.getString(ctx, parameter));
        String actualReplacement = Compiler.getString(ctx, replacement);
        return all ? matcher.replaceAll(actualReplacement) : matcher.replaceFirst(actualReplacement);
    }
}
