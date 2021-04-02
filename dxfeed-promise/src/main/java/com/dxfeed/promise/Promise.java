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
package com.dxfeed.promise;

import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Result of a computation that will be completed normally or exceptionally in the future.
 *
 * <h3>Usage in API</h3>
 *
 * This class is designed to represent a promise to deliver certain result. If there is a service with
 * a synchronous method like this:
 * <pre>
 *     public T findSomething(Args args);</pre>
 * then this method should be represented in promise-oriented way like this:
 * <pre>
 *     public Promise&lt;T&gt; findSomethingPromise(Args args);</pre>
 *
 * <p>In this case, the call to {@code findSomething(args)} is semantically equivalent to
 * <code>findSomethingPromise(args).{@link #await() await}()</code>. There may be service-specific difference between
 * those two forms of invocation. If {@code findSomething} throws exception, then {@link #await() await}
 * wraps them into {@link PromiseException}. If {@code findSomething} consults some kind of local cache with subscription,
 * then it may be defined by the service to return immediately and only retrieve the result from the cache, while
 * {@code findSomethingPromise} is a proper form that makes potentially time-consuming call to a back end data provider.
 *
 * <h3>Usage in service implementations</h3>
 *
 * The basic implementation of {@code findSomethingPromise} via {@code findSomething} using an {@link Executor} for
 * background computation looks like this:
 *
 * <pre>
 *     public Promise&lt;T&gt; findSomethingPromise(Args args) {
 *         Promise&lt;T&gt; promise = new {@link #Promise() Promise}&lt;T&gt;();
 *         executor.{@link Executor#execute(Runnable) execute}(() -&gt; {
 *             try {
 *                 T result = findSomething(args);
 *                 promise.{@link #complete(Object) complete}(result);
 *             } catch (Throwable t) {
 *                 promise.{@link #completeExceptionally(Throwable) completeExceptionally}(t);
 *             }
 *         });
 *         return promise;
 *     }</pre>
 *
 * A more advanced implementation may also cancel pending and/or ongoing computation when client cancels the promise or
 * times out waiting for a promise to be completed.
 * The sketch of such implementation using an {@link ExecutorService} is:
 *
 * <pre>
 *     public Promise&lt;T&gt; findSomethingPromise(Args args) {
 *         Promise&lt;T&gt; promise = new {@link #Promise() Promise}&lt;T&gt;();
 *         Future&lt;?&gt; future = executor.{@link ExecutorService#submit(Runnable) submit}(() -&gt; {
 *             try {
 *                 T result = findSomething(args);
 *                 promise.{@link #complete(Object) complete}(result);
 *             } catch (Throwable t) {
 *                 promise.{@link #completeExceptionally(Throwable) completeExceptionally}(t);
 *             }
 *         });
 *         promise.whenDone(p -&gt; future.{@link Future#cancel(boolean) cancel}(true)); // true to interrupt running task
 *         return promise;
 *     }</pre>
 *
 * <h3>Usage in service clients</h3>
 *
 * The basic usage of a promise that is returned by some service is to wait for result like this:
 *
 * <pre>
 *     try {
 *         findSomething(args).{@link #await() await}();
 *         handleResult(result);
 *     } catch (Throwable t) {
 *         handleException(t);
 *     }</pre>
 *
 * Waiting with timeout is performed by replacing {@link #await() await} call with {@link #await(long, TimeUnit) await(timeout, unit)}.
 * The same handling can be performed in the service provider thread like this:
 *
 * <pre>
 *     findSomething(args).whenDone(promise -&gt; {
 *         if (promise.hasResult())
 *            handleResult(promise.getResult());
 *         else
 *            handleException(promise.getException());
 *     });</pre>
 *
 * <h3>Promise state</h3>
 *
 * Internally, promise can be in one the four states: initial, result, exception, and cancelled. A freshly created
 * promise is in initial state. Every other state if final and is transitioned to by a dedicated transition method as
 * shown in the below table.
 *
 * <table summary="">
 *     <tr>
 *        <th>What \ State</th>
 *        <th>Initial</th>
 *        <th>Result</th>
 *        <th>Exception</th>
 *        <th>Cancelled</th>
 *     </tr>
 *     <tr>
 *         <td>Transition method</td>
 *         <td>{@link #Promise() constructor}</td>
 *         <td>{@link #complete(Object) complete}</td>
 *         <td>{@link #completeExceptionally(Throwable) completeExceptionally}</td>
 *         <td>{@link #cancel() cancel}</td>
 *     </tr>
 *     <tr>
 *         <td>{@link #isDone() isDone}</td>
 *         <td>{@code false}</td>
 *         <td>{@code true}</td>
 *         <td>{@code true}</td>
 *         <td>{@code true}</td>
 *     </tr>
 *     <tr>
 *         <td>{@link #hasResult() hasResult}</td>
 *         <td>{@code false}</td>
 *         <td>{@code true}</td>
 *         <td>{@code false}</td>
 *         <td>{@code false}</td>
 *     </tr>
 *     <tr>
 *         <td>{@link #hasException() hasException}</td>
 *         <td>{@code false}</td>
 *         <td>{@code false}</td>
 *         <td>{@code true}</td>
 *         <td>{@code true}</td>
 *     </tr>
 *     <tr>
 *         <td>{@link #isCancelled() isCancelled}</td>
 *         <td>{@code false}</td>
 *         <td>{@code false}</td>
 *         <td>{@code false}</td>
 *         <td>{@code true}</td>
 *     </tr>
 *     <tr>
 *         <td>{@link #getResult() getResult}</td>
 *         <td>{@code null}</td>
 *         <td>{@code result}</td>
 *         <td>{@code null}</td>
 *         <td>{@code null}</td>
 *     </tr>
 *     <tr>
 *         <td>{@link #getException() getException}</td>
 *         <td>{@code null}</td>
 *         <td>{@code null}</td>
 *         <td>{@code exception}</td>
 *         <td>{@link CancellationException}</td>
 *     </tr>
 * </table>
 *
 * <h3>Threads and locks</h3>
 *
 * This class is thread-safe and can be used concurrently from multiple threads without external synchronization.
 *
 * <p>By default, {@link #whenDone(PromiseHandler) whenDone} notifications are performed in the same thread that
 * invoked the state-changing method like {@link #complete(Object) complete},
 * {@link #completeExceptionally(Throwable) completeExceptionally}, or {@link #cancel() cancel}.
 * <code>Promise</code> class can be extended to override protected {@link #handleDone(PromiseHandler) handleDone}
 * method to move notification to other threads, for example, or to do other kind of processing.
 *
 * <h3>Performance considerations</h3>
 *
 * This class is optimized for a case of a single attached {@link PromiseHandler}, while at the same time
 * supporting multiple invocations of {@link #whenDone(PromiseHandler) whenDone} at the cost of extra
 * memory consumption. Performance-sensitive service implementations that need to cancel their internal computation
 * on the promise cancel are encouraged to extend {@code Promise} class and override {@link #handleDone(PromiseHandler) handleDone} for
 * this purpose instead of using {@link #whenDone(PromiseHandler) whenDone}.
 *
 * <h3>Working with multiple promises</h3>
 *
 * See {@link Promises Promises} class for utility methods that combine multiple {@code Promise} instances to
 * facilitate batch processing and concurrent invocations.
 *
 * <h3>Design note</h3>
 *
 * This class is based on the {@link Future} and {@link CompletableFuture}. However, the methods
 * in this class are carefully selected to minimize the interface weight and the preference is given to methods
 * that do no throw checked exceptions. All the names of the methods are picked to avoid conflicts with
 * {@link CompletionStage}, so this class can be potentially enhanced to implement {@link CompletionStage}
 * while maintaining backwards compatibility.
 */
public class Promise<T> {
    private static final int STATE_INITIAL = 0;
    private static final int STATE_RESULT = 1;
    private static final int STATE_EXCEPTION = 2;
    private static final int STATE_CANCELED = 4;

    private volatile int state;
    private volatile T result;
    private volatile Throwable exception;

    private PromiseHandler<? super T> handler;

    /**
     * Creates promise in the initial state and without an executor for notifications.
     */
    public Promise() {}

    private Promise(T result) {
        this.state = STATE_RESULT;
        this.result = result;
    }

    private Promise(Throwable exception) {
        if (exception == null)
            throw new NullPointerException();
        this.state = STATE_EXCEPTION;
        this.exception = exception;
    }

    /**
     * Returns {@code true} when computation has
     * {@link #complete(Object) completed normally},
     * or {@link #completeExceptionally(Throwable) exceptionally},
     * or was {@link #cancel() cancelled}.
     * @return {@code true} when computation has completed.
     */
    public final boolean isDone() {
        return state != STATE_INITIAL;
    }

    /**
     * Returns {@code true} when computation has completed normally.
     * Use {@link #getResult()} method to get the result of the computation.
     * @return {@code true} when computation has completed normally.
     * @see #getResult()
     */
    public final boolean hasResult() {
        return state == STATE_RESULT;
    }

    /**
     * Returns {@code true} when computation has completed exceptionally or was cancelled.
     * Use {@link #getException()} method to get the exceptional outcome of the computation.
     * @return {@code true} when computation has completed exceptionally or was cancelled.
     */
    public final boolean hasException() {
        return (state & (STATE_EXCEPTION | STATE_CANCELED)) != 0;
    }

    /**
     * Returns {@code true} when computation was {@link #cancel() cancelled}.
     * Use {@link #getException()} method to get the corresponding {@link CancellationException}.
     * @return {@code true} when computation was cancelled.
     * @see #cancel()
     * @see #getException()
     */
    public final boolean isCancelled() {
        return state == STATE_CANCELED;
    }

    /**
     * Returns result of computation. If computation has no {@link #hasResult() result}, then
     * this method returns {@code null}.
     * @return result of computation.
     * @see #hasResult()
     */
    public final T getResult() {
        return result;
    }

    /**
     * Returns exceptional outcome of computation. If computation has no {@link #hasException() exception},
     * then this method returns {@code null}. If computation has completed exceptionally or was cancelled, then
     * the result of this method is not {@code null}.
     * If computation was {@link #isCancelled() cancelled}, then this method returns an
     * instance of {@link CancellationException}.
     *
     * @return exceptional outcome of computation.
     * @see #hasException()
     */
    public final Throwable getException() {
        return exception;
    }

    /**
     * Wait for computation to complete and return its result or throw an exception in case of exceptional completion.
     * If the wait is {@link Thread#interrupt() interrupted}, then the computation is cancelled,
     * the interruption flag on the current thread is set, and {@link CancellationException} is thrown.
     *
     * <p>This method waits forever. See {@link #await(long, TimeUnit) await(timeout, unit)} for a timed wait
     * when the result of this promise is required to proceed or
     * {@link #awaitWithoutException(long, TimeUnit) awaitWithoutException(timeout, unit)} when the result
     * is not required and the normal execution shall continue on timeout.
     *
     * @return result of computation.
     * @throws CancellationException if computation was cancelled.
     * @throws PromiseException if computation has completed exceptionally.
     */
    public final T await() {
        synchronized (this) {
            while (state == STATE_INITIAL)
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
        }
        return joinResult();
    }

    /**
     * Wait for computation to complete or timeout and return its result or throw an exception in case of exceptional completion or timeout.
     * If the wait is {@link Thread#interrupt() interrupted}, then the computation is {@link #cancel() cancelled},
     * the interruption flag on the current thread is set, and {@link CancellationException} is thrown.
     *
     * <p><b>If the wait times out, then the computation is {@link #cancel() cancelled} and {@link CancellationException} is thrown.</b>
     * Use this method in the code that must get and process the result of this promise, which is returned
     * by this method when it completes normally. When the result is not required and the normal execution shall
     * continue on timeout use {@link #awaitWithoutException(long, TimeUnit) awaitWithoutException}.
     *
     * @return result of computation.
     * @throws CancellationException if computation was cancelled or timed out.
     * @throws PromiseException if computation has completed exceptionally.
     */
    public final T await(long timeout, TimeUnit unit) {
        if (!awaitImpl(timeout, unit))
            cancelImpl("await timed out");
        return joinResult();
    }

    /**
     * Wait for computation to complete or timeout or throw an exception in case of exceptional completion.
     * If the wait is {@link Thread#interrupt() interrupted}, then the computation is {@link #cancel() cancelled},
     * the interruption flag on the current thread is set, and {@link CancellationException} is thrown.
     *
     * <p>If the wait times out, then the computation is {@link #cancel() cancelled} and this method returns {@code false}.
     * Use this method in the code that shall continue normal execution in case of timeout.
     *
     * @return {@code true} if the computation has completed normally; {@code false} when wait timed out.
     * @throws CancellationException if computation was cancelled.
     * @throws PromiseException if computation has completed exceptionally.
     */
    public final boolean awaitWithoutException(long timeout, TimeUnit unit) {
        if (!awaitImpl(timeout, unit)) {
            cancelImpl("awaitWithoutException timed out");
            return false;
        }
        joinResult(); // throws required exceptions
        return true;
    }

    /**
     * Cancels computation. This method does nothing if computation has already {@link #isDone() completed}.
     *
     * <p>If cancelled, then {@link #getException() getException} will return {@link CancellationException},
     * {@link #isDone() isDone}, {@link #isCancelled() isCancelled}, and {@link #hasException() hasException} will return {@code true},
     * all {@link PromiseHandler handlers} that were installed with {@link #whenDone(PromiseHandler) whenDone} method
     * are notified by
     * invoking their {@link PromiseHandler#promiseDone(Promise) promiseDone} method, and
     * all waiters on {@link #await() join} method throw {@link CancellationException}.
     */
    public final void cancel() {
        cancelImpl("cancel");
    }

    /**
     * Completes computation normally with a specified result.
     * This method does nothing if computation has already {@link #isDone() completed}
     * (normally, exceptionally, or was cancelled),
     *
     * <p>If completed, then {@link #getResult() getResult} will return the specified result,
     * {@link #isDone() isDone} and {@link #hasResult() hasResult} will return {@code true},
     * all {@link PromiseHandler handlers} that were installed with {@link #whenDone(PromiseHandler) whenDone} method
     * are notified by invoking their {@link PromiseHandler#promiseDone(Promise) promiseDone} method, and
     * all waiters on {@link #await() join} method return the result.
     *
     * @param result the result of computation.
     * @see #getResult()
     */
    public final void complete(T result) {
        PromiseHandler<? super T> handler;
        synchronized (this) {
            if (state != STATE_INITIAL)
                return;
            handler = this.handler;
            this.handler = null;
            this.result = result;
            this.state = STATE_RESULT;
            notifyAll();
        }
        handleDone(handler);
    }

    /**
     * Completes computation exceptionally with a specified exception.
     * This method does nothing if computation has already {@link #isDone() completed},
     * otherwise {@link #getException()} will return the specified exception.
     *
     * <p>If completed exceptionally, then {@link #getException() getException} will return the specified exception,
     * {@link #isDone() isDone} and {@link #hasException() hasException} will return {@code true},
     * all {@link PromiseHandler handlers} that were installed with {@link #whenDone(PromiseHandler) whenDone} method
     * are notified by invoking their {@link PromiseHandler#promiseDone(Promise) promiseDone} method, and
     * all waiters on {@link #await() join} method throw {@link PromiseException} wrapping this exception.
     *
     * @param exception the exception.
     * @throws NullPointerException if exception is null.
     * @see #getException()
     */
    public final void completeExceptionally(Throwable exception) {
        if (exception == null)
            throw new NullPointerException();
        PromiseHandler<? super T> handler;
        synchronized (this) {
            if (state != STATE_INITIAL)
                return;
            handler = this.handler;
            this.handler = null;
            this.exception = exception;
            this.state = STATE_EXCEPTION;
            notifyAll();
        }
        handleDone(handler);
    }

    /**
     * Registers a handler to be invoked exactly once when computation {@link #isDone() completes}.
     * The handler's {@link PromiseHandler#promiseDone(Promise) promiseDone} method
     * is invoked immediately when this computation has already completed,
     * otherwise it will be invoked <b>synchronously</b> in the future when computation
     * {@link #complete(Object) completes normally},
     * or {@link #completeExceptionally(Throwable) exceptionally},
     * or is {@link #cancel() cancelled} from the same thread that had invoked one of the completion methods.
     * Exceptions that are produced by the invocation of
     * {@link PromiseHandler#promiseDone(Promise) PromiseHandler.promiseDone} method
     * are caught and logged.
     *
     * @param handler the handler.
     * @throws NullPointerException if handler is null.
     */
    @SuppressWarnings("unchecked")
    public final void whenDone(PromiseHandler<? super T> handler) {
        if (handler == null)
            throw new NullPointerException();
        boolean done;
        synchronized (this) {
            done = state != STATE_INITIAL;
            if (!done) {
                if (this.handler == null)
                    this.handler = handler;
                else if (this.handler instanceof Handlers)
                    ((Handlers<T>) this.handler).add(handler);
                else
                    this.handler = new Handlers<>(this.handler, handler);
            }
        }
        if (done)
            handler.promiseDone(this);
    }

    /**
     * Registers a handler to be invoked asynchronously exactly once when computation {@link #isDone() completes}.
     *
     * <p>This method is a shortcut for the following code:
     * <pre>
     * {@link #whenDone(PromiseHandler) whenDone}(new PromiseHandler&lt;T&gt;() {
     *     public void promiseDone(Promise&lt;? extends T&gt; promise) {
     *         executor.execute(new Runnable() {
     *             public void run() {
     *                 handler.promiseDone(Promise.this);
     *             }
     *         });
     *     }
     * });
     * </pre>
     *
     * @param handler the handler.
     * @param executor the executor.
     * @throws NullPointerException if handler or executor is null.
     */
    public final void whenDoneAsync(final PromiseHandler<? super T> handler, final Executor executor) {
        if (handler == null || executor == null)
            throw new NullPointerException();
        whenDone(promise -> executor.execute(() -> handler.promiseDone(this)));
    }

    /**
     * Returns new promise that is {@link #complete(Object) completed} with a specified result.
     * @param result the result of computation.
     * @param <T> the result type.
     * @return new promise that is {@link #complete(Object) completed} with a specified result.
     */
    public static <T> Promise<T> completed(T result) {
        return new Promise<>(result);
    }

    /**
     * Returns new promise that is {@link #completeExceptionally(Throwable) completed exceptionally} with a
     * specified exception.
     * @param exception the exception.
     * @throws NullPointerException if exception is null.
     * @param <T> the result type.
     * @return new promise that is {@link #completeExceptionally(Throwable) completed exceptionally} with a
     *         specified exception.
     */
    public static <T> Promise<T> failed(Throwable exception) {
        return new Promise<>(exception);
    }

    // ----------------- protected methods ------------------

    /**
     * Invoked when promise is done. This implementation invokes
     * {@link PromiseHandler#promiseDone(Promise) PromiseHandler.promiseDone} on all handlers.
     * @param handler reference to installed handlers list or {@code null} when no handlers were set.
     */
    protected void handleDone(PromiseHandler<? super T> handler) {
        if (handler != null)
            handler.promiseDone(this);
    }

    // ----------------- private methods ------------------


    private boolean awaitImpl(long timeout, TimeUnit unit) {
        long originalWaitMillis = unit.toMillis(timeout);
        long remainingMillis = originalWaitMillis;
        synchronized (this) {
            while (remainingMillis > 0 && state == STATE_INITIAL) {
                long startWaitTime = System.currentTimeMillis();
                try {
                    wait(remainingMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                // safety measure: remaining wait time cannot be above original wait time
                remainingMillis = Math.min(originalWaitMillis,
                    remainingMillis - (System.currentTimeMillis() - startWaitTime));
            }
        }
        return remainingMillis > 0;
    }

    private T joinResult() {
        if (Thread.currentThread().isInterrupted())
            cancelImpl("interrupted"); // try to cancel
        // assert state != STATE_INITIAL
        switch (state) {
        case STATE_RESULT:
            return result;
        case STATE_CANCELED:
            throw (CancellationException) exception;
        case STATE_EXCEPTION:
            throw new PromiseException(exception);
        default:
            throw new AssertionError();
        }
    }

    void cancelImpl(String message) {
        PromiseHandler<? super T> handler;
        synchronized (this) {
            if (state != STATE_INITIAL)
                return;
            handler = this.handler;
            this.handler = null;
            this.exception = new CancellationException(message);
            this.state = STATE_CANCELED;
            notifyAll();
        }
        handleDone(handler);
    }

    private static class Handlers<T> implements PromiseHandler<T> {
        PromiseHandler<? super T>[] handlers;

        @SafeVarargs
        Handlers(PromiseHandler<? super T>... handlers) {
            this.handlers = handlers;
        }

        void add(PromiseHandler<? super T> handler) {
            int n = handlers.length;
            handlers = Arrays.copyOf(handlers, n + 1);
            handlers[n] = handler;
        }

        @Override
        public void promiseDone(Promise<? extends T> promise) {
            for (PromiseHandler<? super T> handler : handlers)
                handler.promiseDone(promise);
        }
    }
}
