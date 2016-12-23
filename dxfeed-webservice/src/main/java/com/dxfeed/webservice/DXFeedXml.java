/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.webservice;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.*;

import com.dxfeed.impl.XmlNamespace;
import com.dxfeed.webservice.rest.Events;
import com.dxfeed.webservice.rest.SubResponse;
import com.sun.xml.bind.api.JAXBRIContext;
import com.sun.xml.bind.marshaller.NamespacePrefixMapper;

public class DXFeedXml {
    private static final JAXBContext CONTEXT;

    static {
        List<Class<?>> classes = new ArrayList<Class<?>>(DXFeedContext.INSTANCE.getEventTypes().values());
        classes.add(Events.class);
        classes.add(SubResponse.class);
        try {
            // Use JAXB Reference implementation, so that its "namespacePrefixMapper" feature can be used
            CONTEXT = JAXBRIContext.newInstance(classes.toArray(new Class[classes.size()]));
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeTo(Object result, OutputStream out, String indent) throws IOException {
        try {
            marshaller(indent).marshal(result, out);
        } catch (JAXBException e) {
            throw new IOException(e);
        }
    }

    private static Marshaller marshaller(String indent) throws JAXBException {
        Marshaller marshaller = CONTEXT.createMarshaller();
        marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", new NSMapper());
        if (indent != null)
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        return marshaller;
    }

    private static class NSMapper extends NamespacePrefixMapper {
        @Override
        public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
            if (namespaceUri.equals(XmlNamespace.EVENT))
                return "";
            return suggestion;
        }
    }
}
