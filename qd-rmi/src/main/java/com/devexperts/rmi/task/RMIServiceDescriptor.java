/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.task;

import com.devexperts.connector.proto.EndpointId;
import com.devexperts.util.IndexerFunction;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The class contains information about the specific implementation of {@link RMIService}
 */
public class RMIServiceDescriptor {

    /**
     * Indexer function for comparing descriptors for {@link RMIServiceId}.
     */
    public static final IndexerFunction<RMIServiceId, RMIServiceDescriptor> INDEXER_BY_SERVICE_ID =
        RMIServiceDescriptor::getServiceId;

    /**
     * Defines property for capacity implementation of service.
     */
    public static final String SERVICE_CAPACITY_PROPERTY = "capacity";

    /**
     * Defines property for shard implementation of service.
     */
    public static final String SERVICE_SHARD_PROPERTY = "shard";

    /**
     * Defines property for priority implementation of service.
     */
    public static final String SERVICE_PRIORITY_PROPERTY = "priority";

    private final RMIServiceId serviceId;
    private final int distance;
    private final Set<EndpointId> intermediateNodes; // unmodifiable
    private final Map<String, String> properties; // unmodifiable

    /**
     * Create a descriptor with an infinite distance and without intermediate nodes.
     * @param serviceId the id implementation of service (can not be null)
     * @param properties properties implementation of service (may be null)
     * @return descriptor with an infinite distance
     * @throws NullPointerException if serviceId is null
     */
    public static RMIServiceDescriptor createUnavailableDescriptor(RMIServiceId serviceId, Map<String, String> properties) {
        if (serviceId == null)
            throw new NullPointerException("serviceId can not be null");
        return new RMIServiceDescriptor(serviceId, RMIService.UNAVAILABLE_METRIC, null, properties);
    }

    /**
     * Create a descriptor with the same {@link #getServiceId() serviceId}, an infinite distance
     * and without intermediate nodes.
     *
     * @return descriptor with an infinite distance
     * @see #createUnavailableDescriptor
     */
    public RMIServiceDescriptor toUnavailableDescriptor() {
        return createUnavailableDescriptor(serviceId, properties);
    }

    /**
     * Creates descriptor implementation of service
     * @param serviceId the id implementation of service (can not be null)
     * @param distance distance to implementation of service
     * @param intermediateNodes intermediate nodes to implementation of service (may be null)
     * @param properties properties implementation of service (may be null)
     * @return description with the specified parameters
     * @throws NullPointerException if serviceId is null
     */
    public static RMIServiceDescriptor createDescriptor(RMIServiceId serviceId, int distance,
        Set<EndpointId> intermediateNodes, Map<String, String> properties)
    {
        if (serviceId == null)
            throw new NullPointerException("serviceId can not be null");
        return new RMIServiceDescriptor(serviceId, distance, intermediateNodes, properties);
    }

    private RMIServiceDescriptor(RMIServiceId serviceId, int distance, Set<EndpointId> intermediateNodes,
        Map<String, String> properties)
    {
        this.serviceId = serviceId;
        this.distance = distance;
        this.intermediateNodes = intermediateNodes == null || intermediateNodes.isEmpty() ?
            Collections.<EndpointId>emptySet() : intermediateNodes.size() == 1 ?
            Collections.singleton(intermediateNodes.iterator().next()) :
            Collections.unmodifiableSet(new HashSet<>(intermediateNodes));
        this.properties = getUnmodifiableMap(properties);
    }

    // FIXME: RMIRequestMessage require the same algorithm. Maybe should be placed in some utility class?
    private static <K, V> Map<K, V> getUnmodifiableMap(Map<K, V> properties) {
        if (properties == null || properties.isEmpty())
            return Collections.emptyMap();
        if (properties.size() == 1) {
            Map.Entry<K, V> entry = properties.entrySet().iterator().next();
            return Collections.singletonMap(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    /**
     * Creates a copy of the descriptor with provided properties.
     *
     * @param properties the new descriptor properties
     * @return a new instance of RMIServiceDescriptor with the updated properties
     */
    public RMIServiceDescriptor changeProperties(Map<String, String> properties) {
        return new RMIServiceDescriptor(serviceId, distance, intermediateNodes, properties);
    }

    /**
     * Returns service name.
     * @return service name
     */
    public String getServiceName() {
        return serviceId.getName();
    }

    /**
     * Returns unique serviceID.
     * @return unique serviceID
     */
    public RMIServiceId getServiceId() {
        return serviceId;
    }

    /**
     * Returns distance to implementation of service.
     * @return distance to implementation of service
     */
    public int getDistance() {
        return distance;
    }

    /**
     * Returns intermediate nodes to implementation of service.
     * @return intermediate nodes to implementation of service
     */
    public Set<EndpointId> getIntermediateNodes() {
        return intermediateNodes;
    }


    /**
     * Returns properties implementation of service.
     * @return properties implementation of service
     */
    public Map<String, String> getProperties() {
        return properties;
    }


    /**
     * Returns property implementation of service with a unique key.
     * @param key the unique key
     * @return property implementation of service with a unique key
     */
    public String getProperty(String key) {
        return properties.get(key);
    }

    /**
     * Returns true, if distance is less than {@link RMIService#UNAVAILABLE_METRIC}.
     * @return true, if distance is less than {@link RMIService#UNAVAILABLE_METRIC}.
     */
    public boolean isAvailable() {
        return distance != RMIService.UNAVAILABLE_METRIC;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof RMIServiceDescriptor))
            return false;
        RMIServiceDescriptor other = (RMIServiceDescriptor) o;
        return serviceId.equals(other.serviceId) && distance == other.distance &&
            intermediateNodes.equals(other.intermediateNodes) && properties.equals(other.properties);
    }

    @Override
    public int hashCode() {
        return ((serviceId.hashCode() * 17 + distance) * 27 + intermediateNodes.hashCode() * 17) * 27 + properties.hashCode();
    }

    @Override
    public String toString() {
        String dist = distance != RMIService.UNAVAILABLE_METRIC ? String.valueOf(distance) : "INF";
        return serviceId +
            "{distance=" + dist +
            ", intermediateNodes=" + intermediateNodes +
            ", properties=" + properties +
            '}';
    }
}
