/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api;

import com.devexperts.services.Service;
import com.devexperts.services.Services;
import com.dxfeed.api.osub.WildcardSymbol;
import com.dxfeed.event.EventType;
import com.dxfeed.event.market.Quote;
import com.dxfeed.ondemand.OnDemandService;

import java.beans.PropertyChangeListener;
import java.util.Date;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Manages network connections to {@link DXFeed feed} or
 * {@link DXPublisher publisher}. There are per-JVM ready-to-use singleton instances
 * that are available with
 * {@link #getInstance()} and
 * {@link #getInstance(Role)} methods
 * as well as
 * factory methods {@link #create()} and
 * {@link #create(Role)}, and a number of configuration methods. Advanced
 * properties can be configured using
 * {@link #newBuilder() newBuilder()}.{@link Builder#withProperty(String, String) withProperty(key, value)}.{@link Builder#build() build()}.
 *
 * <h3>Sample usage</h3>
 *
 * The following code creates new feed instance that is connected to DXFeed demo server.
 * <pre><tt>
 * DXFeed feed = {@link DXEndpoint DXEndpoint}.{@link DXEndpoint#create() create}()
 *     .{@link #user(String) user}("demo").{@link #password(String) password}("demo")
 *     .{@link DXEndpoint#connect(String) connect}("demo.dxfeed.com:7300")
 *     .{@link DXEndpoint#getFeed() getFeed}();</tt></pre>
 *
 * See {@link DXFeed} for details on how to subscribe to symbols and receive events.
 *
 * <h3>Endpoint role</h3>
 *
 * Each endpoint has a role that is specified on its creation and cannot be changed afterwards.
 * The default factory method {@link #create()} creates an endpoint with a {@link Role#FEED FEED} role.
 * Endpoints with other roles are created with {@link #create(Role)} factory method. Endpoint role is
 * represented by {@link Role DXEndpoint.Role} enumeration.
 *
 * <p> Endpoint role defines the behavior of its {@link #connect(String) connect} method:
 * <ul>
 *     <li>{@link Role#FEED FEED} connects to the remote data feed provider and is optimized for real-time or
 *          delayed data processing (<b>this is a default role</b>).
 *          {@link #getFeed()} method returns
 *          a feed object that subscribes to this remote data feed provider and receives events from it.
 *          When event processing threads cannot keep up (don't have enough CPU time), data is dynamically conflated to
 *          minimize latency between received events and their processing time.
 *          For example:
 *          <ul>
 *              <li>{@code DXEndpoint.create().connect("demo.dxfeed.com:7300").getFeed()} returns a
 *                  demo feed from dxFeed with sample market quotes.</li>
 *              <li>{@code DXEndpoint.create().connect("localhost:7400").getFeed()} returns a feed
 *                  that is connected to a publisher that is running on the same host. See example
 *                  below.</li>
 *              <li>{@code DXEndpoint.create().connect("file:demo-sample.data").getFeed()} returns a feed
 *                  that is connected to a "demo-sample.data" file and plays back it as if it was received in real time.
 *                  File playback is supported only when optional "<b>qds-file.jar</b>" is present in the classpath.</li>
 *          </ul>
 *          This endpoint is automatically connected to the configured data feed as explained in
 *          <a href="#defaultPropertiesSection">default properties section</a>.
 *     </li>
 *     <li>
 *         {@link Role#ON_DEMAND_FEED ON_DEMAND_FEED} is similar to {@link Role#FEED FEED}, but it is designed to be used with
 *         {@link OnDemandService} for historical data replay only. It is configured with
 *         <a href="#defaultPropertiesSection">default properties</a>, but is not connected automatically
 *         to the data provider until {@link OnDemandService#replay(Date, double) OnDemandService.replay}
 *         method is invoked.
 *     </li>
 *     <li>{@link Role#STREAM_FEED STREAM_FEED} is similar to {@link Role#FEED FEED} and also
 *         connects to the remote data feed provider, but is designed for bulk
 *         parsing of data from files. {@link DXEndpoint#getFeed()} method
 *         returns feed object that subscribes to the data from the opened files and receives events from them.
 *         Events from the files are not conflated and are processed as fast as possible.
 *         Note, that in this role, {@link DXFeed#getLastEvent} method does not work and
 *         time-series subscription is not supported.
 *         File playback is supported only when optional "<b>qds-file.jar</b>" is present in the classpath.
 *         For example:
 *         <pre><tt>
 *            DXEndpoint endpoint = DXEndpoint.create(DXEndpoint.Role.{@link Role#STREAM_FEED STREAM_FEED});
 *            {@link DXFeed DXFeed} feed = endpoint.{@link DXEndpoint#getFeed() getFeed}();</tt></pre>
 *        creates a feed that is ready to read data from file as soon as the following code is invoked:
 *        <pre><tt>
 *            endpoint.{@link #connect(String) connect}("file:demo-sample.data[speed=max]");</tt></pre>
 *        "[speed=max]" clause forces to the file reader to play back all
 *        the data from "demo-sample.data" file as fast as data subscribers are processing it.
 *     </li>
 *     <li>{@link Role#PUBLISHER PUBLISHER} connects to the remote publisher hub (also known as multiplexor) or
 *          creates a publisher on the local host. {@link #getPublisher()} method returns
 *          a publisher object that publishes events to all connected feeds. For example:
 *          <ul>
 *              <li>{@code DXEndpoint.create(DXEndpoint.Role.PUBLISHER).connect(":7400").getPublisher()} returns a
 *                  publisher that is waiting for connections on TCP/IP port 7400. The published events will be
 *                  delivered to all feeds that are connected to this publisher.</li>
 *          </ul>
 *          This endpoint is automatically connected to the configured data feed as explained in
 *          <a href="#defaultPropertiesSection">default properties section</a>.
 *     </li>
 *     <li>{@link Role#LOCAL_HUB LOCAL_HUB} creates a local hub without ability to establish network connections.
 *         Events that are published via {@link #getPublisher() publisher} are delivered to local
 *         {@link #getFeed() feed} only.
 *     </li>
 * </ul>
 *
 * <h3>Endpoint state</h3>
 *
 * Each endpoint has a state that can be retrieved with {@link #getState() getState} method.
 * When endpoint is created with any role and default address is not specified in
 * <a href="#defaultPropertiesSection">default properties</a>, then
 * it is not connected to any remote endpoint.
 * Its state is {@link State#NOT_CONNECTED NOT_CONNECTED}.
 *
 * <p> {@link Role#FEED Feed} and {@link Role#PUBLISHER publisher} endpoints can connect to remote endpoints
 * of the opposite role. Connection is initiated by {@link #connect(String) connect} method.
 * The endpoint state becomes {@link State#CONNECTING CONNECTING}.
 *
 * <p> When the actual connection to the remote endpoint is established, the endpoint state becomes
 * {@link State#CONNECTED CONNECTED}.
 *
 * <p>Network connections can temporarily break and return endpoint back into {@link State#CONNECTING CONNECTING} state.
 * File connections can be completed and return endpoint into {@link State#NOT_CONNECTED NOT_CONNECTED} state.
 *
 * <p> Connection to the remote endpoint can be terminated with {@link #disconnect() disconnect} method.
 * The endpoint state becomes {@link State#NOT_CONNECTED NOT_CONNECTED}.
 *
 * <p> Endpoint can be closed with {@link #close() close} method. The endpoint state
 * becomes {@link State#CLOSED CLOSED}. This is a final state. All connection are terminated and
 * all internal resources that are held by this endpoint are freed.
 * No further connections can be initiated.
 *
 * <h3>Event times</h3>
 *
 * <p>The {@link EventType#getEventTime() EventType.getEventTime} on received events is available only when the
 * endpoint is created with with {@link #DXENDPOINT_EVENT_TIME_PROPERTY DXENDPOINT_EVENT_TIME_PROPERTY} property and
 * the data source has embedded event times. This is typically true only for data events
 * that are read from historical tape files (see above) and from {@link OnDemandService OnDemandService}.
 * Events that are coming from a network connections do not have an embedded event time information and
 * event time is not available for them anyway.
 *
 * <h3><a name="defaultPropertiesSection">Default properties</a></h3>
 *
 * Default properties are loaded from "dxfeed.properties" or "dxpublisher.properties" file depending on
 * the {@link Role role} of created endpoint. "dxfeed.properties" is used for
 * {@link Role#FEED FEED} and {@link Role#ON_DEMAND_FEED ON_DEMAND_FEED},
 * "dxpublisher.properties" is used for
 * {@link Role#PUBLISHER PUBLISHER}.
 * {@link Role#STREAM_FEED STREAM_FEED} and {@link Role#LOCAL_HUB LOCAL_HUB} do not support properties file.
 *
 * <p>The location of this file can be specified using
 * {@link Builder#withProperty(String, String) withProperty}({@link #DXFEED_PROPERTIES_PROPERTY}, path) or
 * {@link Builder#withProperty(String, String) withProperty}({@link #DXPUBLISHER_PROPERTIES_PROPERTY}, path)
 * correspondingly. When the location of this file is not explicitly specified using
 * {@link Builder#withProperty(String, String) withProperty} method, then the file path is taken from a system
 * property with the corresponding name.
 *
 * <p>When the path to the above properties file is not provided, then a resource named "dxfeed.properties" or
 * "dxpublisher.properties" is loaded from classpath. When classpath is set to "." (current directory),
 * it means that the corresponding file can be placed into the current directory with any need to specify additional
 * properties.
 *
 * <p>Defaults for individual properties can be also provided using system properties when they are not specified
 * in the configuration file. System properties override configuration loaded from classpath resource, but don't
 * override configuration from the user-specified configuration file.
 *
 * <p>The {@link #NAME_PROPERTY} is the exception from the above rule. It is never loaded from system properties.
 * It can be only specified in configuration file or programmatically. There is a convenience
 * {@link Builder#withName(String) Builder.withName} method for it. It is recommended to assign short and
 * meaningful endpoint names when multiple endpoints are used in the same JVM. The name of the endpoint shall
 * describe its role in the particular application.
 *
 * <p>Note, that individual properties that are programmatically set using {@link Builder#withProperty(String, String) withProperty}
 * method always take precedence.
 *
 * <p>{@link Role#FEED FEED} and {@link Role#PUBLISHER PUBLISHER} automatically establish connection on creation
 * when the corresponding {@link #DXFEED_ADDRESS_PROPERTY} or {@link #DXPUBLISHER_ADDRESS_PROPERTY} is specified.
 *
 * <h3>Permanent subscription</h3>
 *
 * Endpoint properties can define permanent subscription for specific sets of symbols and event types in
 * the data feed, so that {@link DXFeed} methods like {@link DXFeed#getLastEventIfSubscribed getLastEventIfSubscribed},
 * {@link DXFeed#getIndexedEventsIfSubscribed getIndexedEventsIfSubscribed}, and
 * {@link DXFeed#getTimeSeriesIfSubscribed getTimeSeriesIfSubscribed} can be used without a need to create a
 * separate {@link DXFeedSubscription DXFeedSubscription} object. Please, contact dxFeed support for details
 * on the required configuration.
 *
 * <h3>Threads and locks</h3>
 *
 * This class is thread-safe and can be used concurrently from multiple threads without external synchronization.
 *
 * <h3>Implementation details</h3>
 *
 * dxFeed API is implemented on top of QDS. dxFeed API classes itself are in "<b>dxfeed-api.jar</b>", but
 * their implementation is in "<b>qds.jar</b>". You need to have "<b>qds.jar</b>" in your classpath
 * in order to use dxFeed API.
 */
public abstract class DXEndpoint implements AutoCloseable {
    /**
     * Defines property for endpoint name that is used to distinguish multiple endpoints
     * in the same JVM in logs and in other diagnostic means.
     * Use {@link Builder#withProperty(String, String)} method.
     * This property is also changed by {@link Builder#withName(String)} method.
     */
    public static final String NAME_PROPERTY = "name";

    /**
     * Defines path to a file with properties for an endpoint with role
     * {@link Role#FEED FEED} or {@link Role#ON_DEMAND_FEED ON_DEMAND_FEED}.
     * By default, properties a loaded from a classpath resource named "dxfeed.properties".
     * @see Builder#withProperty(String, String) Builder.withProperty
     */
    public static final String DXFEED_PROPERTIES_PROPERTY = "dxfeed.properties";

    /**
     * Defines default connection address for an endpoint with role
     * {@link Role#FEED FEED} or {@link Role#ON_DEMAND_FEED ON_DEMAND_FEED}.
     * Connection is established to this address by role {@link Role#FEED FEED} as soon as endpoint is created,
     * while role {@link Role#ON_DEMAND_FEED ON_DEMAND_FEED} waits until
     * {@link OnDemandService#replay(Date, double) OnDemandService.replay} is invoked before connecting.
     * <p>
     * By default, without this property, connection is not established until
     * {@link #connect(String) connect(address)} is invoked.
     * <p>
     * Credentials for access to premium services may be configured with
     * {@link #DXFEED_USER_PROPERTY} and {@link #DXFEED_PASSWORD_PROPERTY}.
     *
     * @see Builder#withProperty(String, String) Builder.withProperty
     */
    public static final String DXFEED_ADDRESS_PROPERTY = "dxfeed.address";

    /**
     * Defines default user name for an endpoint with role
     * {@link Role#FEED FEED} or {@link Role#ON_DEMAND_FEED ON_DEMAND_FEED}.
     * @see #user(String)
     */
    public static final String DXFEED_USER_PROPERTY = "dxfeed.user";

    /**
     * Defines default password for an endpoint with role
     * {@link Role#FEED FEED} or {@link Role#ON_DEMAND_FEED ON_DEMAND_FEED}.
     * @see #password(String)
     */
    public static final String DXFEED_PASSWORD_PROPERTY = "dxfeed.password";

    /**
     * Defines thread pool size for an endpoint with role {@link Role#FEED FEED}.
     * By default, the thread pool size is equal to the number of available processors.
     * @see Builder#withProperty(String, String) Builder.withProperty
     */
    public static final String DXFEED_THREAD_POOL_SIZE_PROPERTY = "dxfeed.threadPoolSize";

    /**
     * Defines data aggregation period an endpoint with role {@link Role#FEED FEED} that
     * limits the rate of data notifications. For example, setting the value of this property
     * to "0.1s" limits notification to once every 100ms (at most 10 per second).
     * @see Builder#withProperty(String, String) Builder.withProperty
     */
    public static final String DXFEED_AGGREGATION_PERIOD_PROPERTY = "dxfeed.aggregationPeriod";

    /**
     * Set this property to {@code true} to turns on wildcard support.
     * By default, the endpoint does not support wildcards. This property is needed for
     * {@link WildcardSymbol} support and for the use of "tape:..." address in {@link DXPublisher}.
     */
    public static final String DXFEED_WILDCARD_ENABLE_PROPERTY = "dxfeed.wildcard.enable";

    /**
     * Defines symbol striping strategy for an endpoint.
     */
    public static final String DXFEED_STRIPE_PROPERTY = "dxfeed.stripe";

    /**
     * Defines path to a file with properties for an endpoint with role {@link Role#PUBLISHER PUBLISHER}.
     * By default, properties a loaded from a classpath resource named "dxpublisher.properties".
     * @see Builder#withProperty(String, String) Builder.withProperty
     */
    public static final String DXPUBLISHER_PROPERTIES_PROPERTY = "dxpublisher.properties";

    /**
     * Defines default connection address for an endpoint with role {@link Role#PUBLISHER PUBLISHER}.
     * Connection is established to this address as soon as endpoint is created.
     * By default, connection is not established until {@link #connect(String) connect(address)} is invoked.
     * @see Builder#withProperty(String, String) Builder.withProperty
     */
    public static final String DXPUBLISHER_ADDRESS_PROPERTY = "dxpublisher.address";

    /**
     * Defines thread pool size for an endpoint with role {@link Role#PUBLISHER PUBLISHER}.
     * By default, the thread pool size is equal to the number of available processors.
     * @see Builder#withProperty(String, String) Builder.withProperty
     */
    public static final String DXPUBLISHER_THREAD_POOL_SIZE_PROPERTY = "dxpublisher.threadPoolSize";

    /**
     * Set this property to {@code true} to enable {@link EventType#getEventTime() event time} support.
     * By default, the endpoint does not support event time.
     *
     * <p>The event time is available only when the corresponding {@link DXEndpoint} is created
     * with this property and
     * the data source has embedded event times. This is typically true only for data events
     * that are read from historical tape files and from {@link OnDemandService OnDemandService}.
     * Events that are coming from a network connections do not have an embedded event time information and
     * event time is not available for them anyway.
     *
     * <p>Use this property if you need to work with historical data coming from files
     * or from {@link OnDemandService OnDemandService} or writing data with times to file via
     * {@link DXPublisher} using "tape:..." address.
     */
    public static final String DXENDPOINT_EVENT_TIME_PROPERTY = "dxendpoint.eventTime";

    /**
     * Set this property to  to store all {@link com.dxfeed.event.LastingEvent lasting}
     * and {@link com.dxfeed.event.IndexedEvent indexed} events even when there is no subscription on them.
     * By default, the endpoint stores only events from subscriptions. It works in the same way both
     * for {@link DXFeed} and {@link DXPublisher}.
     *
     * <p>Use this property with extreme care,
     * since API does not currently provide any means to remove those events from the storage and there might
     * be an effective memory leak if the spaces of symbols on which events are published grows without bound.
     */
    public static final String DXENDPOINT_STORE_EVERYTHING_PROPERTY = "dxendpoint.storeEverything";

    /**
     * Set this property to {@code true} to turn on nanoseconds precision business time.
     * By default, this feature is turned off.
     * Business time in most events is available with
     * millisecond precision by default, while {@link Quote Quote} events business
     * {@link Quote#getTime() time} is available with seconds precision.
     *
     * <p>This method provides a higher-level control than turning on individual properties that are responsible
     * for nano-time via {@link #DXSCHEME_ENABLED_PROPERTY_PREFIX}. The later can be used to override of fine-time
     * nano-time support for individual fields. Setting this property to {@code true} is essentially
     * equivalent to setting<br>
     * <pre><tt>
     * dxscheme.enabled.Sequence=*
     * dxscheme.enabled.TimeNanoPart=*
     * </tt></pre>
     */
    public static final String DXSCHEME_NANO_TIME_PROPERTY = "dxscheme.nanoTime";

    /**
     * Defines whether a specified field from the scheme should be enabled instead of it's default behaviour.
     * Use it according to following format:
     *
     * <p>{@code dxscheme.enabled.<field_property_name>=<event_name_mask_glob>}
     *
     * <p>For example, <b>{@code dxscheme.enabled.TimeNanoPart=Trade}</b> enables {@code NanoTimePart} internal field
     * only in {@link com.dxfeed.event.market.Trade Trade} events.
     *
     * <p>There is a shortcut for turning on nano-time support using {@link #DXSCHEME_NANO_TIME_PROPERTY}.
     */
    public static final String DXSCHEME_ENABLED_PROPERTY_PREFIX = "dxscheme.enabled.";

    /**
     * Represents the role of endpoint that was specified during its {@link DXEndpoint#create() creation}.
     *
     * @see DXEndpoint
     */
    public enum Role {
        /**
         * {@code FEED} endpoint connects to the remote data feed provider and is optimized for real-time or
         * delayed data processing (<b>this is a default role</b>). {@link DXEndpoint#getFeed()} method
         * returns feed object that subscribes to the remote data feed provider and receives events from it.
         * When event processing threads cannot keep up (don't have enough CPU time), data is dynamically conflated to
         * minimize latency between received events and their processing time.
         *
         * <p>This endpoint is automatically connected to the configured data feed as explained in
         * <a href="#defaultPropertiesSection">default properties section</a>.
         */
        FEED,

        /**
         * {@code ON_DEMAND_FEED} endpoint is similar to {@link #FEED}, but it is designed to be used with
         * {@link OnDemandService} for historical data replay only. It is configured with
         * <a href="#defaultPropertiesSection">default properties</a>, but is not connected automatically
         * to the data provider until {@link OnDemandService#replay(Date, double) OnDemandService.replay}
         * method is invoked.
         *
         * <p>{@code ON_DEMAND_FEED} endpoint cannot be connected to an ordinary data feed at all.
         * {@link OnDemandService#stopAndResume()} will have a similar effect to {@link OnDemandService#stopAndClear()}.
         *
         * @see OnDemandService
         */
        ON_DEMAND_FEED,

        /**
         * {@code STREAM_FEED} endpoint is similar to {@link #FEED} and also connects to the remote data feed provider,
         * but is designed for bulk parsing of data from files. {@link DXEndpoint#getFeed()} method
         * returns feed object that subscribes to the data from the opened files and receives events from them.
         * Events from the files are not conflated, are not skipped, and are processed as fast as possible.
         * Note, that in this role, {@link DXFeed#getLastEvent} method does not work.
         */
        STREAM_FEED,

        /**
         * {@code PUBLISHER} endpoint connects to the remote publisher hub (also known as multiplexor) or
         * creates a publisher on the local host. {@link DXEndpoint#getPublisher()} method returns
         * a publisher object that publishes events to all connected feeds.
         * Note, that in this role, {@link DXFeed#getLastEvent} method does not work and
         * time-series subscription is not supported.
         *
         * <p>This endpoint is automatically connected to the configured data feed as explained in
         * <a href="#defaultPropertiesSection">default properties section</a>.
         */
        PUBLISHER,

        /**
         * {@code STREAM_PUBLISHER} endpoint is similar to {@link #PUBLISHER} and also connects to the remote publisher hub,
         * but is designed for bulk publishing of data. {@link DXEndpoint#getPublisher()} method returns
         * a publisher object that publishes events to all connected feeds.
         * Published events are not conflated, are not skipped, and are processed as fast as possible.
         * Note, that in this role, {@link DXFeed#getLastEvent} method does not work and
         * time-series subscription is not supported.
         */
        STREAM_PUBLISHER,

        /**
         * {@code LOCAL_HUB} endpoint is a local hub without ability to establish network connections.
         * Events that are published via {@link DXEndpoint#getPublisher() publisher} are delivered to local
         * {@link DXEndpoint#getFeed() feed} only.
         */
        LOCAL_HUB
    }

    /**
     * Represents the current state of endpoint.
     *
     * @see DXEndpoint
     */
    public enum State {
        /**
         * Endpoint was created by is not connected to remote endpoints.
         */
        NOT_CONNECTED,

        /**
         * The {@link DXEndpoint#connect(String) connect} method was called to establish connection to remove endpoint,
         * but connection is not actually established yet or was lost.
         */
        CONNECTING,

        /**
         * The connection to remote endpoint is established.
         */
        CONNECTED,

        /**
         * Endpoint was {@link DXEndpoint#close() closed}.
         */
        CLOSED
    }

    /**
     * Protected constructor for implementations of {@code DXEndpoint}.
     */
    protected DXEndpoint() {}

    private static final EnumMap<Role, DXEndpoint> INSTANCES = new EnumMap<>(Role.class);

    /**
     * Returns a default application-wide singleton instance of DXEndpoint with a {@link Role#FEED FEED} role.
     * Most applications use only a single data-source and should rely on this method to get one.
     * This method creates an endpoint on the first use with a default
     * configuration as explained in
     * <a href="#defaultPropertiesSection">default properties section</a> of {@link DXEndpoint} class documentation.
     * You can provide configuration via classpath or via system properties as explained there.
     *
     * <p>This is a shortcut to
     * {@link #getInstance(Role) getInstance}({@link DXEndpoint}.{@link DXEndpoint.Role Role}.{@link DXEndpoint.Role#FEED FEED}).
     * @see #getInstance(Role)
     */
    public static DXEndpoint getInstance() {
        return getInstance(Role.FEED);
    }

    /**
     * Returns a default application-wide singleton instance of DXEndpoint for a specific role.
     * Most applications use only a single data-source and should rely on this method to get one.
     * This method creates an endpoint with the corresponding role on the first use with a default
     * configuration as explained in
     * <a href="#defaultPropertiesSection">default properties section</a> of {@link DXEndpoint} class documentation.
     * You can provide configuration via classpath or via system properties as explained there.
     *
     * <p>The configuration does not have to include an address. You can use {@link #connect(String) connect(addresss)}
     * and {@link #disconnect()} methods on the instance that is returned by this method to programmatically
     * establish and tear-down connection to a user-provided address.
     *
     * <p>If you need a fully programmatic configuration and/or multiple endpoints of the same role in your
     * application, then create a custom instance of {@link DXEndpoint} with
     * {@link DXEndpoint#newBuilder() DXEndoint.newBuilder()} method, configure it,
     * and use {@link Builder#build() build()} method.
     * @throws NullPointerException if role is null.
     */
    public static DXEndpoint getInstance(Role role) {
        synchronized (INSTANCES) {
            DXEndpoint instance = INSTANCES.get(role);
            if (instance == null) {
                instance = newBuilder().withRole(role).build();
                INSTANCES.put(role, instance);
            }
            return instance;
        }
    }

    /**
     * Creates new {@link Builder} instance.
     * Use {@link Builder#build()} to build an instance of {@link DXEndpoint} when
     * all configuration properties were set.
     *
     * @return the created endpoint builder.
     */
    public static Builder newBuilder() {
        Builder builder = Services.createService(Builder.class, null, null);
        if (builder == null)
            throw new IllegalStateException("There is no " + Builder.class + " implementation service in class path");
        return builder;
    }

    /**
     * Creates an endpoint with {@link Role#FEED FEED} role.
     * The result of this method is the same as {@code create(DXEndpoint.Role.FEED)}.
     * This is a shortcut to
     * {@link #newBuilder() newBuilder()}.{@link Builder#build() build()}
     *
     * @return the created endpoint.
     */
    public static DXEndpoint create() {
        return newBuilder().build();
    }

    /**
     * Creates an endpoint with a specified role.
     * This is a shortcut to
     * {@link #newBuilder() newBuilder()}.{@link Builder#withRole(DXEndpoint.Role) withRole(role)}.{@link Builder#build() build()}
     *
     * @param role the role.
     * @return the created endpoint.
     */
    public static DXEndpoint create(Role role) {
        return newBuilder().withRole(role).build();
    }

    /**
     * Returns the role of this endpoint.
     *
     * @return the role.
     *
     * @see DXEndpoint
     */
    public abstract Role getRole();

    /**
     * Returns the state of this endpoint.
     *
     * @return the state.
     *
     * @see DXEndpoint
     */
    public abstract State getState();

    /**
     * Adds listener that is notified about changes in {@link #getState() state} property.
     * Notification will be performed using this endpoint's {@link #executor(Executor) executor}.
     *
     * <p>Installed listener can be removed with
     * {@link #removeStateChangeListener(PropertyChangeListener) removeStateChangeListener} method.
     *
     * @param listener the listener to add.
     */
    public abstract void addStateChangeListener(PropertyChangeListener listener);

    /**
     * Removes listener that is notified about changes in {@link #getState() state} property.
     * It removes the listener that was previously installed with
     * {@link #addStateChangeListener(PropertyChangeListener) addStateChangeListener} method.
     * @param listener the listener to remove.
     */
    public abstract void removeStateChangeListener(PropertyChangeListener listener);

    /**
     * Changes executor that is used for notifications.
     * By default, the thread pool with the size equal to the number of available processors is used.
     * The number of threads in the default pool can be configured using
     * {@link #DXFEED_THREAD_POOL_SIZE_PROPERTY DXFEED_THREAD_POOL_SIZE_PROPERTY}
     * for endpoints with role
     * {@link Role#FEED FEED} and {@link Role#ON_DEMAND_FEED ON_DEMAND_FEED} and
     * with {@link #DXPUBLISHER_THREAD_POOL_SIZE_PROPERTY DXPUBLISHER_THREAD_POOL_SIZE_PROPERTY}
     * for endpoints with role
     * {@link Role#PUBLISHER PUBLISHER}.
     * See also <a href="#defaultPropertiesSection">default properties section</a>.
     *
     * @param executor the executor.
     * @return this {@code DXEndpoint}.
     * @throws NullPointerException if executor is null.
     */
    public abstract DXEndpoint executor(Executor executor);

    /**
     * Changes user name for this endpoint.
     * This method shall be called before {@link #connect(String) connect} together
     * with {@link #password(String) password} to configure service access credentials.
     *
     * @param user user name.
     * @return this {@code DXEndpoint}.
     * @throws NullPointerException if user is null.
     */
    public abstract DXEndpoint user(String user);

    /**
     * Changes password for this endpoint.
     * This method shall be called before {@link #connect(String) connect} together
     * with {@link #user(String) user} to configure service access credentials.
     *
     * @param password password.
     * @return this {@code DXEndpoint}.
     * @throws NullPointerException if password is null.
     */
    public abstract DXEndpoint password(String password);

    /**
     * Connects to the specified remote address. Previously established connections are closed if
     * the new address is different from the old one.
     * This method does nothing if address does not change or if this endpoint is {@link State#CLOSED CLOSED}.
     * The endpoint {@link #getState() state} immediately becomes {@link State#CONNECTING CONNECTING} otherwise.
     *
     * <p> The address string is provided with the market data vendor agreement.
     * Use "demo.dxfeed.com:7300" for a demo quote feed.
     *
     * <p> The simplest address strings have the following format:
     * <ul>
     * <li> {@code host:port} to establish a TCP/IP connection.
     * <li> {@code :port} to listen for a TCP/IP connection with a plain socket connector (good for up to a
     *      few hundred of connections).
     * </ul>
     *
     * <p>For premium services access credentials must be configured before invocation of {@code connect} method
     * using {@link #user(String) user} and {@link #password(String) password} methods.
     *
     * <p> More information on address strings is available via the command-line QDS help tool.
     * Use the following command line to retrieve it:
     * <pre>java -jar qds-tools.jar help address</pre>
     *
     * <p> <b>This method does not wait until connection actually gets established</b>. The actual connection establishment
     * happens asynchronously after the invocation of this method. However, this method waits until notification
     * about state transition from {@link State#NOT_CONNECTED State.NOT_CONNECTED} to {@link State#CONNECTING State.CONNECTING}
     * gets processed by all {@link PropertyChangeListener listeners} that were installed via
     * {@link #addStateChangeListener(PropertyChangeListener) addStateChangeListener} method.
     *
     * @param address the data source address.
     * @return this {@code DXEndpoint}.
     * @throws NullPointerException if address is null.
     * @throws IllegalArgumentException if address string is malformed.
     */
    public abstract DXEndpoint connect(String address);

    /**
     * Terminates all established network connections and initiates connecting again with the same address.
     *
     * <p>The effect of the method is alike to invoking {@link #disconnect()} and {@link #connect(String)}
     * with the current address, but internal resources used for connections may be reused by implementation.
     * TCP connections with multiple target addresses will try switch to an alternative address, configured
     * reconnect timeouts will apply.
     *
     * <p><b>Note:</b> The method will not connect endpoint that was not initially connected with
     * {@link #connect(String)} method or was disconnected with {@link #disconnect()} method.
     *
     * <p>The method initiates a short-path way for reconnecting, so whether observers will have a chance to see
     * an intermediate state {@link State#NOT_CONNECTED State.NOT_CONNECTED} depends on the implementation.
     */
    public abstract void reconnect();

    /**
     * Terminates all remote network connections.
     * This method does nothing if this endpoint is {@link State#CLOSED CLOSED}.
     * The endpoint {@link #getState() state} immediately becomes {@link State#NOT_CONNECTED NOT_CONNECTED} otherwise.
     *
     * <p>This method does not release all resources that are associated with this endpoint.
     * Use {@link #close()} method to release all resources.
     */
    public abstract void disconnect();

    /**
     * Terminates all remote network connections and clears stored data.
     * This method does nothing if this endpoint is {@link State#CLOSED CLOSED}.
     * The endpoint {@link #getState() state} immediately becomes {@link State#NOT_CONNECTED NOT_CONNECTED} otherwise.
     *
     * <p>This method does not release all resources that are associated with this endpoint.
     * Use {@link #close()} method to release all resources.
     */
    public abstract void disconnectAndClear();

    /**
     * Closes this endpoint. All network connection are terminated as with
     * {@link #disconnect() disconnect} method and no further connections
     * can be established.
     * The endpoint {@link #getState() state} immediately becomes {@link State#CLOSED CLOSED}.
     * All resources associated with this endpoint are released.
     */
    @Override
    public abstract void close();

    /**
     * Waits while this endpoint {@link #getState() state} becomes {@link State#NOT_CONNECTED NOT_CONNECTED} or
     * {@link State#CLOSED CLOSED}. It is a signal that any files that were opened with
     * {@link #connect(String) connect("file:...")} method were finished reading, but not necessary were completely
     * processed by the corresponding subscription listeners. Use {@link #closeAndAwaitTermination()} after
     * this method returns to make sure that all processing has completed.
     *
     * <p><b>This method is blocking.</b> When using a <em>single-threaded</em> {@link #executor(Executor) executor}
     * with endpoint, don't invoke this method from the executor thread &mdash; it will wait forever, blocking
     * the same thread that is needed to complete the action that is being waited for.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    public abstract void awaitNotConnected() throws InterruptedException;

    /**
     * Waits until this endpoint stops processing data (becomes quescient).
     * This is important when writing data to file via "tape:..." connector to make sure that
     * all published data was written before closing this endpoint.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    public abstract void awaitProcessed() throws InterruptedException;

    /**
     * Closes this endpoint and wait until all pending data processing tasks are completed.
     * This  method performs the same actions as close {@link #close()}, but also awaits
     * termination of all outstanding data processing tasks. It is designed to be used
     * with {@link Role#STREAM_FEED STREAM_FEED} role after {@link #awaitNotConnected()} method returns
     * to make sure that file was completely processed.
     *
     * <p><b>This method is blocking.</b> When using a <em>single-threaded</em> {@link #executor(Executor) executor}
     * with endpoint, don't invoke this method from the executor thread &mdash; it will wait forever, blocking
     * the same thread that is needed to complete the action that is being waited for.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    public abstract void closeAndAwaitTermination() throws InterruptedException;

    /**
     * Returns a set of all event types supported by this endpoint. The resulting set cannot be modified.
     */
    public abstract Set<Class<? extends EventType<?>>> getEventTypes();

    /**
     * Returns feed that is associated with this endpoint.
     *
     * @return the feed.
     */
    public abstract DXFeed getFeed();

    /**
     * Returns publisher that is associated with this endpoint.
     *
     * @return the publisher.
     */
    public abstract DXPublisher getPublisher();

    /**
     * Builder class for {@link DXEndpoint} that supports additional configuration properties.
     */
    @Service
    public abstract static class Builder {

        /**
         * Current role for implementations of {@link Builder}.
         */
        protected Role role = Role.FEED;

        /**
         * Protected constructor for implementations of {@link Builder}.
         */
        protected Builder() {}

        /**
         * Changes name that is used to distinguish multiple endpoints
         * in the same JVM in logs and in other diagnostic means.
         * This is a shortcut for
         * {@link #withProperty withProperty}({@link #NAME_PROPERTY NAME_PROPERTY},{@code name})
         */
        public final Builder withName(String name) {
            return withProperty(NAME_PROPERTY, name);
        }

        /**
         * Sets role for the created {@link DXEndpoint}.
         * Default role is {@link Role#FEED FEED}.
         *
         * @return {@code this} endpoint builder.
         */
        public Builder withRole(Role role) {
            if (role == null)
                throw new NullPointerException();
            this.role = role;
            return this;
        }

        /**
         * Sets the specified property. Unsupported properties are ignored.
         *
         * @return {@code this} endpoint builder.
         * @see #supportsProperty(String)
         */
        public abstract Builder withProperty(String key, String value);

        /**
         * Sets all supported properties from the provided properties object.
         *
         * @return {@code this} endpoint builder.
         * @see #withProperty(String, String)
         */
        public Builder withProperties(Properties props) {
            for (Map.Entry<Object, Object> entry : props.entrySet()) {
                String key = (String) entry.getKey();
                withProperty(key, (String) entry.getValue());
            }
            return this;
        }

        /**
         * Returns true if the corresponding property key is supported.
         * @see #withProperty(String, String)
         */
        public abstract boolean supportsProperty(String key);

        /**
         * Builds {@link DXEndpoint} instance.
         *
         * @return the created endpoint.
         */
        public abstract DXEndpoint build();
    }
}
