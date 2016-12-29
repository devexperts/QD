/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.tools;

import java.beans.*;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

import com.devexperts.qd.QDFactory;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.help.MessageConnectorProperty;
import com.devexperts.qd.qtp.help.MessageConnectorSummary;
import com.devexperts.qd.spi.QDFilterFactory;
import com.devexperts.services.ServiceProvider;
import com.devexperts.tools.*;

@ServiceProvider
public class QDHelpProvider extends DefaultHelpProvider {
	private static final String CONNECTOR_SUFFIX = "Connector";


	@Override
	public String getMetaTag(String name, String caption, int width) {
		switch (name) {
		case "list-connectors":
			return listConnectors(width) + "\n";
		case "list-specific-filters":
			return listSpecificFilters(width) + "\n";
		case "messageconnector-summary":
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			try (PrintStream printStream = new PrintStream(outputStream, false, StandardCharsets.UTF_8.name())) {
				printMessageConnectorHelpSummary(caption, printStream, width);
				return new String(outputStream.toByteArray(), StandardCharsets.UTF_8) + "\n";
			} catch (Exception e) {
				return "--- Couldn't find connector \"" + caption + "\" ---\n";
			}
		default:
			return super.getMetaTag(name, caption, width);
		}
	}

	private void printMessageConnectorHelpSummary(String caption, PrintStream out, int width) throws NoSuchElementException {
		Class<? extends MessageConnector> connector = MessageConnectors.findMessageConnector(caption + CONNECTOR_SUFFIX, getHelpClassLoader());
		if (connector == null) {
			throw new NoSuchElementException();
		}
		MessageConnectorSummary annotation = connector.getAnnotation(MessageConnectorSummary.class);
		if (annotation == null) {
			Help.printFormat("\t--- No annotation found for connector \"" + getConnectorName(connector) + "\" ---", out, width);
			return;
		}
		Help.printFormat("\t" + getConnectorName(connector) + " - " + annotation.info(), out, width);
		out.append("\n");
		Help.printFormat("Address format: " + annotation.addressFormat(), out, width);
		out.append("\n");
		Map<String, PropertyDescriptor> properties = getAnnotatedConnectorProperties(connector);
		if (properties.isEmpty()) {
			Help.printFormat("This connector has no special properties.", out, width);
		} else try {
			Help.printFormat("Properties:", out, width);
			ArrayList<String[]> table = new ArrayList<>();
			table.add(new String[]{"  ", "[type]", "[name]", "[description]"});
			for (PropertyDescriptor pd : properties.values()) {
				String name = pd.getName();
				String desc = pd.getWriteMethod().getAnnotation(MessageConnectorProperty.class).value();
				String type = pd.getPropertyType().getSimpleName();
				table.add(new String[]{"", type, name, desc});
			}
			out.append(Help.formatTable(table, width, "  "));
		} catch (IllegalArgumentException e) {
			Help.printFormat("\t--- Error occurred while generating properties information ---", out, width);
		}
	}

	private String printSubscriptionFilters(QDFilterFactory filterFactory, int width) {
		ArrayList<String[]> table = new ArrayList<>();
		table.add(new String[]{"[name]", "[description]"});
		if (filterFactory != null) {
			for (Map.Entry<String, String> entry : filterFactory.describeFilters().entrySet()) {
				if (entry.getValue().length() > 0) {
					table.add(new String[]{entry.getKey(), entry.getValue()});
				}
			}
		}
		if (table.size() > 1) {
			return Help.formatTable(table, width, "  ");
		} else {
			return Help.format("--- No project-specific filters found ---", width);
		}
	}

	private ClassLoader getHelpClassLoader() {
		return Help.class.getClassLoader();
	}

	private String getConnectorName(Class<? extends MessageConnector> connector) {
		String name = connector.getSimpleName();
		if (name.endsWith(CONNECTOR_SUFFIX)) {
			name = name.substring(0, name.length() - CONNECTOR_SUFFIX.length());
		}
		return name;
	}

	private Map<String, PropertyDescriptor> getAnnotatedConnectorProperties(Class<? extends MessageConnector> connector) {
		Map<String, PropertyDescriptor> result = new TreeMap<>();
		try {
			BeanInfo bi = Introspector.getBeanInfo(connector);
			for (PropertyDescriptor pd : bi.getPropertyDescriptors()) {
				Method wm = pd.getWriteMethod();
				if (wm != null && wm.getAnnotation(MessageConnectorProperty.class) != null) {
					result.put(pd.getName(), pd);
				}
			}
		} catch (IntrospectionException e) {
			// just ignore a return empty result
		}
		return result;
	}

	private String listConnectors(int width) {
		ArrayList<String[]> table = new ArrayList<>();
		table.add(new String[]{"  ", "[name]", "[address format]", "[description]"});
		for (Class<? extends MessageConnector> connector : MessageConnectors.listMessageConnectors(getHelpClassLoader())) {
			MessageConnectorSummary annotation = connector.getAnnotation(MessageConnectorSummary.class);
			String name = getConnectorName(connector);
			String address = "";
			String description = "";
			if (annotation != null) {
				address = annotation.addressFormat();
				description = annotation.info();
			}
			table.add(new String[]{"", name, address, description});
		}
		return Help.formatTable(table, width, "  ");
	}

	private String listSpecificFilters(int width) {
		return printSubscriptionFilters(QDFactory.getDefaultScheme().getService(QDFilterFactory.class), width);
	}
}
