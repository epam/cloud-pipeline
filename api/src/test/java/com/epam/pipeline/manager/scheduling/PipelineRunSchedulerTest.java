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

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.PipelineRunScheduleManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PipelineRunSchedulerTest extends AbstractSpringTest {

    private static final Long RUN_ID_1 = 1L;
    private static final Long RUN_ID_2 = 2L;
    private static final Long RUN_SCHEDULE_ID_1 = 1L;
    private static final Long RUN_SCHEDULE_ID_2 = 2L;
    private static final Long RUN_SCHEDULE_ID_3 = 3L;
    private static final int TEST_PERIOD_DURATION = 90;
    private static final int[] TEST_TASK_INVOCATION_PERIOD = {10, 15};
    private static final int SCHEDULER_INVOCATION_PERIOD_SEC =
        SystemPreferences.LAUNCH_TASK_STATUS_UPDATE_RATE.getDefaultValue() / 1000;

    /**
     * This cron expression should correspond with {@link #TEST_TASK_INVOCATION_PERIOD}
     */
    private static final String CRON_EXPRESSION_1 = "0/10 * * * * ?"; // to run every 10 seconds
    private static final String CRON_EXPRESSION_2 = "0/15 * * * * ?"; // to run every 15 seconds
    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone("UTC");

    private PipelineRunSchedulerPureJava runScheduler;

    @Autowired
    private CloudRegionDao regionDao;

    @MockBean
    private PipelineRunDao pipelineRunDao;

    @MockBean
    private PipelineRunManager pipelineRunManager;

    @MockBean
    private PipelineRunScheduleManager runScheduleManager;

    @Before
    public void setUp() {
        Assert.assertEquals("TEST_PERIOD_DURATION should be multiple of SCHEDULER_INVOCATION_PERIOD_SEC",
                            0, TEST_PERIOD_DURATION % (SCHEDULER_INVOCATION_PERIOD_SEC));
        for (int period : TEST_TASK_INVOCATION_PERIOD) {
            Assert.assertEquals(0, TimeUnit.MINUTES.toSeconds(1) % period);
        }
        MockitoAnnotations.initMocks(this);

        when(runScheduleManager.loadAllRunSchedules()).thenReturn(new ArrayList<>());

        runScheduler = new PipelineRunSchedulerPureJava(pipelineRunManager);
        createPipelineRunAndBindMocks(RUN_ID_1);
        createPipelineRunAndBindMocks(RUN_ID_2);
    }

    private void createPipelineRunAndBindMocks(final Long id) {
        final PipelineRun pipelineRun = createPipelineRun(id, id);
        when(pipelineRunDao.loadPipelineRun(id)).thenReturn(pipelineRun);
        when(pipelineRunManager.loadPipelineRun(id)).thenReturn(pipelineRun);
        when(pipelineRunManager.pauseRun(id, true)).thenReturn(pipelineRun);
    }

    @Test
    public void testSchedulingAndCheckJobExecution() {
        final RunSchedule runSchedule1 = getRunSchedule(RUN_SCHEDULE_ID_1,
                                                        RUN_ID_1,
                                                        RunScheduledAction.PAUSE, CRON_EXPRESSION_1);
        final RunSchedule runSchedule2 = getRunSchedule(RUN_SCHEDULE_ID_2,
                                                        RUN_ID_1,
                                                        RunScheduledAction.PAUSE, CRON_EXPRESSION_2);
        final RunSchedule runSchedule3 = getRunSchedule(RUN_SCHEDULE_ID_3,
                                                        RUN_ID_2,
                                                        RunScheduledAction.RESUME, CRON_EXPRESSION_1);
        runScheduler.scheduleRunSchedule(runSchedule1);
        runScheduler.scheduleRunSchedule(runSchedule2);
        runScheduler.scheduleRunSchedule(runSchedule3);
        imitateSchedulerTicks();

        final int numberOfInvocations1 = TEST_PERIOD_DURATION / TEST_TASK_INVOCATION_PERIOD[0]
                                                     + TEST_PERIOD_DURATION / TEST_TASK_INVOCATION_PERIOD[1];
        verify(pipelineRunManager, times(numberOfInvocations1)).pauseRun(RUN_ID_1, true);

        final int numberOfInvocations2 = TEST_PERIOD_DURATION / TEST_TASK_INVOCATION_PERIOD[0];
        verify(pipelineRunManager, times(numberOfInvocations2)).resumeRun(RUN_ID_2);

        runScheduler.unscheduleRunSchedule(runSchedule2);
        runScheduler.unscheduleRunSchedule(runSchedule3);
        imitateSchedulerTicks();

        final int numberOfInvocations3 = numberOfInvocations1 + TEST_PERIOD_DURATION / TEST_TASK_INVOCATION_PERIOD[0];
        verify(pipelineRunManager, times(numberOfInvocations3)).pauseRun(RUN_ID_1, true);
        verify(pipelineRunManager, times(numberOfInvocations2)).resumeRun(RUN_ID_2);

        runScheduler.unscheduleRunSchedule(runSchedule1);
        imitateSchedulerTicks();
        Mockito.verifyNoMoreInteractions(pipelineRunManager);
    }

    private void imitateSchedulerTicks() {
        final int schedulerTicks = TEST_PERIOD_DURATION / SCHEDULER_INVOCATION_PERIOD_SEC;
        for (int i = 0; i < schedulerTicks; i++) {
            runScheduler.executeScheduledTasks();
        }
    }

    private RunSchedule getRunSchedule(final Long id, final Long runId, final RunScheduledAction action,
                                       final String cronExpression) {
        final RunSchedule runSchedule = new RunSchedule();
        runSchedule.setId(id);
        runSchedule.setRunId(runId);
        runSchedule.setAction(action);
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
}

