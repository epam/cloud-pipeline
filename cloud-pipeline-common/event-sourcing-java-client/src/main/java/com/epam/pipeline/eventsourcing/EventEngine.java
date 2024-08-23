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

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamReadArgs;
import org.redisson.config.Config;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * EventEngine is a central object to start working with event bus.
 * Enables possibility to publish and handle Redis Streams messages
 * <a href="https://redis.io/docs/latest/develop/data-types/streams/">Redis Streams</a>.
 * */
@Slf4j
public final class EventEngine {

    private final RedissonClient redissonClient;
    private final ScheduledExecutorService executorService;

    final ConcurrentHashMap<String, StreamMessageId> lastReadByHandler;
    final ConcurrentHashMap<String, Future<?>> enabled;

    public EventEngine(final String redisHost, final int redisPort, final int threads,
                       final int redissonThreads, final int redissonNettyThreads) {
        this(redisHost, redisPort, Executors.newScheduledThreadPool(threads),
                redissonThreads, redissonNettyThreads);
    }

    public EventEngine(final String redisHost, final int redisPort,
                       final ScheduledExecutorService executorService,
                       final int redissonThreads, final int redissonNettyThreads) {
        Config redissonConfig = new Config();
        redissonConfig
                .setThreads(redissonThreads)
                .setNettyThreads(redissonNettyThreads)
                .useSingleServer()
                .setAddress(String.format("redis://%s:%s", redisHost, redisPort));

        this.redissonClient = Redisson.create(redissonConfig);
        this.executorService = executorService;
        this.lastReadByHandler = new ConcurrentHashMap<>();
        this.enabled = new ConcurrentHashMap<>();
    }

    public EventEngine(final RedissonClient redissonClient,
                       final ScheduledExecutorService executorService) {
        this.redissonClient = redissonClient;
        this.executorService = executorService;
        this.lastReadByHandler = new ConcurrentHashMap<>();
        this.enabled = new ConcurrentHashMap<>();
    }

    /**
     * Enables {@param eventHandler} {@link EventHandler} to receive only newly published event
     * from the {@link RStream} with name {@param stream}.
     * -
     * {@param frequencyInMills} period in mills for {@link ScheduledExecutorService} to run polling task
     * {@param force} if set to true, this method will enable provided handler and remove another one,
     *                with the same name if already exists.
     *                if false, and handler with the same name already registered,
     *                method will throw an {@link IllegalStateException}
     * */
    public void enableHandlerFromNow(final String stream, final EventHandler eventHandler,
                                     final int frequencyInMills, final boolean force) {
        enableHandler(stream, Long.MAX_VALUE, eventHandler, frequencyInMills, force);
    }

    /**
     * Enables {@param eventHandler} {@link EventHandler} to receive published event,
     * starting from {@param fromEventId} from the {@link RStream} with name {@param stream}.
     * -
     * {@param frequencyInMills} period in seconds for {@link ScheduledExecutorService} to run polling task
     * {@param force} if set to true, this method will enable provided handler and remove another one,
     *                with the same name if already exists.
     *                if false, and handler with the same name already registered,
     *                method will throw an {@link IllegalStateException}
     * */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void enableHandler(final String stream, final long fromEventId,
                              final EventHandler eventHandler, final int frequencyInMills,
                              final boolean force) {
        log.debug("Enabling event handler {}...", eventHandler.getId());

        if (enabled.containsKey(eventHandler.getId()) && !force) {
            throw new IllegalStateException(String.format(
                    "Handler %s already registered", eventHandler.getId()));
        }
        disableHandler(eventHandler.getId());

        lastReadByHandler.put(eventHandler.getId(), calculateMessageToStartFrom(fromEventId));
        final ScheduledFuture<?> future = executorService.scheduleWithFixedDelay(() -> {
            final RStream<String, String> rStream = redissonClient.getStream(stream);
            rStream.read(
                    StreamReadArgs.greaterThan(lastReadByHandler.get(eventHandler.getId()))
                            .count(Integer.MAX_VALUE)
                            .timeout(Duration.ZERO)
            ).forEach((streamMessageId, data) -> {
                final Event event = Event.fromRawData(data);
                try {
                    eventHandler.handle(streamMessageId.getId0(), event);
                    lastReadByHandler.put(eventHandler.getId(), streamMessageId);
                } catch (Exception e) {
                    log.error(String.format("Problem with accepting an event: %s", event), e);
                }
            });
        }, 0, frequencyInMills, TimeUnit.MILLISECONDS);
        enabled.put(eventHandler.getId(), future);
    }

    /**
     * Removes {@param eventHandler} with id {@param eventHandlerId} from receivers of published event
     * */
    public void disableHandler(final String eventHandlerId) {
        log.debug("Disabling event handler {}...", eventHandlerId);
        lastReadByHandler.remove(eventHandlerId);
        Optional.ofNullable(enabled.remove(eventHandlerId)).ifPresent(future -> future.cancel(true));
    }

    /**
     * Enables and returns {@link EventProducer} with id {@param id} to publish events
     * to the stream with name {@param stream}.
     * -
     * {@param applicationId} can be used to mark event with sign of application instance
     *                        which is publishing these events.
     *                        Can be useful when user need to skip from handling events in the {@link EventHandler}
     *                        from the same application.
     * */
    public EventProducer enableProducer(final String id, final String applicationId,
                                        final String type, final String stream) {
        log.debug("Enabling event producer {}...", id);
        return new SingleStreamEventProducer(id, applicationId, type, redissonClient.getStream(stream));
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
