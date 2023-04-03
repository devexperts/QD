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

import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.classloader.Function;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMITask;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class DifferentServices {

    private static final AtomicLong OPERATION_NUMERATOR = new AtomicLong();

    public static final PrintService PRINT_SERVICE = new PrintService();

    static class PrintService extends RMIService<Void> {

        private static final String NAME = "PrintService";

        static final RMIOperation<Void> PRINT = RMIOperation.valueOf(NAME, void.class, "PRINT", String.class);

        PrintService() {
            super(NAME);
        }

        @Override
        public void processTask(RMITask<Void> task) {
            OPERATION_NUMERATOR.incrementAndGet();
            task.setCancelListener(task1 -> task1.cancel(RMIExceptionType.CANCELLED_BEFORE_EXECUTION));
            if (task.getOperation().equals(PRINT)) {
                System.out.println(NAME + " prints: " + (task.getRequestMessage().getParameters().getObject())[0]);
                task.complete(null);
            } else {
                task.completeExceptionally(RMIExceptionType.OPERATION_NOT_PROVIDED, null);
            }
        }
    }

// --------------------------------------------------


    public static final CalculatorService CALCULATOR_SERVICE = new CalculatorService();

    public static class CalculatorService extends RMIService<Double> {

        static final String NAME = "CalculatorService";

        public static final RMIOperation<Double> PLUS =
            RMIOperation.valueOf(NAME, double.class, "PLUS", double.class, double.class);
        static final RMIOperation<Double> MULT =
            RMIOperation.valueOf(NAME, double.class, "MULT", double.class, double.class);
        static final RMIOperation<Double> DIVIDE =
            RMIOperation.valueOf(NAME, double.class, "divide", double.class, double.class);
        static final RMIOperation<Double> SIN =
            RMIOperation.valueOf(NAME, double.class, "SIN", double.class);

        public CalculatorService() {
            super(NAME);
        }

        @Override
        public void processTask(RMITask<Double> task) {
            OPERATION_NUMERATOR.incrementAndGet();
            task.setCancelListener(task1 -> task1.cancel(RMIExceptionType.CANCELLED_BEFORE_EXECUTION));
            if (task.getOperation().equals(PLUS)) {
                double result = (Double) task.getRequestMessage().getParameters().getObject()[0] +
                    (Double) task.getRequestMessage().getParameters().getObject()[1];
                task.complete(result);
            } else if (task.getOperation().equals(MULT)) {
                double result = (Double) task.getRequestMessage().getParameters().getObject()[0] *
                    (Double) task.getRequestMessage().getParameters().getObject()[1];
                task.complete(result);
            } else if (task.getOperation().equals(DIVIDE)) {
                double result = (Double) task.getRequestMessage().getParameters().getObject()[0] /
                    (Double) task.getRequestMessage().getParameters().getObject()[1];
                task.complete(result);
            } else if (task.getOperation().equals(SIN)) {
                double result = Math.sin((Double) task.getRequestMessage().getParameters().getObject()[0]);
                task.complete(result);
            } else {
                task.completeExceptionally(RMIExceptionType.OPERATION_NOT_PROVIDED, null);
            }
        }
    }

    // --------------------------------------------------

    public static final IntegralCalculatorService INTEGRAL_CALCULATOR_SERVICE = new IntegralCalculatorService();

    public static class IntegralCalculatorService extends RMIService<Double> {
        private static final int N = 100000;
        private static final Random RND = new Random();

        private static final String NAME = "IntegralCalculatorService";

        public static final RMIOperation<Double> CALC =
            RMIOperation.valueOf(NAME, double.class, "CALC", double.class, double.class, Function.class);

        IntegralCalculatorService() {
            super(NAME);
        }

        @Override
        public void processTask(RMITask<Double> task) {
            task.setCancelListener(task1 -> task1.cancel(RMIExceptionType.CANCELLED_BEFORE_EXECUTION));
            if (task.getOperation().equals(CALC)) {
                double result = calcIntegral((Double) task.getRequestMessage().getParameters().getObject()[0],
                    (Double) task.getRequestMessage().getParameters().getObject()[1],
                    (Function) task.getRequestMessage().getParameters().getObject()[2]);
                task.complete(result);
            }else {
                task.completeExceptionally(RMIExceptionType.OPERATION_NOT_PROVIDED, null);
            }
        }

        private double calcIntegral(double startCord, double endCord, Function function) {
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

    // --------------------------------------------------

    public static final SomeService SOME_SERVICE = new SomeService();

    private static class SomeService extends RMIService<Double> {
        private RMITask<Double> sumTask;
        private static final String NAME = "someService";

        private static final RMIOperation<Double> sum =
            RMIOperation.valueOf(NAME, double.class, "sum");
        private static final RMIOperation<Double> sumAndSet =
            RMIOperation.valueOf(NAME, double.class, "sumAndSet", double.class, double.class);

        SomeService() {
            super(NAME);
        }

        @Override
        public void processTask(RMITask<Double> task) {
            task.setCancelListener(task1 -> task1.cancel(RMIExceptionType.CANCELLED_BEFORE_EXECUTION));
            if (task.getOperation().equals(sum)) {
                sumTask = RMITask.current(double.class);
                assert sumTask == task;
                while (!task.isCompleted()) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        task.completeExceptionally(RMIExceptionType.APPLICATION_ERROR, e);
                    }
                }
            } else if (task.getOperation().equals(sumAndSet)) {
                double result = (Double) task.getRequestMessage().getParameters().getObject()[0] +
                    (Double) task.getRequestMessage().getParameters().getObject()[1];
                sumTask.complete(result);
                if (!sumTask.isCompleted())
                    task.completeExceptionally(RMIExceptionType.CANCELLED_DURING_EXECUTION, null);
                task.complete(result);
            } else {
                task.completeExceptionally(RMIExceptionType.OPERATION_NOT_PROVIDED, null);
            }
        }
    }
}
