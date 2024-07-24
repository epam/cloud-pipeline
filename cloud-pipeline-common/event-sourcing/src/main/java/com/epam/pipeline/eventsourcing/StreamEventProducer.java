package com.epam.pipeline.eventsourcing;

import lombok.AllArgsConstructor;
import org.redisson.api.RStream;
import org.redisson.api.stream.StreamAddArgs;

import java.util.HashMap;

@AllArgsConstructor
public class StreamEventProducer implements EventProducer{

    private final String id;
    private final String type;
    private final RStream<String, String> stream;

    @Override
    public String getName() {
        return id;
    }

    @Override
    public String getEventType() {
        return type;
    }

    @Override
    public long put(Event event) {
        final HashMap<String, String> data = new HashMap<>(event.getData());
        return stream.add(StreamAddArgs.entries(data)).getId0();
    }

}
