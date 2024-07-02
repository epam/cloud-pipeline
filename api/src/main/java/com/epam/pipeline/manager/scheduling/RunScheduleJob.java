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

package com.epam.pipeline.manager.scheduling;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.pipeline.PipelineRunDockerOperationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@Slf4j
public class RunScheduleJob implements Job {

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private PipelineRunDockerOperationManager pipelineRunDockerOperationManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private UserManager userManager;

    @Autowired
    private AuthManager authManager;

    @Override
    public void execute(final JobExecutionContext context) {
        log.debug("Job {} fired {}", context.getJobDetail().getKey().getName(), context.getFireTime());

        final Long runId = context.getMergedJobDataMap().getLongValue("SchedulableId");
        Assert.notNull(runId, messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_NOT_FOUND, runId));
        PipelineRun pipelineRun = pipelineRunManager.loadPipelineRun(runId, false);
        Assert.notNull(pipelineRun, messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_NOT_FOUND, runId));


        final String userName = context.getMergedJobDataMap().getString("User");
        Assert.notNull(userName, messageHelper.getMessage(MessageConstants.ERROR_USER_NAME_REQUIRED, userName));
        final PipelineUser user = userManager.loadUserByName(userName);
        Assert.notNull(user, messageHelper.getMessage(MessageConstants.ERROR_USER_NAME_NOT_FOUND, userName));
        authManager.setCurrentUser(user);

        final String action = context.getMergedJobDataMap().getString("Action");
        if (RunScheduledAction.RESUME.name().equals(action)) {
            log.debug("Resuming a run with id: {}", runId);
            pipelineRunDockerOperationManager.resumeRun(runId);
        } else if (RunScheduledAction.PAUSE.name().equals(action)) {
            log.debug("Pausing a run with id: {}", runId);
            pipelineRunDockerOperationManager.pauseRun(runId, true);
        } else {
            log.error("Wrong type of action for scheduling run, allowed RESUME and PAUSE, actual: {}", action);
        }

        log.debug("Next job scheduled {}", context.getNextFireTime());
    }
}
