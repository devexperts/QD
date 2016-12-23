/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.news;

import java.util.List;

import com.devexperts.rmi.*;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.impl.DXFeedImpl;
import com.dxfeed.model.ObservableListModel;
import com.dxfeed.news.impl.NewsNotFoundException;
import com.dxfeed.news.impl.RemoteNewsService;
import com.dxfeed.promise.Promise;

/**
 * Model for convenient News management.
 *
 * <h3>Sample usage</h3>
 * The following code will print and update last 5 news from "Business Wire" source:
 * <pre>
 * NewsModel model = new NewsModel(client);
 * model.getNewsList().addListener(new ObservableListModelListener<NewsSummary>() {
 *     public void modelChanged(Change&lt;? extends NewsSummary&gt; change) {
 *         for (NewsSummary news : change.getSource())
 *             System.out.println(news.getSourceId() + ": " + news.getTitle());
 *     }
 * });
 * model.setFilter(new NewsFilter("Business Wire", null, 5));
 * model.setLive(true);
 * </pre>
 *
 * <h3><a name="threadsAndLocksSection">Threads and locks</a></h3>
 *
 * This class is <b>not</b> tread-safe and requires external synchronization.
 * You must query the state of {@link #attach(DXFeed) attached} model only from
 * inside of the notification invocations or from within the thread that performs
 * those notifications.
 *
 * <p>Listeners are invoked in the context of the corresponding {@link DXEndpoint} executor and the corresponding
 * notification is guaranteed to never be concurrent, even though it may happen from different
 * threads if executor is multi-threaded.
 */
public final class NewsModel {

	// ==================== private static fields ====================

	private static final RMIOperation<String> GET_NEWS_CONTENTS = RMIOperation.valueOf(
		RemoteNewsService.class, String.class, "getNewsContent", NewsKey.class);

	private static final RMIOperation<NewsList> FIND_NEWS_FOR_FILTER = RMIOperation.valueOf(
		RemoteNewsService.class, NewsList.class, "findNewsForFilter", NewsFilter.class, NewsKey.class);

	// ==================== private instance fields ====================

	private RMIClient client; // updated only by user code
	private boolean live; // updated only by user code
	private NewsFilter filter = NewsFilter.EMPTY; // updated only by user code

	private RMIRequest<NewsList> request; // GuardedBy(this), set to null only when request complete
	private NewsKey lastKey = NewsKey.FIRST_KEY; // GuardedBy(this)

	private final ObservableNewsList newsList = new ObservableNewsList();
	private final FindNewsForFilterListener findNewsForFilterListener = new FindNewsForFilterListener();

	// ==================== public instance methods ====================

	/**
	 * Creates model with {@link NewsFilter#EMPTY empty filter}. For further use,
	 * this model needs to be {@link #attach(DXFeed) attached} to {@link DXFeed}.
	 */
	public NewsModel() {}

	/**
	 * Creates model with  with {@link NewsFilter#EMPTY empty filter} and the specified {@link DXFeed}
	 * @param feed {@link DXFeed} to connect
	 * @deprecated For consistency with other xxModel classes.
	 *             Use {@link #NewsModel() default} constructor, then {@link #attach(DXFeed) attach}.
	 */
	public NewsModel(DXFeed feed) {
		attach(feed);
	}

	/**
	 * Attach {@link DXFeed} to this model. Inside this method calls {@link #detach()}
	 * @param feed {@link DXFeed} to connect
	 */
	public void attach(DXFeed feed) {
		if (feed == null)
			throw new NullPointerException();
		detach();
		if (feed instanceof DXFeedImpl) {
			RMIEndpoint rmiEndpoint = ((DXFeedImpl)feed).getDXEndpoint().getRMIEndpoint();
			if (rmiEndpoint.getSide().hasClient())
				client = rmiEndpoint.getClient();
		}
		updateRequest();
	}

