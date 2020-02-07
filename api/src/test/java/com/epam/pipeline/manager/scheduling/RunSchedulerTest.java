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

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.entity.pipeline.run.ScheduleType;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.configuration.RunConfigurationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationRunner;
import com.epam.pipeline.manager.user.UserManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

@Transactional
public class RunSchedulerTest extends AbstractSpringTest {

    private static final Long RUN_ID = 1L;
    private static final long CONFIGURATION_ID = 2L;
    private static final int TEST_PERIOD_DURATION = 10;
    private static final int TEST_INVOCATION_PERIOD = 10;

    /**
     * This cron expression should correspond with {@link #TEST_INVOCATION_PERIOD}
     */
    private static final String CRON_EXPRESSION = "*/10 * * * * ?"; // to run every 10 seconds
    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone("UTC");
    private static final String USER_OWNER = "OWNER";

    @Autowired
    private RunScheduler runScheduler;

    @Autowired
    private CloudRegionDao regionDao;

    @MockBean
    private UserManager userManager;

    @MockBean
    private PipelineRunDao pipelineRunDao;

    @MockBean
    private PipelineRunManager pipelineRunManager;

    @MockBean
    private RunConfigurationManager configurationManager;

    @MockBean
    private ConfigurationRunner configurationRunner;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        final PipelineRun pipelineRun = createPipelineRun(RUN_ID, RUN_ID);
        final RunConfiguration runConfiguration = createRunConfiguration();

        Mockito.when(pipelineRunDao.loadPipelineRun(Mockito.anyLong())).thenReturn(pipelineRun);
        Mockito.when(pipelineRunManager.loadPipelineRun(Mockito.anyLong())).thenReturn(pipelineRun);
        Mockito.when(pipelineRunManager.pauseRun(Mockito.anyLong(), Mockito.anyBoolean())).thenReturn(pipelineRun);
        Mockito.when(configurationManager.load(Mockito.anyLong())).thenReturn(runConfiguration);
        Mockito.when(userManager.loadUserByName(Mockito.any())).thenReturn(new PipelineUser(USER_OWNER));
    }

    @Test
    public void testScheduleRunScheduleAndCheckJobExecution() throws InterruptedException {
        final RunSchedule runSchedule = getRunSchedule(RUN_ID, RUN_ID, ScheduleType.PIPELINE_RUN,
                RunScheduledAction.PAUSE, CRON_EXPRESSION);

        runScheduler.scheduleRunSchedule(runSchedule);

        TimeUnit.SECONDS.sleep(TEST_PERIOD_DURATION);
        final int numberOfInvocations = TEST_PERIOD_DURATION / TEST_INVOCATION_PERIOD;
        Mockito.verify(pipelineRunManager, Mockito.times(numberOfInvocations)).pauseRun(RUN_ID, true);
    }

    @Test
    public void testScheduleForRunAndConfWithSameId() throws InterruptedException {
        final RunSchedule runSchedule = getRunSchedule(1L, RUN_ID, ScheduleType.PIPELINE_RUN,
                RunScheduledAction.PAUSE, CRON_EXPRESSION);
        final RunSchedule confSchedule = getRunSchedule(2L, RUN_ID,
                ScheduleType.RUN_CONFIGURATION, RunScheduledAction.RUN, CRON_EXPRESSION);

        runScheduler.scheduleRunSchedule(runSchedule);
        runScheduler.scheduleRunSchedule(confSchedule);

        TimeUnit.SECONDS.sleep(TEST_PERIOD_DURATION);
        final int numberOfInvocations = TEST_PERIOD_DURATION / TEST_INVOCATION_PERIOD;
        Mockito.verify(pipelineRunManager, Mockito.times(numberOfInvocations)).pauseRun(RUN_ID, true);
        Mockito.verify(configurationRunner, Mockito.times(numberOfInvocations))
                .runConfiguration(Mockito.any(), Mockito.any(), Mockito.any());

        runScheduler.unscheduleRunSchedule(runSchedule);
        runScheduler.unscheduleRunSchedule(confSchedule);
    }

    @Test
    public void testScheduleRunConfigurationScheduleAndCheckJobExecution() throws InterruptedException {
        final RunSchedule runSchedule = getRunSchedule(CONFIGURATION_ID, CONFIGURATION_ID,
                ScheduleType.RUN_CONFIGURATION, RunScheduledAction.RUN, CRON_EXPRESSION);

        runScheduler.scheduleRunSchedule(runSchedule);

        TimeUnit.SECONDS.sleep(TEST_PERIOD_DURATION);
        final int numberOfInvocations = TEST_PERIOD_DURATION / TEST_INVOCATION_PERIOD;
        Mockito.verify(configurationRunner, Mockito.times(numberOfInvocations))
                .runConfiguration(Mockito.any(), Mockito.any(), Mockito.any());

        runScheduler.unscheduleRunSchedule(runSchedule);
    }

    @Test
    public void testScheduleRunConfigurationWontExecuteWrongAction() throws InterruptedException {
        final RunSchedule runSchedule = getRunSchedule(CONFIGURATION_ID, CONFIGURATION_ID,
                ScheduleType.RUN_CONFIGURATION, RunScheduledAction.RESUME, CRON_EXPRESSION);

        runScheduler.scheduleRunSchedule(runSchedule);

        TimeUnit.SECONDS.sleep(TEST_PERIOD_DURATION);
        Mockito.verify(configurationRunner, Mockito.never())
                .runConfiguration(Mockito.any(), Mockito.any(), Mockito.any());

        runScheduler.unscheduleRunSchedule(runSchedule);
    }

    @Test
    public void testUnscheduleRunSchedule() throws InterruptedException {
        final RunSchedule runSchedule = getRunSchedule(RUN_ID, RUN_ID, ScheduleType.PIPELINE_RUN,
                RunScheduledAction.PAUSE, CRON_EXPRESSION);

        runScheduler.scheduleRunSchedule(runSchedule);
        runScheduler.unscheduleRunSchedule(runSchedule);

        TimeUnit.SECONDS.sleep(TEST_PERIOD_DURATION);
        Mockito.verify(pipelineRunManager, Mockito.never()).pauseRun(RUN_ID, true);
    }

    private RunSchedule getRunSchedule(final Long id, final Long runId, final ScheduleType type,
                                       final RunScheduledAction action, final String cronExpression) {
        final RunSchedule runSchedule = new RunSchedule();
        runSchedule.setId(id);
        runSchedule.setSchedulableId(runId);
        runSchedule.setType(type);
        runSchedule.setAction(action);
        runSchedule.setUser(USER_OWNER);
        runSchedule.setCronExpression(cronExpression);
        runSchedule.setCreatedDate(DateUtils.now());
        runSchedule.setTimeZone(TIME_ZONE);
        return runSchedule;
    }

    private PipelineRun createPipelineRun(final Long runId, final Long pipelineId) {
        final AbstractCloudRegion cloudRegion = ObjectCreatorUtils.getDefaultAwsRegion();
        regionDao.create(cloudRegion);
        final PipelineRun run = ObjectCreatorUtils.createPipelineRun(runId, pipelineId, null, cloudRegion.getId());
        run.setStatus(TaskStatus.RUNNING);
        run.getInstance().setSpot(false);
        return run;
    }

    private RunConfiguration createRunConfiguration() {
        RunConfiguration runConfiguration = new RunConfiguration();
        runConfiguration.setId(CONFIGURATION_ID);
        return runConfiguration;
    }
}
