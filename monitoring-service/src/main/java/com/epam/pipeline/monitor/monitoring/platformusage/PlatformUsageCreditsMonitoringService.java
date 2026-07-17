/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.monitor.monitoring.platformusage;

import com.epam.pipeline.entity.platformusage.PlatformUsageCreditsUpdateEvent;
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditsUpdateRule;
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditsUpdateRuleType;
import com.epam.pipeline.monitor.monitoring.MonitoringService;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PlatformUsageCreditsMonitoringService implements MonitoringService {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final int FILE_READER_BLOCK_SIZE = 4096;

    private final String monitorEnabledPreferenceName;
    private final String lastExecutionFilePath;
    private final CloudPipelineAPIClient client;
    private final Map<PlatformUsageCreditsUpdateRuleType, PlatformUsageCreditsRuleHandler> handlers;

    public PlatformUsageCreditsMonitoringService(
            @Value("${preference.name.platform.usage.credit.monitor.enable}")
                final String monitorEnabledPreferenceName,
            @Value("${platform.usage.credit.monitor.last.execution.file}")
                final String lastExecutionFilePath,
            final CloudPipelineAPIClient client,
            final List<PlatformUsageCreditsRuleHandler> handlers) {
        this.monitorEnabledPreferenceName = monitorEnabledPreferenceName;
        this.lastExecutionFilePath = lastExecutionFilePath;
        this.client = client;
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(PlatformUsageCreditsRuleHandler::getRuleType, Function.identity()));
    }

    @Override
    public void monitor() {
        if (!client.getBooleanPreference(monitorEnabledPreferenceName)) {
            log.debug("Platform usage credit monitor is not enabled");
            return;
        }

        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final LocalDateTime lastExecTime = readLastExecutionTime();
        log.info("Platform usage credit monitoring started, period: {} - {}",
                lastExecTime != null ? lastExecTime.format(DATE_FORMATTER) : "beginning",
                now.format(DATE_FORMATTER));

        final List<PlatformUsageCreditsUpdateRule> rules = client.loadAllPlatformUsageCreditsRules();
        if (rules.isEmpty()) {
            log.info("No platform usage credit rules found, skipping");
            writeLastExecutionTime(now);
            return;
        }
        log.info("Loaded {} platform usage credit rule(s)", rules.size());

        final Map<PlatformUsageCreditsUpdateRuleType, List<PlatformUsageCreditsUpdateRule>> rulesByType =
                rules.stream()
                        .filter(r -> r.getRuleType() != null)
                        .collect(Collectors.groupingBy(PlatformUsageCreditsUpdateRule::getRuleType));

        final List<PlatformUsageCreditsUpdateEvent> newEvents = new ArrayList<>();

        for (final Map.Entry<PlatformUsageCreditsUpdateRuleType,
                List<PlatformUsageCreditsUpdateRule>> entry : rulesByType.entrySet()) {
            final PlatformUsageCreditsRuleHandler handler = handlers.get(entry.getKey());
            if (handler == null) {
                log.warn("No handler registered for rule type {}, skipping", entry.getKey());
                continue;
            }
            log.info("Processing {} rule(s) of type {}", entry.getValue().size(), entry.getKey());
            newEvents.addAll(handler.process(entry.getValue(), lastExecTime, now));
        }

        log.info("Platform usage credit monitoring produced {} new event(s)", newEvents.size());
        if (!newEvents.isEmpty()) {
            log.info("Saving {} platform usage credit event(s)", newEvents.size());
            client.savePlatformUsageCreditsEvents(newEvents);
        }

        writeLastExecutionTime(now);
        log.info("Platform usage credit monitoring completed");
    }

    private LocalDateTime readLastExecutionTime() {
        try (ReversedLinesFileReader reader = new ReversedLinesFileReader(
                new File(lastExecutionFilePath), FILE_READER_BLOCK_SIZE, StandardCharsets.UTF_8)) {
            final String lastLine = reader.readLine();
            if (StringUtils.isBlank(lastLine)) {
                return null;
            }
            return LocalDateTime.parse(lastLine.trim(), DATE_FORMATTER);
        } catch (IOException e) {
            log.trace("Error reading last execution time file {}", lastExecutionFilePath, e);
            return null;
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse last execution time from {}", lastExecutionFilePath, e);
            return null;
        }
    }

    private void writeLastExecutionTime(final LocalDateTime time) {
        try {
            Files.write(Paths.get(lastExecutionFilePath),
                    (time.format(DATE_FORMATTER) + System.lineSeparator()).getBytes(),
                    StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } catch (IOException e) {
            log.error("Failed to write last execution time to {}", lastExecutionFilePath, e);
        }
    }
}
