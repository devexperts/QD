/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.api.impl;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import com.devexperts.qd.*;
import com.devexperts.qd.kit.ArrayListAttachmentStrategy;
import com.devexperts.qd.ng.*;
import com.devexperts.qd.util.RecordProcessor;
import com.devexperts.util.*;
import com.dxfeed.api.*;
import com.dxfeed.api.osub.*;
import com.dxfeed.event.*;
import com.dxfeed.impl.AbstractIndexedList;
import com.dxfeed.promise.Promise;
import com.dxfeed.promise.PromiseHandler;

public class DXFeedImpl extends DXFeed {
	private static boolean TRACE_LOG = DXFeedImpl.class.desiredAssertionStatus();

	private static final String INVALID_EVENT_MSG = "Invalid event type and/or role";
	private static final QDContract[] CONTRACTS = QDContract.values();
	private static final int N_CONTRACTS = CONTRACTS.length;
	private static final EventProcessorAttachmentStrategy EVENT_PROCESSOR_ATTACHMENT_STRATEGY = new EventProcessorAttachmentStrategy();
	private static final LastEventAttachmentStrategy LAST_EVENT_ATTACHMENT_STRATEGY = new LastEventAttachmentStrategy();

	private static final ThreadLocal<LocalAddBatch> LOCAL_ADD_BATCH = new ThreadLocal<>();
	private static final ThreadLocal<LocalRemoveBatch> LOCAL_REMOVE_BATCH = new ThreadLocal<>();

	private final DXEndpointImpl endpoint;
	private final RecordMode retrieveMode;
	private final QDAgent.Builder[] eventProcessorAgentBuilders = new QDAgent.Builder[N_CONTRACTS];
	private final IndexedSet<DXFeedSubscription<?>, EventProcessor<?, ?>> eventProcessors =
		IndexedSet.create((IndexerFunction<DXFeedSubscription<?>, EventProcessor<?, ?>>)value -> value.subscription);
	private final IndexedSet<Closeable, Closeable> closeables = new IndexedSet<>();
	private final LastEventsProcessor lastEventsProcessor; // != null when we have QDTicker in endpoint
	private final long aggregationPeriodMillis;

	DXFeedImpl(DXEndpointImpl endpoint) {
		this.endpoint = endpoint;
		RecordMode mode = RecordMode.FLAGGED_DATA.withAttachment();
		if (endpoint.getQDEndpoint().hasEventTimeSequence())
			mode = mode.withEventTimeSequence();
		retrieveMode = mode;
		for (QDContract contract : endpoint.getContracts()) {
			QDAgent.Builder builder = endpoint.getCollector(contract).agentBuilder()
				.withHistorySnapshot(true)
				.withAttachmentStrategy(EVENT_PROCESSOR_ATTACHMENT_STRATEGY);
			eventProcessorAgentBuilders[contract.ordinal()] = builder;
		}
		QDTicker ticker = (QDTicker) endpoint.getCollector(QDContract.TICKER);
		if (ticker == null) {
			lastEventsProcessor = null;
		} else {
			lastEventsProcessor = new LastEventsProcessor(ticker);
			lastEventsProcessor.start();
		}
		aggregationPeriodMillis = endpoint.hasProperty(DXEndpoint.DXFEED_AGGREGATION_PERIOD_PROPERTY) ?
			TimePeriod.valueOf(endpoint.getProperty(DXEndpoint.DXFEED_AGGREGATION_PERIOD_PROPERTY)).getTime() : 0;
	}

	// helper method for detachSubscriptionAndClear method and for OnDemandService implementation
	public static void clearDataInBuffer(RecordBuffer buf, boolean keepTime) {
		RecordCursor cur;
		while ((cur = buf.writeNext()) != null) {
			for (int i = (keepTime ? 2 : 0); i < cur.getIntCount(); i++)
				cur.setInt(i, 0);
			for (int i = 0; i < cur.getObjCount(); i++)
				cur.setObj(i, null);
		}
	}

	public void awaitTerminationAndCloseImpl() throws InterruptedException {
		for (EventProcessor<?, ?> processor : eventProcessors.toArray(new EventProcessor[eventProcessors.size()])) {
			if (processor == null)
				break;
			processor.awaitTerminationAndClose();
		}
	}

	public void closeImpl() {
		assert endpoint.isClosed(); // assert that close impl is called on endpoint that is already marked as closed
		// no need to sync, because nothing is added or removed when endpoint is closed
		for (EventProcessor<?, ?> processor : eventProcessors)
			processor.close(false);
		eventProcessors.clear();
		// no need to sync, because nothing is added or removed when endpoint is closed
		for (Closeable c : closeables)
			c.close();
		closeables.clear();
		if (lastEventsProcessor != null)
			lastEventsProcessor.close();
	}

	private void removeEventProcessor(DXFeedSubscription<?> subscription) {
		// don't need to remove when endpoint is already closed (closeImpl cleans up a list of eventProcessors)
		if (!endpoint.isClosed())
			synchronized (endpoint.getLock()) {
				if (!endpoint.isClosed()) // must double-check under synchronization to avoid concurrent modification exception with closeImpl
					eventProcessors.removeKey(subscription);
			}
	}

	private void removeCloseable(Closeable c) {
		// don't need to remove when endpoint is already closed (closeImpl cleans up a list of closeables)
		if (!endpoint.isClosed())
			synchronized (endpoint.getLock()) {
				if (!endpoint.isClosed()) // must double-check under synchronization to avoid concurrent modification exception with closeImpl
					closeables.remove(c);
			}
	}

	@Override
	@SuppressWarnings("unchecked")
	public void attachSubscription(DXFeedSubscription<?> subscription) {
		subscription.addChangeListener(new SubscriptionChangeListener(subscription, false));
	}

