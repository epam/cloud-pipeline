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

package com.epam.pipeline.monitor.monitoring.credits;

import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateEvent;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateRule;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateRuleType;
import com.epam.pipeline.monitor.monitoring.MonitoringService;
import com.epam.pipeline.monitor.monitoring.credits.handler.PlatformUsageCreditsRuleHandler;
import com.epam.pipeline.monitor.monitoring.utils.ExecutionTimestampFile;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PlatformUsageCreditsMonitoringService implements MonitoringService {

    private static final String CREDITS_MODE_OFF = "OFF";

    private final CloudPipelineAPIClient client;
    private final String monitorEnabledPreference;
    private final String creditsModePreference;
    private final ExecutionTimestampFile executionTimestampFile;
    private final Map<PlatformUsageCreditsUpdateRuleType, PlatformUsageCreditsRuleHandler> handlers;

    public PlatformUsageCreditsMonitoringService(
            @Value("${preference.name.platform.usage.credits.monitor.enable:monitoring.platform.usage.credits.enable}")
                final String monitorEnabledPreference,
            @Value("${preference.name.platform.usage.credits.mode:platform.usage.credits.mode}")
                final String creditsModePreference,
            @Value("${platform.usage.credits.monitor.execution.timestamp.file}")
                final String lastExecutionFilePath,
            final CloudPipelineAPIClient client,
            final List<PlatformUsageCreditsRuleHandler> handlers) {
        this.monitorEnabledPreference = monitorEnabledPreference;
        this.creditsModePreference = creditsModePreference;
        this.executionTimestampFile = new ExecutionTimestampFile(lastExecutionFilePath);
        this.client = client;
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(PlatformUsageCreditsRuleHandler::getRuleType, Function.identity()));
    }

    @Override
    public void monitor() {
        if (!client.getBooleanPreference(monitorEnabledPreference)) {
            log.debug("Platform usage credits monitor is not enabled");
            return;
        }
        if (CREDITS_MODE_OFF.equalsIgnoreCase(client.getStringPreference(creditsModePreference))) {
            log.info("Skipping platform usage credits monitoring: credits feature is disabled (mode=OFF)");
            return;
        }

        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final LocalDateTime lastExecTime = executionTimestampFile.read();
        log.info("Platform usage credits monitoring started.");

        final List<PlatformUsageCreditsUpdateRule> rules = client.loadAllPlatformUsageCreditsRules();
        if (rules.isEmpty()) {
            log.info("No platform usage credits rules found, skipping");
            executionTimestampFile.write(now);
            return;
        }
        log.info("Loaded {} platform usage credits rule(s)", rules.size());

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

        log.info("Platform usage credits monitoring produced {} new event(s)", newEvents.size());
        if (!newEvents.isEmpty()) {
            log.info("Saving {} platform usage credits event(s)", newEvents.size());
            client.savePlatformUsageCreditsEvents(newEvents);
        }

        executionTimestampFile.write(now);
        log.info("Platform usage credits monitoring completed");
    }

}
