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

import com.devexperts.qd.util.QDConfig;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileField;
import com.dxfeed.ipf.InstrumentProfileReader;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles and executes instrument profile transform programs.
 * This class is essentially an implementation, see {@link InstrumentProfileTransform} class for official public API.
 */
class Compiler {

    private Tokenizer tokenizer;
    private int breakAllowance;
    private CompileContext context;

    final List<String> lines;
    private final Statement mainStatement;

    // ========== aka "Public API" of Compiler ==========

    Compiler(Reader reader, boolean singleStatement) throws IOException, TransformCompilationException {
        tokenizer = new Tokenizer(reader);
        breakAllowance = 0;
        context = new CompileContext(null);
        for (InstrumentProfileField ipf : InstrumentProfileField.values())
            context.fields.put(ipf.name(), new FieldReference(ipf.name(), ipf.getType()));
        lines = tokenizer.lines;
        try {
            Statement statement = singleStatement ? readStatement(true) : readBlockStatement(Tokenizer.TT_EOF);
            tokenizer.lines.add(tokenizer.line.toString());
            // Successful compilation - store result.
            mainStatement = statement == ControlFlowStatement.RETURN ? ControlFlowStatement.NOP : statement;
        } catch (RuntimeException e) {
            tokenizer.finishLastLine();
            String message = e.getMessage();
            if (e instanceof NumberFormatException)
                message = "Unparseable number: " + message;
            message = niceErrorMessage("Syntax", message, tokenizer.line, tokenizer.tokenLine, tokenizer.tokenPosition);
            TransformCompilationException tce = new TransformCompilationException(message);
            tce.setStackTrace(e.getStackTrace());
            throw tce;
        } finally {
            tokenizer = null;
            breakAllowance = 0;
            context = null;
        }
    }

    List<InstrumentProfile> transform(TransformContext ctx, List<InstrumentProfile> profiles) {
        if (mainStatement == ControlFlowStatement.NOP || profiles.isEmpty())
            return profiles;
        ctx.reset();
        List<InstrumentProfile> transformed = new ArrayList<>(profiles.size());
        for (InstrumentProfile ip : profiles) {
            ctx.setCurrentProfile(ip, false);
            if (mainStatement.execute(ctx) != Statement.ControlFlow.DELETE)
                transformed.add(ctx.currentProfile());
        }
        return transformed;
    }

    InstrumentProfile transform(TransformContext ctx, InstrumentProfile profile) {
        if (mainStatement == ControlFlowStatement.NOP)
            return profile;
        ctx.reset();
        ctx.setCurrentProfile(profile, false);
        return (mainStatement.execute(ctx) != Statement.ControlFlow.DELETE) ? ctx.currentProfile() : null;
    }

    boolean transformInSitu(TransformContext ctx, InstrumentProfile profile) {
        if (mainStatement == ControlFlowStatement.NOP)
            return false;
        ctx.reset();
        ctx.setCurrentProfile(profile, true);
        mainStatement.execute(ctx);
        return ctx.isCurrentProfileModified();
    }

    List<String> getStatistics(TransformContext ctx) {
        int min = 0;
        while (min < lines.size() && lines.get(min).isEmpty() && ctx.getModificationCounter(min) == 0)
            min++;
        int max = lines.size();
        while (max > min && lines.get(max - 1).isEmpty() && ctx.getModificationCounter(max - 1) == 0)
            max--;
        if (max == min)
            return Collections.emptyList();
        String indent = "      ";
        List<String> stats = new ArrayList<>(max - min);
        for (int i = min; i < max; i++) {
            String cnt = Integer.toString(ctx.getModificationCounter(i));
            stats.add("[" + indent.substring(Math.min(cnt.length(), indent.length())) + cnt + "]  " + lines.get(i));
        }
        return stats;
    }

    // ========== Reading, Parsing, Stats Gathering and Error Reporting Implementation ==========

    private int nextToken() throws IOException {
        return tokenizer.nextToken();
    }

    private void pushBack() {
        tokenizer.pushBack();
    }

    int getTokenLine() {
        return tokenizer.tokenLine;
    }

    void skipToken(char token) throws IOException {
        if (nextToken() != token)
            throw new IllegalArgumentException("Token '" + token + "' expected");
    }

