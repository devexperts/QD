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
package com.dxfeed.webservice.comet;

import com.devexperts.logging.Logging;
import org.cometd.bayeux.Promise;
import org.cometd.server.ServerSessionImpl;
import org.cometd.server.websocket.common.AbstractWebSocketEndPoint;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Queue;

public class CometReflectionUtil {
    private static final Logging log = Logging.getLogging(CometReflectionUtil.class);

    private static boolean reflectionIssueHappened;

    /**
     * Predefined Field instance for optimized extraction of AbstractWebSocketEndPoint._session
     */
    private static final Field END_POINT_SESSION_FIELD;
    static {
        try {
            END_POINT_SESSION_FIELD = AbstractWebSocketEndPoint.class.getDeclaredField("_session");
        } catch (NoSuchFieldException e) {
            reportReflectionIssue("AbstractWebSocketEndPoint._session field not found", e);
            throw new IllegalStateException(e);
        }
        END_POINT_SESSION_FIELD.setAccessible(true);
    }

    static void sessionImplCleanup(ServerSessionImpl sessionImpl) {
        try {
            Object flusher = extractFlusher(sessionImpl);
            if (flusher == null)
                return;
            Queue<?> queue = extractFlusherQueueUnsafe(flusher);
            synchronized (flusher) {
                if (queue.isEmpty())
                    return;

                log.info("Found non-empty Flusher queue: " + queue.size());
                Object entryObject;
                while ((entryObject = queue.poll()) != null) {
                    Object serverMessageQueueInstance = getPrivateFieldByName(entryObject, "_queue");
                    if (serverMessageQueueInstance instanceof List) {
                        List<?> serverMessageQueue = (List<?>) serverMessageQueueInstance;
                        serverMessageQueue.clear();
                    }
                    Object promiseInstance = getPrivateFieldByName(entryObject, "_promise");
                    if (promiseInstance instanceof Promise) {
                        Promise<?> promise = (Promise<?>) promiseInstance;
                        promise.fail(new IllegalStateException("ServerSessionImpl is closed"));
                    }
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            reportReflectionIssue("Field not found in decomposition of ServerSessionImpl class.", e);
        }
    }

    static Object extractFlusher(AbstractWebSocketEndPoint endPoint) {
        try {
            return extractFlusherUnsafe(endPoint);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            reportReflectionIssue("Flusher queue not found in AbstractWebSocketEndPoint.", e);
            return null;
        }
    }

    static Queue<?> extractFlusherQueue(AbstractWebSocketEndPoint endPoint) {
        try {
            return extractFlusherQueueUnsafe(extractFlusher(endPoint));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            reportReflectionIssue("Flusher queue not found in AbstractWebSocketEndPoint.", e);
            return null;
        }
    }

    static ServerSessionImpl extractSessionInstance(AbstractWebSocketEndPoint endPoint) {
        try {
            return (ServerSessionImpl) getPrivateFieldByField(endPoint, END_POINT_SESSION_FIELD);
        } catch (IllegalAccessException e) {
            reportReflectionIssue("ServerSessionImpl not found in AbstractWebSocketEndPoint.Context.", e);
            return null;
        }
    }

    /**
     * Extracts "private final Flusher flusher" field from AbstractWebSocketEndPoint instance.
     *
     * @param endPoint An instance of {@link AbstractWebSocketEndPoint}.
     * @return An instance of {@link AbstractWebSocketEndPoint.Flusher}.
     *          It would be great to cast the result to a specific Flusher type but it can't be done
     *          because Flusher is a private sub-class of AbstractWebSocketEndPoint and can't be referred.
     */
    private static Object extractFlusherUnsafe(AbstractWebSocketEndPoint endPoint)
        throws NoSuchFieldException, IllegalAccessException
    {
        Object flusherInstance = getPrivateFieldByName(endPoint, "flusher",
            AbstractWebSocketEndPoint.class);
        if (flusherInstance == null) {
            throw new IllegalAccessException("No reflection exceptions " +
                "but Flusher instance not found in decomposition of AbstractWebSocketEndPoint.");
        }
        return flusherInstance;
    }

    /**
     * @param flusherInstance An instance of {@link AbstractWebSocketEndPoint.Flusher}.
     */
    private static Queue<?> extractFlusherQueueUnsafe(Object flusherInstance)
        throws NoSuchFieldException, IllegalAccessException
    {
        Object queueInstance = getPrivateFieldByName(flusherInstance, "_entries");
        if (!(queueInstance instanceof Queue)) {
            throw new IllegalAccessException("No reflection exceptions " +
                "but Queue<> field of Flusher not found in decomposition of AbstractWebSocketEndPoint.");
        }
        return (Queue<?>) queueInstance;
    }

    /**
     * @return An instance of {@link AbstractWebSocketEndPoint.Flusher} or <code>null</code> if not found.
     *          It would be great to cast the result to a specific Flusher type but it can't be done
     *          because Flusher is a private sub-class of AbstractWebSocketEndPoint and can't be referred.
     */
    private static Object extractFlusher(ServerSessionImpl sessionImpl)
        throws NoSuchFieldException, IllegalAccessException
    {
        // Extracting "private AbstractServerTransport.Scheduler _scheduler" from ServerSessionImpl instance,
        // expecting to find an instance of AbstractWebSocketEndPoint.WebSocketScheduler.
        Object schedulerInstance = getPrivateFieldByName(sessionImpl, "_scheduler");
        if (schedulerInstance != null) {
            // schedulerInstance now holds an instance of AbstractWebSocketEndPoint.WebSocketScheduler.

            // WebSocketScheduler is a private non-static inner class of AbstractWebSocketEndPoint.
            // Extracting an outer class (this$0) of WebSocketScheduler instance,
            // expecting to find an instance of AbstractWebSocketEndPoint.
            Object delegateInstance = getPrivateFieldByName(schedulerInstance, "this$0");
            if (delegateInstance != null) {
                // delegateInstance now holds an instance of WebSocketTransportExtension.EndPointWithQueueCheck
                // (which is a QD-specific customized extension of AbstractWebSocketEndPoint).

                // Extracting "private final Flusher flusher" from AbstractWebSocketEndPoint instance,
                // expecting to find an instance of AbstractWebSocketEndPoint.Flusher.
                Object flusherInstance = getPrivateFieldByName(delegateInstance, "flusher",
                    AbstractWebSocketEndPoint.class);
                if (flusherInstance != null) {
                    // flusherInstance now holds an instance of AbstractWebSocketEndPoint.Flusher.

                    // It would be great to cast the result to a specific type but it can't be done
                    // because Flusher is a private sub-class of AbstractWebSocketEndPoint
                    // so we have to return it as a generic Object.
                    return flusherInstance;
                }
            }
        } else {
            return null;
        }
        throw new IllegalAccessException("No reflection exceptions " +
            "but field not found in decomposition of ServerSessionImpl class.");
    }

    private static Object getPrivateFieldByName(Object instance, String fieldName)
        throws NoSuchFieldException, IllegalAccessException
    {
        return getPrivateFieldByName(instance, fieldName, instance.getClass());
    }

    private static Object getPrivateFieldByName(Object instance, String fieldName, Class<?> instanceClass)
        throws NoSuchFieldException, IllegalAccessException
    {
        Field field = instanceClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return getPrivateFieldByField(instance, field);
    }

    private static Object getPrivateFieldByField(Object instance, Field field) throws IllegalAccessException {
        return field.get(instance);
    }

    private static void reportReflectionIssue(String details, ReflectiveOperationException exception) {
        // Reporting CometD binary incompatibility only once per application run to prevent logs bloating
        if (reflectionIssueHappened)
            return;

        log.error("Incompatibility in CometD runtime version detected:");
        log.error(details, exception);
        log.error("The application will continue running but may fail to detect and mitigate " +
            "some resource leak conditions.");
        log.error("This should normally never happen, please report the incident to development team.");
        reflectionIssueHappened = true;
    }
}