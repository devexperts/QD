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

import com.devexperts.logging.Logging;
import com.dxfeed.scheme.EmbeddedTypes;
import com.dxfeed.scheme.SchemeException;
import com.dxfeed.scheme.impl.ImportProcessor;
import com.dxfeed.scheme.impl.SchemeModelReader;
import com.dxfeed.scheme.model.NamedEntity;
import com.dxfeed.scheme.model.SchemeEnum;
import com.dxfeed.scheme.model.SchemeImport;
import com.dxfeed.scheme.model.SchemeModel;
import com.dxfeed.scheme.model.SchemeRecord;
import com.dxfeed.scheme.model.SchemeRecordGenerator;
import com.dxfeed.scheme.model.SchemeType;
import com.dxfeed.scheme.model.VisibilityRule;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;

public class XmlSchemeModelReader extends XmlSchemeModelFormat implements SchemeModelReader {

    private static final Logging log = Logging.getLogging(XmlSchemeModelReader.class);

    @Override
    public void readModel(SchemeModel model, String parent, String name, InputStream in,
        ImportProcessor importProcessor) throws IOException, SchemeException
    {
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            XMLErrorHandler errorHandler = new XMLErrorHandler();
            db.setErrorHandler(errorHandler);
            Element xml = db.parse(in).getDocumentElement();

            // Check errors
            for (SAXException ex : errorHandler.getExceptions()) {
                log.error("Cannot parse XML scheme model \"" + name + "\": " + ex.getMessage());
            }
            if (!errorHandler.getExceptions().isEmpty()) {
                throw new SchemeException("Cannot parse XML Scheme: XML Errors in \"" + name + "\"");
            }

            // XML must be validated already, we don't have imports deep in tree in our schema
            NodeList nl = xml.getElementsByTagNameNS(SCHEMA_NS, EL_IMPORT);
            if (nl != null && nl.getLength() > 0) {
                loadImports(model, name, nl, importProcessor);
            }

            processChildCollection(xml, EL_COLLECTION_TYPES, (a) -> loadType(model, name, a));
            processChildCollection(xml, EL_COLLECTION_ENUMS, (a) -> loadEnum(model, name, a));
            processChildCollection(xml, EL_COLLECTION_RECORDS, (a) -> loadRecordOrGenerator(model, name, a));
            processChildCollection(xml, EL_COLLECTION_MAPPINGS, (a) -> loadMappings(model, name, a));
            processChildCollection(xml, EL_COLLECTION_VISIBILITY, (a) -> loadVisibility(model, name, a));
        } catch (SAXException | ParserConfigurationException e) {
            throw new SchemeException("Cannot read XML scheme model \"" + name + "\": " + e.getMessage());
        }
    }

    private void loadImports(SchemeModel model, String currentName, NodeList ximports,
        ImportProcessor importProcessor)
        throws IOException, SchemeException
    {
        for (int i = 0; i < ximports.getLength(); i++) {
            SchemeImport imp = new SchemeImport(ximports.item(i).getTextContent(), currentName);
            model.addImport(imp);
            importProcessor.processImport(currentName, imp);
        }
    }

    private void loadType(SchemeModel model, String currentName, Element tpy) throws SchemeException {
        String name = tpy.getAttribute(ATT_NAME);
        String base = tpy.getAttribute(ATT_TYPE_BASE);
        model.addType(new SchemeType(name, getCreationMode(tpy), base, getDoc(tpy), currentName));
    }

    private void loadEnum(SchemeModel model, String currentName, Element enm) throws SchemeException {
        String name = enm.getAttribute(ATT_NAME);
        SchemeEnum e = new SchemeEnum(name, getCreationMode(enm), getDoc(enm), currentName);
        // Parse enum here
        // XML must be validated already, we don't have value deep in tree in our schema
        NodeList vals = enm.getElementsByTagNameNS(SCHEMA_NS, EL_ENUM_VALUE);
        for (int j = 0; vals != null && j < vals.getLength(); j++) {
            Element val = (Element) vals.item(j);
            String valName = val.getAttribute(ATT_NAME);
            String valOrdStr = val.getAttribute(ATT_EVALUE_ORD);
            int ord = "".equals(valOrdStr) ? -1 : Integer.parseInt(valOrdStr);
            e.addValue(valName, getCreationMode(val), ord, getDoc(val));
        }
        model.addEnum(e);
    }

    private void loadRecordOrGenerator(SchemeModel model, String currentName, Element rcrd)
        throws SchemeException
    {
        switch (rcrd.getLocalName()) {
            case EL_RECORD:
                loadRecord(model, currentName, rcrd);
                break;
            case EL_GENERATOR:
                loadGenerator(model, currentName, rcrd);
                break;
            default:
                throw new IllegalArgumentException("Unknown record descriptor \"" + rcrd.getLocalName() + "\"");
        }
    }

    private void loadRecord(SchemeModel model, String currentName, Element rcrd)
        throws SchemeException
    {
        model.addRecord(parseRecord(model, currentName, rcrd, null));
    }

    private SchemeRecord parseRecord(SchemeModel model, String currentName, Element rcrd, String parent)
        throws SchemeException
    {
        String name = rcrd.getAttribute(ATT_NAME);
        String base = rcrd.getAttribute(ATT_REC_COPY_FROM);
        SchemeRecord r;

        if (base != null && !base.isEmpty()) {
            // Try to start this record as clone of existing
            SchemeRecord br = model.getRecords().get(base);
            if (br == null) {
                throw new SchemeException("Unknown base record \"" + base + "\" for record \"" + name + "\"",
                    model.getSources());
            }
            r = br.copyFrom(parent, name, getCreationMode(rcrd), currentName);
        } else {
            r = new SchemeRecord(name, getCreationMode(rcrd), parent, getBooleanAttr(rcrd, ATT_REC_REGIONALS),
                getDoc(rcrd), currentName);
        }
        // Check "Disabled"
        Boolean disabled = getBooleanAttr(rcrd, ATT_DISABLED);
        if (disabled != null) {
            r.setDisabled(disabled);
        }

        // eventName?
        String eventName = rcrd.getAttribute(ATT_REC_EVENT_NAME);
        if (eventName != null && !eventName.isEmpty()) {
            r.setEventName(eventName);
        }

        // Index?
        // XML must be validated already, we don't have index deep in tree in our schema
        NodeList idx = rcrd.getElementsByTagNameNS(SCHEMA_NS, EL_REC_INDEX);
        if (idx != null && idx.getLength() > 0) {
            Element e = (Element) idx.item(0);
            r.setIndex(getStringAttr(e, ATT_REC_INDEX_0), getStringAttr(e, ATT_REC_INDEX_1));
        }

        // Fields
        processChildNodes(rcrd, EL_REC_FIELD, (f) -> addRecordField(model.getEmbeddedTypes(), r, f));

        return r;
    }

    private void addRecordField(EmbeddedTypes embeddedTypes, SchemeRecord r, Element fld)
        throws SchemeException
    {
        String name = fld.getAttribute(ATT_NAME);
        String type = getStringAttr(fld, ATT_REC_FLD_TYPE);
        boolean hasBitfields = embeddedTypes.canHaveBitfields(type);
        Boolean disabled = getBooleanAttr(fld, ATT_DISABLED);
        Boolean compositeOnly = getBooleanAttr(fld, ATT_REC_FLD_COMPOSITE_ONLY);

        SchemeRecord.Field f = r.addField(name, getCreationMode(fld), disabled, type, hasBitfields, compositeOnly,
            getDoc(fld));

        // eventName?
        String eventName = fld.getAttribute(ATT_REC_FLD_EVENT_NAME);
        if (eventName != null && !eventName.isEmpty()) {
            f.setEventName(eventName);
        }

        // Add aliases
        processChildNodes(fld, EL_REC_FLD_ALIAS, (a) ->
            f.addAlias(a.getAttribute(ATT_NAME),
                Boolean.parseBoolean(a.getAttribute(ATT_ALIAS_MAIN)),
                SchemeRecord.Field.AliasOrTagMode.valueOf(a.getAttribute(ATT_MODE).toUpperCase())
            )
        );

        // Add tags
        processChildNodes(fld, EL_REC_FLD_TAG, (a) ->
            f.addTag(a.getAttribute(ATT_NAME),
                SchemeRecord.Field.AliasOrTagMode.valueOf(a.getAttribute(ATT_MODE).toUpperCase())
            )
        );

        // And bitfields, if we have one
        if (hasBitfields) {
            processChildCollection(fld, EL_REC_FLD_BITFIELDS, (bf) -> addRecordFieldBitField(f, bf));
        }
    }

    private void addRecordFieldBitField(SchemeRecord.Field f, Element bf)
        throws SchemeException
    {
        String name = bf.getAttribute(ATT_NAME);
        Integer offset = bf.hasAttribute(ATT_BITFLD_OFFSET) ?
            Integer.parseInt(bf.getAttribute(ATT_BITFLD_OFFSET), 10) :
            null;
        Integer size = bf.hasAttribute(ATT_BITFLD_SIZE) ?
            Integer.parseInt(bf.getAttribute(ATT_BITFLD_SIZE), 10) :
            null;
        f.addBitfield(name, getCreationMode(bf), offset, size, getDoc(bf), f.getLastFile());
    }

    private void loadGenerator( SchemeModel model, String currentName, Element gen)
        throws SchemeException
    {
        String name = gen.getAttribute(ATT_NAME);

        SchemeRecordGenerator g = new SchemeRecordGenerator(name, getCreationMode(gen), getDoc(gen), currentName);
        g.setType(SchemeRecordGenerator.Type.valueOf(gen.getAttribute(ATT_GEN_TYPE).toUpperCase()));
        g.setDelimiter(gen.getAttribute(ATT_GEN_DELIMITER));

        // Get iterator
        // XML must be validated already, we don't have iterator deep in tree in our schema
        Element iter = (Element) gen.getElementsByTagNameNS(SCHEMA_NS, EL_GEN_ITERATOR).item(0);
        if (iter != null) {
            g.setIteratorMode(
                SchemeRecordGenerator.IteratorMode.valueOf(iter.getAttribute(ATT_ITER_MODE).toUpperCase()));
            processChildNodes(iter, EL_ITER_VALUE, v -> g.addIteratorValue(v.getTextContent()));
        }

        // Get all records (templates)
        processChildNodes(gen, EL_RECORD, r -> g.addTemplate(parseRecord(model, currentName, r, name)));

        // Add this generator to model
        model.addGenerator(g);
    }

    private void loadMappings(SchemeModel model, String currentName, Element map) {
        // Do nothing now
    }

    private void loadVisibility(SchemeModel model, String currentName, Element r) throws SchemeException {
        VisibilityRule rule;
        switch (r.getLocalName()) {
            case EL_VIS_ENABLE:
                rule = new VisibilityRule(r.getAttribute(ATT_VIS_RECORD),
                    getBooleanAttr(r, ATT_VIS_USE_EVENT_NAME, false),
                    r.getAttribute(ATT_VIS_FIELD),
                    true,
                    currentName
                );
                break;
            case EL_VIS_DISABLE:
                rule = new VisibilityRule(r.getAttribute(ATT_VIS_RECORD),
                    getBooleanAttr(r, ATT_VIS_USE_EVENT_NAME, false),
                    r.getAttribute(ATT_VIS_FIELD),
                    false,
                    currentName
                );
                break;
            default:
                throw new IllegalArgumentException("Unknown visibility rule \"" + r.getLocalName() + "\"");
        }
        // Add include/exclude tags
        processChildCollection(r, EL_VIS_TAGINC, (a) -> rule.addIncludedTag(a.getTextContent()));
        processChildCollection(r, EL_VIS_TAGEXC, (a) -> rule.addExcludedTag(a.getTextContent()));

        model.addVisibilityRule(rule);
    }

    private void processChildCollection(Element xml, String collectionName, XMLConsumer<Element> loader) throws
        SchemeException
    {
        Element coll = findOnlyChildElement(xml, collectionName);
        if (coll == null) {
            return;
        }
        NodeList nl = coll.getChildNodes();
        if (nl == null || nl.getLength() == 0) {
            return;
        }
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE || !SCHEMA_NS.equals(n.getNamespaceURI())) {
                continue;
            }
            loader.accept((Element) n);
        }
    }

    private void processChildNodes(Element xml, String childName, XMLConsumer<Element> loader)
        throws SchemeException
    {
        NodeList nl = xml.getChildNodes();
        if (nl == null || nl.getLength() == 0) {
            return;
        }
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE ||
                !SCHEMA_NS.equals(n.getNamespaceURI()) ||
                !childName.equals(n.getNodeName()))
            {
                continue;
            }
            loader.accept((Element) n);
        }
    }

    private NamedEntity.Mode getCreationMode(Element e) {
        return NamedEntity.Mode.valueOf(e.getAttribute(ATT_MODE).toUpperCase());
    }

    private String getDoc(Element e) {
        Element doc = findOnlyChildElement(e, "doc");
        return doc == null ? null : doc.getTextContent();
    }

    private Boolean getBooleanAttr(Element e, String name) {
        if (!e.hasAttribute(name)) {
            return null;
        }
        return Boolean.valueOf(e.getAttribute(name));
    }

    private boolean getBooleanAttr(Element e, String name, boolean def) {
        if (!e.hasAttribute(name)) {
            return def;
        }
        return Boolean.parseBoolean(e.getAttribute(name));
    }

    private String getStringAttr(Element e, String name) {
        if (!e.hasAttribute(name)) {
            return null;
        }
        return e.getAttribute(name);
    }

    private Element findOnlyChildElement(Element e, String name) {
        NodeList nl = e.getChildNodes();
        if (nl == null) {
            return null;
        }
        // Find "doc"
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE ||
                !n.getNamespaceURI().equals(SCHEMA_NS) ||
                !n.getNodeName().equals(name))
            {
                continue;
            }
            return (Element) n;
        }
        return null;
    }

    private static class XMLErrorHandler implements ErrorHandler {
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

    @FunctionalInterface
    private interface XMLConsumer<T> {
        void accept(T v) throws SchemeException;
    }
}
