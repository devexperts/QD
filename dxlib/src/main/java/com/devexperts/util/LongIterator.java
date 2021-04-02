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
package com.devexperts.util;

import java.util.Iterator;
import java.util.PrimitiveIterator;

/**
 * This class extends {@link Iterator} with methods that are specific
 * to <code>long</code> values.
 *
 * @deprecated Use {@link PrimitiveIterator.OfLong} instead.
 */
@Deprecated
public interface LongIterator extends PrimitiveIterator.OfLong {
}
