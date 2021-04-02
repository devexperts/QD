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
package com.dxfeed.webservice.rest;

import com.devexperts.logging.Logging;
import com.dxfeed.webservice.DXFeedContext;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class EventsServlet extends HttpServlet {
    private static final Logging log = Logging.getLogging(EventsServlet.class);
    
    Thread sseConnectionChecker;

    @Override
    public void init() throws ServletException {
        DXFeedContext.INSTANCE.acquire();
        sseConnectionChecker = new Thread("SSEConnectionChecker") {
            @Override
            public void run() {
                try {
                    while (!interrupted()) {
                        sleep(SSEConnection.HEARTBEAT_PERIOD);
                        SSEConnection.checkAndHeartbeatAll();
                    }
                } catch (InterruptedException e) {
                    // destroy
                }
            }
        };
        sseConnectionChecker.start();
    }

    @Override
    public void destroy() {
        sseConnectionChecker.interrupt();
        DXFeedContext.INSTANCE.release();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        process(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        process(req, resp);
    }

    /**
     * Validates access to REST Web functionality for the given {@code pathInfo}.
     * @param pathInfo path for which to check security
     * @param request HTTP Servlet request, additional security information can be added to request attributes
     * @param response HTTP Servlet response
     * @throws HttpErrorException if security is not valid
     * @deprecated For internal use only, do not use!
     */
    @Deprecated
    protected void validateSecurity(PathInfo pathInfo, HttpServletRequest request, HttpServletResponse response)
        throws HttpErrorException
    {
    }

    @SuppressWarnings("deprecation")
    private void process(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // parse URI
        String path = req.getPathInfo();
        Format format = Format.getByExtension(path);
        if (format != null)
            path = format.trimExtension(path);
        else
            format = Format.getByRequestMediaType(req.getHeader("Accept"));
        // convert root request to help
        if (path != null && path.equals("/"))
            path = EventsResource.HELP_PATH;
        // find resource
        PathInfo pathInfo = EventsResource.PATHS.get(path);
        if (pathInfo == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // create resource, configure and invoke
        try {
            validateSecurity(pathInfo, req, resp);
            pathInfo.invokeFor(new EventsResource(req, resp, format));
        } catch (HttpErrorException e) {
            handleHttpException(e, resp);
        } catch (InvocationTargetException e) {
            // handle errors
            Throwable cause = e.getCause();
            if (cause instanceof HttpErrorException) {
                handleHttpException((HttpErrorException) cause, resp);
                return;
            }
            log.error("Error processing the resource: " + req +
                ", remoteAddr " + req.getRemoteAddr(), cause != null ? cause : e);
            if (cause instanceof ServletException)
                throw (ServletException) cause;
            if (cause instanceof IOException)
                throw (IOException) cause;
            throw new ServletException(cause);
        }
    }

    private void handleHttpException(HttpErrorException e, HttpServletResponse response) throws IOException {
        log.warn("HttpException: " + e.getMessage());
        if (response.isCommitted())
            return;

        for (Map.Entry<String, String> header : e.getHeaders().entrySet()) {
            response.setHeader(header.getKey(), header.getValue());
        }
        response.sendError(e.getStatusCode(), e.getMessage());
    }
}
