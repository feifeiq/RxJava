/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rx.internal.operators;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import org.junit.*;
import org.mockito.*;

import rx.*;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Observer;
import rx.exceptions.TestException;
import rx.functions.*;
import rx.observers.*;
import rx.schedulers.TestScheduler;
import rx.subjects.*;

public class OperatorTimeoutTests {
    private PublishSubject<String> underlyingSubject;
    private TestScheduler testScheduler;
    private Observable<String> withTimeout;
    private static final long TIMEOUT = 3;
    private static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        underlyingSubject = PublishSubject.create();
        testScheduler = new TestScheduler();
        withTimeout = underlyingSubject.timeout(TIMEOUT, TIME_UNIT, testScheduler);
    }

    @Test
    public void shouldNotTimeoutIfOnNextWithinTimeout() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = withTimeout.subscribe(observer);
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onNext("One");
        verify(observer).onNext("One");
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        verify(observer, never()).onError(any(Throwable.class));
        subscription.unsubscribe();
    }

    @Test
    public void shouldNotTimeoutIfSecondOnNextWithinTimeout() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = withTimeout.subscribe(observer);
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onNext("One");
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onNext("Two");
        verify(observer).onNext("Two");
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        verify(observer, never()).onError(any(Throwable.class));
        subscription.unsubscribe();
    }

    @Test
    public void shouldTimeoutIfOnNextNotWithinTimeout() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = withTimeout.subscribe(observer);
        testScheduler.advanceTimeBy(TIMEOUT + 1, TimeUnit.SECONDS);
        verify(observer).onError(any(TimeoutException.class));
        subscription.unsubscribe();
    }

    @Test
    public void shouldTimeoutIfSecondOnNextNotWithinTimeout() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = withTimeout.subscribe(observer);
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onNext("One");
        verify(observer).onNext("One");
        testScheduler.advanceTimeBy(TIMEOUT + 1, TimeUnit.SECONDS);
        verify(observer).onError(any(TimeoutException.class));
        subscription.unsubscribe();
    }

    @Test
    public void shouldCompleteIfUnderlyingCompletes() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = withTimeout.subscribe(observer);
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onCompleted();
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        verify(observer).onCompleted();
        verify(observer, never()).onError(any(Throwable.class));
        subscription.unsubscribe();
    }

    @Test
    public void shouldErrorIfUnderlyingErrors() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = withTimeout.subscribe(observer);
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onError(new UnsupportedOperationException());
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        verify(observer).onError(any(UnsupportedOperationException.class));
        subscription.unsubscribe();
    }

    @Test
    public void shouldSwitchToOtherIfOnNextNotWithinTimeout() {
        Observable<String> other = Observable.just("a", "b", "c");
        Observable<String> source = underlyingSubject.timeout(TIMEOUT, TIME_UNIT, other, testScheduler);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = source.subscribe(observer);

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onNext("One");
        testScheduler.advanceTimeBy(4, TimeUnit.SECONDS);
        underlyingSubject.onNext("Two");
        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext("One");
        inOrder.verify(observer, times(1)).onNext("a");
        inOrder.verify(observer, times(1)).onNext("b");
        inOrder.verify(observer, times(1)).onNext("c");
        inOrder.verify(observer, times(1)).onCompleted();
        inOrder.verifyNoMoreInteractions();
        subscription.unsubscribe();
    }

    @Test
    public void shouldSwitchToOtherIfOnErrorNotWithinTimeout() {
        Observable<String> other = Observable.just("a", "b", "c");
        Observable<String> source = underlyingSubject.timeout(TIMEOUT, TIME_UNIT, other, testScheduler);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = source.subscribe(observer);

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onNext("One");
        testScheduler.advanceTimeBy(4, TimeUnit.SECONDS);
        underlyingSubject.onError(new UnsupportedOperationException());
        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext("One");
        inOrder.verify(observer, times(1)).onNext("a");
        inOrder.verify(observer, times(1)).onNext("b");
        inOrder.verify(observer, times(1)).onNext("c");
        inOrder.verify(observer, times(1)).onCompleted();
        inOrder.verifyNoMoreInteractions();
        subscription.unsubscribe();
    }

    @Test
    public void shouldSwitchToOtherIfOnCompletedNotWithinTimeout() {
        Observable<String> other = Observable.just("a", "b", "c");
        Observable<String> source = underlyingSubject.timeout(TIMEOUT, TIME_UNIT, other, testScheduler);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = source.subscribe(observer);

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onNext("One");
        testScheduler.advanceTimeBy(4, TimeUnit.SECONDS);
        underlyingSubject.onCompleted();
        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext("One");
        inOrder.verify(observer, times(1)).onNext("a");
        inOrder.verify(observer, times(1)).onNext("b");
        inOrder.verify(observer, times(1)).onNext("c");
        inOrder.verify(observer, times(1)).onCompleted();
        inOrder.verifyNoMoreInteractions();
        subscription.unsubscribe();
    }

    @Test
    public void shouldSwitchToOtherAndCanBeUnsubscribedIfOnNextNotWithinTimeout() {
        PublishSubject<String> other = PublishSubject.create();
        Observable<String> source = underlyingSubject.timeout(TIMEOUT, TIME_UNIT, other, testScheduler);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = source.subscribe(observer);

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onNext("One");
        testScheduler.advanceTimeBy(4, TimeUnit.SECONDS);
        underlyingSubject.onNext("Two");

        other.onNext("a");
        other.onNext("b");
        subscription.unsubscribe();

        // The following messages should not be delivered.
        other.onNext("c");
        other.onNext("d");
        other.onCompleted();

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext("One");
        inOrder.verify(observer, times(1)).onNext("a");
        inOrder.verify(observer, times(1)).onNext("b");
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void shouldTimeoutIfSynchronizedObservableEmitFirstOnNextNotWithinTimeout()
            throws InterruptedException {
        final CountDownLatch exit = new CountDownLatch(1);
        final CountDownLatch timeoutSetuped = new CountDownLatch(1);

        @SuppressWarnings("unchecked")
        final Observer<String> observer = mock(Observer.class);
        new Thread(new Runnable() {

            @Override
            public void run() {
                Observable.unsafeCreate(new OnSubscribe<String>() {

                    @Override
                    public void call(Subscriber<? super String> subscriber) {
                        try {
                            timeoutSetuped.countDown();
                            exit.await();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        subscriber.onNext("a");
                        subscriber.onCompleted();
                    }

                }).timeout(1, TimeUnit.SECONDS, testScheduler)
                        .subscribe(observer);
            }
        }).start();

        timeoutSetuped.await();
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onError(isA(TimeoutException.class));
        inOrder.verifyNoMoreInteractions();

        exit.countDown(); // exit the thread
    }

    @Test
    public void shouldUnsubscribeFromUnderlyingSubscriptionOnTimeout() throws InterruptedException {
        // From https://github.com/ReactiveX/RxJava/pull/951
        final Subscription s = mock(Subscription.class);

        Observable<String> never = Observable.unsafeCreate(new OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                subscriber.add(s);
            }
        });

        TestScheduler testScheduler = new TestScheduler();
        Observable<String> observableWithTimeout = never.timeout(1000, TimeUnit.MILLISECONDS, testScheduler);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        observableWithTimeout.subscribe(observer);

        testScheduler.advanceTimeBy(2000, TimeUnit.MILLISECONDS);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer).onError(isA(TimeoutException.class));
        inOrder.verifyNoMoreInteractions();

        verify(s, times(1)).unsubscribe();
    }

    @Test
    public void shouldUnsubscribeFromUnderlyingSubscriptionOnImmediatelyComplete() {
        // From https://github.com/ReactiveX/RxJava/pull/951
        final Subscription s = mock(Subscription.class);

        Observable<String> immediatelyComplete = Observable.unsafeCreate(new OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                subscriber.add(s);
                subscriber.onCompleted();
            }
        });

        TestScheduler testScheduler = new TestScheduler();
        Observable<String> observableWithTimeout = immediatelyComplete.timeout(1000, TimeUnit.MILLISECONDS,
                testScheduler);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        observableWithTimeout.subscribe(observer);

        testScheduler.advanceTimeBy(2000, TimeUnit.MILLISECONDS);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer).onCompleted();
        inOrder.verifyNoMoreInteractions();

        verify(s, times(1)).unsubscribe();
    }

    @Test
    public void shouldUnsubscribeFromUnderlyingSubscriptionOnImmediatelyErrored() throws InterruptedException {
        // From https://github.com/ReactiveX/RxJava/pull/951
        final Subscription s = mock(Subscription.class);

        Observable<String> immediatelyError = Observable.unsafeCreate(new OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                subscriber.add(s);
                subscriber.onError(new IOException("Error"));
            }
        });

        TestScheduler testScheduler = new TestScheduler();
        Observable<String> observableWithTimeout = immediatelyError.timeout(1000, TimeUnit.MILLISECONDS,
                testScheduler);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        observableWithTimeout.subscribe(observer);

        testScheduler.advanceTimeBy(2000, TimeUnit.MILLISECONDS);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer).onError(isA(IOException.class));
        inOrder.verifyNoMoreInteractions();

        verify(s, times(1)).unsubscribe();
    }

    @Test
    public void withDefaultScheduler() {

        TestSubscriber<Integer> ts = TestSubscriber.create();

        Observable.just(1).timeout(5, TimeUnit.SECONDS).subscribe(ts);

        ts.assertValue(1);
        ts.assertNoErrors();
        ts.assertCompleted();
    }

    @Test
    public void withSelector() {

        TestSubscriber<Integer> ts = TestSubscriber.create();

        Observable.just(1).timeout(new Func1<Integer, Observable<Object>>() {
            @Override
            public Observable<Object> call(Integer t) {
                return Observable.never();
            }
        }).subscribe(ts);

        ts.assertValue(1);
        ts.assertNoErrors();
        ts.assertCompleted();
    }

    @Test
    public void withSelectorAndDefault() {

        TestSubscriber<Integer> ts = TestSubscriber.create();

        Observable.just(1).timeout(new Func1<Integer, Observable<Object>>() {
            @Override
            public Observable<Object> call(Integer t) {
                return Observable.never();
            }
        }, Observable.just(2)).subscribe(ts);

        ts.assertValue(1);
        ts.assertNoErrors();
        ts.assertCompleted();
    }

    @Test
    public void withSelectorAndDefault2() {

        TestSubscriber<Integer> ts = TestSubscriber.create();

        Observable.just(1).concatWith(
        Observable.<Integer>never())
        .timeout(new Func1<Integer, Observable<Object>>() {
            @Override
            public Observable<Object> call(Integer t) {
                return Observable.just((Object)1);
            }
        }, Observable.just(2)).subscribe(ts);

        ts.assertValues(1, 2);
        ts.assertNoErrors();
        ts.assertCompleted();
    }

    @Test
    public void withDefaultSchedulerAndOther() {

        TestSubscriber<Integer> ts = TestSubscriber.create();

        Observable.just(1).timeout(5, TimeUnit.SECONDS, Observable.just(2)).subscribe(ts);

        ts.assertValue(1);
        ts.assertNoErrors();
        ts.assertCompleted();
    }

    @Test
    public void disconnectOnTimeout() {
        final List<String> list = Collections.synchronizedList(new ArrayList<String>());

        TestScheduler sch = new TestScheduler();

        Subject<Long, Long> subject = PublishSubject.create();
        Observable<Long> initialObservable = subject.share()
        .map(new Func1<Long, Long>() {
            @Override
            public Long call(Long value) {
                list.add("Received value " + value);
                return value;
            }
        });

        Observable<Long> timeoutObservable = initialObservable
        .map(new Func1<Long, Long>() {
            @Override
            public Long call(Long value) {
               list.add("Timeout received value " + value);
               return value;
            }
        });

        TestSubscriber<Long> subscriber = new TestSubscriber<Long>();
        initialObservable
        .doOnUnsubscribe(new Action0() {
            @Override
            public void call() {
                list.add("Unsubscribed");
            }
        })
        .timeout(1, TimeUnit.SECONDS, timeoutObservable, sch).subscribe(subscriber);

        subject.onNext(5L);

        sch.advanceTimeBy(2, TimeUnit.SECONDS);

        subject.onNext(10L);
        subject.onCompleted();

        subscriber.awaitTerminalEvent();
        subscriber.assertNoErrors();
        subscriber.assertValues(5L, 10L);

        assertEquals(Arrays.asList(
                "Received value 5",
                "Unsubscribed",
                "Received value 10",
                "Timeout received value 10"
        ), list);
    }

    @Test
    public void fallbackIsError() {
        TestScheduler sch = new TestScheduler();

        AssertableSubscriber<Object> as = Observable.never()
                .timeout(1, TimeUnit.SECONDS, Observable.error(new TestException()), sch)
        .test();

        sch.advanceTimeBy(1, TimeUnit.SECONDS);

        as.assertFailure(TestException.class);
    }
}