	@Override
	@SuppressWarnings("unchecked")
	public void detachSubscription(DXFeedSubscription<?> subscription) {
		subscription.removeChangeListener(new SubscriptionChangeListener(subscription, false));
	}

	@Override
	@SuppressWarnings("unchecked")
	public void detachSubscriptionAndClear(DXFeedSubscription<?> subscription) {
		subscription.removeChangeListener(new SubscriptionChangeListener(subscription, true));
	}

	@Override
	@SuppressWarnings("unchecked")
	public <E extends LastingEvent<?>> E getLastEvent(E event) {
		EventDelegate<E> delegate = getLastingEventDelegateOrNull((Class<E>)event.getClass(), event.getEventSymbol());
		if (delegate == null)
			return event;
		QDTicker ticker = (QDTicker)endpoint.getCollector(QDContract.TICKER); // assert != null (if we have delegate)
		LocalAddBatch lb = getLocalAddBatch();
		String qdSymbol = delegate.getQDSymbolByEvent(event);
		int cipher = endpoint.encode(qdSymbol);
		if (ticker.getDataIfAvailable(lb.owner, delegate.getRecord(), cipher, qdSymbol))
			return delegate.getEvent(event, lb.owner.cursor());
		// not found -- return unmodified event
		return event;
	}

	@Override
	public <E extends LastingEvent<?>> E getLastEventIfSubscribed(Class<E> eventType, Object symbol) {
		EventDelegate<E> delegate = getLastingEventDelegateOrNull(eventType, symbol);
		if (delegate == null)
			return null;
		assert lastEventsProcessor != null; // if we have delegate
		LocalAddBatch lb = getLocalAddBatch();
		String qdSymbol = delegate.getQDSymbolByEventSymbol(symbol);
		int cipher = endpoint.encode(qdSymbol);
		if (lastEventsProcessor.ticker.getDataIfSubscribed(lb.owner, delegate.getRecord(), cipher, qdSymbol))
			return delegate.createEvent(symbol, lb.owner.cursor());
		// not subscribed -- return null
		return null;
	}

	@Override
	public <E extends LastingEvent<?>> Promise<E> getLastEventPromise(Class<E> eventType, Object symbol) {
		if (eventType == null || symbol == null)
			throw new NullPointerException();
		EventDelegate<E> delegate = getLastingEventDelegateOrNull(eventType, symbol);
		if (delegate == null)
			return Promise.failed(new IllegalArgumentException(INVALID_EVENT_MSG));
		assert lastEventsProcessor != null; // if we have delegate
		LocalAddBatch lb = getLocalAddBatch();
		String qdSymbol = delegate.getQDSymbolByEventSymbol(symbol);
		int cipher = endpoint.encode(qdSymbol);
		// optimization for single event -- check that it is immediately available without subscription
		if (lastEventsProcessor.ticker.getDataIfAvailable(lb.owner, delegate.getRecord(), cipher, qdSymbol))
			return Promise.completed(delegate.createEvent(symbol, lb.owner.cursor()));
		// not found -- need to subscribe
		LastEventPromise<E> promise = new LastEventPromise<>(symbol, delegate, cipher, qdSymbol);
		lb.subscribeStartBatch();
		lb.subscribeAddBatch(promise);
		if (!lb.completeAddSubBatch(lastEventsProcessor)) {
			promise.cancel();
			return promise;
		}
		return promise;
	}

	@Override
	public <E extends LastingEvent<?>> List<Promise<E>> getLastEventsPromises(Class<E> eventType, Collection<?> symbols) {
		if (eventType == null)
			throw new NullPointerException();
		List<Promise<E>> result = new ArrayList<>(symbols.size());
		assert lastEventsProcessor != null; // if we have delegate
		LocalAddBatch lb = null;
		for (Object symbol : symbols) {
			EventDelegate<E> delegate = getLastingEventDelegateOrNull(eventType, symbol);
			if (delegate == null) {
				result.add(Promise.failed(new IllegalArgumentException(INVALID_EVENT_MSG)));
				continue;
			}
			if (lb == null) {
				lb = getLocalAddBatch();
				lb.subscribeStartBatch();
			}
			String qdSymbol = delegate.getQDSymbolByEventSymbol(symbol);
			int cipher = endpoint.encode(qdSymbol);
			LastEventPromise<E> promise = new LastEventPromise<>(symbol, delegate, cipher, qdSymbol);
			result.add(promise);
			// optimization for single event -- check that it is immediately available without subscription
			if (lastEventsProcessor.ticker.getDataIfAvailable(lb.owner, delegate.getRecord(), cipher, qdSymbol)) {
				promise.complete(delegate.createEvent(symbol, lb.owner.cursor()));
				continue;
			}
			// not found -- need to subscribe
			lb.subscribeAddBatch(promise);
		}
		// now subscribe for all of them in batch
		if (lb != null && !lb.completeAddSubBatch(lastEventsProcessor)) {
			for (Promise<E> promise : result)
				promise.cancel(); // will cancel all that were not complete
		}
		return result;
	}

