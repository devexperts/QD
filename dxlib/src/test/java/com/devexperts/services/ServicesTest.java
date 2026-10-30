/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.services;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ServicesTest {

    private static final String UNLINKABLE_PLUGIN = "com.devexperts.services.UnlinkablePlugin";
    private static final String SEALED_PLUGIN = "com.devexperts.services.SealedPlugin";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testProviderClassThatFailsToLoadOrConstructIsSkipped() throws IOException {
        File services = temporaryFolder.newFile();
        Files.write(services.toPath(), Arrays.asList(
            "com.devexperts.services.AbsentPlugin",
            UNLINKABLE_PLUGIN,
            SEALED_PLUGIN,
            ThrowingPlugin.class.getName(),
            WorkingPlugin.class.getName()), StandardCharsets.UTF_8);

        List<Plugin> plugins = new ArrayList<>();
        for (Plugin plugin : Services.createServices(Plugin.class, new ServicesLoader(services))) {
            plugins.add(plugin);
        }

        assertEquals(1, plugins.size());
        assertTrue(plugins.get(0) instanceof WorkingPlugin);
    }

    public interface Plugin {
    }

    public static class WorkingPlugin implements Plugin {
    }

    public static class ThrowingPlugin implements Plugin {
        public ThrowingPlugin() {
            throw new IllegalStateException("the constructor fails");
        }
    }

    // lists the provider classes of the file, fails to link one of them, as the JVM does for an absent superclass,
    // and refuses to define another, as URLClassLoader does for a class of a sealed package from another jar
    private static class ServicesLoader extends ClassLoader {
        private final URL services;

        ServicesLoader(File services) throws IOException {
            super(ServicesTest.class.getClassLoader());
            this.services = services.toURI().toURL();
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return name.equals(Services.META_INF_SERVICES + Plugin.class.getName()) ?
                Collections.enumeration(Collections.singletonList(services)) : super.getResources(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(UNLINKABLE_PLUGIN))
                throw new NoClassDefFoundError("com/devexperts/services/AbsentSuperclass");
            if (name.equals(SEALED_PLUGIN))
                throw new SecurityException("sealing violation: package com.devexperts.services is sealed");
            return super.loadClass(name, resolve);
        }
    }
}
