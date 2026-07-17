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
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import com.epam.pipeline.vo.PagingRunFilterVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PipelineRunUsageCreditsRuleHandler implements PlatformUsageCreditsRuleHandler {

    private static final List<TaskStatus> ACTIVE_RUN_STATUSES = Arrays.asList(
            TaskStatus.RUNNING, TaskStatus.PAUSING, TaskStatus.PAUSED, TaskStatus.RESUMING);
    private static final List<TaskStatus> FINAL_RUN_STATUSES = Arrays.asList(
            TaskStatus.SUCCESS, TaskStatus.FAILURE, TaskStatus.STOPPED);

    private final CloudPipelineAPIClient client;
    private final PlatformUsageCreditsUpdateRuleEvaluator evaluator;

    public PipelineRunUsageCreditsRuleHandler(final CloudPipelineAPIClient client,
                                              final PlatformUsageCreditsUpdateRuleEvaluator evaluator) {
        this.client = client;
        this.evaluator = evaluator;
    }

    @Override
    public PlatformUsageCreditsUpdateRuleType getRuleType() {
        return PlatformUsageCreditsUpdateRuleType.RUN_STATE;
    }

    @Override
    public List<PlatformUsageCreditsUpdateEvent> process(final List<PlatformUsageCreditsUpdateRule> rules,
                                                          final LocalDateTime from, final LocalDateTime now) {
        final List<PipelineRun> runs = loadAllPlatformUsageCreditsRuns(from);
        if (runs.isEmpty()) {
            log.info("No runs found for credit evaluation, skipping");
            return Collections.emptyList();
        }

        final Map<String, List<PipelineRun>> runsByOwner = runs.stream()
                .collect(Collectors.groupingBy(PipelineRun::getOwner));
        log.info("Evaluating {} RUN_STATE rule(s) against {} run(s) across {} user(s)",
                rules.size(), runs.size(), runsByOwner.size());

        final List<PlatformUsageCreditsUpdateEvent> newEvents = new ArrayList<>();

        for (final PlatformUsageCreditsUpdateRule rule : rules) {
            log.debug("Processing rule '{}' (id={})", rule.getName(), rule.getId());
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
                    log.debug("Rule '{}': no matching runs for user '{}'", rule.getName(), owner);
                    continue;
                }

                final Set<Long> processedRuns = buildProcessedRunIds(rule, user, matchingRuns);

                if (rule.getAction().isPerIncident()) {
                    matchingRuns.stream()
                            .filter(r -> !processedRuns.contains(r.getId()))
                            .forEach(run -> newEvents.add(buildEvent(rule, user, run)));
                    log.debug("Rule '{}': {} matching run(s) for user '{}', {} already processed",
                            rule.getName(), matchingRuns.size(), owner, processedRuns.size());
                } else if (processedRuns.isEmpty()) {
                    newEvents.add(buildEvent(rule, user, matchingRuns.get(0)));
                    log.debug("Rule '{}': firing single event for user '{}'", rule.getName(), owner);
                } else {
                    log.debug("Rule '{}': already fired for user '{}', skipping", rule.getName(), owner);
                }
            }
        }

        log.info("RUN_STATE handler produced {} new event(s)", newEvents.size());
        return newEvents;
    }

    private Set<Long> buildProcessedRunIds(final PlatformUsageCreditsUpdateRule rule,
                                           final PipelineUser user,
                                           final List<PipelineRun> matchingRuns) {
        PlatformUsageCreditsEventFilterVO filter;
        if (rule.getAction().isPerIncident()) {
            filter = PlatformUsageCreditsEventFilterVO.builder()
                    .entities(matchingRuns.stream()
                            .map(r -> SecuredEntityVO.from(PipelineRun.class, r.getId()))
                            .collect(Collectors.toList()))
                    .ruleId(rule.getId())
                    .page(1)
                    .pageSize(matchingRuns.size())
                    .build();
        } else {
            filter = PlatformUsageCreditsEventFilterVO.builder()
                    .userIds(Collections.singletonList(user.getId()))
                    .ruleId(rule.getId())
                    .page(1)
                    .pageSize(matchingRuns.size())
                    .build();
        }
        final List<PlatformUsageCreditsUpdateEvent> existing = client.filterPlatformUsageCreditsEvents(filter);
        return existing.stream()
                .map(PlatformUsageCreditsUpdateEvent::getEntity)
                .filter(Objects::nonNull)
                .map(SecuredEntityVO::getEntityId)
                .collect(Collectors.toSet());
    }

    private PlatformUsageCreditsUpdateEvent buildEvent(final PlatformUsageCreditsUpdateRule rule,
                                                       final PipelineUser user,
                                                       final PipelineRun run) {
        return PlatformUsageCreditsUpdateEvent.builder()
                .userId(user.getId())
                .ruleId(rule.getId())
                .entity(run != null ? SecuredEntityVO.from(PipelineRun.class, run.getId()) : null)
                .incidentType(rule.getAction().getType())
                .value(rule.getAction().getValue())
                .message(rule.getAction().getMessage())
                .build();
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
}
