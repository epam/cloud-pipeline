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
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PipelineRunSchedulerTest extends AbstractSpringTest {

    private static final Long RUN_ID = 1L;
    private static final int TEST_PERIOD_DURATION = 10;
    private static final int TEST_INVOCATION_PERIOD = 10;

    /**
     * This cron expression should correspond with {@link #TEST_INVOCATION_PERIOD}
     */
    private static final String CRON_EXPRESSION = "0/10 * * * * ?"; // to run every 10 seconds
    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone("UTC");

    @Autowired
    private PipelineRunScheduler runScheduler;

    @Autowired
    private CloudRegionDao regionDao;

    @MockBean
    private PipelineRunDao pipelineRunDao;

    @MockBean
    private PipelineRunManager pipelineRunManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        final PipelineRun pipelineRun = createPipelineRun(RUN_ID, RUN_ID);

        when(pipelineRunDao.loadPipelineRun(anyLong())).thenReturn(pipelineRun);
        when(pipelineRunManager.loadPipelineRun(anyLong())).thenReturn(pipelineRun);
        when(pipelineRunManager.pauseRun(anyLong(), anyBoolean())).thenReturn(pipelineRun);
    }

    @Test
    public void testScheduleRunScheduleAndCheckJobExecution() throws InterruptedException {
        final RunSchedule runSchedule = getRunSchedule(RUN_ID, RUN_ID, RunScheduledAction.PAUSE, CRON_EXPRESSION);

        runScheduler.scheduleRunSchedule(runSchedule);

        TimeUnit.SECONDS.sleep(TEST_PERIOD_DURATION);
        final int numberOfInvocations = TEST_PERIOD_DURATION / TEST_INVOCATION_PERIOD;
        verify(pipelineRunManager, times(numberOfInvocations)).pauseRun(RUN_ID, true);
    }

    @Test
    public void testUnscheduleRunSchedule() throws InterruptedException {
        final RunSchedule runSchedule = getRunSchedule(RUN_ID, RUN_ID, RunScheduledAction.PAUSE, CRON_EXPRESSION);

        runScheduler.unscheduleRunSchedule(runSchedule);

        runScheduler.scheduleRunSchedule(runSchedule);

        TimeUnit.SECONDS.sleep(TEST_PERIOD_DURATION);
        final int numberOfInvocations = TEST_PERIOD_DURATION / TEST_INVOCATION_PERIOD;
        verify(pipelineRunManager, times(numberOfInvocations)).pauseRun(RUN_ID, true);
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
