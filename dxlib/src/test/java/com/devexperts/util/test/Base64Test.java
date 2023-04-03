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
package com.devexperts.util.test;

import com.devexperts.util.Base64;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

public class Base64Test {

    @Test
    public void testBase64() {
        assertThrows(Exception.class, () -> new Base64("short alphabet"));
        assertThrows(Exception.class,
            () -> new Base64("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789++="));
        assertThrows(Exception.class,
            () -> new Base64("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/+"));
        doTest(Base64.DEFAULT);
        doTest(Base64.URLSAFE);
        doTest(new Base64("EZ7KG_r4d0-SUQnzmiFawucD6YCpfVqWeo1T35OIxyMh9jsNvJAPkB2Lglt8XHRb+"));
    }

    private void doTest(Base64 basePadded) {
        char pad = basePadded.getAlphabet().charAt(64);
        Base64 baseUnpadded = new Base64(basePadded.getAlphabet().substring(0, 64));
        Random r = new Random();
        for (int i = 0; i < 1000; i++) {
            byte[] src = new byte[r.nextInt(100)];
            r.nextBytes(src);
            String strPadded = basePadded.encode(src);
            String strUnpadded = baseUnpadded.encode(src);
            if (strUnpadded.indexOf(pad) >= 0)
                fail(basePadded.getAlphabet() + " for " + Arrays.toString(src) + ": " + strUnpadded + " contains pad");
            StringBuilder sb = new StringBuilder(strUnpadded);
            while (sb.length() < strPadded.length()) {
                sb.append(pad);
            }
            if (!strPadded.equals(sb.toString())) {
                fail(basePadded.getAlphabet() + " for " + Arrays.toString(src) + ": " +
                    strPadded + " does not extend " + strUnpadded);
            }
            byte[] padded = basePadded.decode(strPadded);
            if (!Arrays.equals(src, padded)) {
                fail(basePadded.getAlphabet() + " for " + Arrays.toString(src) + ": padded " +
                    strPadded + " decodes to " + Arrays.toString(padded));
            }
            byte[] unpadded = baseUnpadded.decode(strUnpadded);
            if (!Arrays.equals(src, unpadded)) {
                fail(basePadded.getAlphabet() + " for " + Arrays.toString(src) + ": unpadded " +
                    strUnpadded + " decodes to " + Arrays.toString(unpadded));
            }
        }
    }
}
