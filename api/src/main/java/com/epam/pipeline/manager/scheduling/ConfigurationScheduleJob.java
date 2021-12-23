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
import com.epam.pipeline.entity.configuration.AbstractRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.configuration.RunConfigurationManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationRunner;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.stream.Collectors;

@Component
@Slf4j
public class ConfigurationScheduleJob implements Job {

    @Autowired
    private ConfigurationRunner configurationRunner;

    @Autowired
    private RunConfigurationManager configurationManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private UserManager userManager;

    @Autowired
    private AuthManager authManager;

    @Override
    public void execute(final JobExecutionContext context) {
        log.debug("Job {} fired {}", context.getJobDetail().getKey().getName(), context.getFireTime());

        final Long configurationId = context.getMergedJobDataMap().getLongValue("SchedulableId");
        Assert.notNull(configurationId, messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_NOT_FOUND,
                configurationId));
        final RunConfigurationWithEntitiesVO configuration = createConfigurationVOToRun(configurationId);

        final String userName = context.getMergedJobDataMap().getString("User");
        Assert.notNull(userName, messageHelper.getMessage(MessageConstants.ERROR_USER_NAME_REQUIRED, userName));
        final PipelineUser user = userManager.loadUserByName(userName);
        Assert.notNull(user, messageHelper.getMessage(MessageConstants.ERROR_USER_NAME_NOT_FOUND, userName));
        authManager.setCurrentUser(user);

        final String action = context.getMergedJobDataMap().getString("Action");
        if (RunScheduledAction.RUN.name().equals(action)) {
            log.debug("Execute a configuration with id: {}", configurationId);
            configurationRunner.runConfiguration(null, configuration, null);
        } else {
            log.error("Wrong type of action for scheduling configuration, allowed RUN, actual: {}", action);
        }

        log.debug("Next job scheduled {}", context.getNextFireTime());
    }

    private RunConfigurationWithEntitiesVO createConfigurationVOToRun(final Long configurationId) {
        final RunConfiguration runConfiguration = configurationManager.load(configurationId);
        final RunConfigurationWithEntitiesVO configuration = new RunConfigurationWithEntitiesVO();
        configuration.setEntries(
                runConfiguration.getEntries()
                        .stream()
                        .filter(AbstractRunConfigurationEntry::isDefaultConfiguration)
                        .collect(Collectors.toList())
        );
        configuration.setId(configurationId);
        return configuration;
    }
}
