/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.kit;

import com.devexperts.io.ByteArrayOutput;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.spi.DataSchemeService;
import com.devexperts.services.Services;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.devexperts.util.Base64.URLSAFE_UNPADDED;

/**
 * The <code>DefaultScheme</code> is a basic implementation of data scheme.
 * It uses record identifier and <code>CompactInt</code> format for record identity serialization.
 * Note, that this implementation works only with records that are derived from
 * {@link DefaultRecord} class and fields that are derived from
 * {@link AbstractDataIntField} and {@link AbstractDataObjField} classes.
 */
public class DefaultScheme implements DataScheme {

    protected final Logging log = Logging.getLogging(getClass());

    protected final SymbolCodec codec;
    protected final DefaultRecord[] records;

    // :TODO: consider replacing HashMap with ObjectMatrix for speed-up and avoidance of Collections.
    private final HashMap<String, DefaultRecord> records_by_name;
    private final HashMap<String, AbstractDataIntField> int_fields_by_name;
    private final HashMap<String, AbstractDataObjField> obj_fields_by_name;

    private final Map<Class<?>, Object> services = Collections.synchronizedMap(new HashMap<>());

    protected String digest;

    public DefaultScheme(SymbolCodec codec, DataRecord... records) {
        if (codec == null)
            throw new NullPointerException("SymbolCodec is null.");
        if (codec.getClass() != PentaCodec.class) {
            log.warn("WARNING: DEPRECATED use of custom SymbolCodec implementation " + codec.getClass().getName());
        }
        this.codec = codec;

        int n = records == null ? 0 : records.length;
        this.records = new DefaultRecord[n];
        if (n > 0)
            System.arraycopy(records, 0, this.records, 0, n);

        records_by_name = new HashMap<>();
        int_fields_by_name = new HashMap<>();
        obj_fields_by_name = new HashMap<>();
        for (int i = n; --i >= 0;) {
            DefaultRecord record = this.records[i];
            if (record.getId() != i)
                throw new IllegalArgumentException("Record's id does not coincide with record index.");
            if (records_by_name.put(record.getName(), record) != null)
                throw new IllegalArgumentException("Duplicate record name.");
            for (int j = record.getIntFieldCount(); --j >= 0;) {
                AbstractDataIntField field = record.getIntField(j);
                if (int_fields_by_name.put(field.getName(), field) != null)
                    throw new IllegalArgumentException("Duplicate int-field name.");
            }
            for (int j = record.getObjFieldCount(); --j >= 0;) {
                AbstractDataObjField field = record.getObjField(j);
                if (obj_fields_by_name.put(field.getName(), field) != null)
                    throw new IllegalArgumentException("Duplicate obj-field name.");
            }
        }
        setParentReferences();
    }

    @Override
    public final SymbolCodec getCodec() {
        return codec;
    }

    @Override
    public final int getRecordCount() {
        return records.length;
    }

    @Override
    public final DefaultRecord getRecord(int index) {
        return records[index];
    }

    @Override
    public final DefaultRecord findRecordByName(String name) {
        return records_by_name.get(name);
    }

    @Override
    public final AbstractDataIntField findIntFieldByName(String name) {
        return int_fields_by_name.get(name);
    }

    @Override
    public final AbstractDataObjField findObjFieldByName(String name) {
        return obj_fields_by_name.get(name);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getService(Class<T> serviceClass) {
        // check-and-act sequence of steps on synchronized map is not a problem here.
        // in the worst case we end up create the same service multiple times which is not
        // really an issue.
        T result = (T) services.get(serviceClass);
        if (result == null) {
            result = Services.createService(serviceClass, getClass().getClassLoader(), null);
            if (result != null) {
                if (result instanceof DataSchemeService)
                    ((DataSchemeService) result).setScheme(this);
                services.put(serviceClass, result);
            }
        }
        return result;
    }

    @Override
    public String getDigest() {
        String d = digest;
        if (d == null) {
            d = calculateDigest(this);
            digest = d;
        }
        return d;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + ", Digest " + getDigest();
    }

    // ========== Miscellaneous Stuff ==========

    /**
     * Sets references to corresponding parent entities in the specified scheme.
     * Requires that scheme is instance of {@link DefaultScheme}.
     *
     * @throws ClassCastException if scheme is not an instance of {@link DefaultScheme}.
     * @throws IllegalStateException if some parent reference already set to different instance.
     * @deprecated No replacement. Just remove usages of this method.
     *        {@link #DefaultScheme(SymbolCodec, DataRecord[])} constructor now does it automatically.
     */
    public static void setParentReferences(DataScheme scheme) {
        ((DefaultScheme) scheme).setParentReferences();
    }

    private void setParentReferences() {
        for (DefaultRecord record : records)
            record.setScheme(this);
    }

    /**
     * This method does nothing.
     * @deprecated No replacement. Just remove usages of this method.
     *        {@link #DefaultScheme(SymbolCodec, DataRecord[])} constructor does it all automatically.
     */
    public static void verifyScheme(DataScheme scheme) {
    }

    public static String calculateDigest(DataScheme scheme) {
        MessageDigest digest;
        byte[] output;
        try {
            digest = MessageDigest.getInstance("SHA-256");
            ByteArrayOutput out = new ByteArrayOutput(1024);
            for (int ridx = 0; ridx < scheme.getRecordCount(); ridx++) {
                DataRecord rec = scheme.getRecord(ridx);
                // Put name of record into hash
                out.writeUTFString(rec.getName());
                out.writeCompactInt((rec.hasTime() ? 1 : 0));
                for (int fidx = 0; fidx < rec.getIntFieldCount(); fidx++) {
                    DataField fld = rec.getIntField(fidx);
                    serializeField(out, fld);
                }
                for (int fidx = 0; fidx < rec.getObjFieldCount(); fidx++) {
                    DataField fld = rec.getObjField(fidx);
                    serializeField(out, fld);
                }
            }
            digest.update(out.toByteArray());
            output = digest.digest();
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
        return URLSAFE_UNPADDED.encode(output);
    }

    private static void serializeField(ByteArrayOutput out, DataField fld) throws IOException {
        // Skip hashing of names of VOID fields, as they are not using in protocol at all
        if (fld.getSerialType() != SerialFieldType.VOID) {
            out.writeUTFString(fld.getName());
            out.writeUTFString(fld.getLocalName());
            out.writeUTFString(fld.getPropertyName());
        }
        out.writeCompactInt(fld.getSerialType().getId());
    }
}
