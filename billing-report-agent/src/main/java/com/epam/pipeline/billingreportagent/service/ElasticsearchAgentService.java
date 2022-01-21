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

package com.epam.pipeline.billingreportagent.service;

import com.epam.pipeline.entity.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    private final ExecutorService executor;
    private final Set<ElasticsearchDailySynchronizer> dailySynchronizers;
    private final Set<ElasticsearchMergingSynchronizer> mergingSynchronizers;
    private final String lastSynchronizationTimeFilePath;
    private final LocalDateTime billingStartDate;

    public ElasticsearchAgentService(final ExecutorService elasticsearchAgentThreadPool,
                                     final Optional<Set<ElasticsearchDailySynchronizer>> dailySynchronizers,
                                     final Optional<Set<ElasticsearchMergingSynchronizer>> mergingSynchronizers,
                                     final @Value("${sync.last.synchronization.file}")
                                         String lastSynchronizationTimeFilePath,
                                     final @Value("${sync.billing.initial.date:}") String startDateStringValue) {
        this.executor = elasticsearchAgentThreadPool;
        this.dailySynchronizers = dailySynchronizers.orElse(Collections.emptySet());
        this.mergingSynchronizers = mergingSynchronizers.orElse(Collections.emptySet());
        this.lastSynchronizationTimeFilePath = lastSynchronizationTimeFilePath;
        this.billingStartDate = Optional.of(startDateStringValue)
            .filter(StringUtils::isNotEmpty)
            .map(LocalDate::parse)
            .map(LocalDate::atStartOfDay)
            .orElse(null);
    }

    /**
     * Scheduled task to retrieve entities' execution stat and send it to Elasticsearch service to index the data as
     * documents inside Elasticsearch
     */
    @Scheduled(cron = "${sync.billing.schedule}")
    public void startElasticsearchAgent() {
        final LocalDateTime from = getLastSyncTime();
        final LocalDateTime to = DateUtils.nowUTC();
        try {
            CompletableFuture.runAsync(() -> log.debug("Start synchronising billing data..."), executor)
                    .thenComposeAsync(nothing -> sync(from, to), executor)
                    .thenComposeAsync(nothing -> merge(from, to), executor)
                    .thenRunAsync(() -> persistCurrentSyncTimestamp(to), executor)
                    .thenRunAsync(() -> log.debug("Finished billing data synchronization..."), executor)
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("An error occurred during synchronization: {}", e.getMessage(), e);
        }
        log.debug("Stop Elasticsearch agent.");
    }

    private LocalDateTime getLastSyncTime() {
        final String lastTime = getLastLineFromFile();
        if (StringUtils.isEmpty(lastTime)) {
            return billingStartDate;
        }
        try {
            return LocalDateTime.parse(lastTime, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            log.error(e.getMessage(), e);
            return billingStartDate;
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

    private CompletableFuture<Void> sync(final LocalDateTime from, final LocalDateTime to) {
        return CompletableFuture.runAsync(() -> log.debug("Starting SYNC phase..."), executor)
                .thenComposeAsync(nothing -> CompletableFuture.allOf(dailySynchronizers.stream()
                        .map(synchronizer -> sync(from, to, synchronizer))
                        .toArray(CompletableFuture[]::new)))
                .thenRunAsync(() -> log.debug("Finished SYNC phase."), executor);
    }

    private CompletableFuture<Void> sync(final LocalDateTime from, final LocalDateTime to,
                                         final ElasticsearchSynchronizer synchronizer) {
        return CompletableFuture.runAsync(() -> {
                    final LocalDateTime startSyncTime = DateUtils.nowUTC();
                    log.debug("Synchronizer {} starts work at {} ", synchronizer.name(), startSyncTime);
                    synchronizer.synchronize(from, to);
                    final LocalDateTime stopSyncTime = DateUtils.nowUTC();
                    log.debug("Synchronizer {} stops work at {}. Duration is {} seconds.",
                            synchronizer.name(),
                            stopSyncTime,
                            Duration.between(startSyncTime, stopSyncTime)
                                    .abs()
                                    .getSeconds());
                }, executor)
                .exceptionally(throwable -> {
                    log.warn("Synchronizer {} fails work at {}.", synchronizer.name(), DateUtils.nowUTC(), throwable);
                    return null;
                });
    }

    private CompletableFuture<Void> merge(final LocalDateTime from, final LocalDateTime to) {
        return mergingSynchronizers.stream()
                .collect(Collectors.groupingBy(ElasticsearchMergingSynchronizer::frame, Collectors.toList()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey(ElasticsearchMergingFrame.comparingByDuration()))
                .reduce(CompletableFuture.runAsync(() -> log.debug("Starting MERGE phase..."), executor),
                        (phase, entry) ->
                                phase.thenComposeAsync(nothing ->
                                        merge(from, to, entry.getValue(), entry.getKey()), executor),
                        CompletableFuture::allOf)
                .thenRunAsync(() -> log.debug("Finished MERGE phase."), executor);
    }

    private CompletableFuture<Void> merge(final LocalDateTime from, final LocalDateTime to,
                                          final List<ElasticsearchMergingSynchronizer> mergers,
                                          final ElasticsearchMergingFrame frame) {
        return CompletableFuture.runAsync(() -> log.debug("Starting MERGE {} stage...", frame), executor)
                .thenComposeAsync(nothing -> CompletableFuture.allOf(mergers.stream()
                        .map(synchronizer -> sync(from, to, synchronizer))
                        .toArray(CompletableFuture[]::new)))
                .thenRunAsync(() -> log.debug("Finished MERGE {} stage.", frame), executor);
    }

    private void persistCurrentSyncTimestamp(final LocalDateTime to) {
        try {
            Files.write(Paths.get(lastSynchronizationTimeFilePath),
                    (to.toString() + System.lineSeparator()).getBytes(),
                    StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } catch (IOException e) {
            log.error("An error occurred during current sync timestamp persisting: {}", e.getMessage(), e);
        }
    }
}
