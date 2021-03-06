/**
 * Copyright (C) 2014 Couchbase, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALING
 * IN THE SOFTWARE.
 */
package com.couchbase.client.core.env;

import com.couchbase.client.core.env.resources.ShutdownHook;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action0;
import rx.internal.schedulers.NewThreadWorker;
import rx.internal.schedulers.ScheduledAction;
import rx.internal.util.RxThreadFactory;
import rx.internal.util.SubscriptionList;
import rx.subscriptions.CompositeSubscription;
import rx.subscriptions.Subscriptions;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Core scheduler which is modeled after the Event Loops Scheduler (which is package private).
 *
 * @author Michael Nitschinger
 * @author Simon Baslé
 */
/*
 * Modelled after EventLoopScheduler in RxJava 1.0.15. Main aim is to change the thread naming and to allow for
 * a configurable size for the pool which is not capped at the number of CPUs.
 * Also aims at not depending on 1.0.15 new interface right away.
 */
//TODO when depending on RxJava 1.0.15+, this would implement SchedulerLifecycle instead
public class CoreScheduler extends Scheduler implements ShutdownHook {

    private static final String THREAD_NAME_PREFIX = "cb-computations-";
    private static final RxThreadFactory THREAD_FACTORY = new RxThreadFactory(THREAD_NAME_PREFIX);


    final AtomicReference<FixedSchedulerPool> pool;
    private final int poolSize;

    /** This will indicate no pool is active. */
    static final FixedSchedulerPool NONE = new FixedSchedulerPool(0);

    static final PoolWorker SHUTDOWN_WORKER;
    static {
        SHUTDOWN_WORKER = new PoolWorker(new RxThreadFactory("cb-computationShutdown-"));
        SHUTDOWN_WORKER.unsubscribe();
    }

    /**
     * Create a scheduler with specified pool size and using
     * least-recent worker selection policy.
     */
    public CoreScheduler(int poolSize) {
        this.poolSize = poolSize;
        this.pool = new AtomicReference<FixedSchedulerPool>(NONE);
        start();
    }

    //TODO when depending on RxJava 1.0.15+, this would be an @Override
    public void start() {
        FixedSchedulerPool update = new FixedSchedulerPool(poolSize);
        if (!pool.compareAndSet(NONE, update)) {
            update.shutdown();
        }
    }

    //TODO when depending on RxJava 1.0.15+, this would be an @Override of shutdown()
    public Observable<Boolean> shutdown() {
        for (;;) {
            FixedSchedulerPool curr = pool.get();
            if (curr == NONE) {
                return Observable.just(true);
            }
            if (pool.compareAndSet(curr, NONE)) {
                curr.shutdown();
                return Observable.just(true);
            }
        }
    }

    @Override
    public boolean isShutdown() {
        return pool.get() == NONE;
    }

    @Override
    public Worker createWorker() {
        return new EventLoopWorker(pool.get().getEventLoop());
    }

    static final class FixedSchedulerPool {
        final int size;

        final PoolWorker[] eventLoops;
        long n;

        FixedSchedulerPool(int poolSize) {
            // initialize event loops
            this.size = poolSize;
            this.eventLoops = new PoolWorker[size];
            for (int i = 0; i < size; i++) {
                this.eventLoops[i] = new PoolWorker(THREAD_FACTORY);
            }
        }

        public PoolWorker getEventLoop() {
            int c = size;
            if (c == 0) {
                return SHUTDOWN_WORKER;
            }
            // simple round robin, improvements to come
            return eventLoops[(int)(n++ % c)];
        }

        public void shutdown() {
            for (PoolWorker w : eventLoops) {
                w.unsubscribe();
            }
        }
    }

    /**
     * Schedules the action directly on one of the event loop workers
     * without the additional infrastructure and checking.
     * @param action the action to schedule
     * @return the subscription
     */
    public Subscription scheduleDirect(Action0 action) {
        PoolWorker pw = pool.get().getEventLoop();
        return pw.scheduleActual(action, -1, TimeUnit.NANOSECONDS);
    }

    private static class EventLoopWorker extends Scheduler.Worker {
        private final SubscriptionList serial = new SubscriptionList();
        private final CompositeSubscription timed = new CompositeSubscription();
        private final SubscriptionList both = new SubscriptionList(serial, timed);
        private final PoolWorker poolWorker;

        EventLoopWorker(PoolWorker poolWorker) {
            this.poolWorker = poolWorker;

        }

        @Override
        public void unsubscribe() {
            both.unsubscribe();
        }

        @Override
        public boolean isUnsubscribed() {
            return both.isUnsubscribed();
        }

        @Override
        public Subscription schedule(Action0 action) {
            if (isUnsubscribed()) {
                return Subscriptions.unsubscribed();
            }
            ScheduledAction s = poolWorker.scheduleActual(action, 0, null, serial);

            return s;
        }
        @Override
        public Subscription schedule(Action0 action, long delayTime, TimeUnit unit) {
            if (isUnsubscribed()) {
                return Subscriptions.unsubscribed();
            }
            ScheduledAction s = poolWorker.scheduleActual(action, delayTime, unit, timed);

            return s;
        }
    }

    private static final class PoolWorker extends NewThreadWorker {
        PoolWorker(ThreadFactory threadFactory) {
            super(threadFactory);
        }
    }

}
