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
package com.devexperts.qd.hub;

import com.devexperts.qd.config.ConfigProvider;
import com.devexperts.qd.hub.config.ChannelConfig;
import com.devexperts.qd.hub.config.DownlinkConfig;
import com.devexperts.qd.hub.config.HubConfig;
import com.devexperts.qd.hub.config.SpaceConfig;
import com.devexperts.qd.hub.config.UniverseConfig;
import com.devexperts.qd.hub.config.UplinkConfig;
import com.devexperts.qd.tools.Tools;
import com.devexperts.qd.tools.launcher.Launcher;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import org.junit.Test;

import java.net.URL;
import java.util.Locale;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HubConfigTest {

    URL configURL = HubConfig.class.getResource("/hub-config/hub.conf");

    @Test
    public void testReferenceLauncherConfigValidated() throws Exception {
        // Make sure that reference hub config is parsable and accepted by Launcher
        // Do not try real start here - full config might be highly environment-dependent
        assertTrue(Tools.invoke(Launcher.class.getSimpleName(), "--check", configURL.toString()));
    }

    @Test
    public void testReferenceLauncherConfig() {
        // Check the reference hub-config parsing
        ConfigParseOptions configParseOptions = ConfigParseOptions.defaults().setAllowMissing(false);
        Config config = ConfigFactory.parseURL(configURL, configParseOptions).resolve();

        // Parse config before creating tool to catch errors earlier.
        Config hubConfigNode = config.getConfigList(Launcher.CFG_MODULES_PATH).get(0);
        HubConfig hubConfig = ConfigProvider.getConfigBean(hubConfigNode, HubConfig.class);
        assertEquals("Hub module name", "retail", hubConfig.getName());
        assertEquals("Hub module type", "hub", hubConfig.getType().toLowerCase(Locale.ROOT));
        Object vError = hub().validate(hubConfig);
        assertNull("Validation result", vError);
    }

    // ======== Tests for Hub.validate

    private static Hub hub() {
        return new Hub(new TestModuleContext("test-hub", new Properties()));
    }

    @Test
    public void testValidateOk() {
        HubConfig cfg = new HubConfig()
            .withName("h1")
            .withUniverseConfig(new UniverseConfig()
                .withSpace(new SpaceConfig("A"))
                .withSpace(new SpaceConfig("B"))
                .withProduct("mixAB",
                    new ChannelConfig("A").withFilter("A*"),
                    new ChannelConfig("B").withFilter("B*")))
            .withUplink(new UplinkConfig("ul-A", ":0").withSpace("A"))
            .withDownlink(new DownlinkConfig("dl-AB", ":0").withProduct(new ChannelConfig("mixAB")))
            .withDownlink(new DownlinkConfig("dl-A", ":0").withSpace("A"))
            .withDownlink(new DownlinkConfig("dl-B", ":0").withProduct(new ChannelConfig("B")));

        Object res = hub().validate(cfg);
        assertNull(res);
    }
    
    @Test
    public void testValidateSpaceAndProductNameSyntax() {
        checkNameSyntaxValidation("A-Name_012", true);
        checkNameSyntaxValidation("_A", true);
        checkNameSyntaxValidation("1A", false);
        checkNameSyntaxValidation("A!", false);
        // no spaces allowed
        checkNameSyntaxValidation(" A", false);
        checkNameSyntaxValidation("A ", false);
        checkNameSyntaxValidation("A B", false);
    }

    private static void checkNameSyntaxValidation(String name, boolean expectedValid) {
        HubConfig cfgSpace = new HubConfig()
            .withUniverseConfig(new UniverseConfig().withSpace(new SpaceConfig(name)));
        if (expectedValid) {
            assertNull("expected valid: '" + name + '"', hub().validate(cfgSpace));
        } else {
            expectValidationMessage(hub().validate(cfgSpace), "Invalid space name '" + name + "'");
        }

        HubConfig cfgProduct = new HubConfig()
            .withUniverseConfig(new UniverseConfig()
                .withSpace(new SpaceConfig("A"))
                .withProduct(name, new ChannelConfig("A")));
        if (expectedValid) {
            assertNull("expected valid: '" + name + '"', hub().validate(cfgProduct));
        } else {
            expectValidationMessage(hub().validate(cfgProduct), "Invalid product name '" + name + "'");
        }
    }

    @Test
    public void testValidateUplinkShouldReferenceSingleSpace() {
        // no spaces referenced
        HubConfig cfgNone = new HubConfig()
            .withUniverseConfig(new UniverseConfig().withSpace(new SpaceConfig("A")))
            .withUplink(new UplinkConfig("ul", ":0"));
        expectValidationMessage(hub().validate(cfgNone), "Uplink ul should reference a space");

        // multiple spaces referenced
        HubConfig cfgMulti = new HubConfig()
            .withUniverseConfig(new UniverseConfig().withSpace(new SpaceConfig("A")))
            .withUplink(new UplinkConfig("ul", ":0").withSpace("A").withSpace("A2"));
        expectValidationMessage(hub().validate(cfgMulti), "Uplink ul references multiple spaces");
    }

    @Test
    public void testValidateUplinkUnknownOrInactiveSpace() {
        // unknown space
        HubConfig cfgUnknown = new HubConfig()
            .withUniverseConfig(new UniverseConfig().withSpace(new SpaceConfig("A")))
            .withUplink(new UplinkConfig("ul", ":0").withSpace("X"));
        expectValidationMessage(hub().validate(cfgUnknown), "Uplink ul references unknown space X");

        // inactive space
        HubConfig cfgInactive = new HubConfig()
            .withUniverseConfig(new UniverseConfig().withSpace(new SpaceConfig("A").withActive(false)))
            .withUplink(new UplinkConfig("ul", ":0").withSpace("A"));
        expectValidationMessage(hub().validate(cfgInactive), "Uplink ul references disabled space A");
    }

    @Test
    public void testValidateDownlinkUnknownOrInactiveSpace() {
        // unknown space
        HubConfig cfgUnknown = new HubConfig()
            .withUniverseConfig(new UniverseConfig().withSpace(new SpaceConfig("A")))
            .withDownlink(new DownlinkConfig("dl", ":0").withSpace("X"));
        expectValidationMessage(hub().validate(cfgUnknown), "Downlink dl references unknown space X");

        // inactive space
        HubConfig cfgInactive = new HubConfig()
            .withUniverseConfig(new UniverseConfig().withSpace(new SpaceConfig("A").withActive(false)))
            .withDownlink(new DownlinkConfig("dl", ":0").withSpace("A"));
        expectValidationMessage(hub().validate(cfgInactive), "Downlink dl references disabled space A");
    }

    @Test
    public void testValidateDownlinkSpaceVsProducts() {
        // neither space nor products
        HubConfig cfgNone = new HubConfig()
            .withUniverseConfig(new UniverseConfig().withSpace(new SpaceConfig("A")))
            .withDownlink(new DownlinkConfig("dl", ":0"));
        expectValidationMessage(hub().validate(cfgNone), "configuration shall define either a space or a product list");

        // multiple spaces on downlink
        HubConfig cfgMulti = new HubConfig()
            .withUniverseConfig(new UniverseConfig().withSpace(new SpaceConfig("A")).withSpace(new SpaceConfig("B")))
            .withDownlink(new DownlinkConfig("dl", ":0").withSpace("A").withSpace("B"));
        expectValidationMessage(hub().validate(cfgMulti), "Downlink dl references multiple spaces");

        // both space and products
        HubConfig cfgBoth = new HubConfig()
            .withUniverseConfig(new UniverseConfig().withSpace(new SpaceConfig("A")).withSpace(new SpaceConfig("B")))
            .withDownlink(new DownlinkConfig("dl", ":0").withSpace("A").withProduct(new ChannelConfig("B")));
        expectValidationMessage(hub().validate(cfgBoth), "configuration shall define either a space or a product list");
    }

    @Test
    public void testValidateDownlinkProductsUnknownOrInactiveSpace() {
        // unknown referenced base in product list
        HubConfig cfgUnknown = new HubConfig()
            .withUniverseConfig(new UniverseConfig().withSpace(new SpaceConfig("A")))
            .withUplinks(java.util.Collections.emptyMap())
            .withDownlink(new DownlinkConfig("dl", ":0").withProduct(new ChannelConfig("X")));
        expectValidationMessage(hub().validate(cfgUnknown), "Downlink dl references unknown space X");

        // inactive space referenced in product list
        HubConfig cfgInactive = new HubConfig()
            .withUniverseConfig(new UniverseConfig().withSpace(new SpaceConfig("A").withActive(false)))
            .withDownlink(new DownlinkConfig("dl", ":0").withProduct(new ChannelConfig("A")));
        expectValidationMessage(hub().validate(cfgInactive), "Downlink dl references disabled space A");
    }

    @Test
    public void testValidateProductCycleDetected() {
        // P1 -> P2 -> P1 cycle
        HubConfig cfg = new HubConfig()
            .withUniverseConfig(new UniverseConfig()
                .withSpace(new SpaceConfig("A"))
                .withProduct("P1", new ChannelConfig("P2"))
                .withProduct("P2", new ChannelConfig("P1")))
            .withDownlink(new DownlinkConfig("dl", ":0").withProduct(new ChannelConfig("P1")));
        expectValidationMessage(hub().validate(cfg), "Products definition cycle detected");
    }

    @Test
    public void testValidateProductsReferencesUnknownOrInactiveSpace() {
        HubConfig cfgUnknown = new HubConfig()
            .withUniverseConfig(new UniverseConfig()
                .withProduct("P", new ChannelConfig("UNKNOWN")));
        expectValidationMessage(hub().validate(cfgUnknown), "Unknown product/space UNKNOWN is referenced by product P");

        HubConfig cfgInactive = new HubConfig()
            .withUniverseConfig(new UniverseConfig()
                .withSpace(new SpaceConfig("S").withActive(false))
                .withProduct("P", new ChannelConfig("S")));
        expectValidationMessage(hub().validate(cfgInactive), "Inactive space S is referenced by product P");
    }

    private static void expectValidationMessage(Object validationResult, String expected) {
        assertNotNull("Null validation result", validationResult);
        String msg = String.valueOf(validationResult);
        assertTrue(msg, msg.contains(expected));
    }
}
