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
import com.epam.pipeline.controller.vo.configuration.RunConfigurationWithEntitiesVO;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationRunner;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@Slf4j
public class ConfigurationScheduleJob implements Job {

    @Autowired
    private ConfigurationRunner configurationRunner;

    @Autowired
    private MessageHelper messageHelper;

    @Override
    public void execute(final JobExecutionContext context) {
        log.debug("Job " + context.getJobDetail().getKey().getName() + " fired " + context.getFireTime());

        final Long configurationId = context.getMergedJobDataMap().getLongValue("SchedulableId");
        final String action = context.getMergedJobDataMap().getString("Action");
        Assert.notNull(configurationId,
                       messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_NOT_FOUND, configurationId));
        final RunConfigurationWithEntitiesVO configuration = createConfigurationVOToRun(configurationId);
        if (action.equals(RunScheduledAction.RUN.name())) {
            log.debug("Execute a configuration with id: "+ configurationId);
            configurationRunner.runConfiguration(null, configuration, null);
        } else {
            log.error("Wrong type of action for scheduling configuration, allowed RUN, actual: " + action);
        }
        log.debug("Next job scheduled " + context.getNextFireTime());
    }

    private RunConfigurationWithEntitiesVO createConfigurationVOToRun(final Long configurationId) {
        RunConfigurationWithEntitiesVO configuration = new RunConfigurationWithEntitiesVO();
        configuration.setId(configurationId);
        return configuration;
    }
}
