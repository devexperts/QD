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

import com.dxfeed.scheme.impl.SchemeModelWriter;
import com.dxfeed.scheme.model.NamedEntity;
import com.dxfeed.scheme.model.SchemeEntity;
import com.dxfeed.scheme.model.SchemeEnum;
import com.dxfeed.scheme.model.SchemeModel;
import com.dxfeed.scheme.model.SchemeRecord;
import com.dxfeed.scheme.model.SchemeRecordGenerator;
import com.dxfeed.scheme.model.SchemeType;
import com.dxfeed.scheme.model.VisibilityRule;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class XmlSchemeModelWriter extends XmlSchemeModelFormat implements SchemeModelWriter {
    @Override
    public void writeModel(OutputStream out, SchemeModel model) throws IOException {
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            XMLErrorHandler errorHandler = new XMLErrorHandler();
            db.setErrorHandler(errorHandler);

            Document doc = db.newDocument();

            Element root = doc.createElementNS(SCHEMA_NS, EL_ROOT);
            root.setAttributeNS(W3C_XSI, "xsi:schemaLocation", SCHEMA_NS + " " + SCHEMA_NAME);
            doc.appendChild(root);

            // Add comment about sources of this document
            root.appendChild(doc.createTextNode("\n"));
            root.appendChild(doc.createComment(formatHeader(model)));
            root.appendChild(doc.createTextNode("\n\n"));

            serializeCollection(root, EL_COLLECTION_TYPES, model.getTypes().values(), this::typeSerializer);
            serializeCollection(root, EL_COLLECTION_ENUMS, model.getEnums().values(), this::enumSerializer);
            // Records are more complex, as has two types
            serializeRecordsAndGenerators(root, model);
            serializeCollection(root, EL_COLLECTION_VISIBILITY, model.getVisibilityRules(),
                this::visibilityRuleSerializer);

            serializeDocument(out, doc);
        } catch (ParserConfigurationException | TransformerException e) {
            throw new IOException("Cannot write XML Scheme: " + e.getMessage(), e);
        }
    }

    private <T> void serializeCollection(Element root, String collectionName, Collection<T> collection,
        ElementSerializer<T> serializer)
    {
        Element ce = root.getOwnerDocument().createElementNS(SCHEMA_NS, collectionName);
        root.appendChild(ce);
        if (collection.isEmpty()) {
            ce.appendChild(root.getOwnerDocument().createComment("No elements of this type have been defined"));
        } else {
            for (T e : collection) {
                ce.appendChild(serializer.serialize(root.getOwnerDocument(), e));
            }
        }
    }

    private Element typeSerializer(Document doc, SchemeType data) {
        Element type = doc.createElementNS(SCHEMA_NS, EL_TYPE);
        type.setAttribute(ATT_NAME, data.getName());
        type.setAttribute(ATT_TYPE_BASE, data.getBase());
        addSources(type, null, data);
        if (!data.getBase().equals(data.getResolvedType())) {
            type.appendChild(doc.createComment("Resolved to " + data.getResolvedType()));
        }
        addDoc(type, data);
        return type;
    }

    private Element enumSerializer(Document doc, SchemeEnum data) {
        Element enm = doc.createElementNS(SCHEMA_NS, EL_ENUM);
        enm.setAttribute(ATT_NAME, data.getName());
        addSources(enm, null, data);
        addDoc(enm, data);

        // Add values
        int expectedOrd = 0;
        for (SchemeEnum.Value v : data.getValuesByOrd()) {
            // Serialize values
            Element val = doc.createElementNS(SCHEMA_NS, EL_ENUM_VALUE);
            val.setAttribute(ATT_NAME, v.getName());
            if (expectedOrd != v.getOrd()) {
                val.setAttribute(ATT_EVALUE_ORD, "" + v.getOrd());
            }
            expectedOrd = v.getOrd() + 1;
            addSources(val, data, v);
            addDoc(val, v);

            enm.appendChild(val);
        }
        return enm;
    }

    private void serializeRecordsAndGenerators(Element root, SchemeModel file) {
        Element ce = root.getOwnerDocument().createElementNS(SCHEMA_NS, EL_COLLECTION_RECORDS);
        if (file.getRecords().isEmpty() && file.getGenerators().isEmpty()) {
            ce.appendChild(root.getOwnerDocument().createComment("No elements of this type have been defined"));
            return;
        }
        // Serialize records
        for (SchemeRecord r : file.getRecords().values()) {
            if (r.isTemplate()) {
                continue;
            }
            serializeRecord(ce, r);
        }

        // Serialize Generators
        for (SchemeRecordGenerator g : file.getGenerators().values()) {
            serializeGenerator(ce, g);
        }

        root.appendChild(ce);
    }

    private void serializeRecord(Element root, SchemeRecord r) {
        Element rec = root.getOwnerDocument().createElementNS(SCHEMA_NS, EL_RECORD);
        rec.setAttribute(ATT_NAME, r.getName());
        rec.setAttribute(ATT_DISABLED, "" + r.isDisabled());
        rec.setAttribute(ATT_REC_REGIONALS, "" + r.hasRegionals());
        if (!r.getEventName().equals(r.getName())) {
            rec.setAttribute(ATT_REC_EVENT_NAME, r.getEventName());
        }
        addSources(rec, null, r);
        addDoc(rec, r);
        if (r.hasBase()) {
            rec.appendChild(rec.getOwnerDocument().createComment("Record was based on \"" + r.getBase() + "\""));
        }

        // Add index
        if (r.hasEventFlags()) {
            Element index = rec.getOwnerDocument().createElementNS(SCHEMA_NS, EL_REC_INDEX);
            if (r.getIndex1() != null) {
                index.setAttribute(ATT_REC_INDEX_0, r.getIndex1());
            }
            if (r.getIndex2() != null) {
                index.setAttribute(ATT_REC_INDEX_1, r.getIndex2());
            }
            rec.appendChild(index);
        }

        // Add fields
        for (SchemeRecord.Field f : r.getFields()) {
            serializeRecordField(rec, r, f);
        }

        root.appendChild(rec);
    }

    private void serializeRecordField(Element rec, SchemeRecord r, SchemeRecord.Field f) {
        Element fld = rec.getOwnerDocument().createElementNS(SCHEMA_NS, EL_REC_FIELD);
        fld.setAttribute(ATT_NAME, f.getName());
        fld.setAttribute(ATT_REC_FLD_TYPE, f.getType());
        fld.setAttribute(ATT_DISABLED, "" + f.isDisabled());
        fld.setAttribute(ATT_REC_FLD_COMPOSITE_ONLY, "" + f.isCompositeOnly());
        if (!f.getEventName().equals(r.getEventName())) {
            fld.setAttribute(ATT_REC_FLD_EVENT_NAME, f.getEventName());
        }
        addSources(fld, r, f);
        addDoc(fld, f);

        // Add aliases
        for (SchemeRecord.Field.Alias a : f.getAliases()) {
            Element alias = fld.getOwnerDocument().createElementNS(SCHEMA_NS, EL_REC_FLD_ALIAS);
            alias.setAttribute(ATT_NAME, a.getValue());
            alias.setAttribute(ATT_ALIAS_MAIN, "" + a.isMain());
            fld.appendChild(alias);
        }

        // Add tags
        for (String t : f.getTags()) {
            Element tag = fld.getOwnerDocument().createElementNS(SCHEMA_NS, EL_REC_FLD_TAG);
            tag.setAttribute(ATT_NAME, t);
            fld.appendChild(tag);
        }

        // Add bitfields
        if (f.hasBitfields()) {
            serializeCollection(fld, EL_REC_FLD_BITFIELDS, f.getBitfields(),
                (doc, data) -> bitfieldSerializer(doc, f, data)
            );
        }

        rec.appendChild(fld);
    }

    private Element bitfieldSerializer(Document doc, SchemeRecord.Field f, SchemeRecord.Field.Bitfield data) {
        Element bf = doc.createElementNS(SCHEMA_NS, EL_REC_FLD_BITFIELD);
        bf.setAttribute(ATT_NAME, data.getName());
        bf.setAttribute(ATT_BITFLD_OFFSET, "" + data.getOffset());
        bf.setAttribute(ATT_BITFLD_SIZE, "" + data.getSize());
        addSources(bf, f, data);
        addDoc(bf, data);

        return bf;
    }

    private void serializeGenerator(Element root, SchemeRecordGenerator g) {
        Element gen = root.getOwnerDocument().createElementNS(SCHEMA_NS, EL_GENERATOR);
        gen.setAttribute(ATT_NAME, g.getName());
        gen.setAttribute(ATT_GEN_TYPE, g.getType().name().toLowerCase());
        if (!"".equals(g.getDelimiter())) {
            gen.setAttribute(ATT_GEN_DELIMITER, g.getDelimiter());
        }
        addSources(gen, null, g);
        addDoc(gen, g);

        Element iter = gen.getOwnerDocument().createElementNS(SCHEMA_NS, EL_GEN_ITERATOR);
        iter.setAttribute(ATT_ITER_MODE, SchemeRecordGenerator.IteratorMode.APPEND.name().toLowerCase());
        for (String s : g.getIterator()) {
            Element val = iter.getOwnerDocument().createElementNS(SCHEMA_NS, EL_ITER_VALUE);
            if (!"".equals(s)) {
                val.appendChild(val.getOwnerDocument().createTextNode(s));
            }
            iter.appendChild(val);
        }
        gen.appendChild(iter);

        // Format all records
        for (SchemeRecord r : g.getTemplates()) {
            serializeRecord(gen, r);
        }

        root.appendChild(gen);
    }

    private Element visibilityRuleSerializer(Document doc, VisibilityRule data) {
        Element vr;
        if (data.isEnable()) {
            vr = doc.createElementNS(SCHEMA_NS, EL_VIS_ENABLE);
        } else {
            vr = doc.createElementNS(SCHEMA_NS, EL_VIS_DISABLE);
        }
        vr.setAttribute(ATT_VIS_RECORD, data.getRecord().pattern());
        if (data.getType() == VisibilityRule.Type.FIELD) {
            vr.setAttribute(ATT_VIS_FIELD, data.getField().pattern());
        }
        if (data.useEventName()) {
            vr.setAttribute(ATT_VIS_USE_EVENT_NAME, "true");
        }
        addSources(vr, null, data);

        // Add tags
        visibilityRuleTagsSerializer(doc, vr, EL_VIS_TAGINC, data.getIncludedTags());
        visibilityRuleTagsSerializer(doc, vr, EL_VIS_TAGEXC, data.getExcludedTags());

        return vr;
    }

    private void visibilityRuleTagsSerializer(Document doc, Element vr, String name, Set<String> tags) {
        if (tags.isEmpty()) {
            return;
        }
        Element inc = doc.createElementNS(SCHEMA_NS, name);
        for (String t : tags) {
            Element tag = doc.createElementNS(SCHEMA_NS, EL_VIS_TAG);
            tag.setTextContent(t);
            inc.appendChild(tag);
        }
        vr.appendChild(inc);
    }

    private <T extends NamedEntity<T>> void addDoc(Element root, T data) {
        if (data.getDoc() != null) {
            Element doc = root.getOwnerDocument().createElementNS(SCHEMA_NS, EL_DOC);
            doc.appendChild(root.getOwnerDocument().createTextNode(data.getDoc()));
            root.appendChild(doc);
        }
    }

    private <P extends SchemeEntity, T extends SchemeEntity> void addSources(Element root, P parent, T data) {
        List<String> sources = data.getFilesList();
        // Skip if same as parent
        if (parent != null && parent.getFilesList().equals(sources)) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        if (sources.size() == 1) {
            sb.append("Defined in: ");
            sb.append(sources.get(0));
        } else {
            sb.append("\nDefined in:\n");
            for (String s : sources) {
                sb.append(s).append("\n");
            }
        }
        root.appendChild(root.getOwnerDocument().createComment(sb.toString()));
    }

    private String formatHeader(SchemeModel file) {
        StringBuilder sb = new StringBuilder();
        List<String> sources = file.getSources();

        // Filter out all technical names
        for (int i = 0; i < sources.size();) {
            if (sources.get(i).startsWith("<")) {
                sources.remove(i);
            } else {
                i++;
            }
        }

        if (sources.size() == 1) {
            sb.append("\nThis file was automatically created from scheme loaded from file\n");
        } else {
            sb.append("\nThis file was automatically created from scheme loaded from files\n");
        }
        for (String f : sources) {
            sb.append(f).append("\n");
        }
        return sb.toString();
    }

    private void serializeDocument(OutputStream out, Document doc) throws TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        StreamResult result = new StreamResult(out);
        DOMSource source = new DOMSource(doc);
        transformer.transform(source, result);
    }

    @FunctionalInterface
    private interface ElementSerializer<T> {
        public Element serialize(Document doc, T data);
    }
}
