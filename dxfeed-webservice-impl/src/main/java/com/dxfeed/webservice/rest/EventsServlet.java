/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.webservice.rest;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import javax.servlet.ServletException;
import javax.servlet.http.*;

import com.dxfeed.webservice.DXFeedContext;

public class EventsServlet extends HttpServlet {
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
            pathInfo.invokeFor(new EventsResource(req, resp, format));
        } catch (InvocationTargetException e) {
            // handle errors
            Throwable cause = e.getCause();
            if (cause instanceof HttpErrorException) {
                resp.sendError(((HttpErrorException) cause).getStatusCode());
                return;
            }
            if (cause instanceof ServletException)
                throw (ServletException) cause;
            if (cause instanceof IOException)
                throw (IOException) cause;
            throw new ServletException(cause);
        }
    }
}
