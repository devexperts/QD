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
package com.dxfeed.scheme.impl.xml;

import com.devexperts.services.Services;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

public class XmlSchemeModelFormat {
    protected static final String SCHEMA_NAME = "dxfeed-schema.xsd";
    protected static final String SCHEMA_NS = "https://www.dxfeed.com/datascheme";

    protected static final String W3C_XSI = "http://www.w3.org/2001/XMLSchema-instance";

    protected static final String JAXP_SCHEMA_LANGUAGE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";
    protected static final String W3C_XML_SCHEMA = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    protected static final String JAXP_SCHEMA_SOURCE = "http://java.sun.com/xml/jaxp/properties/schemaSource";

    /* Root of document */
    protected static final String EL_ROOT = "dxfeed";

    /* Common elements and attributes */
    protected static final String EL_DOC = "doc";
    protected static final String ATT_NAME = "name";
    protected static final String ATT_MODE = "mode";
    protected static final String ATT_DISABLED = "disabled";

    /* Imports support */
    protected static final String EL_IMPORT = "import";

    /* Types support */
    protected static final String EL_COLLECTION_TYPES = "types";
    protected static final String EL_TYPE = "type";
    protected static final String ATT_TYPE_BASE = "base";

    /* Enum support */
    protected static final String EL_COLLECTION_ENUMS = "enums";
    protected static final String EL_ENUM = "enum";
    protected static final String EL_ENUM_VALUE = "value";
    protected static final String ATT_EVALUE_ORD = "ord";

    /* Records and generators support */
    protected static final String EL_COLLECTION_RECORDS = "records";

    protected static final String EL_RECORD = "record";
    protected static final String ATT_REC_COPY_FROM = "copyFrom";
    protected static final String ATT_REC_REGIONALS = "regionals";
    protected static final String ATT_REC_EVENT_NAME = "eventName";
    protected static final String EL_REC_INDEX = "index";
    protected static final String ATT_REC_INDEX_0 = "field0";
    protected static final String ATT_REC_INDEX_1 = "field1";
    protected static final String EL_REC_FIELD = "field";
    protected static final String ATT_REC_FLD_TYPE = "type";
    protected static final String ATT_REC_FLD_COMPOSITE_ONLY = "compositeOnly";
    protected static final String ATT_REC_FLD_EVENT_NAME = "eventName";
    protected static final String EL_REC_FLD_ALIAS = "alias";
    protected static final String ATT_ALIAS_MAIN = "main";
    protected static final String EL_REC_FLD_TAG = "tag";
    protected static final String EL_REC_FLD_BITFIELDS = "bitfields";
    protected static final String EL_REC_FLD_BITFIELD = "field";
    protected static final String ATT_BITFLD_OFFSET = "offset";
    protected static final String ATT_BITFLD_SIZE = "size";

    protected static final String EL_GENERATOR = "generator";
    protected static final String ATT_GEN_TYPE = "type";
    protected static final String ATT_GEN_DELIMITER = "delimiter";
    protected static final String EL_GEN_ITERATOR = "iterator";
    protected static final String ATT_ITER_MODE = "mode";
    protected static final String EL_ITER_VALUE = "value";

    /* Mappings support (not supported now) */
    protected static final String EL_COLLECTION_MAPPINGS = "mappings";

    /* Visibility support */
    protected static final String EL_COLLECTION_VISIBILITY = "visibility";
    protected static final String EL_VIS_ENABLE = "enable";
    protected static final String EL_VIS_DISABLE = "disable";
    protected static final String ATT_VIS_RECORD = "record";
    protected static final String ATT_VIS_USE_EVENT_NAME = "useEventName";
    protected static final String ATT_VIS_FIELD = "field";
    protected static final String EL_VIS_TAGINC = "include-tags";
    protected static final String EL_VIS_TAGEXC = "exclude-tags";
    protected static final String EL_VIS_TAG = "tag";

    protected final DocumentBuilderFactory dbf;

    protected XmlSchemeModelFormat() {
        // Configure scheme
        URL schemeURL = this.getClass().getClassLoader().getResource(SCHEMA_NAME);
        if (schemeURL == null) {
            throw new IllegalStateException("Cannot find XSD scheme, XML load failed");
        }
        DocumentBuilderFactory factory =
            Services.createService(DocumentBuilderFactory.class, getClass().getClassLoader(), null);
        if (factory == null)
            factory = DocumentBuilderFactory.newInstance();
        dbf = factory;
        dbf.setNamespaceAware(true);
        dbf.setValidating(true);
        dbf.setCoalescing(true);
        dbf.setExpandEntityReferences(true);
        dbf.setIgnoringComments(true);
        dbf.setAttribute(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
        dbf.setAttribute(JAXP_SCHEMA_SOURCE, schemeURL.toString());
    }

    protected static class XMLErrorHandler implements ErrorHandler {
        private final List<SAXParseException> exceptions = new ArrayList<>();

        public List<SAXParseException> getExceptions() {
            return exceptions;
        }

        @Override
        public void warning(SAXParseException exception) {
            exceptions.add(exception);
        }

        @Override
        public void error(SAXParseException exception) {
            exceptions.add(exception);
        }

        @Override
        public void fatalError(SAXParseException exception) {
            exceptions.add(exception);
        }
    }
}
