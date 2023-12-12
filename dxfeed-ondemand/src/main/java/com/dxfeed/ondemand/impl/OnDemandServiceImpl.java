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
package com.dxfeed.ondemand.impl;

import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.util.TimePeriod;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.impl.DXEndpointImpl;
import com.dxfeed.api.impl.DXFeedImpl;
import com.dxfeed.ondemand.OnDemandService;
import com.dxfeed.ondemand.impl.connector.OnDemandConnector;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationListener;

public final class OnDemandServiceImpl extends OnDemandService implements NotificationListener {

    private static final Logging log = Logging.getLogging(OnDemandServiceImpl.class);

    // non-null after init, effectively final
    private DXEndpointImpl dxEndpoint;
    private DXFeedImpl dxFeed;

    // non-null after init, effectively final, used for synchronization for all fields on write
    private Object lock;

    private volatile boolean replay;
    private volatile boolean clear; // true when stopAndClear called and not resumed
    private volatile OnDemandConnector onDemandConnector; // != null when replay
    private volatile long time;
    private volatile double speed;

    private final List<PropertyChangeListener> propertyChangeListeners = new CopyOnWriteArrayList<>();
    private final PropertyChange change = new PropertyChange();

    @Override
    // SYNC(lock)
    protected void initImpl(DXEndpoint endpoint) {
        if (!(endpoint instanceof DXEndpointImpl))
            throw new IllegalArgumentException("Unsupported endpoint class: " + endpoint.getClass().getName());
        if (this.dxEndpoint != null)
            throw new IllegalStateException("Already initialized");
        dxEndpoint = (DXEndpointImpl) endpoint;
        dxFeed = (DXFeedImpl) dxEndpoint.getFeed();
        lock = dxEndpoint.getLock();
        dxEndpoint.getQDEndpoint().addPlugin(new QDEndpoint.Plugin() {
            @Override
            public boolean skipConnectorOnStart(MessageConnector connector) {
                return replay && !(connector instanceof OnDemandConnector);
            }

            @Override
            public void connectorsChanged(List<MessageConnector> connectors) {
                updateConnectors();
            }
        });
        updateConnectors();
    }

    void updateConnectors() {
        synchronized (lock) {
            // save current connector's time
            if (replay)
                captureOnDemandConnectorTime();
            // figure out new on-demand connector
            OnDemandConnector oldOnDemandConnector = onDemandConnector;
            if (onDemandConnector != null) {
                try {
                    onDemandConnector.removeNotificationListener(this);
                } catch (ListenerNotFoundException e) {
                    throw new RuntimeException(e); // cannot really happen
                }
                onDemandConnector = null;
            }
            for (MessageConnector connector : dxEndpoint.getQDEndpoint().getConnectors()) {
                if (connector instanceof OnDemandConnector) {
                    onDemandConnector = (OnDemandConnector) connector;
                    break; // use first found connector
                }
            }
            if (onDemandConnector != null) {
                onDemandConnector.addNotificationListener(this, null, null);
                // respect feed's aggregation period in connector's tick period
                if (onDemandConnector != oldOnDemandConnector)
                    onDemandConnector.setTickPeriod(TimePeriod.valueOf(Math.max(
                        onDemandConnector.getTickPeriod().getTime(),
                        dxFeed.getAggregationPeriodMillis())
                    ));
            }
            configureOnDemandConnectorReplay();
            change.schedule();
        }
    }

    // called when connector's time is updated. It must be lock-free.
    @Override
    public void handleNotification(Notification notification, Object handback) {
        captureOnDemandConnectorTime();
        change.schedule();
    }

    private void captureOnDemandConnectorTime() {
        OnDemandConnector onDemandConnector = this.onDemandConnector; // atomic read
        if (onDemandConnector != null) {
            Date timeObj = onDemandConnector.getTime();
            if (timeObj != null && timeObj.getTime() != 0)
                this.time = timeObj.getTime();
        }
    }

    private void configureOnDemandConnectorReplay() {
        if (replay) {
            if (onDemandConnector == null) {
                replay = false; // turn off replay when it cannot be started
            } else {
                onDemandConnector.setSpeed(speed);
                onDemandConnector.setTime(new Date(time));
            }
        } else {
            // stops ondemand connector in non-replay mode and prevents it from starting
            if (onDemandConnector != null)
                onDemandConnector.setTime(null);
        }
    }

    @Override
    public DXEndpoint getEndpoint() {
        return dxEndpoint;
    }

    @Override
    public boolean isReplaySupported() {
        return onDemandConnector != null;
    }

    @Override
    public boolean isReplay() {
        return replay;
    }

    @Override
    public boolean isClear() {
        return clear;
    }

    @Override
    public Date getTime() {
        return new Date(time);
    }

    @Override
    public double getSpeed() {
        return speed;
    }

