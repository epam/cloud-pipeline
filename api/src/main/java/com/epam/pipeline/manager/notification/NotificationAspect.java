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

import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.RunStatusManager;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * This aspect controls sending notifications
 */
@Aspect
@Component
public class NotificationAspect {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationAspect.class);

    @Autowired
    private PipelineManager pipelineManager;

    @Autowired
    private NotificationManager notificationManager;

    @Autowired
    private RunStatusManager runStatusManager;

    /**
     * Generates system notifications for PipelineRun status changes
     * @param joinPoint
     * @param run
     */
    @AfterReturning(
        pointcut =
            "execution(* com.epam.pipeline.manager.pipeline.PipelineRunManager.runPipeline(..)) || " +
            "execution(* com.epam.pipeline.manager.pipeline.PipelineRunManager.runCmd(..)) ||" +
            "execution(* com.epam.pipeline.manager.pipeline.PipelineRunManager.updatePipelineStatus(" +
            "com.epam.pipeline.entity.pipeline.PipelineRun)) || " +
            "execution(* com.epam.pipeline.manager.pipeline.PipelineRunManager.updatePipelineStatusIfNotFinal(..)) ||" +
            "execution(* com.epam.pipeline.manager.pipeline.PipelineRunManager"
            + ".updatePipelineStatusIfNotFinalExternal(..)))",
        returning = "run"
        )
    @Async("notificationsExecutor")
    public void notifyRunStatusChanged(JoinPoint joinPoint, PipelineRun run) {
        runStatusManager.saveStatus(RunStatus.builder()
                .runId(run.getId()).status(run.getStatus())
                .timestamp(DateUtils.nowUTC())
                .build());
        if (run.isTerminating()) {
            LOGGER.debug("Won't send a notification [{} {}: {}] (filtered by status type)", run.getPipelineName(),
                          run.getVersion(), run.getStatus());
            return;
        }

        if (run.getPipelineName() == null) {
            if (run.getPipelineId() != null) {
                Pipeline pipeline = pipelineManager.load(run.getPipelineId());
                run.setPipelineName(pipeline.getName());
            } else {
                run.setPipelineName("pipeline");
            }
        }

        LOGGER.debug("Notify all about pipelineRun status changed {} {} {}: {}",
                     run.getPodId(), run.getPipelineName(), run.getVersion(), run.getStatus());
        notificationManager.notifyRunStatusChanged(run);
    }
}

