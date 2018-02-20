/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.news.test;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javax.annotation.Nonnull;

import com.devexperts.rmi.RMIEndpoint;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.model.ObservableListModelListener;
import com.dxfeed.news.*;

public class NewsClient {
    public static void main(String[] args) throws InterruptedException {
        RMIEndpoint rmiEndpoint = RMIEndpoint.newBuilder()
            .withSide(RMIEndpoint.Side.CLIENT)
            .withRole(DXEndpoint.Role.FEED)
            .build();
        rmiEndpoint.getClient().setDefaultExecutor(Executors.newFixedThreadPool(50, new DaemonThreadFactory("rmi")));
        DXEndpoint endpoint = rmiEndpoint.getDXEndpoint();
        endpoint.connect(args[0]);
        final NewsModel model = new NewsModel(endpoint.getFeed());
        model.getNewsList().addListener(new ObservableListModelListener<NewsSummary>() {
            int counter = 1;

            @Override
            public void modelChanged(Change<? extends NewsSummary> change) {
                System.out.println("News stories:");
                for (NewsSummary news : change.getSource())
                    System.out.println(news.getSourceId() + ": " + news.getTitle());


                if (!change.getSource().isEmpty() && counter % 3 == 0) {
                    model.getNews(change.getSource().get(0)).whenDone(promise -> {
                        if (promise.hasResult())
                            System.out.println("Full news story: " + promise.getResult().getSummary().getSourceId() + ", " + promise.getResult().getContent());
                    });
                }
                counter++;
            }
        });
        model.setFilter(new NewsFilter(null, null, 3));
        model.setLive(true);
        Thread.sleep(100000);
        rmiEndpoint.close();
    }

    private static class DaemonThreadFactory implements ThreadFactory {
        private final String name;
        private static int counter = 0;

        DaemonThreadFactory(String name) {
            this.name = name;
        }

        @Override
        public Thread newThread(@Nonnull Runnable r) {
            Thread t = new Thread(r, name + "-" + counter++);
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}
