/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2020 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd;

import com.devexperts.qd.stats.QDStatsContainer;

/**
 * Error handler interface for exceptions in data and subscription listeners.
 */
public interface QDErrorHandler {
    /**
     * Default error handler. It dumps errors to the {@link QDLog log}, retrieves and ignores all the
     * data/subscription from the corresponding provider.
     */
    public static final QDErrorHandler DEFAULT = new QDErrorHandler() {
        public void handleDataError(DataProvider provider, Throwable t) {
            QDLog.log.error(annotate(provider, "Error while processing data notification"), t);
            while (provider.retrieveData(DataVisitor.VOID)); // retrieve while it returns true
        }

        public void handleSubscriptionError(SubscriptionProvider provider, Throwable t) {
            QDLog.log.error(annotate(provider, "Error while processing subscription notification"), t);
            while (provider.retrieveSubscription(SubscriptionVisitor.VOID)); // retrieve while it returns true
        }

        private String annotate(Object obj, String message) {
            if (obj instanceof QDStatsContainer)
                message += " [" + ((QDStatsContainer) obj).getStats().getFullKeyProperties() + "]";
            return message;
        }
    };

    public void handleDataError(DataProvider provider, Throwable t);

    public void handleSubscriptionError(SubscriptionProvider provider, Throwable t);
}