    private boolean checkCharacter(char c) throws IOException {
        if (tokenizer.peekNextChar() != c)
            return false;
        tokenizer.skipNextChar();
        return true;
    }

    private void skipCharacter(char c) throws IOException {
        if (!checkCharacter(c)) {
            tokenizer.tokenPosition++;
            throw new IllegalArgumentException("Character '" + c + "' expected");
        }
    }

    private IllegalArgumentException unexpectedToken() {
        int t = tokenizer.ttype;
        double d = tokenizer.nval;
        String s = t == Tokenizer.TT_EOF ? "EOF" :
            t == Tokenizer.TT_NUMBER ? (d == (double) (int) d ? Integer.toString((int) d) : Double.toString(d)) :
            t == Tokenizer.TT_WORD ? tokenizer.sval :
            t == '"' ? '"' + tokenizer.sval + '"' :
            t == '\'' ? "'" + tokenizer.sval + "'" :
            "'" + (char) t + "'";
        return new IllegalArgumentException("Unexpected token " + s);
    }

    private static String niceErrorMessage(String prefix, String message, CharSequence line, int tokenLine, int tokenPosition) {
        // Construct copy of transform line filled with whitespace but with preserved tabs to properly place position marker.
        StringBuilder marker = new StringBuilder(line);
        marker.setLength(tokenPosition + 1);
        for (int i = 0; i < marker.length(); i++)
            if (marker.charAt(i) == 0 || marker.charAt(i) > ' ')
                marker.setCharAt(i, ' ');
        marker.setCharAt(tokenPosition, '^');
        // Construct "nice" error message with source code excerpt and error marker.
        return prefix + " error on line " + tokenLine + ": " + message + "\n" + line + "\n" + marker;
    }