	private static LocalAddBatch getLocalAddBatch() {
		LocalAddBatch localAddBatch = LOCAL_ADD_BATCH.get();
		if (localAddBatch == null)
			LOCAL_ADD_BATCH.set(localAddBatch = new LocalAddBatch());
		return localAddBatch;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private <E extends EventType<?>> EventDelegate<E> getLastingEventDelegateOrNull(Class<E> eventType, Object symbol) {
		EventDelegateSet delegateSet = endpoint.getDelegateSetByEventType(eventType);
		if (delegateSet == null)
			return null;
		EventDelegate<E> delegate = (EventDelegate<E>)delegateSet.getLastingDelegateByEventSymbol(delegateSet.convertSymbol(symbol));
		if (delegate == null)
			return null;
		if (delegate.getContract() != QDContract.TICKER)
			return null;
		return delegate;
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public <E extends IndexedEvent<?>> Promise<List<E>> getIndexedEventsPromise(Class<E> eventType, Object symbol,
		IndexedEventSource source)
	{
		if (eventType == null || symbol == null || source == null)
			throw new NullPointerException();
		EventDelegateSet delegateSet = endpoint.getDelegateSetByEventType(eventType);
		if (delegateSet == null)
			return Promise.failed(new IllegalArgumentException(INVALID_EVENT_MSG));
		symbol = delegateSet.convertSymbol(symbol); // convert symbol to a class supported by delegate
		List<EventDelegate<E>> delegates = (List<EventDelegate<E>>)delegateSet.
			getSubDelegatesBySubscriptionSymbol(symbol, source.id());
		if (delegates.size() != 1)
			return Promise.failed(new IllegalArgumentException(INVALID_EVENT_MSG));
		EventDelegate<E> delegate = delegates.get(0);
		if (delegate.getContract() != QDContract.HISTORY)
			return Promise.failed(new IllegalArgumentException(INVALID_EVENT_MSG));
		return fetchOrSubscribeFromHistory(symbol, delegate, 0, 0, Long.MAX_VALUE);
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public <E extends IndexedEvent<?>> List<E> getIndexedEventsIfSubscribed(Class<E> eventType, Object symbol,
		IndexedEventSource source)
	{
		if (eventType == null || symbol == null || source == null)
			throw new NullPointerException();
		EventDelegateSet delegateSet = endpoint.getDelegateSetByEventType(eventType);
		if (delegateSet == null)
			return null; // invalid event type for this method
		symbol = delegateSet.convertSymbol(symbol); // convert symbol to a class supported by delegate
		List<EventDelegate<E>> delegates = (List<EventDelegate<E>>)delegateSet.
			getSubDelegatesBySubscriptionSymbol(symbol, source.id());
		if (delegates.size() != 1)
			return null; // invalid event type for this method
		EventDelegate<E> delegate = delegates.get(0);
		if (delegate.getContract() != QDContract.HISTORY)
			return null; // invalid event type for this method
		return fetchFromHistoryIfSubscribed(symbol, delegate, 0, Long.MAX_VALUE);
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public <E extends TimeSeriesEvent<?>> Promise<List<E>> getTimeSeriesPromise(Class<E> eventType, Object symbol,
		long fromTime, long toTime)
	{
		if (eventType == null || symbol == null)
			throw new NullPointerException();
		EventDelegateSet delegateSet = endpoint.getDelegateSetByEventType(eventType);
		if (delegateSet == null)
			return Promise.failed(new IllegalArgumentException(INVALID_EVENT_MSG));
		symbol = delegateSet.convertSymbol(symbol); // convert symbol to a class supported by delegate
		List<EventDelegate<E>> delegates = (List<EventDelegate<E>>)delegateSet.
			getTimeSeriesDelegatesByEventSymbol(symbol);
		if (delegates.size() != 1)
			return Promise.failed(new IllegalArgumentException(INVALID_EVENT_MSG));
		EventDelegate<E> delegate = delegates.get(0);
		if (delegate.getContract() != QDContract.HISTORY)
			return Promise.failed(new IllegalArgumentException(INVALID_EVENT_MSG));
		long fetchTime = delegate.getFetchTimeHeuristicByEventSymbolAndFromTime(symbol, fromTime);
		return fetchOrSubscribeFromHistory(symbol, delegate,
			delegate.getQDTimeByEventTime(fetchTime),
			delegate.getQDTimeByEventTime(fromTime),
			delegate.getQDTimeByEventTime(toTime));
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public <E extends TimeSeriesEvent<?>> List<E> getTimeSeriesIfSubscribed(Class<E> eventType, Object symbol,
		long fromTime, long toTime)
	{
		if (eventType == null || symbol == null)
			throw new NullPointerException();
		EventDelegateSet delegateSet = endpoint.getDelegateSetByEventType(eventType);
		if (delegateSet == null)
			return null; // invalid event type for this method
		symbol = delegateSet.convertSymbol(symbol); // convert symbol to a class supported by delegate
		List<EventDelegate<E>> delegates = (List<EventDelegate<E>>)delegateSet.
			getTimeSeriesDelegatesByEventSymbol(symbol);
		if (delegates.size() != 1)
			return null; // invalid event type for this method
		EventDelegate<E> delegate = delegates.get(0);
		if (delegate.getContract() != QDContract.HISTORY)
			return null; // invalid event type for this method
		return fetchFromHistoryIfSubscribed(symbol, delegate,
			delegate.getQDTimeByEventTime(fromTime),
			delegate.getQDTimeByEventTime(toTime));
	}

	@Nullable
	private <E extends IndexedEvent<?>> List<E> fetchFromHistoryIfSubscribed(Object symbol,
		EventDelegate<E> delegate, long fromQDTime, long toQDTime)
	{
		QDHistory history = (QDHistory)endpoint.getCollector(QDContract.HISTORY); // assert != null (if we have delegate)
		String qdSymbol = delegate.getQDSymbolByEventSymbol(symbol);
		int cipher = endpoint.encode(qdSymbol);
		// check subscription
		if (!history.isSubscribed(delegate.getRecord(), cipher, qdSymbol, fromQDTime))
			return null; // not subscribed
		// fetch data
		HistoryFetchResult<E> fetch = new HistoryFetchResult<>(symbol, 0, delegate, false);
		history.examineData(delegate.getRecord(), cipher, qdSymbol, fromQDTime, toQDTime, fetch);
		return fetch.result != null ? fetch.result : Collections.emptyList();
	}

	private <E extends IndexedEvent<?>> Promise<List<E>> fetchOrSubscribeFromHistory(
		Object symbol, EventDelegate<E> delegate, long fetchQDTime, long fromQDTime, long toQDTime)
	{
		QDHistory history = (QDHistory)endpoint.getCollector(QDContract.HISTORY); // assert != null (if we have delegate)
		String qdSymbol = delegate.getQDSymbolByEventSymbol(symbol);
		int cipher = endpoint.encode(qdSymbol);
		// check if data is available in history without subscription
		HistoryFetchResult<E> fetch = new HistoryFetchResult<>(symbol, fromQDTime, delegate, true);
		history.examineData(delegate.getRecord(), cipher, qdSymbol, fetchQDTime, toQDTime, fetch);
		if (fetch.result != null) { // we've got a snapshot of our data and there...
			if (!fetch.txPending) // ... and there is no tx pending -- lucky we!
				return Promise.completed(fetch.result);
			// otherwise we're in transaction and need to subscribe
			// we'll reuse created result object for subscription
			fetch.result.clearImpl();
		}
		// not found (or no consistent snapshot) -- need to subscribe
		// create anonymous agent, so that JMX beans are not registered (see QD-445)
		QDAgent agent = history.agentBuilder().withHistorySnapshot(true).build();
		RecordBuffer sub = RecordBuffer.getInstance(RecordMode.HISTORY_SUBSCRIPTION);
		sub.add(delegate.getRecord(), cipher, qdSymbol).setTime(fetchQDTime);
		agent.addSubscription(sub);
		sub.release();
		HistoryPromiseCompleter<E> completer =
			new HistoryPromiseCompleter<>(agent, symbol, fromQDTime, toQDTime, delegate, fetch.result);
		register(completer);
		return completer.promise;
	}

	@SuppressWarnings("unchecked")
	private EventProcessor<?, ?> getOrCreateEventProcessor(DXFeedSubscription<?> subscription) {
		EventProcessor<?, ?> processor = eventProcessors.getByKey(subscription);
		if (processor != null)
			return processor;
		// double check under lock (do not create new event processors on a closed endpoint !!!)
		synchronized (endpoint.getLock()) {
			if (endpoint.isClosed())
				return null;
			processor = eventProcessors.getByKey(subscription);
			if (processor == null)
				eventProcessors.add(processor = new EventProcessor(subscription));
		}
		return processor;
	}

	@SuppressWarnings("unchecked")
	private void closeEventProcessor(DXFeedSubscription<?> subscription, boolean clear) {
		EventProcessor<?, ?> processor = eventProcessors.getByKey(subscription);
		if (processor == null)
			return;
		processor.close(clear);
		removeEventProcessor(subscription);
	}

	private EnumMap<QDContract, RecordBuffer> toSubscription(DXFeedSubscription<?> subscription, Set<?> symbols,
		boolean isAddSub)
	{
		EnumMap<QDContract, RecordBuffer> result = new EnumMap<>(QDContract.class);
		for (Class<?> eventType: subscription.getEventTypes()) {
			EventDelegateSet<?, ?> delegateSet = endpoint.getDelegateSetByEventType(eventType);
			if (delegateSet == null)
				continue;
			for (Object subSymbol : symbols) {
				List<? extends EventDelegate<?>> delegates;
				Object eventSymbol;
				long fromTime = 0;
				if (subSymbol instanceof TimeSeriesSubscriptionSymbol<?>) {
					TimeSeriesSubscriptionSymbol<?> tss = (TimeSeriesSubscriptionSymbol<?>)subSymbol;
					fromTime = tss.getFromTime();
					eventSymbol = delegateSet.convertSymbol(tss.getEventSymbol());
					delegates = delegateSet.getTimeSeriesDelegatesByEventSymbol(eventSymbol);
				} else if (subSymbol instanceof IndexedEventSubscriptionSymbol) {
					IndexedEventSubscriptionSymbol<?> ies = (IndexedEventSubscriptionSymbol<?>)subSymbol;
					eventSymbol = delegateSet.convertSymbol(ies.getEventSymbol());
					delegates = delegateSet.getSubDelegatesBySubscriptionSymbol(eventSymbol, ies.getSource().id());
				} else {
					eventSymbol = delegateSet.convertSymbol(subSymbol);
					delegates = delegateSet.getSubDelegatesBySubscriptionSymbol(eventSymbol, -1);
				}
				for (EventDelegate<?> delegate : delegates) {
					RecordBuffer sub = result.get(delegate.getContract());
					if (sub == null)
						result.put(delegate.getContract(),
							sub = RecordBuffer.getInstance(
								(isAddSub ? RecordMode.addedSubscriptionFor(delegate.getContract()) :
									RecordMode.SUBSCRIPTION).withAttachment()));
					String qdSymbol = delegate.getQDSymbolByEventSymbol(eventSymbol);
					RecordCursor cur = sub.add(delegate.getRecord(), endpoint.encode(qdSymbol), qdSymbol);
					if (fromTime != 0 && isAddSub)
						cur.setTime(delegate.getQDTimeByEventTime(fromTime));
					cur.setAttachment(new SymbolDelegate(eventSymbol, delegate));
				}
			}
		}
		return result;
	}
	
	public DXEndpointImpl getDXEndpoint() {
		return endpoint;
	}

	public boolean hasAggregationPeriod() {
		return aggregationPeriodMillis > 0;
	}

	public long getAggregationPeriodMillis() {
		return aggregationPeriodMillis;
	}

	private <E> void executePromiseHandler(final Promise<E> promise, final PromiseHandler<? super E> handler) {
		if (handler != null)
			endpoint.getOrCreateExecutor().execute(() -> handler.promiseDone(promise));
	}

	private <E extends IndexedEvent<?>> HistoryPromiseCompleter<E> register(HistoryPromiseCompleter<E> completer) {
		synchronized (endpoint.getLock()) {
			if (endpoint.isClosed()) {
				completer.promise.cancel();            // ... this cancel will close agent
				return completer;
			}
			closeables.add(completer);
		}
		completer.agent.setRecordListener(completer);  // ... and if this one fires immediately, close agent
		return completer;
	}

	private static class HistoryFetchResult<E extends IndexedEvent<?>> extends AbstractRecordSink {
		private final Object symbol;
		private final long fromQDTime;
		private final EventDelegate<E> delegate;

		ResultList<E> result; // consistent type with HistoryPromiseCompleter
		boolean txPending;

		HistoryFetchResult(Object symbol, long fromQDTime, EventDelegate<E> delegate, boolean needSnapshot) {
			this.symbol = symbol;
			this.fromQDTime = fromQDTime;
			this.delegate = delegate;
			if (!needSnapshot)
				result = new ResultList<>(); // allocate for result if does not need a snapshot
		}

		@Override
		public void append(RecordCursor cursor) {
			long time = cursor.getTime();
			int eventFlags = cursor.getEventFlags();
			if (result == null && (time <= fromQDTime || EventFlag.SNAPSHOT_SNIP.in(eventFlags))) {
				// it means that we actually have complete result snapshot
				result = new ResultList<>();
			}
			if (time < fromQDTime)
				return;
			if (result != null && !EventFlag.REMOVE_EVENT.in(eventFlags)) {
				E event = delegate.createEvent(symbol, cursor);
				event.setEventFlags(0); // do not return any event flags
				result.updateImpl(event, false);
			}
			if (EventFlag.TX_PENDING.in(eventFlags))
				txPending = true;
		}
	}

	private class HistoryPromiseCompleter<E extends IndexedEvent<?>> extends AbstractRecordSink
		implements RecordListener, Closeable
	{
		final HistoryPromise<E> promise = new HistoryPromise<>(this);
		final QDAgent agent;

		private final Object symbol;
		private final long fromQDTime;
		private final long toQDTime;
		private final EventDelegate<E> delegate;

		ResultList<E> result; // consistent type with HistoryFetchResult
		boolean txPending;
		boolean complete;

		private HistoryPromiseCompleter(QDAgent agent, Object symbol,
			long fromQDTime, long toQDTime, EventDelegate<E> delegate, ResultList<E> result)
		{
			this.agent = agent;
			this.symbol = symbol;
			this.toQDTime = toQDTime;
			this.delegate = delegate;
			this.result = result;
			this.fromQDTime = fromQDTime;
		}

		@Override
		public void close() {
			promise.cancel(); // will cause handleDone to be called which does the rest of cleanup
		}

		@Override
		public void recordsAvailable(RecordProvider provider) {
			agent.retrieve(this);
			if (complete && !txPending)
				promise.complete(getOrCreateResult());
		}

		@Override
		public void append(RecordCursor cursor) {
			long time = cursor.getTime();
			int eventFlags = cursor.getEventFlags();
			txPending = EventFlag.TX_PENDING.in(eventFlags);
			if (time >= fromQDTime && time <= toQDTime) {
				boolean remove = EventFlag.REMOVE_EVENT.in(eventFlags);
				E event = delegate.createEvent(symbol, cursor);
				event.setEventFlags(0); // do not return any event flags
				getOrCreateResult().updateImpl(event, remove);
			}
			if (time <= fromQDTime || EventFlag.SNAPSHOT_SNIP.in(eventFlags))
				complete = true;
		}

		private AbstractIndexedList<E> getOrCreateResult() {
			if (result == null)
				result = new ResultList<>();
			return result;
		}
	}

	private class HistoryPromise<E extends IndexedEvent<?>> extends Promise<List<E>> {
		private final HistoryPromiseCompleter<E> completer;

		HistoryPromise(HistoryPromiseCompleter<E> completer) {
			this.completer = completer;
		}

		@Override
		protected void handleDone(PromiseHandler<? super List<E>> handler) {
			completer.agent.close();
			removeCloseable(completer);
			executePromiseHandler(this, handler);
		}
	}

	private class SubscriptionChangeListener<E extends EventType<?>> implements ObservableSubscriptionChangeListener {
		private final DXFeedSubscription<E> subscription;
		private final boolean clearOnClose;

		SubscriptionChangeListener(DXFeedSubscription<E> subscription, boolean clearOnClose) {
			this.subscription = subscription;
			this.clearOnClose = clearOnClose;
		}

		// These notification are guarded by DXFeedSubscription monitor
		@Override
		public void symbolsAdded(Set<?> symbols) {
			EnumMap<QDContract, RecordBuffer> sub = toSubscription(subscription, symbols, true);
			if (sub.isEmpty())
				return;
			EventProcessor<?, ?> processor = getOrCreateEventProcessor(subscription);
			//noinspection KeySetIterationMayUseEntrySet // for performance reason cannot do
			for (QDContract contract : sub.keySet()) {
				RecordBuffer buffer = sub.get(contract);
				if (processor != null)
					processor.getOrCreateAgent(contract).addSubscription(buffer);
				buffer.release();
			}
		}

		// These notification are guarded by DXFeedSubscription monitor
		@Override
		public void symbolsRemoved(Set<?> symbols) {
			if (subscription.getSymbols().isEmpty()) {
				subscriptionClosed();
				return;
			}
			EnumMap<QDContract, RecordBuffer> sub = toSubscription(subscription, symbols, false);
			if (sub.isEmpty())
				return;
			EventProcessor<?, ?> processor = eventProcessors.getByKey(subscription);
			//noinspection KeySetIterationMayUseEntrySet // for performance reason cannot do
			for (QDContract contract : sub.keySet()) {
				RecordBuffer buffer = sub.get(contract);
				if (processor != null)
					processor.getOrCreateAgent(contract).removeSubscription(buffer);
				buffer.release();
			}
		}

		@Override
		public void subscriptionClosed() {
			closeEventProcessor(subscription, clearOnClose);
		}

		public DXFeedImpl feed() {
			return DXFeedImpl.this;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof SubscriptionChangeListener))
				return false;
			SubscriptionChangeListener<?> that = (SubscriptionChangeListener<?>)o;
			return subscription == that.subscription && feed() == that.feed();
		}

		@Override
		public int hashCode() {
			return subscription.hashCode() ^ feed().hashCode();
		}
	}

	// States for EventProcessor
	private static final int STATE_AVAILABLE_DATA_MASK = (1 << N_CONTRACTS) - 1;
	private static final int STATE_AVAILABLE_SNAPSHOT_MASK = ((1 << N_CONTRACTS) - 1) << N_CONTRACTS;
	private static final int STATE_SCHEDULED_DATA = 1 << 30;
	private static final int STATE_SCHEDULED_SNAPSHOT = 1 << 31;

	private class EventProcessor<T, E extends EventType<T>> implements RecordListener, Runnable {
		// agents in this event processor
		final QDAgent[] agents = new QDAgent[N_CONTRACTS];

		// State bits:
		//  -- first N_CONTRACTS bits are for dataAvailable,
		//  -- next N_CONTRACT bits for snapshotAvailable (only used when hasAggregationPeriod is true)
		//  -- last bits for "taskScheduled" flags
		final AtomicInteger state = new AtomicInteger();

		// Associated subscription
		final DXFeedSubscription<E> subscription;

		// == null when only updates are processed (DXFeed does not have aggregation period)
		// != null when hasAggregationPeriod is true
		final Runnable snapshotTask;

		// Latch to make sure all data is processed before close
		volatile CountDownLatch terminationLatch;

		// Events cache
		List<E> events;

		EventProcessor(DXFeedSubscription<E> subscription) {
			this.subscription = subscription;
			snapshotTask = hasAggregationPeriod() ? () -> executeTask(true) : null;
		}

		private boolean setState(int mask) {
			int cur;
			do {
				cur = state.get();
				if ((cur & mask) != 0)
					return false;
			} while (!state.compareAndSet(cur, cur | mask));
			return true;
		}

		private void clearState(int mask) {
			int cur;
			do {
				cur = state.get();
			} while ((cur & mask) != 0 && !state.compareAndSet(cur, cur & ~mask));
		}

		private void rescheduleTask(boolean snapshot) {
			Executor executor = subscription.getExecutor();
			if (executor == null)
				executor = endpoint.getOrCreateExecutor();
			long aggregationPeriodMillis = getAggregationPeriodMillis();
			if (snapshot)
				executor.execute(snapshotTask);
			else if (aggregationPeriodMillis == 0 || !(executor instanceof ScheduledExecutorService))
				executor.execute(this);
			else
				((ScheduledExecutorService)executor).schedule(this, aggregationPeriodMillis, TimeUnit.MILLISECONDS);
		}

		private void scheduleTaskIfNeeded(boolean snapshot) {
			if (setState(snapshot ? STATE_SCHEDULED_SNAPSHOT : STATE_SCHEDULED_DATA))
				rescheduleTask(snapshot);
		}

		@Override
		public void run() {
			executeTask(false);
		}

		/**
		 * Executes data processing task.
		 * Synchronized is needed here to ensure at most one copy is run concurrently.
		 * Note, that two copies can be scheduled -- snapshot & data.
		 * Data can be scheduled for retrieval at some future time (when aggregation is set),
		 * while snapshot can arrive and get scheduled for immediate processing.
		 */
		synchronized void executeTask(boolean snapshot) {
			boolean rescheduleTask = true; // Reschedule task if an exception was thrown.
			int availableMask = snapshot ? STATE_AVAILABLE_SNAPSHOT_MASK : STATE_AVAILABLE_DATA_MASK;
			try {
				// INVARIANT: taskScheduled == true here
				int available = state.get();
				if ((available & availableMask) != 0) { // check available just in case
					RecordBuffer buf = RecordBuffer.getInstance(retrieveMode);
					buf.setCapacityLimited(true); // retrieve up to buffer capacity only
					retrieveImpl(buf, available, snapshot);
					if (!buf.isEmpty())
						process(buf);
					buf.release();
				}
				// available state was updated by retrieveImpl and concurrent notifications
				rescheduleTask = (state.get() & availableMask) != 0;
			} finally {
				if (rescheduleTask)
					rescheduleTask(snapshot);
				else {
					// Task should not be scheduled anymore
					if (!snapshot)
						signalNoMoreDataToProcess();
					clearState(snapshot ? STATE_SCHEDULED_SNAPSHOT : STATE_SCHEDULED_DATA);
					// Concurrent dataAvailable notification might have happened - recheck available flags
					if ((state.get() & availableMask) != 0)
						scheduleTaskIfNeeded(snapshot);
				}
			}
		}

		private void retrieveImpl(RecordBuffer buf, int available, boolean snapshot) {
			int offset = snapshot ? N_CONTRACTS : 0;
			for (int i = 0; i < N_CONTRACTS; i++) {
				int mask = 1 << (i + offset);
				if ((available & mask) != 0) {
					clearState(mask); // clear before starting retrieve
					// Note, that [QD-981] dxFeed API: Parsing events from file sporadically fails
					// could have been reproduced by introducing a Thread.sleep at this point
					boolean more = true;
					try {
						RecordProvider provider = snapshot ? agents[i].getSnapshotProvider() : agents[i];
						more = provider.retrieve(buf);
					} finally {
						// if crashed, consider it as still available
						if (more) { // ... normally this happens when there's no more capacity in buffer
							setState(mask);
							return;
						}
					}
				}
			}
		}

		@Override
		public void recordsAvailable(RecordProvider provider) {
			// updates
			for (int i = 0; i < N_CONTRACTS; i++) {
				if (provider == agents[i]) {
					if (setState(1 << i))
						scheduleTaskIfNeeded(false);
					return;
				}
			}
			if (snapshotTask != null) {
				for (int i = 0; i < N_CONTRACTS; i++) {
					QDAgent agent = agents[i];
					if (agent != null && provider == agent.getSnapshotProvider()) {
						if (setState(1 << (i + N_CONTRACTS)))
							scheduleTaskIfNeeded(true);
						return;
					}
				}
			}
		}

		// This method is guarded by DXFeedSubscription monitor
		QDAgent getOrCreateAgent(QDContract contract) {
			QDAgent agent = agents[contract.ordinal()];
			if (agent != null)
				return agent;
			agent = eventProcessorAgentBuilders[contract.ordinal()].build();
			if (endpoint.getRole() == DXEndpoint.Role.STREAM_FEED)
				agent.setBufferOverflowStrategy(QDAgent.BufferOverflowStrategy.BLOCK);
			agents[contract.ordinal()] = agent;
			agent.setRecordListener(this);
			if (snapshotTask != null)
				agent.getSnapshotProvider().setRecordListener(this);
			return agent;
		}

		// This close method is never concurrent with getOrCreateAgent
		void closeAgents() {
			for (int i = 0; i < N_CONTRACTS; i++) {
				QDAgent agent = agents[i];
				if (agent != null)
					agent.close();
			}
		}

		// This closeAndExamineDataBySubscription method is never concurrent with getOrCreateAgent
		void closeAgentsAndExamineDataBySubscription(RecordBuffer buf) {
			for (int i = 0; i < N_CONTRACTS; i++) {
				QDAgent agent = agents[i];
				if (agent != null) {
					agent.closeAndExamineDataBySubscription(buf);
					clearDataInBuffer(buf, CONTRACTS[i] == QDContract.HISTORY);
				}
			}
		}

		void close(boolean clear) {
			if (clear) {
				RecordBuffer buf = RecordBuffer.getInstance();
				closeAgentsAndExamineDataBySubscription(buf);
				try {
					awaitTermination(); // make sure that any processing in progress completes
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt(); // reassert interruption flag
				}
				process(buf);
				buf.release();
			} else
				closeAgents();
		}

		private boolean hasMoreDataToProcess() {
			// [QD-981] dxFeed API: Parsing events from file sporadically fails
			// It is critical to check for both available & scheduled masks
			return (state.get() & (STATE_AVAILABLE_DATA_MASK | STATE_SCHEDULED_DATA)) != 0;
		}

		private void signalNoMoreDataToProcess() {
			if (TRACE_LOG)
				QDLog.log.trace("signalNoMoreDataToProcess on " + this);
			if (terminationLatch != null)
				terminationLatch.countDown();
		}

		void awaitTerminationAndClose() throws InterruptedException {
			awaitTermination();
			close(false);
		}

		private void awaitTermination() throws InterruptedException {
			terminationLatch = new CountDownLatch(1);
			if (hasMoreDataToProcess()) {
				if (TRACE_LOG)
					QDLog.log.trace("awaitTermination on " + this + " -- await");
				terminationLatch.await();
			} else {
				if (TRACE_LOG)
					QDLog.log.trace("awaitTermination on " + this + " -- no more data to process");
			}
		}

		@SuppressWarnings("unchecked")
		protected void process(RecordSource source) {
			events = new ArrayList<>();
			try {
				RecordCursor cursor;
				while ((cursor = source.next()) != null)
					EVENT_PROCESSOR_ATTACHMENT_STRATEGY.processEach(cursor, this); // will invoke processEvent
				if (events.isEmpty())
					return;
				processEvents(subscription, events);
			} finally {
				events = null;
			}
		}

		@SuppressWarnings("unchecked")
		void processWildcardEvent(RecordCursor cursor) {
			List<EventDelegate<?>> delegates = endpoint.getDelegateListByContractAndRecord(QDContract.STREAM, cursor.getRecord());
			if (delegates == null)
				return;
			for (EventDelegate<?> delegate : delegates) {
				if (subscription.containsEventType(delegate.getEventType())) {
					events.add((E)delegate.createEvent(cursor));
				}
			}
		}

		void processEvent(RecordCursor cursor, T symbol, EventDelegate<E> delegate) {
			events.add(delegate.createEvent(symbol, cursor));
		}
	}

	private static final class SymbolDelegate {
		final Object symbol;
		final EventDelegate<?> delegate;
		int count = 1;

		SymbolDelegate(Object symbol, EventDelegate<?> delegate) {
			this.symbol = symbol;
			this.delegate = delegate;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof SymbolDelegate)) return false;
			SymbolDelegate that = (SymbolDelegate)o;
			return symbol.equals(that.symbol) && delegate.equals(that.delegate);
		}

		@Override
		public int hashCode() {
			return 31 * symbol.hashCode() + delegate.hashCode();
		}
	}

	private static class EventProcessorAttachmentStrategy extends ArrayListAttachmentStrategy<SymbolDelegate, EventProcessor<?, ?>> {
		@SuppressWarnings({"unchecked", "rawtypes"})
		@Override
		public void process(RecordCursor cursor, SymbolDelegate attachment, EventProcessor ctx) {
			if (attachment == null)
				ctx.processWildcardEvent(cursor);
			else
				ctx.processEvent(cursor, attachment.symbol, attachment.delegate);
		}

		@Override
		protected boolean incrementCombines(SymbolDelegate attachment) {
			attachment.count++;
			return true;
		}

		@Override
		protected boolean decrementAndNotEmpty(SymbolDelegate attachment) {
			return --attachment.count > 0;
		}
	}

	private class LastEventPromise<E extends EventType<?>> extends Promise<E> {
		final Object symbol;
		final EventDelegate<E> delegate;
		final int cipher;
		final String qdSymbol;

		private volatile boolean subscribed; // set only when subscribed successfully and need to unsubscribe

		LastEventPromise(Object symbol, EventDelegate<E> delegate, int cipher, String qdSymbol) {
			this.symbol = symbol;
			this.delegate = delegate;
			this.cipher = cipher;
			this.qdSymbol = qdSymbol;
		}

		void subscribed() {
			subscribed = true;
			if (isDone()) // already done before "subscribed" was called -- cancel subscription immediately
				lastEventsProcessor.cancel(this);
		}

		@Override
		protected void handleDone(PromiseHandler<? super E> handler) {
			if (subscribed)
				lastEventsProcessor.cancel(this);
			executePromiseHandler(this, handler);
		}
	}

	private static class LastEventAttachmentStrategy extends ArrayListAttachmentStrategy<LastEventPromise<?>, LastEventsProcessor> {
		@SuppressWarnings({"unchecked", "rawtypes"})
		@Override
		public void process(RecordCursor cursor, LastEventPromise attachment, LastEventsProcessor ctx) {
			ctx.processEvent(cursor, attachment);
		}
	}

	private static class LocalAddBatch {
		final RecordCursor.Owner owner = RecordCursor.allocateOwner();
		RecordBuffer addSub;

		void subscribeStartBatch() {
			addSub = null;
		}

		// Must must invoked from LocalProcessor's thread only
		<E extends EventType<?>> void subscribeAddBatch(LastEventPromise<E> a) {
			if (addSub == null)
				addSub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION.withAttachment());
			addSub.add(a.delegate.getRecord(), a.cipher, a.qdSymbol).setAttachment(a);
		}

		// Must must invoked from LocalProcessor's thread only
		boolean completeAddSubBatch(LastEventsProcessor processor) {
			if (addSub == null)
				return true;
			if (!processor.addSubscription(addSub))
				return false;
			// on successful addSubscription register this LocalProcess with all PromiseSymbolDelegate instances
			addSub.rewind();
			RecordCursor cur;
			while ((cur = addSub.next()) != null) {
				LastEventPromise<?> a = (LastEventPromise<?>)cur.getAttachment();
				a.subscribed();
			}
			addSub.release();
			addSub = null;
			return true;
		}

	}