    @Override
    public void replay(Date time, double speed) {
        if (time == null)
            throw new NullPointerException();
        if (speed < 0)
            throw new IllegalArgumentException();
        synchronized (lock) {
            // check state
            if (onDemandConnector == null)
                throw new IllegalStateException("Not connected to (ondemand:<address>)");
            // stop everything first
            dxEndpoint.getQDEndpoint().stopConnectorsAndWaitUninterruptibly();
            // cleanup storage
            dxEndpoint.clearImpl();
            // update internal mode
            this.replay = true;
            this.time = time.getTime();
            this.speed = speed;
            // update connector
            configureOnDemandConnectorReplay();
            // now start connector & don't wait before start of ondemand connector even if it was just started
            onDemandConnector.startImmediately();
            clear = false;
            change.schedule();
        }
    }

    @Override
    public void pause() {
        setSpeed(0);
    }

    @Override
    public void setSpeed(double speed) {
        if (speed < 0)
            throw new IllegalArgumentException();
        synchronized (lock) {
            if (!replay && speed != 0)
                throw new IllegalStateException("Not in replay mode");
            if (speed == this.speed)
                return; // nothing to do!
            onDemandConnector.setSpeed(speed);
            this.speed = speed;
            change.schedule();
        }
    }

    @Override
    public void stopAndResume() {
        stopImpl(true);
    }

    @Override
    public void stopAndClear() {
        stopImpl(false);
    }

    private void stopImpl(boolean resume) {
        synchronized (lock) {
            if (!replay && resume && !clear)
                return; // nothing to do
            if (replay && !resume && clear)
                return; // nothing to do
            // stop everything first
            dxEndpoint.getQDEndpoint().stopConnectorsAndWaitUninterruptibly();
            // cleanup storage
            dxEndpoint.clearImpl();
            // update mode
            this.replay = false;
            this.speed = 0;
            captureOnDemandConnectorTime();
            // update connector with new mode
            configureOnDemandConnectorReplay();
            // now start other connectors if need to resume
            if (resume)
                dxEndpoint.getQDEndpoint().startConnectors();
            clear = !resume;
            change.schedule();
        }
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeListeners.add(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeListeners.remove(listener);
    }

    private void firePropertyChangeEvent(String name, Object oldVal, Object newVal) {
        if (propertyChangeListeners.isEmpty())
            return;
        PropertyChangeEvent event = new PropertyChangeEvent(this, name, oldVal, newVal);
        for (PropertyChangeListener listener : propertyChangeListeners)
            try {
                listener.propertyChange(event);
            } catch (Throwable t) {
                log.error("Exception in OnDemandService property change listener", t);
            }
    }

    private void firePropertyChangeEventIfNeeded(String name, boolean oldVal, boolean newVal) {
        if (oldVal != newVal)
            firePropertyChangeEvent(name, oldVal, newVal);
    }

    private void firePropertyChangeEventIfNeeded(String name, double oldVal, double newVal) {
        if (Double.compare(oldVal, newVal) != 0)
            firePropertyChangeEvent(name, oldVal, newVal);
    }

    private void firePropertyChangeEventIfNeeded(String name, Date oldVal, Date newVal) {
        if (oldVal.getTime() != newVal.getTime())
            firePropertyChangeEvent(name, oldVal, newVal);
    }

    private class PropertyChange implements Runnable {
        boolean scheduled;

        boolean oldReplaySupported;
        boolean oldReplay;
        boolean oldClear;
        double oldSpeed;
        Date oldTime;

        boolean newReplaySupported = isReplaySupported();
        boolean newReplay = isReplay();
        boolean newClear = isClear();
        double newSpeed = getSpeed();
        Date newTime = getTime();

        public synchronized void schedule() {
            if (scheduled)
                return;
            scheduled = true;
            dxEndpoint.getOrCreateExecutor().execute(this);
        }

        @Override
        public void run() {
            while (true) {
                synchronized (this) {
                    oldReplaySupported = newReplaySupported;
                    oldReplay = newReplay;
                    oldClear = newClear;
                    oldSpeed = newSpeed;
                    oldTime = newTime;
                    newReplaySupported = isReplaySupported();
                    newReplay = isReplay();
                    newClear = isClear();
                    newSpeed = getSpeed();
                    newTime = getTime();
                    if (oldReplaySupported == newReplaySupported &&
                        oldReplay == newReplay &&
                        Double.compare(oldSpeed, newSpeed) == 0 &&
                        oldTime.equals(newTime))
                    {
                        scheduled = false;
                        return;
                    }
                }
                firePropertyChangeEventIfNeeded("replaySupported", oldReplaySupported, newReplaySupported);
                firePropertyChangeEventIfNeeded("replay", oldReplay, newReplay);
                firePropertyChangeEventIfNeeded("clear", oldClear, newClear);
                firePropertyChangeEventIfNeeded("speed", oldSpeed, newSpeed);
                firePropertyChangeEventIfNeeded("time", oldTime, newTime);
            }
        }
    }
}
