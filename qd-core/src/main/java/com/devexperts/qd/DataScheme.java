/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd;

import com.devexperts.qd.spi.QDFilterFactory;
import com.devexperts.services.Service;

/**
 * The <code>DataScheme</code> defines overall scheme of data records and fields.
 * It contains an indexed list of data records and provides quick access to them.
 * <p>
 * For the QD, the data scheme represents description of processed data.
 * It shall be provided by some external entity (the APS), and it shall be
 * constant throughout the lifetime of the QD systems.
 */
@Service
public interface DataScheme {

    /**
     * Returns symbol codec used in this scheme.
     */
    public SymbolCodec getCodec();

    /**
     * Returns number of records in this scheme.
     */
    public int getRecordCount();

    /**
     * Returns data record by its index within this scheme.
     * The record index in the scheme coincides with its identifier and
     * is also used for identification of data record in serialized form.
     *
     * @throws IndexOutOfBoundsException if the index is out of range
     *         (index &lt; 0 || index &gt;= getRecordCount()).
     */
    public DataRecord getRecord(int index);

    /**
     * Returns data record by its name.
     * Returns null if no such records exists.
     */
    public DataRecord findRecordByName(String name);

    /**
     * Returns Int-field by its {@link DataRecord#getName() full name}.
     * Returns null if no such fields exists.
     * @deprecated Find record using {@link #findRecordByName(String)}
     *      and find its field using {@link DataRecord#findFieldByName(String)}.
     */
    public DataIntField findIntFieldByName(String name);

    /**
     * Returns Obj-field by its {@link DataRecord#getName() full name}.
     * Returns null if no such fields exists.
     * @deprecated Find record using {@link #findRecordByName(String)}
     *      and find its field using {@link DataRecord#findFieldByName(String)}.
     */
    public DataObjField findObjFieldByName(String name);

    /**
     * Extension point for additional scheme-specific services. The following services are
     * now supported:
     * <ul>
     *   <li>{@link QDFilterFactory QDFilterFactory}
     *   <li>{@link HistorySubscriptionFilter HistorySubscriptionFilter}
     *   <li>{@link QDErrorHandler QDErrorHandler}
     * </ul>
     * This method returns <code>null</code> if no scheme-specific override for the corresponding service is found.
     *
     * <p>Default implementation in {@link com.devexperts.qd.kit.DefaultScheme DefaultScheme} is to use
     * {@link com.devexperts.services.Services#createService} with this scheme's class loader.
     */
    public <T> T getService(Class<T> service_class);

    /**
     * Returns digest of the scheme.
     * <p>
     * Returns {@code null} by default.
     *
     * @return Digest of the scheme.
     */
    public default String getDigest() {
        return null;
    }
}
