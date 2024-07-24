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

@Slf4j
public class EventEngine {

    private final RedissonClient redissonClient;
    private final ScheduledExecutorService executorService;
    private final ConcurrentHashMap<String, StreamMessageId> lastReadByHandler;
    private final ConcurrentHashMap<String, Future<?>> enabled;

    public EventEngine(final String redisHost, final int redisPort) {
        this(redisHost, redisPort, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    }

    public EventEngine(final String redisHost, final int redisPort, final int threads) {
        this(redisHost, redisPort, Executors.newScheduledThreadPool(threads));
    }

    public EventEngine(final String redisHost, final int redisPort,
                       final ScheduledExecutorService executorService) {
        Config redissonConfig = new Config();
        redissonConfig.useSingleServer()
                .setAddress(String.format("redis://%s:%s", redisHost, redisPort));

        this.redissonClient = Redisson.create(redissonConfig);
        this.executorService = executorService;
        this.lastReadByHandler = new ConcurrentHashMap<>();
        this.enabled = new ConcurrentHashMap<>();
    }

    public void enableHandler(final String stream, final long messagePointer, final EventHandler eventHandler,
                              final int frequencyInSec, final boolean force) {

        if (!force) {
            throw new IllegalStateException(String.format(
                    "Handler %s already registered", eventHandler.getName()));
        }
        disableHandler(eventHandler);

        lastReadByHandler.put(eventHandler.getName(), calculateMessageToStartFrom(messagePointer));
        final ScheduledFuture<?> future = executorService.scheduleWithFixedDelay(() -> {
            final RStream<String, String> rStream = redissonClient.getStream(stream);
            rStream.read(
                    StreamReadArgs.greaterThan(lastReadByHandler.get(eventHandler.getName()))
                            .count(Integer.MAX_VALUE)
                            .timeout(Duration.ZERO)
            ).forEach((streamMessageId, data) -> {
                final Event event = Event.builder().data(data).build();
                try {
                    eventHandler.handle(event);
                    lastReadByHandler.put(eventHandler.getName(), streamMessageId);
                } catch (Exception e) {
                    log.error(String.format("Problem with accepting an event: %s", event), e);
                }
            });
        }, 0, frequencyInSec, TimeUnit.SECONDS);
        enabled.put(eventHandler.getName(), future);
    }

    public void disableHandler(final EventHandler eventHandler) {
        Optional.ofNullable(enabled.remove(eventHandler.getName())).ifPresent(future -> future.cancel(true));
    }

    public EventProducer registerProducer(final String id, final String type, final String stream) {
        return new StreamEventProducer(id, type, redissonClient.getStream(stream));
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
