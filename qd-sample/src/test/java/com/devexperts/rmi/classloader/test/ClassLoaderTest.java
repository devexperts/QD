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
package com.devexperts.rmi.classloader.test;

import com.devexperts.io.SerialClassContext;
import com.devexperts.logging.Logging;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.classloader.Function;
import com.devexperts.rmi.samples.DifferentServices;
import com.devexperts.rmi.task.RMIServiceImplementation;
import com.devexperts.rmi.test.NTU;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Random;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(TraceRunner.class)
public class ClassLoaderTest {
    private static final Logging log = Logging.getLogging(ClassLoaderTest.class);

    private static final String CLASS_PATH = "target/for-classloader-test/";
    private static final String CLASS_NAME = "LogFunction";
    private static final String FILE_PROTOCOL_PREFIX = "file:";
    private RMIEndpoint server;
    private RMIEndpoint client;

    // random port offset is static to make sure that all tests here use the same offset
    private static final int PORT_00 = (100 + new Random().nextInt(300)) * 100;

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
    }

    @After
    public void tearDown() {
        server.close();
        client.close();
        ThreadCleanCheck.after();
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testIntegralCalculation() throws ClassNotFoundException, MalformedURLException {

        IntegralCalculator calculator = new IntegralCalculatorLeftRectangle();
        double start = 1;
        double end = 2;
        double res = analyticCalcIntegralLn(start, end);
        Function function = null;

        String path = FILE_PROTOCOL_PREFIX + new File("./").getAbsolutePath();
        path = path.substring(0, path.length() - 1) + CLASS_PATH;
        URL url = new URL(path);
        URLClassLoader urlClassLoader = new URLClassLoader(new URL[] {url}, ClassLoader.getSystemClassLoader());

        try {
            Class clazz1 = urlClassLoader.loadClass(CLASS_NAME);
            function = (Function) clazz1.newInstance();
            assertTrue("CustomClassLoader is not working", calculator.calcIntegral(start, end, function) - res < 0.01);
        } catch (InstantiationException | IllegalAccessException | RMIException e) {
            fail(e.getMessage());
        }

        server = RMIEndpoint.createEndpoint(RMIEndpoint.Side.SERVER);
        client = RMIEndpoint.createEndpoint(RMIEndpoint.Side.CLIENT);
        client.getClient().setRequestRunningTimeout(60 * 1000);
        NTU.connect(server, ":" + (41 + PORT_00));
        NTU.connect(client, "127.0.0.1:" + (41 + PORT_00));

        server.getServer().export(DifferentServices.INTEGRAL_CALCULATOR_SERVICE);
        @SuppressWarnings("unchecked")
        RMIRequest<Double> resIntegral = client.getClient().createRequest(
            null, DifferentServices.IntegralCalculatorService.CALC, start, end, new LinearFunction(1, 0));
        resIntegral.send();
        try {
            assertTrue(resIntegral.getBlocking() - analyticCalcIntegralLinear(start, end) < 0.01);

        } catch (RMIException e) {
            fail(e.getMessage());
        }

        //used classLoader
        log.info("used classLoader");
        RMIServiceImplementation service = new RMIServiceImplementation<>(new IntegralCalculatorLeftRectangle(),
            IntegralCalculator.class, "integral");
        service.setSerialClassContext(SerialClassContext.getDefaultSerialContext(urlClassLoader));
        server.getServer().export(service);
        IntegralCalculator integral = client.getClient().getProxy(IntegralCalculator.class, "integral");
        try {
            assertTrue(integral.calcIntegral(start, end, function) - analyticCalcIntegralLn(start, end) < 0.01);
        } catch (RMIException e) {
            fail(e.getMessage());
        }
    }

    private double analyticCalcIntegralLinear(double start, double end) {
        return end * end * LinearFunction.a / 2 + end * LinearFunction.b -
            (start * start * LinearFunction.a / 2 + start * LinearFunction.b);
    }

    private double analyticCalcIntegralLn(double start, double end) {
        return end * Math.log(end) - end - (start * Math.log(start) - start);
    }

    private static class LinearFunction implements Function {

        private static final long serialVersionUID = -2508035266098818713L;
        static double a;
        static double b;

        LinearFunction(double a, double b) {
            LinearFunction.a = a;
            LinearFunction.b = b;
        }

        @Override
        public double getFunctionValue(double x) {
            return a * x + b;
        }
    }

    interface IntegralCalculator {

        double calcIntegral(double startCord, double endCord, Function function) throws RMIException;
    }

    private static class IntegralCalculatorLeftRectangle implements IntegralCalculator {

        private static final int N = 100000;
        private static final Random RND = new Random();

        @Override
        public double calcIntegral(double startCord, double endCord, Function function) {
            double res = 0;
            double x;
            for (int i = 0; i < N; i++) {
                x = startCord + RND.nextDouble() * (endCord - startCord);
                res += function.getFunctionValue(x);
            }
            res = res * (endCord - startCord) / N;
            return res;
        }
    }
}
