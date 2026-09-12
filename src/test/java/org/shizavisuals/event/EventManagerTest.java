package org.shizavisuals.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dispatch semantics of {@link EventManager}: priority ordering (higher
 * priority runs first), unsubscribe, and cancellation cutting off
 * lower-priority listeners.
 *
 * <p>Listener and event classes are public with public methods because
 * invokers are generated reflectively (see
 * org.shizavisuals.event.AsmEventInvokerFactory).</p>
 */
public class EventManagerTest {

    public static class TestEvent extends Event {
    }

    public static class TestCancellableEvent extends CancellableEvent {
    }

    public static class OrderedListener {
        public final List<String> calls = new ArrayList<>();

        @EventTarget(priority = 100)
        public void first(TestEvent event) {
            calls.add("first");
        }

        @EventTarget
        public void middle(TestEvent event) {
            calls.add("middle");
        }

        @EventTarget(priority = -100)
        public void last(TestEvent event) {
            calls.add("last");
        }
    }

    public static class CancellingListener {
        public final List<String> calls = new ArrayList<>();

        @EventTarget(priority = 50)
        public void cancels(TestCancellableEvent event) {
            calls.add("cancels");
            event.cancel();
        }

        @EventTarget(priority = -50)
        public void neverReached(TestCancellableEvent event) {
            calls.add("neverReached");
        }
    }

    public static class PassiveListener {
        public final List<String> calls = new ArrayList<>();

        @EventTarget(priority = -75)
        public void onCancellable(TestCancellableEvent event) {
            calls.add("passive");
        }
    }

    @AfterEach
    void cleanUp() {
        EventManager.unregisterAll();
    }

    @Test
    void listenersRunInPriorityOrderHighestFirst() {
        OrderedListener listener = new OrderedListener();
        EventManager.subscribe(listener);

        EventManager.call(new TestEvent());

        assertEquals(List.of("first", "middle", "last"), listener.calls);
    }

    @Test
    void callCompletesEventAndReturnsIt() {
        TestEvent event = new TestEvent();
        TestEvent returned = EventManager.call(event);

        assertEquals(event, returned);
        assertTrue(event.isCompleted());
        assertFalse(event.isCancelled());
    }

    @Test
    void unsubscribeStopsDelivery() {
        OrderedListener listener = new OrderedListener();
        EventManager.subscribe(listener);

        EventManager.call(new TestEvent());
        assertEquals(3, listener.calls.size(), "subscribed listener must receive the event");

        EventManager.unsubscribe(listener);
        EventManager.call(new TestEvent());
        assertEquals(3, listener.calls.size(), "unsubscribed listener must not receive further events");
        assertFalse(EventManager.hasListeners(TestEvent.class));
    }

    @Test
    void cancellationStopsLowerPriorityListeners() {
        CancellingListener canceller = new CancellingListener();
        PassiveListener passive = new PassiveListener();
        EventManager.subscribe(canceller);
        EventManager.subscribe(passive);

        TestCancellableEvent event = EventManager.call(new TestCancellableEvent());

        assertTrue(event.isCancelled());
        assertEquals(List.of("cancels"), canceller.calls,
                "the same listener's lower-priority method must not run after cancel");
        assertTrue(passive.calls.isEmpty(),
                "other lower-priority listeners must not run after cancel");
    }

    @Test
    void nonCancelledEventReachesAllListeners() {
        PassiveListener passive = new PassiveListener();
        EventManager.subscribe(passive);

        TestCancellableEvent event = EventManager.call(new TestCancellableEvent());

        assertFalse(event.isCancelled());
        assertEquals(List.of("passive"), passive.calls);
    }

    @Test
    void doubleSubscribeDeliversOnce() {
        OrderedListener listener = new OrderedListener();
        EventManager.subscribe(listener);
        EventManager.subscribe(listener);

        EventManager.call(new TestEvent());

        assertEquals(List.of("first", "middle", "last"), listener.calls);
    }
}
