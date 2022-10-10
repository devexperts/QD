/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.benchmark.transfer.blob;

public class DummyBlobService implements BlobService {

    private volatile byte[] blob;

    public byte[] getBlob(int blobSize) {
        byte[] blob = this.blob;
        if (blob == null || blob.length != blobSize) {
            blob = new byte[blobSize];
            this.blob = blob;
        }
        return blob;
    }
}
