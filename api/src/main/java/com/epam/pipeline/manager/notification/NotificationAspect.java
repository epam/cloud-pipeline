/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.notification;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.exception.PipelineException;
import com.epam.pipeline.manager.cluster.performancemonitoring.UsageMonitoringManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunStatusManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;


/**
 * This aspect controls sending notifications
 */
@Aspect
@Component
@Slf4j
public class NotificationAspect {

    @Autowired
    private NotificationManager notificationManager;

    @Autowired
    private RunStatusManager runStatusManager;

    @Autowired
    private UsageMonitoringManager usageMonitoringManager;

    @Autowired
    private PipelineRunManager pipelineRunManager;


    /**
     * Generates system notifications for PipelineRun status changes
     * @param joinPoint
     * @param run
     */
    @AfterReturning(
        pointcut =
            "execution(* com.epam.pipeline.manager.pipeline.PipelineRunManager.runPipeline(..)) || " +
            "execution(* com.epam.pipeline.manager.pipeline.PipelineRunManager.runCmd(..)) ||" +
            "execution(* com.epam.pipeline.manager.pipeline.PipelineRunCRUDService.updateRunStatus(" +
            "com.epam.pipeline.entity.pipeline.PipelineRun)) || " +
            "execution(* com.epam.pipeline.manager.pipeline.PipelineRunManager.updatePipelineStatusIfNotFinal(..)) ||" +
            "execution(* com.epam.pipeline.manager.pipeline.PipelineRunManager"
            + ".updatePipelineStatusIfNotFinalExternal(..)))",
        returning = "run"
        )
    @Async("notificationsExecutor")
    public void notifyRunStatusChanged(JoinPoint joinPoint, PipelineRun run) {
        final RunStatus newStatus = RunStatus.builder()
                .runId(run.getId()).status(run.getStatus())
                .timestamp(DateUtils.nowUTC())
                .reason(run.getStateReasonMessage())
                .build();

        final boolean updated = runStatusManager.saveStatus(newStatus);
        if (!updated) {
            log.debug("Won't send notification for run {} as new status {} matches an existing one.",
                    run.getId(), run.getStatus());
            return;
        }

        if (newStatus.getStatus().isFinal()) {
            storeRunPerformanceMetrics(run);
        }

        log.debug("Notify all about pipelineRun status changed {} {} {}: {}",
                run.getPodId(), run.getPipelineName(), run.getVersion(), run.getStatus());
        notificationManager.notifyRunStatusChanged(run, Collections.emptyMap());
    }

    private void storeRunPerformanceMetrics(final PipelineRun run) {
        final long runId = run.getId();
        final String nodeName = Optional.ofNullable(run.getInstance())
                .map(RunInstance::getNodeName).orElse(StringUtils.EMPTY);

        if (StringUtils.isBlank(nodeName)) {
            log.warn("Can't find nodeName for run: {}", runId);
            return;
        }

        if (usageMonitoringManager != null) {
            try {
                final LocalDateTime from = DateUtils.convertDateToLocalDateTime(run.getStartDate());
                final LocalDateTime to = Optional.ofNullable(run.getEndDate())
                        .map(DateUtils::convertDateToLocalDateTime).orElse(DateUtils.nowUTC());
                Optional.ofNullable(
                        usageMonitoringManager.getMeanPerformanceMetricsForRun(nodeName, runId, from, to)
                ).ifPresent(pipelineRunManager::savePipelineRunPerformanceMetrics);
            } catch (PipelineException e) {
                log.warn("Can't request pipeline run performance metrics, it won't be saved for run: {}", runId);
            }
        } else {
            log.debug(
                    "UsageMonitoringManager is not initialized! Performance metrics won't be saved for run: {}",
                    runId
            );
        }
    }
}

