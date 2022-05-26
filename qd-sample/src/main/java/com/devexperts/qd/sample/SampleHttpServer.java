/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.sample;

import com.devexperts.qd.QDLog;
import com.devexperts.qd.qtp.http.QDServlet;
import com.devexperts.qd.qtp.http.QDServletConfig;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.UserStore;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;

/**
 * @deprecated HTTP connector is deprecated and will be removed in the future.
 */
@Deprecated
public class SampleHttpServer {

    public static final String CONTEXT_PATH = "/sample";
    public static final String QD_SERVLET_PATH = "/QDServlet";
    public static final String ROLE = "dxRole";

    public static void main(String... args) throws Exception {
        if (args.length == 0) {
            System.err.println("Use: java " + SampleHttpServer.class.getName() + " <port> [<user> [<password>]]");
            return;
        }
        int port = Integer.parseInt(args[0]);

        Log.getLog().info("Starting HTTP server at port " + port + " with QDServlet at " + CONTEXT_PATH + QD_SERVLET_PATH);

        ServletContextHandler context = new ServletContextHandler ();
        context.setContextPath(CONTEXT_PATH);
        ServletHolder servletHolder = context.addServlet(QDServlet.class.getName(), QD_SERVLET_PATH);
        servletHolder.setInitParameter(QDServletConfig.class.getName(), SampleQDServletConfig.class.getName());
        Handler handler = context;

        if (args.length > 1) {
            String user = args[1];
            String password = args.length > 2 ? args[2] : "";

            DefaultIdentityService identityService = new DefaultIdentityService();
            HashLoginService loginService = new HashLoginService();
            loginService.setIdentityService(identityService);
            UserStore userStore = new UserStore();
            userStore.addUser(user, Credential.getCredential(password), new String[]{ROLE});
            loginService.setUserStore(userStore);

            Constraint constraint = new Constraint();
            constraint.setRoles(new String[]{ROLE});
            constraint.setAuthenticate(true);

            ConstraintMapping mapping = new ConstraintMapping();
            mapping.setPathSpec(CONTEXT_PATH + QD_SERVLET_PATH);
            mapping.setConstraint(constraint);

            ConstraintSecurityHandler security = new ConstraintSecurityHandler();
            security.setIdentityService(identityService);
            security.setLoginService(loginService);
            security.addConstraintMapping(mapping);
            security.setRealmName("Sample Realm");
            security.setHandler(context);
            handler = security;

            QDLog.log.info("Authentication enabled");
        }

        SessionHandler session = new SessionHandler();
        session.setHandler(handler);
        handler = session;

        Server server = new Server(port);
        server.setHandler(handler);
        server.start();

        Log.getLog().info("Use http://localhost:" + port + CONTEXT_PATH + QD_SERVLET_PATH);
    }
}
