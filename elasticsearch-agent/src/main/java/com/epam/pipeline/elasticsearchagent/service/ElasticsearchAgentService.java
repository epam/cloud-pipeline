/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.elasticsearchagent.service;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ElasticsearchAgentService {

    private static final String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_PATTERN);

    private final ExecutorService elasticsearchAgentThreadPool;
    private final Set<ElasticsearchSynchronizer> synchronizers;
    private final String lastSynchronizationTimeFilePath;

    public ElasticsearchAgentService(
            final ExecutorService elasticsearchAgentThreadPool,
            final Optional<Set<ElasticsearchSynchronizer>> synchronizers,
            final @Value("${sync.last.synchronization.file}") String lastSynchronizationTimeFilePath) {
        this.elasticsearchAgentThreadPool = elasticsearchAgentThreadPool;
        this.synchronizers = synchronizers.orElse(Collections.emptySet());
        this.lastSynchronizationTimeFilePath = lastSynchronizationTimeFilePath;
    }

    /**
     * Scheduled task to monitor changes in the database,
     * to retrieve and send data to Elasticsearch service
     * to indexes the data as documents inside Elasticsearch
     */
    @Scheduled(fixedDelayString = "${sync.scheduler.delay}")
    @Transactional(propagation = Propagation.REQUIRED)
    @SchedulerLock(name = "ElasticsearchAgentService_startElasticsearchAgent", lockAtMostForString = "PT3H")
    public void startElasticsearchAgent() {
        log.debug("Start scheduled database changes...");

        LocalDateTime lastSyncTime = getLastSyncTime();
        LocalDateTime syncStart = LocalDateTime.now(Clock.systemUTC());

        List<CompletableFuture<Void>> results = synchronizers.stream()
                .map(synchronizer -> CompletableFuture.runAsync(() -> {
                    LocalDateTime startSyncTime = LocalDateTime.now(Clock.systemUTC());
                    log.debug("Synchronizer {} starts work at {} ", synchronizer.getClass().getSimpleName(),
                            startSyncTime);
                    synchronizer.synchronize(lastSyncTime, syncStart);
                    log.debug("Synchronizer {} stops work at {}. Duration is {} seconds.",
                            synchronizer.getClass().getSimpleName(),
                            LocalDateTime.now(Clock.systemUTC()),
                            Duration.between(startSyncTime, LocalDateTime.now(Clock.systemUTC()))
                                    .abs()
                                    .getSeconds());
                }, elasticsearchAgentThreadPool)
                .exceptionally(throwable -> {
                    log.warn("Exception while trying to send data to Elasticsearch service", throwable);
                    log.debug("Synchronizer {} stops work at {}.",
                            synchronizer.getClass().getSimpleName(), LocalDateTime.now(Clock.systemUTC()));
                    return null;
                })).collect(Collectors.toList());
        try {
            CompletableFuture.allOf(results.toArray(new CompletableFuture[0])).get();
            Files.write(Paths.get(lastSynchronizationTimeFilePath),
                    (syncStart.toString() + System.lineSeparator()).getBytes(),
                    StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            log.debug("Finished synchronization...");
        } catch (IOException | InterruptedException | ExecutionException e) {
            log.error("An error occurred synchronization: {}", e.getMessage());
            log.error(e.getMessage(), e);
        }

        log.debug("Stop Elasticsearch agent.");
    }

    private LocalDateTime getLastSyncTime() {
        String lastTime = getLastLineFromFile();
        if (StringUtils.isEmpty(lastTime)) {
            return LocalDateTime.now(Clock.systemUTC());
        }
        try {
            return LocalDateTime.parse(lastTime, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            log.error(e.getMessage(), e);
            return LocalDateTime.now(Clock.systemUTC());
        }
    }

    private String getLastLineFromFile() {
        try (ReversedLinesFileReader linesFileReader = new ReversedLinesFileReader(
                new File(lastSynchronizationTimeFilePath), StandardCharsets.UTF_8)) {
            return linesFileReader.readLine();
        } catch (IOException e) {
            log.trace("Error reading file " + lastSynchronizationTimeFilePath, e);
            return "";
        }
    }
}
