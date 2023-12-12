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
package com.devexperts.rmi.impl;

import com.devexperts.connector.proto.EndpointId;
import com.devexperts.logging.Logging;
import com.devexperts.rmi.task.RMIObservableServiceDescriptors;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMIServiceDescriptor;
import com.devexperts.rmi.task.RMIServiceDescriptorsListener;
import com.devexperts.rmi.task.RMIServiceId;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.IndexerFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;

public class ServiceRouter<T> implements RMIObservableServiceDescriptors {

    private static final Logging log = Logging.getLogging(ServiceRouter.class);

    // ==================== fields ====================

    private final IndexerFunction<T, Ref<T>> indexerRefByT = (IndexerFunction<T, Ref<T>>) value -> value.obj;

    private final List<Ref<T>> nearest = new ArrayList<>();
    private final Set<EndpointId> intermediateNodes = new HashSet<>();
    private int bestDistance = RMIService.UNAVAILABLE_METRIC;
    private int lastSendDistance = RMIService.UNAVAILABLE_METRIC;
    private final List<RMIServiceDescriptorsListener> listeners = new CopyOnWriteArrayList<>();

    private final IndexedSet<T, Ref<T>> descriptors = IndexedSet.create(indexerRefByT);

    private final RMIServiceId serviceId;
    private final EndpointId endpointId;

    // ==================== static factory methods ====================

    public static <T> ServiceRouter<T> createRouter(EndpointId endpoint, RMIServiceId serviceId) {
        if (serviceId == null)
            throw new NullPointerException("ServiceId can not be null");
        return new ServiceRouter<>(endpoint, serviceId);
    }

    static ServiceRouter<RMIConnection> createAnonymousRouter(EndpointId endpointId) {
        return new AnonymousRouter(endpointId);
    }

    // ==================== private constructor ====================

    private ServiceRouter(EndpointId endpointId, RMIServiceId serviceId) {
        this.endpointId = endpointId;
        this.serviceId = serviceId;
    }

    // ==================== public methods ====================

    public synchronized void updateDescriptor(RMIServiceDescriptor descriptor, int dist, T obj) {
        if (dist == RMIService.UNAVAILABLE_METRIC) {
            removeDescriptor(descriptor, obj);
            return;
        }
        Ref<T> ref = new Ref<>(descriptor, obj);
        descriptors.add(ref);
        if (updateDistanceInfo(ref))
            notifyListener(pickFirstDescriptor(), bestDistance);
    }

    public synchronized void removeDescriptor(RMIServiceDescriptor descriptor, T obj) {
        if (descriptors.removeKey(obj) != null &&
            updateDistanceInfo(new Ref<>(descriptor, obj)))
        {
            notifyListener(nearest.isEmpty() ? descriptor : pickFirstDescriptor(), bestDistance);
        }
    }

    public synchronized RMIServiceDescriptor pickFirstDescriptor() {
        if (nearest.isEmpty())
            return null;
        int randomIndex = ThreadLocalRandom.current().nextInt(nearest.size());
        RMIServiceDescriptor descriptor = nearest.get(randomIndex).descriptor;
        return RMIServiceDescriptor.createDescriptor(serviceId, descriptor.getDistance(), intermediateNodes, descriptor.getProperties());
    }

    public synchronized T pickRandom() {
        if (nearest.isEmpty())
            return null;
        int randomIndex = ThreadLocalRandom.current().nextInt(nearest.size());
        return nearest.get(randomIndex).obj;
    }

    @Override
    public synchronized void addServiceDescriptorsListener(RMIServiceDescriptorsListener listener) {
        if (!isEmpty())
            listener.descriptorsUpdated(Collections.singletonList(pickFirstDescriptor()));
        listeners.add(listener);
    }

    @Override
    public synchronized void removeServiceDescriptorsListener(RMIServiceDescriptorsListener listener) {
        listener.descriptorsUpdated(Collections.singletonList(RMIServiceDescriptor.createUnavailableDescriptor(serviceId, null)));
        listeners.remove(listener);
    }

    @Override
    public boolean isAvailable() {
        return !isEmpty();
    }

    @Override
    public synchronized String toString() {
        return "Server " + endpointId + " ServiceRoute{" +
            "serviceId=" + serviceId + ", " +
            "nearest=" + nearest + ", bestDist=" + bestDistance + ", " +
            "descriptors=" + descriptors +
            "lastSendDist=" + lastSendDistance + "}";
    }

