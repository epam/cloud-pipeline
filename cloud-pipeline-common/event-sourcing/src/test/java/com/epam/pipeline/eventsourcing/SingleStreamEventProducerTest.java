package com.epam.pipeline.eventsourcing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RStream;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddParams;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SingleStreamEventProducerTest {

    public static final String PRODUCER_ID = "testId";
    public static final String APP_TEST_ID = "appTestId";
    public static final String EVENT_TYPE = "testType";

    EventProducer eventProducer;

    @Spy
    RStream<String, String> mockedStream;

    @Captor
    ArgumentCaptor<StreamAddParams<String, String>> dataCapture;

    @BeforeEach
    public void setUp() {
        Mockito.when(mockedStream.add(Mockito.any())).thenReturn(StreamMessageId.MIN);
        eventProducer = new SingleStreamEventProducer(PRODUCER_ID, APP_TEST_ID, EVENT_TYPE, mockedStream);
    }

    @Test
    public void producerAddsIdAndApplicationIdToTheEvent() {
        eventProducer.put(Collections.singletonMap("key", "value"));

        final Map<String, String> expected = new HashMap<String, String>() {{
            put("key", "value");
            put(Event.APPLICATION_ID_FIELD, APP_TEST_ID);
            put(Event.EVENT_TYPE_FIELD, EVENT_TYPE);
        }};

        Mockito.verify(mockedStream).add(dataCapture.capture());
        assertEquals(expected, dataCapture.getValue().getEntries());
    }
}