    // ========== Statement Parsing ==========

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "return", "break",
        "if", "else",
        "switch", "case", "default",
        "retainFields",
        "delete", "primary", "rename", "cmeproduct", "fixopol", "osi",
        "hasItem", "nothasItem", "like", "notlike", "in", "notin",
        "true", "false",
        "boolean", "date", "number", "string",
        "addItem", "removeItem",
        "replaceAll", "replaceFirst",
        "sysdate", "bs", "bsopt", "fut", "futopt", "spread",
        "getDayOfWeek", "setDayOfWeek", "getDayOfMonth", "setDayOfMonth", "isTrading", "findTrading"
    ));

    private static boolean isField(String name) {
        if (InstrumentProfileField.find(name) != null)
            return true;
        if (KEYWORDS.contains(name))
            return false;
        if (!Character.isJavaIdentifierStart(name.charAt(0)))
            return false;
        for (int i = 1; i < name.length(); i++)
            if (!Character.isJavaIdentifierPart(name.charAt(i)))
                return false;
        return true;
    }

    private FieldReference getFieldReference(String name) {
        for (CompileContext cc = context; cc != null; cc = cc.parent) {
            FieldReference field = cc.fields.get(name);
            if (field != null)
                return field;
        }
        if (!InstrumentProfileTransform.ALLOW_UNDECLARED_FIELD_ACCESS)
            throw new IllegalArgumentException("Undeclared field " + name);
        FieldReference field = new FieldReference(name, null);
        context.fields.put(name, field);
        return field;
    }

    private Statement readStatement(boolean allowDeclaration) throws IOException {
        int token = nextToken();
        if (token == ';')
            return ControlFlowStatement.NOP;
        if (token == '{')
            return readBlockStatement('}');
        if (token == Tokenizer.TT_WORD) {
            // General language keywords and constructs.
            if (tokenizer.sval.equals("boolean"))
                return readDeclarationStatement(allowDeclaration, Boolean.class);
            if (tokenizer.sval.equals("date"))
                return readDeclarationStatement(allowDeclaration, Date.class);
            if (tokenizer.sval.equals("number"))
                return readDeclarationStatement(allowDeclaration, Double.class);
            if (tokenizer.sval.equals("string"))
                return readDeclarationStatement(allowDeclaration, String.class);
            if (tokenizer.sval.equals("break")) {
                if (breakAllowance == 0)
                    throw new IllegalArgumentException("break outside switch");
                skipToken(';');
                return ControlFlowStatement.BREAK;
            }
            if (tokenizer.sval.equals("return")) {
                skipToken(';');
                return ControlFlowStatement.RETURN;
            }
            if (tokenizer.sval.equals("if"))
                return readIfStatement();
            if (tokenizer.sval.equals("switch"))
                return readSwitchStatement();
            if (tokenizer.sval.equals("retainFields"))
                return readRetainFieldsStatement();
            // Here go custom hard-coded "procedures". They perform their own parsing of parameters.
            if (tokenizer.sval.equals("delete"))
                return new DeleteStatement(this);
            if (tokenizer.sval.equals("primary"))
                return new PrimaryStatement(this);
            if (tokenizer.sval.equals("rename"))
                return new RenameStatement(this);
            if (tokenizer.sval.equals("cmeproduct"))
                return new CMEProductStatement(this);
            if (tokenizer.sval.equals("fixopol"))
                return new FixOPOLStatement(this);
            if (tokenizer.sval.equals("osi"))
                return new OSIStatement(this);
            // Not a keyword - assume field reference.
            if (isField(tokenizer.sval))
                return readAssignmentStatement(getFieldReference(tokenizer.sval));
        }
        throw unexpectedToken();
    }

    private Statement readBlockStatement(int endToken) throws IOException {
        ArrayList<Statement> statements = new ArrayList<>();
        context = new CompileContext(context);
        while (nextToken() != endToken) {
            pushBack();
            Statement statement = readStatement(true);
            if (statement != ControlFlowStatement.NOP)
                statements.add(statement);
        }
        context = context.parent;
        if (statements.isEmpty())
            return ControlFlowStatement.NOP;
        if (statements.size() == 1)
            return statements.get(0);
        return new BlockStatement(statements.toArray(new Statement[statements.size()]));
    }

    private Statement readDeclarationStatement(boolean allowDeclaration, Class<?> type) throws IOException {
        // Field type declaration is not actually a statement - it is a field type declaration.
        // We use Statement together with ControlFlowStatement.NOP in order to simplify code.
        if (!allowDeclaration)
            throw new IllegalArgumentException("Not a statement");
        while (true) {
            int token = nextToken();
            if (token != Tokenizer.TT_WORD || !isField(tokenizer.sval))
                throw unexpectedToken();
            context.fields.put(tokenizer.sval, new FieldReference(tokenizer.sval, type));
            token = nextToken();
            if (token == ';')
                break;
            if (token != ',')
                throw unexpectedToken();
        }
        return ControlFlowStatement.NOP;
    }

    private Statement readIfStatement() throws IOException {
        skipToken('(');
        Object parameter = readExpression();
        getBoolean(newTestContext(), parameter); // Early check of expression constraints (data types)
        skipToken(')');
        Statement thenStatement = readStatement(false);
        Statement elseStatement = ControlFlowStatement.NOP;
        if (nextToken() == Tokenizer.TT_WORD && tokenizer.sval.equals("else"))
            elseStatement = readStatement(false);
        else
            pushBack();
        if (thenStatement == elseStatement) // Can happen for ControlFlowStatement variants.
            return thenStatement;
        return new IfStatement(parameter, thenStatement, elseStatement);
    }

    private Statement readSwitchStatement() throws IOException {
        skipToken('(');
        Object parameter = readExpression();
        Class<?> parameterType = getType(parameter);
        if (parameterType != String.class && parameterType != Double.class)
            throw new IllegalArgumentException("String or Double expression expected");
        skipToken(')');
        skipToken('{');
        Map<Object, Statement> cases = new HashMap<>();
        Statement defaultStatement = null;
        while (true) {
            int token = nextToken();
            if (token == '}')
                break;
            if (token == Tokenizer.TT_WORD) {
                if (tokenizer.sval.equals("case")) {
                    Set<Object> keys = new HashSet<>();
                    while (true) {
                        Object key = getValue(null, readExpression(), parameterType);
                        if (cases.containsKey(key) || keys.contains(key))
                            throw new IllegalArgumentException("Duplicate label \"" + key + "\"");
                        keys.add(key);
                        token = nextToken();
                        if (token == ':')
                            break;
                        if (token != ',')
                            throw unexpectedToken();
                    }
                    breakAllowance++;
                    Statement statement = readStatement(false);
                    breakAllowance--;
                    if (statement == ControlFlowStatement.BREAK)
                        statement = ControlFlowStatement.NOP;
                    for (Object key : keys)
                        cases.put(key, statement);
                    continue;
                }
                if (tokenizer.sval.equals("default")) {
                    if (defaultStatement != null)
                        throw new IllegalArgumentException("Duplicate default label");
                    skipToken(':');
                    breakAllowance++;
                    Statement statement = readStatement(false);
                    breakAllowance--;
                    if (statement == ControlFlowStatement.BREAK)
                        statement = ControlFlowStatement.NOP;
                    defaultStatement = statement;
                    continue;
                }
            }
            throw unexpectedToken();
        }
        if (defaultStatement == null)
            defaultStatement = ControlFlowStatement.NOP;
        if (defaultStatement == ControlFlowStatement.NOP || defaultStatement == ControlFlowStatement.RETURN)
            for (Iterator<Statement> it = cases.values().iterator(); it.hasNext();)
                if (it.next() == defaultStatement) // Can happen for ControlFlowStatement variants.
                    it.remove();
        if (cases.isEmpty())
            return defaultStatement == ControlFlowStatement.BREAK ? ControlFlowStatement.NOP : defaultStatement;
        return new SwitchStatement(parameter, parameterType, cases, defaultStatement);
    }

    private Statement readRetainFieldsStatement() throws IOException {
        EnumSet<InstrumentProfileField> removeStandard = EnumSet.allOf(InstrumentProfileField.class);
        HashSet<String> retainCustom = new HashSet<>();
        List<InstrumentProfileField> removeStandardList = new ArrayList<>();
        Statement statement = new RetainFieldsStatement(this, removeStandardList, retainCustom);
        skipToken('(');
        if (nextToken() != ')') {
            pushBack();
            while (true) {
                int token = nextToken();
                if (token != Tokenizer.TT_WORD || !isField(tokenizer.sval))
                    throw unexpectedToken();
                InstrumentProfileField ipf = InstrumentProfileField.find(tokenizer.sval);
                if (ipf != null)
                    removeStandard.remove(ipf);
                else
                    retainCustom.add(tokenizer.sval);
                token = nextToken();
                if (token == ')')
                    break;
                if (token != ',')
                    throw unexpectedToken();
            }
        }
        skipToken(';');
        removeStandardList.addAll(removeStandard);
        return statement;
    }

    private Statement readAssignmentStatement(FieldReference field) throws IOException {
        int token = nextToken();
        if (token == '=')
            return new AssignmentStatement(this, field, -1);
        if (!MathExpression.isOperator(token))
            throw unexpectedToken();
        skipCharacter('=');
        return new AssignmentStatement(this, field, token);
    }


    // ========== Expression Parsing ==========
    // NOTE: methods below are ordered by the precedence of corresponding operators from lowest to highest.

    Object readExpression() throws IOException {
        return readConditionalExpression();
    }

    private Object readConditionalExpression() throws IOException {
        Object expression = readConditionalOrExpression();
        if (nextToken() == '?') {
            Object first = readExpression();
            skipToken(':');
            Object second = readExpression();
            return new ConditionalExpression(expression, first, second);
        }
        pushBack();
        return expression;
    }

    private Object readConditionalOrExpression() throws IOException {
        Object expression = readConditionalAndExpression();
        while (nextToken() == '|') {
            skipCharacter('|');
            expression = new ConditionalOrExpression(expression, readConditionalAndExpression());
        }
        pushBack();
        return expression;
    }

    private Object readConditionalAndExpression() throws IOException {
        Object expression = readEqualityExpression();
        while (nextToken() == '&') {
            skipCharacter('&');
            expression = new ConditionalAndExpression(expression, readEqualityExpression());
        }
        pushBack();
        return expression;
    }

    private Object readEqualityExpression() throws IOException {
        Object expression = readRelationalExpression();
        for (int token = nextToken(); token == '=' || token == '!'; token = nextToken()) {
            skipCharacter('=');
            expression = new EqualityExpression(expression, readRelationalExpression());
            if (token == '!')
                expression = new NotExpression(expression);
        }
        pushBack();
        return expression;
    }

    private Object readRelationalExpression() throws IOException {
        Object expression = readAdditiveExpression();
        int token = nextToken();
        if (token == Tokenizer.TT_WORD) {
            // Read special subclasses of "RelationalExpression".
            if (tokenizer.sval.equals("hasItem"))
                return new HasItemExpression(expression, readAdditiveExpression());
            if (tokenizer.sval.equals("nothasItem"))
                return new NotExpression(new HasItemExpression(expression, readAdditiveExpression()));
            if (tokenizer.sval.equals("like"))
                return new LikeExpression(expression, getString(null, readAdditiveExpression()));
            if (tokenizer.sval.equals("notlike"))
                return new NotExpression(new LikeExpression(expression, getString(null, readAdditiveExpression())));
            if (tokenizer.sval.equals("in"))
                return readInExpression(expression);
            if (tokenizer.sval.equals("notin"))
                return new NotExpression(readInExpression(expression));
        }
        int operator;
        if (token == '<') {
            operator = checkCharacter('=') ? RelationalExpression.LE : RelationalExpression.LT;
        } else if (token == '>') {
            operator = checkCharacter('=') ? RelationalExpression.GE : RelationalExpression.GT;
        } else {
            pushBack();
            return expression;
        }
        return new RelationalExpression(expression, operator, readAdditiveExpression());
    }

    private Object readInExpression(Object parameter) throws IOException {
        // It is a subclass of "RelationalExpression". Code is extracted to make it cleaner.
        int token = nextToken();
        if (token == '(')
            return readSimpleInExpression(parameter);
        if (token == Tokenizer.TT_WORD && tokenizer.sval.equals("ipf"))
            return readIpfInExpression(parameter);
        throw unexpectedToken();
    }

    private Object readSimpleInExpression(Object parameter) throws IOException {
        if (nextToken() == ')')
            return Boolean.FALSE;
        pushBack();
        Class<?> parameterType = getType(parameter);
        HashSet<Object> values = new HashSet<>();
        Object orExpression = null;
        while (true) {
            Object expression = readExpression();
            try {
                values.add(getValue(null, expression, parameterType));
            } catch (Exception e) {
                Object eq = new EqualityExpression(parameter, expression);
                orExpression = orExpression == null ? eq : new ConditionalOrExpression(orExpression, eq);
            }
            int token = nextToken();
            if (token == ')')
                break;
            if (token != ',')
                throw unexpectedToken();
        }
        Object inExpression = values.isEmpty() ? null : new InExpression(parameterType, parameter, values);
        return inExpression == null ? orExpression : orExpression == null ? inExpression : new ConditionalOrExpression(inExpression, orExpression);
    }

    private Object readIpfInExpression(Object parameter) throws IOException {
        if (getType(parameter) != String.class)
            throw new IllegalArgumentException("String expression expected");
        skipToken('(');
        String spec = getString(null, readExpression());
        skipToken(')');
        List<String> props = new ArrayList<>();
        QDConfig.parseProperties("[" + spec + "]", props);
        String address = QDConfig.unescape(props.get(0)); // allow for escaped special characters in address
        Config config = new Config();
        QDConfig.setProperties(config, props.subList(1, props.size()));
        HashSet<String> values = new HashSet<>();
        for (InstrumentProfile profile : new InstrumentProfileReader().readFromFile(address, config.user, config.password))
            values.add(profile.getSymbol());
        return values.isEmpty() ? Boolean.FALSE : new InExpression(String.class, parameter, values);
    }

    public static class Config {
        String user;
        String password;

        Config() {}

        // There are no getter methods for user and password by design, so that they do not show in a string representation.
        public void setUser(String user) {
            this.user = user;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    private Object readAdditiveExpression() throws IOException {
        Object expression = readMultiplicativeExpression();
        for (int token = nextToken(); token == '+' || token == '-'; token = nextToken())
            expression = MathExpression.create(expression, token, readMultiplicativeExpression());
        pushBack();
        return expression;
    }

    private Object readMultiplicativeExpression() throws IOException {
        Object expression = readUnaryExpression();
        for (int token = nextToken(); token == '*' || token == '/' || token == '%'; token = nextToken())
            expression = MathExpression.create(expression, token, readUnaryExpression());
        pushBack();
        return expression;
    }

    private Object readUnaryExpression() throws IOException {
        int token = nextToken();
        if (token == '+')
            return MathExpression.create(getDouble(0), '+', readTerminalExpression());
        if (token == '-')
            return MathExpression.create(getDouble(0), '-', readTerminalExpression());
        if (token == '!')
            return new NotExpression(readTerminalExpression());
        pushBack();
        return readTerminalExpression();
    }

    private Object readTerminalExpression() throws IOException {
        int token = nextToken();
        if (token == '(') {
            Object expression = readExpression();
            skipToken(')');
            return expression;
        }
        if (token == '"' || token == '\'')
            return tokenizer.sval;
        if (token == Tokenizer.TT_NUMBER)
            return getDouble(tokenizer.nval);
        if (token == Tokenizer.TT_WORD) {
            // General language keywords and constructs.
            if (tokenizer.sval.equals("true"))
                return Boolean.TRUE;
            if (tokenizer.sval.equals("false"))
                return Boolean.FALSE;
            if (tokenizer.sval.equals("boolean"))
                return new TypeCastExpression(this, Boolean.class);
            if (tokenizer.sval.equals("date"))
                return new TypeCastExpression(this, Date.class);
            if (tokenizer.sval.equals("number"))
                return new TypeCastExpression(this, Double.class);
            if (tokenizer.sval.equals("string"))
                return new TypeCastExpression(this, String.class);
            // Here go custom hard-coded "functions". They perform their own parsing of parameters.
            if (tokenizer.sval.equals("addItem"))
                return new AddItemExpression(this);
            if (tokenizer.sval.equals("removeItem"))
                return new RemoveItemExpression(this);
            if (tokenizer.sval.equals("replaceAll"))
                return new ReplaceExpression(this, true);
            if (tokenizer.sval.equals("replaceFirst"))
                return new ReplaceExpression(this, false);
            if (tokenizer.sval.equals("sysdate"))
                return SysdateExpression.SYSDATE;
            if (tokenizer.sval.equals("bs"))
                return SymbolCategoryExpression.BS;
            if (tokenizer.sval.equals("bsopt"))
                return SymbolCategoryExpression.BSOPT;
            if (tokenizer.sval.equals("fut"))
                return SymbolCategoryExpression.FUT;
            if (tokenizer.sval.equals("futopt"))
                return SymbolCategoryExpression.FUTOPT;
            if (tokenizer.sval.equals("spread"))
                return SymbolCategoryExpression.SPREAD;
            if (tokenizer.sval.equals("getDayOfWeek"))
                return new GetDayOfWeekExpression(this);
            if (tokenizer.sval.equals("setDayOfWeek"))
                return new SetDayOfWeekExpression(this);
            if (tokenizer.sval.equals("getDayOfMonth"))
                return new GetDayOfMonthExpression(this);
            if (tokenizer.sval.equals("setDayOfMonth"))
                return new SetDayOfMonthExpression(this);
            if (tokenizer.sval.equals("isTrading"))
                return new IsTradingExpression(this);
            if (tokenizer.sval.equals("findTrading"))
                return new FindTradingExpression(this);
            // Not a keyword - assume field reference.
            if (isField(tokenizer.sval))
                return getFieldReference(tokenizer.sval);
        }
        throw unexpectedToken();
    }


    // ========== Data Handling ==========
    // NOTE: methods below provide convenient and high-performance access and conversion of data.

    static TransformContext newTestContext() {
        TransformContext ctx = new TransformContext();
        ctx.setCurrentProfile(newTestProfile(), true);
        return ctx;
    }

    static InstrumentProfile newTestProfile() {
        InstrumentProfile ip = new InstrumentProfile();
        ip.setTradingHours("OPRA()");
        return ip;
    }

    private static final double[] POWERS = new double[15];
    static {
        POWERS[0] = 1;
        for (int i = 1; i < POWERS.length; i++)
            POWERS[i] = POWERS[i - 1] * 10;
    }

    static double round(double d) {
        double ad = Math.abs(d);
        for (int i = 0; i < POWERS.length; i++)
            if (ad < POWERS[i]) {
                double power = POWERS[POWERS.length - i - 1];
                return Math.floor(d * power + 0.5) / power;
            }
        return d;
    }

    private static final Double[] DOUBLE_CACHE = new Double[20000];

    static Double getDouble(double d) {
        int n4 = (int) (d * 4) + 4000;
        if (n4 == d * 4 + 4000 && n4 >= 0 && n4 < DOUBLE_CACHE.length) {
            Double cached = DOUBLE_CACHE[n4]; // Atomic read.
            if (cached == null || cached != d)
                DOUBLE_CACHE[n4] = cached = d;
            return cached;
        }
        return d;
    }

    private static final Date[] DATE_CACHE = new Date[30000];

    static Date getDate(double dayId) {
        int id = (int) dayId;
        if (id >= 0 && id < DATE_CACHE.length) {
            Date cached = DATE_CACHE[id]; // Atomic read.
            if (cached == null || cached.getTime() != id * 86400000L)
                DATE_CACHE[id] = cached = new Date(id * 86400000L);
            return cached;
        }
        return new Date(id * 86400000L);
    }

    static int getDayId(Date value) {
        return (int) (value.getTime() / 86400000);
    }

    static Boolean parseBoolean(String value) {
        if (value == null || value.isEmpty())
            return Boolean.FALSE;
        if ("true".equalsIgnoreCase(value))
            return Boolean.TRUE;
        if ("false".equalsIgnoreCase(value))
            return Boolean.FALSE;
        throw new IllegalArgumentException("Boolean expression expected");
    }

    static String formatBoolean(Boolean value) {
        return value ? "true" : "";
    }

    static Date parseDate(String value) {
        try {
            return getDate(InstrumentProfileField.parseDate(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Date expression expected");
        }
    }

    static String formatDate(Date value) {
        return InstrumentProfileField.formatDate(getDayId(value));
    }

    static Double parseDouble(String value) {
        try {
            return getDouble(InstrumentProfileField.parseNumber(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Double expression expected");
        }
    }

    static String formatDouble(Double value) {
        return InstrumentProfileField.formatNumber(value);
    }

    static Boolean toBoolean(Object value) {
        if (value instanceof Boolean)
            return (Boolean) value;
        if (value instanceof String)
            return parseBoolean((String) value);
        throw new IllegalArgumentException("Boolean expression expected");
    }

    static Date toDate(Object value) {
        if (value instanceof Date)
            return (Date) value;
        if (value instanceof String)
            return parseDate((String) value);
        throw new IllegalArgumentException("Date expression expected");
    }

    static Double toDouble(Object value) {
        if (value instanceof Double)
            return (Double) value;
        if (value instanceof String)
            return parseDouble((String) value);
        throw new IllegalArgumentException("Double expression expected");
    }

    static String toString(Object value) {
        if (value instanceof String)
            return (String) value;
        if (value instanceof Boolean)
            return formatBoolean((Boolean) value);
        if (value instanceof Date)
            return formatDate((Date) value);
        if (value instanceof Double)
            return formatDouble((Double) value);
        throw new IllegalArgumentException("String expression expected");
    }

    static Class<?> getType(Object parameter) {
        if (parameter instanceof FieldReference)
            return ((FieldReference) parameter).type;
        if (parameter instanceof Expression)
            return ((Expression<?>) parameter).type;
        return parameter.getClass();
    }

    static Class<?> getType(Object parameter1, Object parameter2) {
        Class<?> c1 = getType(parameter1);
        Class<?> c2 = getType(parameter2);
        if (c1 == c2)
            return c1;
        if (c1 == String.class)
            return c2;
        if (c2 == String.class)
            return c1;
        throw new IllegalArgumentException("Incompatible types");
    }

    private static Object getValue(TransformContext ctx, Object parameter) {
        if (parameter instanceof FieldReference) {
            if (ctx == null)
                throw new IllegalArgumentException("Constant expression expected");
            return ((FieldReference) parameter).getValue(ctx.currentProfile());
        }
        if (parameter instanceof Expression)
            return ((Expression<?>) parameter).evaluate(ctx);
        return parameter;
    }

    static Object getValue(TransformContext ctx, Object parameter, Class<?> type) {
        Object value = getValue(ctx, parameter);
        if (type == Boolean.class)
            return toBoolean(value);
        if (type == Date.class)
            return toDate(value);
        if (type == Double.class)
            return toDouble(value);
        if (type == String.class)
            return toString(value);
        throw new IllegalArgumentException("Unknown type " + type.getName());
    }

    static Boolean getBoolean(TransformContext ctx, Object parameter) {
        return toBoolean(getValue(ctx, parameter));
    }

    static Date getDate(TransformContext ctx, Object parameter) {
        return toDate(getValue(ctx, parameter));
    }

    static Double getDouble(TransformContext ctx, Object parameter) {
        return toDouble(getValue(ctx, parameter));
    }

    static String getString(TransformContext ctx, Object parameter) {
        return toString(getValue(ctx, parameter));
    }
}
