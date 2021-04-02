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
package com.devexperts.qd.sample;

import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMITask;
import com.devexperts.rmi.task.RMITaskCancelListener;
import com.dxfeed.api.DXFeed;
import com.dxfeed.event.market.Quote;
import com.dxfeed.promise.Promise;
import com.dxfeed.promise.PromiseHandler;

public class SampleRMIQuoteServer {
    public static void main(String[] args) throws InterruptedException {
        RMIEndpoint rmi = RMIEndpoint.createEndpoint(RMIEndpoint.Side.SERVER);
        rmi.connect(args[0]);
        rmi.getServer().export(new QuoteService("quoteService"));
        Thread.sleep(Long.MAX_VALUE);
    }

    private static class QuoteService extends RMIService<Quote> {
        private final RMIOperation<Quote> operation = RMIOperation.valueOf(serviceName, Quote.class, "getQuote", String.class); // the operation we're going to provide
        Promise<Quote> promise;

        QuoteService(String serviceName) {
            super(serviceName);
        }

        @Override
        public void processTask(final RMITask<Quote> task) {
            if (!operation.equals(task.getOperation())) {// Note, that RMIOperation defines signature-comparing equals method
                task.completeExceptionally(RMIExceptionType.OPERATION_NOT_PROVIDED, null);
                return;
            }
            // make asynchronous getLastEvent call
            String symbol = (String) task.getRequestMessage().getParameters().getObject()[0];
            promise = DXFeed.getInstance().getLastEventPromise(Quote.class, symbol);
            // complete task when async getLastEvent request is done
            // pay attention to handling both exceptional and normal completion
            promise.whenDone(new PromiseHandler<Quote>() {
                @Override
                public void promiseDone(Promise<? extends Quote> promise) {
                    if (promise.hasResult())
                        task.complete(promise.getResult());
                    else
                        task.completeExceptionally(promise.getException());
                }
            });
            // Listen to task cancellation to cancel the async getLastEvent request and confirm cancellation
            task.setCancelListener(new RMITaskCancelListener() {
                @Override
                public void taskCompletedOrCancelling(RMITask<?> task) {
                    promise.cancel(); // ignored if promise is already complete
                    task.cancel();
                }
            });
        }
    }
}
