package com.epam.pipeline.eventsourcing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.StreamMessageId;

import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class EventEngineTest {

    private static final int FROM_EVENT_ID = 15;
    private static final String HANDLER_ID = "testHandler";
    public static final String TEST = "test";

    final EventHandler eventHandler = new EventHandler() {
        @Override
        public String getId() {
            return HANDLER_ID;
        }

        @Override
        public String getApplicationId() {
            return "testApplication";
        }

        @Override
        public String getEventType() {
            return "test-event";
        }

        @Override
        public void handle(long eventId, Event event) {}
    };

    EventEngine eventEngine;

    @BeforeEach
    public void setUp() {
        eventEngine = new EventEngine(null, Executors.newScheduledThreadPool(1));
    }

    @Test
    public void afterEnablingHandlerItsFeatureIsStoredInInternalState() {
        eventEngine.enableHandler(TEST, FROM_EVENT_ID, eventHandler, 1, true);
        assertTrue(eventEngine.enabled.containsKey(eventHandler.getId()));
        final StreamMessageId streamMessageId = eventEngine.lastReadByHandler.get(eventHandler.getId());
        assertNotNull(streamMessageId);
        assertEquals(streamMessageId.getId0(), FROM_EVENT_ID);
    }

    @Test
    public void enablingHandleFailsIfAlreadyExistsIfNotForced() {
        assertFalse(eventEngine.enabled.containsKey(eventHandler.getId()));
        eventEngine.enableHandler(TEST, FROM_EVENT_ID, eventHandler, 1, false);
        assertTrue(eventEngine.enabled.containsKey(eventHandler.getId()));
        assertThrows(IllegalStateException.class,
            () -> eventEngine.enableHandler(TEST, FROM_EVENT_ID, eventHandler,
                    1, false));
    }

    @Test
    public void disablingHandlerRemovesHandler() {
        assertFalse(eventEngine.enabled.containsKey(eventHandler.getId()));
        assertFalse(eventEngine.lastReadByHandler.containsKey(eventHandler.getId()));

        eventEngine.enableHandler(TEST, FROM_EVENT_ID, eventHandler, 1, false);
        assertTrue(eventEngine.enabled.containsKey(eventHandler.getId()));
        assertTrue(eventEngine.lastReadByHandler.containsKey(eventHandler.getId()));

        eventEngine.disableHandler(eventHandler.getId());
        assertFalse(eventEngine.enabled.containsKey(eventHandler.getId()));
        assertFalse(eventEngine.lastReadByHandler.containsKey(eventHandler.getId()));
    }

    @Test
    public void enablingHandleDoesntFailIfAlreadyExistsAndForced() {
        assertFalse(eventEngine.enabled.containsKey(eventHandler.getId()));
        eventEngine.enableHandler(TEST, FROM_EVENT_ID, eventHandler, 1, false);
        assertTrue(eventEngine.enabled.containsKey(eventHandler.getId()));

        int eventFromId = FROM_EVENT_ID + 1;

        eventEngine.enableHandler(TEST, eventFromId, eventHandler, 1, true);
        final StreamMessageId streamMessageId = eventEngine.lastReadByHandler.get(eventHandler.getId());
        assertNotNull(streamMessageId);
        assertEquals(streamMessageId.getId0(), eventFromId);
    }

}