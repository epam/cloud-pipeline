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
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.security.jwt.JwtAuthenticationToken;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@Slf4j
public class RunScheduleJob implements Job {

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private UserManager userManager;

    @Override
    public void execute(final JobExecutionContext context) {
        log.debug("Job " + context.getJobDetail().getKey().getName() + " fired " + context.getFireTime());

        setAuth(context.getMergedJobDataMap().getString("User"));
        final Long runId = context.getMergedJobDataMap().getLongValue("SchedulableId");
        final String action = context.getMergedJobDataMap().getString("Action");
        Assert.notNull(runId,
                       messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_NOT_FOUND, runId));
        PipelineRun pipelineRun = pipelineRunManager.loadPipelineRun(runId);
        Assert.notNull(pipelineRun,
                       messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_NOT_FOUND, pipelineRun.getName()));
        if (action.equals(RunScheduledAction.RESUME.name())) {
            pipelineRunManager.resumeRun(runId);
        } else if (action.equals(RunScheduledAction.PAUSE.name())) {
            pipelineRunManager.pauseRun(runId, true);
        }

        log.debug("Next job scheduled " + context.getNextFireTime());
    }

    private void setAuth(final String userName) {
        Assert.notNull(userName, "User is not provided!");
        PipelineUser pipelineUser = userManager.loadUserByName(userName);
        UserContext userContext = new UserContext(pipelineUser);
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(userContext, userContext.getAuthorities())
        );
    }
}
