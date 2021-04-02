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
package com.dxfeed.news.test;

import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.task.RMIServiceImplementation;
import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.model.ObservableListModelListener;
import com.dxfeed.news.News;
import com.dxfeed.news.NewsFilter;
import com.dxfeed.news.NewsKey;
import com.dxfeed.news.NewsList;
import com.dxfeed.news.NewsModel;
import com.dxfeed.news.NewsSummary;
import com.dxfeed.news.impl.NewsNotFoundException;
import com.dxfeed.news.impl.RemoteNewsService;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class NewsApiTest extends TestCase {

    private RMIEndpoint client;
    private RMIEndpoint server;
    private ExecutorService executor;


    // random port offset is static to make sure that all tests here use the same offset
    private static final int PORT_00 = (100 + new Random().nextInt(300)) * 100;

    private RMIServiceImplementation<RemoteNewsService> instance;

    @Override
    public void setUp() {
        ThreadCleanCheck.before();
        client = RMIEndpoint.newBuilder()
            .withSide(RMIEndpoint.Side.CLIENT)
            .withRole(DXEndpoint.Role.FEED)
            .build();
        server = RMIEndpoint.createEndpoint(RMIEndpoint.Side.SERVER);
    }

    @Override
    public void tearDown() {
        server.close();
        client.close();
        if (client.getDXEndpoint() != null)
            client.getDXEndpoint().close();
        if (executor != null)
            executor.shutdown();
        ThreadCleanCheck.after();
    }

    private void connectDefault(int port) {
        server.connect(":" + (PORT_00 + port));
        client.connect("127.0.0.1:" + (PORT_00 + port));
    }

    public void testProcessingNews() throws InterruptedException {
        final CountDownLatch processNews = new CountDownLatch(2);
        instance  =  new RMIServiceImplementation<>(new RemoteNewsServiceImpl(), RemoteNewsService.class);
        client = RMIEndpoint.newBuilder()
            .withSide(RMIEndpoint.Side.CLIENT)
            .withRole(DXEndpoint.Role.FEED)
            .build();
        executor = Executors.newFixedThreadPool(50, new DaemonThreadFactory("rmi"));
        client.getClient().setDefaultExecutor(executor);
        server.getServer().export(instance);
        connectDefault(10);
        final NewsModel model = new NewsModel(client.getDXEndpoint().getFeed());
        model.getNewsList().addListener(new ObservableListModelListener<NewsSummary>() {
            int counter = 1;
            int testCounter = 2;

            @Override
            public void modelChanged(Change<? extends NewsSummary> change) {
                System.out.println("News stories:");
                for (NewsSummary news : change.getSource())
                    System.out.println(news.getSourceId() + ": " + news.getTitle());
                assertEquals(change.getSource().get(0).getSourceId(), RemoteNewsServiceImpl.SOURCE_ID + testCounter);
                processNews.countDown();
                if (!change.getSource().isEmpty() && counter % 3 == 0) {
                    final int id  = Integer.valueOf(change.getSource().get(0).getSourceId().substring(RemoteNewsServiceImpl.SOURCE_ID.length()));
                    model.getNews(change.getSource().get(0)).whenDone(promise -> {
                        if (promise.hasResult()) {
                            System.out.println("Full news story: " + promise.getResult().getSummary().getSourceId() + ", " + promise.getResult().getContent());
                            assertEquals(promise.getResult().getContent(), RemoteNewsServiceImpl.BODY + id);
                            processNews.countDown();
                        }
                    });
                }
                testCounter += 2;
                counter++;
            }
        });
        model.setFilter(new NewsFilter(null, null, 3));
        model.setLive(true);
        processNews.await(10, TimeUnit.SECONDS);
        model.setLive(false);
    }

    public void testSpamNewsServer() throws InterruptedException {
        instance  =  new RMIServiceImplementation<>(new BadRemoteNewsServiceImpl(), RemoteNewsService.class);
        client = RMIEndpoint.newBuilder()
            .withSide(RMIEndpoint.Side.CLIENT)
            .withRole(DXEndpoint.Role.FEED)
            .build();
        executor = Executors.newFixedThreadPool(50, new DaemonThreadFactory("rmi"));
        client.getClient().setDefaultExecutor(executor);
        server.getServer().export(instance);
        connectDefault(10);
        final NewsModel model = new NewsModel(client.getDXEndpoint().getFeed());
        model.getNewsList().addListener(change -> fail());
        model.setFilter(new NewsFilter(null, null, 15));
        model.setLive(true);
        Thread.sleep(3000);
        model.setLive(false);
    }

    static class BadRemoteNewsServiceImpl implements RemoteNewsService {

        @Override
        public String getNewsContent(NewsKey newsKey) throws NewsNotFoundException {
            return null;
        }

        @Override
        public NewsList findNewsForFilter(NewsFilter filter, NewsKey lastKey) {
            throw new ArrayIndexOutOfBoundsException();
        }
    }


    static class RemoteNewsServiceImpl implements RemoteNewsService {

        private volatile Map<NewsKey, News> newsBodies = new HashMap<>();
        private volatile List<NewsKey> newsKeys = new ArrayList<>();

        private volatile long lastUpdate = 0;

        private static final long DELTA = 800;
        private static final String SOURCE_ID = "Source_ID";
        public static final String BODY = "BODY";

        private static final String SOURCE = "My_Source";
        public static final String TITLE = "title";

        private static final int LENGTH_KEY = 10;

        @Override
        public String getNewsContent(NewsKey newsKey) throws NewsNotFoundException {
            return newsBodies.get(newsKey).getContent();
        }

        @Override
        public NewsList findNewsForFilter(NewsFilter filter, NewsKey lastKey) {
            long timeWait = DELTA - (System.currentTimeMillis() - lastUpdate);
            if (timeWait > 0) {
                try {
                    Thread.sleep(timeWait);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            NewsKey currentKey = lastKey.getCode().isEmpty() ? new NewsKey(getString(0)) : lastKey;
            List<NewsSummary> newsSummaries = new ArrayList<>();
            News news = null;
            for (int i = 0; i < filter.getLimit(); i++) {
                if (newsBodies.containsKey(currentKey)) {
                    newsSummaries.add(newsBodies.get(currentKey).getSummary());
                    if (newsKeys.size() - 1 >= newsKeys.indexOf(currentKey) + 1)
                        currentKey = newsKeys.get(newsKeys.indexOf(currentKey) + 1);
                    else
                        currentKey = null;
                } else {
                    news = generateNews();
                    newsSummaries.add(news.getSummary());
                }
            }
            NewsList newsList = new NewsList(newsSummaries, news.getKey());
            lastUpdate = System.currentTimeMillis();
            return newsList;
        }

        @SuppressWarnings("unchecked")
        private News generateNews() {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            NewsKey newsKey = new NewsKey(getString(newsBodies.size()));
            News news = new News(new NewsSummary(newsKey, SOURCE_ID + newsBodies.size(), System.currentTimeMillis(),
                TITLE + newsBodies.size(), SOURCE, Collections.EMPTY_MAP), BODY + newsBodies.size());
            newsKeys.add(newsKey);
            newsBodies.put(newsKey, news);
            return news;
        }

        private String getString(int i) {
            char[] a = new char[LENGTH_KEY - (i + "").length()];
            for (int j = 0; j < a.length; j++) {
                a[j] = "0".toCharArray()[0];
            }
            return new String(a) + i;
        }
    }

    private static class DaemonThreadFactory implements ThreadFactory,  Thread.UncaughtExceptionHandler {
        private final String name;
        private static int counter = 0;

        DaemonThreadFactory(String name) {
            this.name = name;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, name + "-" + counter++);
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            t.setUncaughtExceptionHandler(this);
            return t;
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            if (e.getCause() instanceof RMIException) {
                assertEquals(((RMIException) e.getCause()).getType(), RMIExceptionType.APPLICATION_ERROR);
                if (!((e.getCause()).getCause() instanceof ArrayIndexOutOfBoundsException))
                    fail();
            } else {
                fail();
            }
        }

    }
}
