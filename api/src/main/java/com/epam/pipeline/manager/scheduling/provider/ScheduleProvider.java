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
import com.epam.pipeline.controller.vo.PipelineRunScheduleVO;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.entity.pipeline.run.ScheduleType;
import org.quartz.CronExpression;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public interface ScheduleProvider {

    ScheduleType getScheduleType();

    default void verifyScheduleVO(Long schedulableId, PipelineRunScheduleVO runScheduleVO) {
        Assert.notNull(runScheduleVO.getAction(),
                getMessageHelper().getMessage(MessageConstants.SCHEDULE_ACTION_IS_NOT_PROVIDED,
                        getScheduleType(), schedulableId));

        Assert.isTrue(StringUtils.hasText(runScheduleVO.getTimeZone()),
                getMessageHelper().getMessage(MessageConstants.ERROR_TIME_ZONE_IS_NOT_PROVIDED, schedulableId));

        Assert.notNull(runScheduleVO.getCronExpression(),
                getMessageHelper().getMessage(
                        MessageConstants.CRON_EXPRESSION_IS_NOT_PROVIDED, getScheduleType(), schedulableId));

        Assert.isTrue(CronExpression.isValidExpression(runScheduleVO.getCronExpression()),
                getMessageHelper().getMessage(
                        MessageConstants.CRON_EXPRESSION_IS_NOT_VALID, getScheduleType(), schedulableId));
        verifyScheduleAction(schedulableId, runScheduleVO.getAction());
    }

    void verifyScheduleAction(Long schedulableId, RunScheduledAction action);

    void verifySchedulable(Long schedulableId);

    Class<?> getScheduleJobClass();

    MessageHelper getMessageHelper();

}