	/**
	 * Detach attached {@link DXFeed} from this model.
	 */
	public void detach() {
		newsList.clear();
		client = null;
		updateRequest();
	}

	/**
	 * Returns currently active news filter.
	 * @return current {@link NewsFilter news filter}.
	 */
	public NewsFilter getFilter() {
		return filter; // no need to synchronize, as write by setFilter is caller's responsibility to synchronize
	}

	/**
	 * Sets news filter.
	 * @param filter {@link NewsFilter news filter} to use
	 */
	public synchronized void setFilter(NewsFilter filter) {
		if (filter == null)
			throw new NullPointerException("filter is null");

		RMIRequest<NewsList> request = this.request;

		this.filter = filter;
		this.newsList.setLimit(filter.getLimit());
		if (request != null) {
			lastKey = NewsKey.FIRST_KEY;
			request.cancelOrAbort(); // will create new request on cancel

			newsList.beginChange();
			newsList.clear();
			newsList.endChange();
		}
	}

	/**
	 * Returns whether news model is "live", i.e. receives news updates.
	 * @return flag, indicating "live" status of the model.
	 */
	public boolean isLive() {
		return live;
	}

	/**
	 * Sets news model "live" status of the model. When model is "live"
	 * it receives news updates from the news server.
	 * @param live flag, indicating "live" status of the model.
	 */
	public void setLive(boolean live) {
		if (this.live == live)
			return;
		this.live = live;
		updateRequest();
	}

	/**
	 * Returns the view of an observable list of news.
	 * This method returns the reference to the same object on each invocation.
	 * The resulting list is immutable. It reflects the current list of events
	 * and is updated on arrival of new events. See
	 * <a href="#threadsAndLocksSection">Threads and locks</a> section for
	 * details on concurrency of these updates.
	 *
	 * @return the view of an observable list of news.
	 */
	public ObservableListModel<NewsSummary> getNewsList() {
		return newsList;
	}

	/**
	 * Returns news body for the specified news summary.
	 * @param news news summary.
	 * @return promise that will either return {@link News} or throw {@link NewsNotFoundException}.
	 */
	public Promise<News> getNews(final NewsSummary news) {
		if (client == null) {
			Promise<News> result = new Promise<>();
			result.cancel();
			return result;
		}
		RMIRequest<String> req = client.createRequest(null, GET_NEWS_CONTENTS, news.getKey());
		req.send();
		final Promise<String> contentsPromise = req.getPromise();
		final Promise<News> result = new Promise<>();
		result.whenDone(p -> contentsPromise.cancel());
		contentsPromise.whenDone(p -> {
			if (p.hasResult())
				result.complete(new News(news, p.getResult()));
			else
				result.completeExceptionally(p.getException());
		});
		return result;
	}

	// ==================== implementation ====================

	private synchronized void updateRequest() {
		if (live && client != null) {
			request = client.createRequest(null, FIND_NEWS_FOR_FILTER, filter, lastKey);
			request.send();
			request.setListener(findNewsForFilterListener);
		} else if (request != null)
			request.cancelOrAbort();
	}

	private synchronized void makeNotRunning() {
		request = null;
	}

	private void processNews(NewsList remoteNews) {
		synchronized (this) {
			lastKey = remoteNews.getLastKey();
		}
		List<NewsSummary> list = remoteNews.getNews();
		newsList.beginChange();
		for (int i = list.size(); --i >= 0; )
			newsList.addChange(list.get(i));
		newsList.endChange();
	}

	private class FindNewsForFilterListener implements RMIRequestListener {
		@Override
		public void requestCompleted(RMIRequest<?> request) {
			try {
				RMIException e = request.getException();
				if (e != null) {
					if (!e.getType().isCancelled())
						throw new RuntimeRMIException(e); // failure will got to executor to handle
				} else
					processNews((NewsList)request.getNonBlocking());
			} finally {
				makeNotRunning();
			}
			// only if completed successfully
			updateRequest();
		}
	}
}
