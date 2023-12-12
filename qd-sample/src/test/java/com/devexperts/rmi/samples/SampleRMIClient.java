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
package com.devexperts.rmi.samples;

import com.devexperts.logging.Logging;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIRequest;

import java.util.Random;

public class SampleRMIClient {
    private static final Logging log = Logging.getLogging(SampleRMIClient.class);

    private static RMIEndpoint client;
    private static Random rnd = new Random();
    @SuppressWarnings("unchecked")
    private static RMIOperation<Void> print = DifferentServices.PrintService.PRINT;

    public static void main(String[] args) {
        initClient(args.length <= 0 ? "localhost:4567" : args[0], 1235);
    }

    private static void initClient(String address, int i) {

        double r = 2;
        double s = 12.56637;
        double result;
        RMIEndpoint.Builder builder = RMIEndpoint.newBuilder();
        builder.withName("Sample com.devexperts.rmi.RMIClient");
        builder.withSide(RMIEndpoint.Side.CLIENT);
        client = builder.build();
        client.connect(address);
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        RMIRequest<Double> reqOperation = client.getClient().createRequest(
            null, DifferentServices.CalculatorService.MULT, r, r);
        reqOperation.send();
        try {
            result = reqOperation.getBlocking();
        } catch (RMIException e) {
            log.error("operation \"MULT\" fall with Exception " + e.getType(), e);
            return;
        }

        RMIRequest<Void> print = client.getClient().createRequest(null, SampleRMIClient.print, "r * r = " + result +
            ", where r = " + r);
        print.send();

        reqOperation = client.getClient().createRequest(null, DifferentServices.CalculatorService.DIVIDE, s, result);
        reqOperation.send();
        try {
            result = reqOperation.getBlocking();
        } catch (RMIException e) {
            log.error("operation \"DIVIDE\" fall with Exception " + e.getType(), e);
            return;
        }
        print = client.getClient().createRequest(null, SampleRMIClient.print, "s / r^2 = " + result +
            ", where r = " + r + ", s = " + s);
        print.send();

        reqOperation = client.getClient().createRequest(null, DifferentServices.CalculatorService.SIN, result);
        reqOperation.send();
        try {
            result = reqOperation.getBlocking();
        } catch (RMIException e) {
            log.error("operation \"SIN\" fall with Exception " + e.getType(), e);
            return;
        }
        print = client.getClient().createRequest(null, SampleRMIClient.print,
            "SIN(s/r^2) = " + result + ", where r = " + r + ", s = " + s);
        print.send();

        while (true) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                return;
            }
            RMIOperation<Double> operation = getRandomOperation();
            StringBuilder text = new StringBuilder();
            if (operation.getMethodName().equals("SIN")) {
                reqOperation = client.getClient().createRequest(null, operation, rnd.nextDouble() * 100);
                text.append("a = ").append(reqOperation.getParameters()[0])
                    .append("; SIN(a) = ");
            } else {
                reqOperation = client.getClient().createRequest(
                    null, operation, rnd.nextDouble() * 100, rnd.nextDouble() * 100);
                text.append("a = ").append(reqOperation.getParameters()[0])
                    .append(", b = ").append(reqOperation.getParameters()[1]).append("; ")
                    .append(operation.getMethodName()).append("(a, b) = ");
            }
            reqOperation.send();
            try {
                result = reqOperation.getBlocking();
            } catch (RMIException e) {
                log.error("operation \"" + reqOperation.getOperation().getMethodName() +
                    "\" fall with Exception " + e.getType(), e);
                return;
            }
            text.append(result);
            print = client.getClient().createRequest(null, SampleRMIClient.print, text.toString());
            print.send();

        }
    }

    private static RMIOperation<Double> getRandomOperation() {
        switch (rnd.nextInt(4)) {
        case 0:
            return DifferentServices.CalculatorService.PLUS;
        case 1:
            return DifferentServices.CalculatorService.MULT;
        case 2:
            return DifferentServices.CalculatorService.DIVIDE;
        case 3:
            return DifferentServices.CalculatorService.SIN;
        default:
            return null;
        }
    }
}