	private static class LocalRemoveBatch {
		RecordBuffer removeSub;
		LastEventsProcessor lastEventsProcessor;

		LocalRemoveBatch(LastEventsProcessor lastEventsProcessor) {
			this.removeSub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION.withAttachment());
			this.lastEventsProcessor = lastEventsProcessor;
		}

		void completeRemoveSubBatch() {
			if (!removeSub.isEmpty())
				lastEventsProcessor.tickerAgent.removeSubscription(removeSub);
			removeSub.release();
		}
	}

	private class LastEventsProcessor extends RecordProcessor {
		final QDTicker ticker;
		final QDAgent tickerAgent;

		LastEventsProcessor(QDTicker ticker) {
			super(endpoint.getOrCreateExecutor());
			this.ticker = ticker;
			tickerAgent = ticker.agentBuilder() // anonymous, so that JMX beans are not registered (see QD-445)
				.withAttachmentStrategy(LAST_EVENT_ATTACHMENT_STRATEGY)
				.build();
		}

		void start() {
			startProcessing(tickerAgent);
		}

		void close() {
			assert endpoint.isClosed(); // should have been already closed
			stopProcessing();
			tickerAgent.close();
		}

		boolean addSubscription(RecordBuffer sub) {
			if (endpoint.isClosed())
				return false;
			tickerAgent.addSubscription(sub);
			return true;
		}

		@Override
		protected synchronized void process(RecordSource source) {
			LocalRemoveBatch oldRemoveBatch = LOCAL_REMOVE_BATCH.get();
			LocalRemoveBatch removeBatch = oldRemoveBatch;
			if (removeBatch == null || removeBatch.lastEventsProcessor != this)
				LOCAL_REMOVE_BATCH.set(removeBatch = new LocalRemoveBatch(this));
			try {
				RecordCursor cursor;
				while ((cursor = source.next()) != null)
					LAST_EVENT_ATTACHMENT_STRATEGY.processEach(cursor, this); // will invoke processEvent
			} finally {
				if (removeBatch != oldRemoveBatch) {
					LOCAL_REMOVE_BATCH.set(oldRemoveBatch);
					// will do batch remove for all completed in here
					removeBatch.completeRemoveSubBatch();
				}
			}
		}

		// is invoked from under process(...) method in an arbitrary thread
		<E extends EventType<?>> void processEvent(RecordCursor cursor, LastEventPromise<E> promise) {
			if (promise.isDone())
				return; // quick bail out on duplicate / late processing
			promise.complete(promise.delegate.createEvent(promise.symbol, cursor)); // may invoke cancel method below
		}

		// is invoked when promise is done/canceled method in an arbitrary thread
		// maybe from inside process method, too
		<E extends EventType<?>> void cancel(LastEventPromise<E> a) {
			LocalRemoveBatch localRemoveBatch = LOCAL_REMOVE_BATCH.get();
			if (localRemoveBatch != null && localRemoveBatch.lastEventsProcessor == this) {
				// it is invoked from inside process(...). Batch all removals in this case
				localRemoveBatch.removeSub.add(a.delegate.getRecord(), a.cipher, a.qdSymbol).setAttachment(a);
				return;
			}
			// otherwise it is invoked separately -- remove just one item
			RecordBuffer removeSub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION.withAttachment());
			removeSub.add(a.delegate.getRecord(), a.cipher, a.qdSymbol).setAttachment(a);
			tickerAgent.removeSubscription(removeSub);
			removeSub.release();
		}
	}

	private interface Closeable {
		public void close();
	}

	private static class ResultList<E extends IndexedEvent<?>> extends AbstractIndexedList<E> {
		@Override
		protected long getIndex(E event) {
			return event.getIndex();
		}
	}
}