    synchronized boolean isEmpty() {
        return descriptors.isEmpty();
    }

    // ==================== private methods ====================

    private boolean updateDistanceInfo(Ref<T> ref) {
        if (!ref.descriptor.isAvailable()) {
            if (nearest.isEmpty())
                return false;
        } else {
            if (nearest.isEmpty() || bestDistance > ref.distance) {
                nearest.clear();
                nearest.add(ref);
                intermediateNodes.clear();
                intermediateNodes.addAll(ref.descriptor.getIntermediateNodes());
                bestDistance = ref.distance;
                return true;
            }
        }
        nearest.remove(ref);
        if (!nearest.isEmpty()) {
            int sizeIntermediate = intermediateNodes.size();
            intermediateNodes.clear();
            for (Ref<T> tRef : nearest)
                intermediateNodes.addAll(tRef.descriptor.getIntermediateNodes());
            return sizeIntermediate != intermediateNodes.size();
        }
        updateNearest();
        return true;
    }

    private void updateNearest() {
        nearest.clear();
        intermediateNodes.clear();
        bestDistance = RMIService.UNAVAILABLE_METRIC;
        for (Ref<T> ref : descriptors) {
            if (ref.distance == bestDistance) {
                nearest.add(ref);
                intermediateNodes.addAll(ref.descriptor.getIntermediateNodes());
            } else if (ref.distance < bestDistance) {
                nearest.clear();
                intermediateNodes.clear();
                bestDistance = ref.distance;
                nearest.add(ref);
                intermediateNodes.addAll(ref.descriptor.getIntermediateNodes());
            }
        }
    }

    private void notifyListener(RMIServiceDescriptor descriptor, int newDistance) {
        lastSendDistance = newDistance;
        for (RMIServiceDescriptorsListener listener : listeners)
            try {
                listener.descriptorsUpdated(Collections.singletonList(descriptor));
            } catch (Throwable t) {
                log.error("Failed to update service descriptors", t);
            }
    }

    //----------------------------- AnonymousRouter for processing old version RMI -----------------------------

    static class AnonymousRouter extends ServiceRouter<RMIConnection> {

        ArrayList<RMIConnection> connections = new ArrayList<>();

        AnonymousRouter(EndpointId endpointId) {
            super(endpointId, null);
        }

        @Override
        public synchronized void updateDescriptor(RMIServiceDescriptor descriptor, int dist, RMIConnection connection) {
            connections.add(connection);
        }

        @Override
        public synchronized void removeDescriptor(RMIServiceDescriptor descriptor, RMIConnection obj) {
            connections.remove(obj);
        }

        @Override
        public synchronized void addServiceDescriptorsListener(RMIServiceDescriptorsListener listener) {
        }

        @Override
        public synchronized void removeServiceDescriptorsListener(RMIServiceDescriptorsListener listener) {
        }

        @Override
        public synchronized RMIConnection pickRandom() {
            if (connections.isEmpty())
                return null;
            int randomIndex = ThreadLocalRandom.current().nextInt(connections.size());
            return connections.get(randomIndex);
        }
    }

    //----------------------------- auxiliary container -----------------------------


    static class Ref<T> implements Comparable<Ref<T>> {
        final int distance;
        final RMIServiceDescriptor descriptor;
        final T obj;

        Ref(RMIServiceDescriptor descriptor, T obj) {
            this.descriptor = descriptor;
            this.distance = descriptor.getDistance();
            this.obj = obj;
        }

        @Override
        public int compareTo(@Nonnull Ref<T> o) {
            return distance - o.distance;
        }

        @Override
        public int hashCode() {
            return (descriptor != null ? descriptor.getServiceId().hashCode() : super.hashCode()) * 27 + obj.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Ref))
                return false;
            Ref<?> other = (Ref<?>) obj;
            return descriptor != null ? descriptor.getServiceId().equals(other.descriptor.getServiceId()) && this.obj.equals(other.obj) :
                other.descriptor == null && obj.equals(other.obj);
        }

        @Override
        public String toString() {
            return "Ref{" +
                "descriptor=" + descriptor +
                ", distance=" + distance +
                ", obj=" + obj +
                "}";
        }
    }
}
