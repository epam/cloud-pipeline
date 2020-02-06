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

package com.epam.pipeline.manager.scheduling.provider;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.entity.pipeline.run.ScheduleType;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.scheduling.RunScheduleJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
public class PipelineRunScheduleProvider implements ScheduleProvider {

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @Override
    public ScheduleType getScheduleType() {
        return ScheduleType.PIPELINE_RUN;
    }

    @Override
    public void verifyScheduleAction(final Long schedulableId, final RunScheduledAction action) {
        Assert.isTrue(action == RunScheduledAction.PAUSE || action == RunScheduledAction.RESUME,
                messageHelper.getMessage(MessageConstants.SCHEDULE_ACTION_IS_NOT_ALLOWED,
                        RunScheduledAction.RESUME.name() + ", " + RunScheduledAction.PAUSE.name(), action));
    }

    @Override
    public void verifySchedulable(final Long schedulableId) {
        final PipelineRun pipelineRun = pipelineRunManager.loadPipelineRun(schedulableId);
        Assert.notNull(pipelineRun, messageHelper
                .getMessage(MessageConstants.ERROR_RUN_PIPELINES_NOT_FOUND, pipelineRun.getName()));
        Assert.isTrue(!pipelineRun.getInstance().getSpot(), messageHelper.getMessage(
                MessageConstants.ERROR_ON_DEMAND_REQUIRED));
        Assert.isTrue(!pipelineRun.getStatus().isFinal(), messageHelper.getMessage(
                MessageConstants.ERROR_PIPELINE_RUN_FINISHED, schedulableId));
        Assert.isTrue(!pipelineRun.isClusterRun() && !pipelineRun.isNonPause(),
                messageHelper.getMessage(MessageConstants.DEBUG_RUN_IDLE_SKIP_CHECK));
    }

    @Override
    public Class<?> getScheduleJobClass() {
        return RunScheduleJob.class;
    }

    @Override
    public MessageHelper getMessageHelper() {
        return messageHelper;
    }
}
