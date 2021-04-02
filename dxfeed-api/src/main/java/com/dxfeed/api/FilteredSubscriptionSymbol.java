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
package com.dxfeed.api;

import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;

/**
 * This marker interface marks subscription symbol classes (like {@link TimeSeriesSubscriptionSymbol})
 * that attach "filters" to the actual symbols. Filtered subscription symbols implement their
 * {@link Object#equals(Object)} and {@link Object#hashCode()} methods based on their symbol only,
 * without considering their "filter" part (for example, a {@link TimeSeriesSubscriptionSymbol} has
 * a {@link TimeSeriesSubscriptionSymbol#getFromTime() fromTime} filter on a time range of events).
 *
 * <p>{@link DXFeedSubscription} has the following behavior for filtered symbols. There can be only one
 * active filter per symbol. Adding new filtered symbol with the same symbol but different filter
 * removes the old one from subscription, adds the new one, and <em>notifies</em>
 * {@link ObservableSubscriptionChangeListener} <em>about this subscription change</em>.
 * The later is a special behaviour for filtered subscription symbols, because on other types of
 * symbols (like {@link String}, for example) there is no notification when replacing one symbol
 * with the other that is "equal" to the first one.
 */
public interface FilteredSubscriptionSymbol {
}
