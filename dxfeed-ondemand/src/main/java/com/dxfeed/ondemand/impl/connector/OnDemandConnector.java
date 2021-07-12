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
package com.dxfeed.ondemand.impl.connector;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.qd.qtp.AbstractConnectionHandler;
import com.devexperts.qd.qtp.AbstractMessageConnector;
import com.devexperts.qd.qtp.MessageConnectorState;
import com.devexperts.qd.qtp.ReconnectHelper;
import com.devexperts.qd.qtp.help.MessageConnectorProperty;
import com.devexperts.qd.qtp.help.MessageConnectorSummary;
import com.devexperts.util.LogUtil;
import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimePeriod;
import com.dxfeed.api.impl.OnDemandConnectorMarker;
import com.dxfeed.ondemand.impl.MarketDataReplay;
import com.dxfeed.ondemand.impl.MarketDataToken;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import javax.management.AttributeChangeNotification;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;

@MessageConnectorSummary(
    info = "On-demand historical data replay.",
    addressFormat = "ondemand:<address>"
)
public class OnDemandConnector extends AbstractMessageConnector
    implements OnDemandConnectorMBean, NotificationBroadcaster, OnDemandConnectorMarker
{
    private final String address;
    private final ReconnectHelper reconnectHelper;
    private final MarketDataReplay replay = new MarketDataReplay();

    private volatile ReplayConnectionHandler handler; // !=null when active
    private Date time;
    private double speed = 1;

    private volatile TimePeriod tickPeriod = TimePeriod.valueOf(20); // tick at 50 fps by default

    // JMX notifications
    private final NotificationBroadcasterSupport broadcaster = new NotificationBroadcasterSupport();
    private long notificationSequence = 0; // sequence number for notificationSequence

    public OnDemandConnector(ApplicationConnectionFactory factory, String address) {
        super(factory);
        this.address = address;
        reconnectHelper = new ReconnectHelper(getReconnectDelay());
    }

    @Override
    public synchronized void start() {
        if (isActive())
            return; // already active
        if (time == null)
            return; // will not start until time is set
        log.info("Starting OnDemandConnector to " + LogUtil.hideCredentials(getAddress()) + " at " +
            TimeFormat.DEFAULT.format(time));
        reconnectHelper.setReconnectDelay(getReconnectDelay()); // update reconnect delay
        handler = new ReplayConnectionHandler(this);
        handler.start();
        notifyMessageConnectorListeners();
    }

    @Override
    protected Joinable stopImpl() {
        ReplayConnectionHandler handler;
        synchronized (this) {
            if (!isActive())
                return null;
            handler = this.handler;
            this.handler = null; // mark as stopped, so we don't need to restart on handlerClosed
        }
        if (handler != null) {
            log.info("Stopping OnDemandConnector");
            handler.close();
        }
        notifyMessageConnectorListeners();
        return handler;
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public boolean isActive() {
        return handler != null;
    }

    @Override
    public MessageConnectorState getState() {
        ReplayConnectionHandler handler = this.handler;
        return handler != null ? handler.getConnectionState() : MessageConnectorState.DISCONNECTED;
    }

    @Override
    public int getConnectionCount() {
        return isActive() ? 1 : 0;
    }

    @Override
    public TimePeriod getTickPeriod() {
        return tickPeriod;
    }

    @Override
    @MessageConnectorProperty("Tick period")
    public void setTickPeriod(TimePeriod tickPeriod) {
        if (tickPeriod == null)
            throw new NullPointerException();
        this.tickPeriod = tickPeriod;
    }

    @Override
    public long getCacheLimit() {
        return replay.getCacheLimit();
    }

    @Override
    @MessageConnectorProperty("Cache limit size in bytes")
    public void setCacheLimit(long amount) {
        replay.setCacheLimit(amount);
    }

    @Override
    public long getFileCacheLimit() {
        return replay.getFileCacheLimit();
    }

    @Override
    @MessageConnectorProperty("File cache limit size in bytes")
    public void setFileCacheLimit(long amount) {
        replay.setFileCacheLimit(amount);
    }

    @Override
    public synchronized String getFileCachePath() {
        return replay.getFileCachePath();
    }

    @Override
    @MessageConnectorProperty("Cache file directory, empty by default (current dir)")
    public synchronized void setFileCachePath(String cacheFileDir) {
        replay.setFileCachePath(cacheFileDir);
    }

    @Override
    public synchronized Date getTime() {
        return time;
    }

    @Override
    @MessageConnectorProperty("Replay time")
    public synchronized void setTime(Date time) {
        updateTime(time);
        reconfigure();
    }

    void updateTime(Date newTime) {
        Date oldTime;
        long sequence;
        synchronized (this) {
            oldTime = this.time;
            this.time = newTime;
            sequence = ++notificationSequence;
        }
        broadcaster.sendNotification(new AttributeChangeNotification(
            this, sequence, System.currentTimeMillis(),
            "Time changed", "time", Date.class.getName(), oldTime, newTime));
    }

    @Override
    public synchronized double getSpeed() {
        return speed;
    }

    @Override
    @MessageConnectorProperty("Replay speed, 1.0 by default (real-time speed)")
    public synchronized void setSpeed(double speed) {
        this.speed = speed;
        if (handler != null)
            handler.setSpeed(speed);
    }

    @Override
    public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
        broadcaster.addNotificationListener(listener, filter, handback);
    }

    @Override
    public void removeNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener);
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[] {
            new MBeanNotificationInfo(
                new String[] { AttributeChangeNotification.ATTRIBUTE_CHANGE },
                AttributeChangeNotification.class.getName(),
                "Attribute change notification"
            )
        };
    }

    public void startImmediately() {
        reconnectHelper.reset();
        start();
    }

    ReconnectHelper getReconnectHelper() {
        return reconnectHelper;
    }

    MarketDataReplay getMarketDataReplay() {
        return replay;
    }

    MarketDataToken resolveToken() {
        StringBuilder resolvedAddress = new StringBuilder();
        for (String addr : address.split(",")) {
            int i = addr.lastIndexOf(':');
            String host = i >= 0 ? addr.substring(0, i) : addr;
            String port = i >= 0 ? addr.substring(i) : "";
            try {
                log.info("Resolving IPs for " + host);
                InetAddress[] all = InetAddress.getAllByName(host);
                Arrays.sort(all, new Comparator<InetAddress>() {
                    @Override
                    public int compare(InetAddress o1, InetAddress o2) {
                        byte[] a1 = o1.getAddress();
                        byte[] a2 = o2.getAddress();
                        int n = Math.min(a1.length, a2.length);
                        for (int i = 0; i < n; i++) {
                            int delta = (a1[i] & 0xff) - (a2[i] & 0xff);
                            if (delta != 0)
                                return delta;
                        }
                        return a1.length - a2.length;
                    }
                });
                for (InetAddress a : all) {
                    appendAddress(resolvedAddress, a.getHostAddress() + port);
                }
            } catch (UnknownHostException e) {
                log.warn("Failed to resolve IPs for " + host);
                appendAddress(resolvedAddress, addr);
            }
        }
        return MarketDataToken.fromUserPassword(getUser(), getPassword(), resolvedAddress.toString());
    }

    private void appendAddress(StringBuilder resolvedAddress, String addr) {
        if (resolvedAddress.length() > 0)
            resolvedAddress.append(",");
        resolvedAddress.append(addr);
    }

    @Override
    protected synchronized void handlerClosed(AbstractConnectionHandler handler) {
        if (handler != this.handler)
            return;
        this.handler = null;
        start();
    }
}
