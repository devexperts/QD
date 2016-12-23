/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.news;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import com.dxfeed.model.ObservableListModel;
import com.dxfeed.model.ObservableListModelListener;

/**
 * Implementation of the observable list model for news.
 * It holds limited number of news sorted by time starting with the latest news.
 */
class ObservableNewsList extends AbstractList<NewsSummary> implements ObservableListModel<NewsSummary> {

	// ==================== private static fields ====================

	private static final Comparator<NewsSummary> REVERSE_COMPARATOR = Collections.reverseOrder();

	// ==================== private instance fields ====================

	private List<ObservableListModelListener<? super NewsSummary>> listeners =
		new CopyOnWriteArrayList<>();

	private int limit;
	private ArrayList<NewsSummary> news = new ArrayList<>(NewsFilter.DEFAULT_LIMIT);
	private boolean changed;

	// ==================== public instance fields ====================

	@Override
	public NewsSummary get(int index) {
		return news.get(index);
	}

	@Override
	public int size() {
		return news.size();
	}

	@Override
	public void clear() {
		news.clear();
		changed = true;
	}

	@Override
	public void addListener(ObservableListModelListener<? super NewsSummary> listener) {
		listeners.add(listener);
	}

	@Override
	public void removeListener(ObservableListModelListener<? super NewsSummary> listener) {
		listeners.remove(listener);
	}

	// ==================== Implementation ====================

	void setLimit(int limit) {
		if (limit <= 0)
			throw new IllegalArgumentException("limit must be positive: " + limit);
		this.limit = limit;

		if (news.size() > limit) {
			while (news.size() > limit)
				news.remove(news.size() - 1);
			fireModelChanged();
		}
	}

	protected void beginChange() {
		changed = false;
	}

	protected boolean addChange(NewsSummary newsSummary) {
		if (newsSummary == null)
			throw new NullPointerException("newsSummary");

		int index = Collections.binarySearch(news, newsSummary, REVERSE_COMPARATOR);
		if (index >= 0)
			return false;

		news.add(-index - 1, newsSummary);
		while (news.size() > limit)
			news.remove(news.size() - 1);

		changed = true;
		return true;
	}

	protected void endChange() {
		if (changed)
			fireModelChanged();
	}

	protected void fireModelChanged() {
		ObservableListModelListener.Change<? extends NewsSummary> change =
			new ObservableListModelListener.Change<>(this);
		for (ObservableListModelListener<? super NewsSummary> listener : listeners)
			listener.modelChanged(change);
	}
}
