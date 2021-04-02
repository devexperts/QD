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
package com.devexperts.rmi.task;

import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRoute;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.SystemProperties;
import com.dxfeed.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;

/**
 * This strategy implements consistent hashing algorithm.
 */
public class ConsistentLoadBalancer implements RMILoadBalancer {

    private static final int DEFAULT_CAPACITY = SystemProperties.getIntProperty(
        ConsistentLoadBalancer.class, "defaultCapacity", 100);
    private static final int DEFAULT_PRIORITY = SystemProperties.getIntProperty(
        ConsistentLoadBalancer.class, "defaultPriority", 10);

    private static final int MAGIC = 0x9E3779B9;

    private final IndexedSet<RMIServiceId, RMIServiceDescriptor> descriptors =
        IndexedSet.create(RMIServiceDescriptor.INDEXER_BY_SERVICE_ID);
    private final NavigableMap<Integer, List<RMIServiceDescriptor>> ring = new TreeMap<>();

    @Nonnull
    @Override
    public synchronized Promise<BalanceResult> balance(@Nonnull RMIRequestMessage<?> request) {
        if (descriptors.size() == 0 || request.getTarget() != null)
            return Promise.completed(BalanceResult.route(request.getTarget()));

        if (descriptors.size() == 1)
            return Promise.completed(BalanceResult.route(descriptors.iterator().next().getServiceId()));

        if (ring.isEmpty())
            descriptors.forEach(this::addInRing);
        int requestKey = getRequestKey(request);
        Map.Entry<Integer, List<RMIServiceDescriptor>> ceiling = ring.ceilingEntry(requestKey);
        Map.Entry<Integer, List<RMIServiceDescriptor>> next = ceiling != null ? ceiling : ring.firstEntry();
        return Promise.completed(BalanceResult.route(next.getValue().get(0).getServiceId()));
    }

    @Override
    public synchronized void updateServiceDescriptor(@Nonnull RMIServiceDescriptor descriptor) {
        if (descriptor.isAvailable())
            processAvailableDescriptor(descriptor);
        else
            processUnavailableDescriptor(descriptor);
    }

    @Override
    public void close() {
        // No resource to release
    }

    @GuardedBy("this")
    private void processAvailableDescriptor(@Nonnull RMIServiceDescriptor descriptor) {
        RMIServiceDescriptor existing = descriptors.put(descriptor);
        if (existing == null) {
            if (!ring.isEmpty())
                addInRing(descriptor);
            return;
        }

        // Descriptor already exists, rebuild the ring if properties changed
        if (getCapacity(descriptor) != getCapacity(existing) ||
            getPriority(descriptor) != getPriority(existing) ||
            !Objects.equals(getShardName(descriptor), getShardName(existing)))
        {
            ring.clear();
        }
    }

    @GuardedBy("this")
    private void processUnavailableDescriptor(@Nonnull RMIServiceDescriptor descriptor) {
        if (!descriptors.remove(descriptor))
            return;
        ring.clear();
    }

    // can be overridden
    /**
     * Returns the capacity for the specified descriptor if it is set or returns the capacity of the default.
     * @param descriptor the specified service descriptor
     * @return the capacity for the specified descriptor if it is set or returns the capacity of the default
     */
    public int getCapacity(RMIServiceDescriptor descriptor) {
        return getIntProperty(descriptor, RMIServiceDescriptor.SERVICE_CAPACITY_PROPERTY, DEFAULT_CAPACITY);
    }

    // can be overridden

    /**
     * Returns the shard name for the specified descriptor if it is set or null.
     * @param descriptor the specified service descriptor
     * @return the shard name for the specified descriptor if it is set or null
     */
    public String getShardName(RMIServiceDescriptor descriptor) {
        return descriptor.getProperty(RMIServiceDescriptor.SERVICE_SHARD_PROPERTY);
    }

    // can be overridden

    /**
     * Returns the service seed for the specified descriptor if it is set or seed of the default.
     * @param descriptor the specified service descriptor
     * @return the service seed for the specified descriptor if it is set or seed of the default
     */
    public byte[] getServiceSeed(RMIServiceDescriptor descriptor) {
        String shard = getShardName(descriptor);
        return shard != null ? shard.getBytes(StandardCharsets.UTF_8) : descriptor.getServiceId().getBytes();
    }

    // can be overridden
    /**
     * Returns the service priority for the specified descriptor if it is set or priority of the default.
     * @param descriptor the specified service descriptor
     * @return the service priority for the specified descriptor if it is set or priority of the default
     */
    public int getPriority(RMIServiceDescriptor descriptor) {
        return getIntProperty(descriptor, RMIServiceDescriptor.SERVICE_PRIORITY_PROPERTY, DEFAULT_PRIORITY);
    }

    private int getIntProperty(RMIServiceDescriptor descriptor, String key, int def) {
        try {
            String value = descriptor.getProperty(key);
            return value == null || value.isEmpty() ? def : Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // can be overridden

    /**
     * Compares the shard names these service descriptors.
     * @param descriptor1 the first service descriptor to be compared
     * @param descriptor2 the second service descriptor to be compared
     * @return a negative integer, zero, or a positive integer as the first argument is less than, equal to,
     * or greater than the second.
     */
    public int compareInShard(RMIServiceDescriptor descriptor1, RMIServiceDescriptor descriptor2) {
        int pr1 = getPriority(descriptor1);
        int pr2 = getPriority(descriptor2);
        return pr1 < pr2 ? -1 : pr1 > pr2 ? 1 : descriptor1.getServiceId().compareTo(descriptor2.getServiceId());
    }

    private final Comparator<RMIServiceDescriptor> inShardComparator = this::compareInShard;

    // can be overridden
    /**
     * Returns key for the specified request.
     * @param request the request message
     * @return  key for the specified request
     */
    public int getRequestKey(RMIRequestMessage<?> request) {
        RMIRoute route = request.getRoute();
        if (route.isEmpty())
            return 0;
        return route.getFirst().hashCode() * MAGIC;
    }

    private void addInRing(RMIServiceDescriptor descriptor) {
        SecureRandom random;
        try {
            random = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        random.setSeed(getServiceSeed(descriptor));
        int pos;
        for (int i = 0; i < getCapacity(descriptor); i++) {
            pos = random.nextInt();
            List<RMIServiceDescriptor> ids = ring.get(pos);
            if (ids == null) {
                ids = new ArrayList<>(1); // we'll almost never need more than 1 item (very low probability)
                ids.add(descriptor);
                ring.put(pos, ids);
            } else {
                int index = Collections.binarySearch(ids, descriptor, inShardComparator);
                if (index < 0) {
                    ids.add(-index - 1, descriptor);
                }  else {
                    ids.add(index + 1, descriptor);
                }
            }
        }
    }
}
