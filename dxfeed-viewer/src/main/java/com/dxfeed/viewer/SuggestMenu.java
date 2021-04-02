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
package com.dxfeed.viewer;

import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileReader;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.Timer;

public class SuggestMenu extends JPopupMenu implements ActionListener {
    public static final int DEFAULT_SUGGEST_LIMIT = 10;
    public static final int DELAY = 200; // ms.

    private static final DateFormat DATE_FORMATTER = new SimpleDateFormat("YYYYMMDD");

    public final String prefix;
    public final int limit;
    public final String ipfAddress;
    public final String date;
    public final List<InstrumentProfile> ipfList;
    public final JTextField textField;

    private final Action symbolSelected;
    private final Component invoker;
    private final String user;
    private final String password;

    private SwingWorker<List<InstrumentProfile>, Void> downloadTask;
    private Timer timer;

    public SuggestMenu(String prefix, Action symbolSelected, Component invoker, JTextField textField, String ipfAddress, List<InstrumentProfile> ipfList) {
        this(prefix, DEFAULT_SUGGEST_LIMIT, symbolSelected, ipfAddress, null, ipfList, invoker, textField, null, null);
    }


    public SuggestMenu(String prefix, Action symbolSelected, Component invoker, JTextField textField, String ipfAddress, List<InstrumentProfile> ipfList, String user, String password) {
        this(prefix, DEFAULT_SUGGEST_LIMIT, symbolSelected, ipfAddress, null, ipfList, invoker, textField, user, password);
    }

    public SuggestMenu(String prefix, int limit, Action symbolSelected, String ipfAddress, Date date, List<InstrumentProfile> ipfList, Component invoker, JTextField textField, String user, String password) {
        this.prefix = prefix;
        this.limit = limit;
        this.ipfAddress = ipfAddress;
        this.ipfList = ipfList;
        this.date = date == null ? null : DATE_FORMATTER.format(date);
        this.symbolSelected = symbolSelected;
        this.invoker = invoker;
        this.textField = textField;
        this.user = user;
        this.password = password;
        if (!prefix.isEmpty()) {
            startDownload();
            startTimer();
        }
        setLightWeightPopupEnabled(true);
    }

    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        if (downloadTask != null) {
            downloadTask.cancel(true);
            downloadTask = null;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!downloadTask.isDone()) {
            return;
        }
        timer.stop();
        List<InstrumentProfile> list;
        try {
            list = downloadTask.get();
        } catch (InterruptedException ignore) {
            return;
        } catch (ExecutionException what) {
            what.printStackTrace();
            return;
        }

        for (InstrumentProfile instrumentProfile : list) {
            final String text = instrumentProfile.getSymbol() + " (" + instrumentProfile.getDescription() + ")";
            final JMenuItem item = new JMenuItem(text);
            item.addActionListener(symbolSelected);
            add(item);
        }
        show(invoker, textField.getX(), textField.getY() + textField.getHeight());
        textField.requestFocusInWindow();
    }

    private void startDownload() {
        this.downloadTask = ipfList ==  null ? new WebServiceDownloader() : new LocalDownloader();
        downloadTask.execute();
    }

    private void startTimer() {
        timer = new Timer(DELAY, this);
        timer.start();
    }

    private class LocalDownloader extends SwingWorker<List<InstrumentProfile>, Void> {
        public final String needle = SuggestMenu.this.prefix;

        @Override
        protected List<InstrumentProfile> doInBackground() throws Exception {
            List<InstrumentProfile> result = new ArrayList<>(limit);
            for (InstrumentProfile ipf : ipfList) {
                if (ipf.getSymbol().startsWith(needle)) {
                    result.add(ipf);
                    if (result.size() == limit) {
                        break;
                    }
                }
            }
            return result;
        }
    }

    private class WebServiceDownloader extends SwingWorker<List<InstrumentProfile>, Void> {
        public final String needle = SuggestMenu.this.prefix;

        @Override
        protected List<InstrumentProfile> doInBackground() throws Exception {
            InstrumentProfileReader reader = new InstrumentProfileReader();
            String url = ipfAddress + "?" + "mode=ui" + "&" + "text=" + needle + "&" + "limit=" + limit;
            if (date != null) {
//                url += "&" + "date=" + DATE_FORMATTER.format(date);
            }
            List<InstrumentProfile> list = reader.readFromFile(url, user, password);
            return list;
        }
    }
}
