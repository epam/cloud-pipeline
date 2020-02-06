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

package com.epam.pipeline.manager.scheduling;

import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.controller.vo.PipelineUserVO;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.entity.pipeline.run.ScheduleType;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.security.JwtTokenClaims;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.configuration.RunConfigurationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationRunner;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.jwt.JwtTokenGenerator;
import com.epam.pipeline.security.jwt.JwtTokenVerifier;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.*;

@DirtiesContext
@Transactional
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
public class RunSchedulerTest extends AbstractManagerTest {

    private static final Long RUN_ID = 1L;
    private static final long CONFIGURATION_ID = 2L;
    private static final int TEST_PERIOD_DURATION = 1;
    private static final int TEST_INVOCATION_PERIOD = 1;

    /**
     * This cron expression should correspond with {@link #TEST_INVOCATION_PERIOD}
     */
    private static final String CRON_EXPRESSION = "* * * * * ?"; // to run every 10 seconds
    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone("UTC");
    private static final String USER_OWNER = "OWNER";

    @Autowired
    private RunScheduler runScheduler;

    @Autowired
    private CloudRegionDao regionDao;

    @Autowired
    private UserManager userManager;

    @Autowired
    private JwtTokenGenerator jwtTokenGenerator;

    @Autowired
    private JwtTokenVerifier jwtTokenVerifier;

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

        PipelineUserVO userVO = new PipelineUserVO();
        userVO.setUserName(USER_OWNER);
        userManager.createUser(userVO);

        when(pipelineRunDao.loadPipelineRun(anyLong())).thenReturn(pipelineRun);
        when(pipelineRunManager.loadPipelineRun(anyLong())).thenReturn(pipelineRun);
        when(pipelineRunManager.pauseRun(anyLong(), anyBoolean())).thenReturn(pipelineRun);
        when(configurationManager.load(anyLong())).thenReturn(runConfiguration);
        when(jwtTokenGenerator.encodeToken(any(), any())).thenReturn("token");
        when(jwtTokenVerifier.readClaims(any())).thenReturn(
                JwtTokenClaims.builder().userId("1").userName(USER_OWNER).roles(Collections.emptyList()).build());
    }

    @Test
    @WithMockUser(username = USER_OWNER)
    public void testScheduleRunScheduleAndCheckJobExecution() throws InterruptedException {
        final RunSchedule runSchedule = getRunSchedule(RUN_ID, RUN_ID, ScheduleType.PIPELINE_RUN,
                RunScheduledAction.PAUSE, CRON_EXPRESSION);

        runScheduler.scheduleRunSchedule(runSchedule);

        TimeUnit.SECONDS.sleep(TEST_PERIOD_DURATION);
        final int numberOfInvocations = TEST_PERIOD_DURATION / TEST_INVOCATION_PERIOD;
        verify(pipelineRunManager, times(numberOfInvocations)).pauseRun(RUN_ID, true);
    }

    @Test
    @WithMockUser(username = USER_OWNER)
    public void testScheduleForRunAndConfWithSameId() throws InterruptedException {
        final RunSchedule runSchedule = getRunSchedule(1L, RUN_ID, ScheduleType.PIPELINE_RUN,
                RunScheduledAction.PAUSE, CRON_EXPRESSION);
        final RunSchedule confSchedule = getRunSchedule(2L, RUN_ID,
                ScheduleType.RUN_CONFIGURATION, RunScheduledAction.RUN, CRON_EXPRESSION);

        runScheduler.scheduleRunSchedule(runSchedule);
        runScheduler.scheduleRunSchedule(confSchedule);

        TimeUnit.SECONDS.sleep(TEST_PERIOD_DURATION);
        final int numberOfInvocations = TEST_PERIOD_DURATION / TEST_INVOCATION_PERIOD;
        verify(pipelineRunManager, times(numberOfInvocations)).pauseRun(RUN_ID, true);
        verify(configurationRunner, times(numberOfInvocations)).runConfiguration(any(), any(), any());

        runScheduler.unscheduleRunSchedule(runSchedule);
        runScheduler.unscheduleRunSchedule(confSchedule);
    }

    @Test
    @WithMockUser(username = USER_OWNER)
    public void testScheduleRunConfigurationScheduleAndCheckJobExecution() throws InterruptedException {
        final RunSchedule runSchedule = getRunSchedule(CONFIGURATION_ID, CONFIGURATION_ID,
                ScheduleType.RUN_CONFIGURATION, RunScheduledAction.RUN, CRON_EXPRESSION);

        runScheduler.scheduleRunSchedule(runSchedule);

        TimeUnit.SECONDS.sleep(TEST_PERIOD_DURATION);
        final int numberOfInvocations = TEST_PERIOD_DURATION / TEST_INVOCATION_PERIOD;
        verify(configurationRunner, times(numberOfInvocations)).runConfiguration(any(), any(), any());

        runScheduler.unscheduleRunSchedule(runSchedule);
    }

    @Test
    @WithMockUser(username = USER_OWNER)
    public void testScheduleRunConfigurationWontExecuteWrongAction() throws InterruptedException {
        final RunSchedule runSchedule = getRunSchedule(CONFIGURATION_ID, CONFIGURATION_ID,
                ScheduleType.RUN_CONFIGURATION, RunScheduledAction.RESUME, CRON_EXPRESSION);

        runScheduler.scheduleRunSchedule(runSchedule);

        TimeUnit.SECONDS.sleep(TEST_PERIOD_DURATION);
        verify(configurationRunner, never()).runConfiguration(any(), any(), any());

        runScheduler.unscheduleRunSchedule(runSchedule);
    }

    @Test
    @WithMockUser(username = USER_OWNER)
    public void testUnscheduleRunSchedule() throws InterruptedException {
        final RunSchedule runSchedule = getRunSchedule(RUN_ID, RUN_ID, ScheduleType.PIPELINE_RUN,
                RunScheduledAction.PAUSE, CRON_EXPRESSION);

        runScheduler.scheduleRunSchedule(runSchedule);
        runScheduler.unscheduleRunSchedule(runSchedule);

        TimeUnit.SECONDS.sleep(TEST_PERIOD_DURATION);
        verify(pipelineRunManager, never()).pauseRun(RUN_ID, true);
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
