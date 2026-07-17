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

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.vo.platformusage.PlatformUsageCreditsEventFilterVO;
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditsUpdateEvent;
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditsUpdateRule;
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditsUpdateRuleType;
import com.epam.pipeline.vo.SecuredEntityVO;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.monitor.monitoring.MonitoringService;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import com.epam.pipeline.vo.PagingRunFilterVO;
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
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PipelineRunUsageCreditsMonitoringService implements MonitoringService {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final List<TaskStatus> ACTIVE_RUN_STATUSES = Arrays.asList(
            TaskStatus.RUNNING, TaskStatus.PAUSING, TaskStatus.PAUSED, TaskStatus.RESUMING);
    private static final List<TaskStatus> FINAL_RUN_STATUSES = Arrays.asList(
            TaskStatus.SUCCESS, TaskStatus.FAILURE, TaskStatus.STOPPED);
    private static final int FILE_READER_BLOCK_SIZE = 4096;

    private final String monitorEnabledPreferenceName;
    private final String lastExecutionFilePath;
    private final CloudPipelineAPIClient client;
    private final PlatformUsageCreditsUpdateRuleEvaluator evaluator;

    public PipelineRunUsageCreditsMonitoringService(
            @Value("${preference.name.platform.usage.credit.monitor.enable}")
                final String monitorEnabledPreferenceName,
            @Value("${platform.usage.credit.monitor.last.execution.file}")
                final String lastExecutionFilePath,
            final CloudPipelineAPIClient client,
            final PlatformUsageCreditsUpdateRuleEvaluator evaluator) {
        this.monitorEnabledPreferenceName = monitorEnabledPreferenceName;
        this.lastExecutionFilePath = lastExecutionFilePath;
        this.client = client;
        this.evaluator = evaluator;
    }

    @Override
    public void monitor() {
        if (!client.getBooleanPreference(monitorEnabledPreferenceName)) {
            log.debug("Platform usage credit monitor is not enabled");
            return;
        }

        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final LocalDateTime lastExecTime = readLastExecutionTime();

        final List<PlatformUsageCreditsUpdateRule> rules = client.loadAllPlatformUsageCreditsRules();
        if (rules.isEmpty()) {
            log.info("No platform usage credit rules found, skipping");
            writeLastExecutionTime(now);
            return;
        }

        final List<PipelineRun> runs = loadAllPlatformUsageCreditsRuns(lastExecTime);
        if (runs.isEmpty()) {
            log.info("No runs found for credit evaluation, skipping");
            writeLastExecutionTime(now);
            return;
        }

        final Map<String, List<PipelineRun>> runsByOwner = runs.stream()
                .collect(Collectors.groupingBy(PipelineRun::getOwner));

        final List<PlatformUsageCreditsUpdateEvent> newEvents = new ArrayList<>();

        for (final PlatformUsageCreditsUpdateRule rule : rules) {
            if (rule.getRuleType() != PlatformUsageCreditsUpdateRuleType.RUN_STATE) {
                continue;
            }
            for (final Map.Entry<String, List<PipelineRun>> entry : runsByOwner.entrySet()) {
                final String owner = entry.getKey();
                final List<PipelineRun> userRuns = entry.getValue();

                final PipelineUser user = client.loadUserByName(owner);
                if (user == null) {
                    log.warn("Cannot resolve user '{}', skipping", owner);
                    continue;
                }

                final List<PipelineRun> matchingRuns = userRuns.stream()
                        .filter(run -> evaluator.matches(rule, run, now))
                        .collect(Collectors.toList());
                if (matchingRuns.isEmpty()) {
                    continue;
                }

                final List<PlatformUsageCreditsUpdateEvent> existingEvents =
                        client.filterPlatformUsageCreditsEvents(
                                PlatformUsageCreditsEventFilterVO.builder()
                                        .entities(matchingRuns.stream()
                                                .map(r -> SecuredEntityVO.from(PipelineRun.class, r.getId()))
                                                .collect(Collectors.toList()))
                                        .ruleId(rule.getId())
                                        .build());

                final Set<Long> processedEntityIds = existingEvents.stream()
                        .map(PlatformUsageCreditsUpdateEvent::getEntity)
                        .filter(Objects::nonNull)
                        .map(SecuredEntityVO::getEntityId)
                        .collect(Collectors.toSet());

                final List<PipelineRun> newRuns = matchingRuns.stream()
                        .filter(r -> !processedEntityIds.contains(r.getId()))
                        .collect(Collectors.toList());
                if (newRuns.isEmpty()) {
                    continue;
                }

                newRuns.forEach(run ->
                        newEvents.add(PlatformUsageCreditsUpdateEvent.builder()
                                .userId(user.getId())
                                .ruleId(rule.getId())
                                .entity(SecuredEntityVO.from(PipelineRun.class, run.getId()))
                                .incidentType(rule.getAction().getType())
                                .value(rule.getAction().getValue())
                                .message(rule.getAction().getMessage())
                                .build()));
            }
        }

        if (!newEvents.isEmpty()) {
            client.savePlatformUsageCreditsEvents(newEvents);
        }

        writeLastExecutionTime(now);
    }

    private List<PipelineRun> loadAllPlatformUsageCreditsRuns(final LocalDateTime from) {
        final Map<Long, PipelineRun> result = new LinkedHashMap<>();
        final PagingRunFilterVO activeFilter = new PagingRunFilterVO();
        activeFilter.setStatuses(ACTIVE_RUN_STATUSES);
        client.filterRuns(activeFilter).forEach(r -> result.put(r.getId(), r));
        if (from != null) {
            final PagingRunFilterVO completedFilter = new PagingRunFilterVO();
            completedFilter.setStatuses(FINAL_RUN_STATUSES);
            completedFilter.setStartDateFrom(Date.from(from.toInstant(ZoneOffset.UTC)));
            client.filterRuns(completedFilter).forEach(r -> result.putIfAbsent(r.getId(), r));
        }
        return new ArrayList<>(result.values());
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
