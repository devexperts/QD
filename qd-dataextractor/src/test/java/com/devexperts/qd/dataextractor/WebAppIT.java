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
package com.devexperts.qd.dataextractor;

import org.junit.Ignore;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.org.bouncycastle.util.io.Streams;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.net.URL;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Ignore(value = "Integration test. To be run manually")
public class WebAppIT {

    @Test
    public void testWebAppOnJetty() throws Exception {
        testWebApp("jetty", "jetty:9.4.57-jre8", "/var/lib/jetty/webapps/ROOT.war");
    }

    @Test
    public void testWebAppOnTomcat() throws Exception {
        testWebApp("tomcat", "tomcat:9.0", "/usr/local/tomcat/webapps/ROOT.war");
    }

    private void testWebApp(String container, String dockerName, String warPath) throws Exception {
        File file = new File("target/test.war");
        assertTrue("WAR file not found: " + file, file.exists());

        try (GenericContainer<?> web = new GenericContainer<>(DockerImageName.parse(dockerName))
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/"))
            .withCopyFileToContainer(MountableFile.forHostPath(file.getAbsolutePath()), warPath))
        {
            web.start();
            String address = "http://" + web.getHost() + ":" + web.getMappedPort(8080) + "/";
            String request = address + "data?records=Quote&symbols=IBM&start=20250808-120000&stop=20250808-120000";

            assertFalse("Log exceptions in " + container, web.getLogs().contains("Exception"));
            assertTrue("Main page in " + container, addressContains(address, "Welcome @ Data Extractor"));
            assertTrue("Simple request in " + container, addressContains(request, "==DXP3\ttype=extract"));
        }
    }

    private static boolean addressContains(String address, String match) throws Exception {
        return new String(Streams.readAll(new URL(address).openStream())).contains(match);
    }
}
