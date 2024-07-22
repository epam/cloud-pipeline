/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.eventsourcing;

import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamReadArgs;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class EventSourcingEngine {

    private final RedissonClient redissonClient;
    private final ScheduledExecutorService executorService;
    private final ConcurrentHashMap<String, StreamMessageId> lastReadByHandler;
    private final ConcurrentHashMap<String, Future<?>> enabled;

    public EventSourcingEngine(final RedissonClient redissonClient) {
        this(redissonClient, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    }

    public EventSourcingEngine(final RedissonClient redissonClient, final int threads) {
        this(redissonClient, Executors.newScheduledThreadPool(threads));
    }

    public EventSourcingEngine(final RedissonClient redissonClient,
                               final ScheduledExecutorService executorService) {
        this.redissonClient = redissonClient;
        this.executorService = executorService;
        this.lastReadByHandler = new ConcurrentHashMap<>();
        this.enabled = new ConcurrentHashMap<>();
    }

    public void enableHandler(final String stream, final long messagePointer, final EventHandler eventHandler,
                              final int frequencyInSec, final boolean force) {

        final Future<?> previouslyConfigured = enabled.get(eventHandler.getName());
        if (previouslyConfigured != null) {
            if (!force) {
                throw new IllegalStateException(String.format(
                        "Handler %s already registered", eventHandler.getName()));
            }
            previouslyConfigured.cancel(true);
        }

        lastReadByHandler.put(eventHandler.getName(), calculateMessageToStartFrom(messagePointer));

        ScheduledFuture<?> future = executorService.scheduleWithFixedDelay(() ->
        {
            final RStream<String, String> rStream = redissonClient.getStream(stream);
            rStream.read(
                    StreamReadArgs.greaterThan(lastReadByHandler.get(eventHandler.getName()))
                            .count(Integer.MAX_VALUE)
                            .timeout(Duration.ZERO)
            ).forEach((streamMessageId, data) -> {
                eventHandler.handle(new Event(eventHandler.getEventType(), data));
                lastReadByHandler.put(eventHandler.getName(), streamMessageId);
            });
        }, frequencyInSec, frequencyInSec, TimeUnit.SECONDS);
        enabled.put(eventHandler.getName(), future);
    }

    public EventProducer registerProducer(final String stream) {
        final RStream<String, String> rStream = redissonClient.getStream(stream);
        return event -> {
            final HashMap<String, String> data = new HashMap<>(event.getData());
            data.put("eventType", event.getType());
            return rStream.add(StreamAddArgs.entries(data)).getId0();
        };
    }

    private static StreamMessageId calculateMessageToStartFrom(long messagePointer) {
        final StreamMessageId messageIdToStart;
        if (messagePointer == Long.MAX_VALUE) {
            messageIdToStart = StreamMessageId.NEWEST;
        } else if (messagePointer == 0) {
            messageIdToStart = StreamMessageId.ALL;
        } else {
            messageIdToStart = new StreamMessageId(messagePointer);
        }
        return messageIdToStart;
    }

}
