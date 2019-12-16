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

package com.epam.pipeline.dao.pipeline;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Transactional
public class PipelineRunScheduleDaoTest extends AbstractSpringTest {

    private static final Long RUN_ID_1 = 1L;
    private static final Long RUN_ID_2 = 2L;
    private static final String CRON_EXPRESSION1 = "0 0 12 * * ?";
    private static final String CRON_EXPRESSION2 = "0 15 10 ? * *";
    private static final String TEST_NAME = "TEST";
    private static final String TEST_REPOSITORY = "///";
    private static final String TEST_PIPELINE_REPO_SSH = "git@test";

    @Autowired
    private PipelineRunScheduleDao runScheduleDao;

    @Autowired
    private CloudRegionDao regionDao;

    @Autowired
    private PipelineRunDao pipelineRunDao;

    @Autowired
    private PipelineDao pipelineDao;

    private Pipeline testPipeline;
    private RunSchedule testRunSchedule;
    private RunSchedule testUpdatedRunSchedule;
    private RunSchedule testRunSchedule2;
    private RunSchedule testUpdatedRunSchedule2;

    @Before
    public void setUp() {
        testPipeline = new Pipeline();
        testPipeline.setName(TEST_NAME);
        testPipeline.setRepository(TEST_REPOSITORY);
        testPipeline.setOwner(TEST_NAME);
        testPipeline.setRepositorySsh(TEST_PIPELINE_REPO_SSH);
        pipelineDao.createPipeline(testPipeline);

        createPipelineRun(RUN_ID_1, testPipeline.getId());
        createPipelineRun(RUN_ID_2, testPipeline.getId());

        testRunSchedule = getRunSchedule(RUN_ID_1, RunScheduledAction.PAUSE, CRON_EXPRESSION1);
        testUpdatedRunSchedule = getRunSchedule(RUN_ID_1, RunScheduledAction.RESUME, CRON_EXPRESSION2);
        testRunSchedule2 = getRunSchedule(RUN_ID_2, RunScheduledAction.RESUME, CRON_EXPRESSION2);
        testUpdatedRunSchedule2 = getRunSchedule(RUN_ID_2, RunScheduledAction.RESUME, CRON_EXPRESSION1);
    }

    @Test
    public void testCreateRunSchedules() {
        runScheduleDao.createRunSchedules(Arrays.asList(testRunSchedule, testRunSchedule2));
        loadAndAssertSchedule(testRunSchedule);
        loadAndAssertSchedule(testRunSchedule2);
    }

    @Test
    public void testUpdateRunSchedules() {
        runScheduleDao.createRunSchedules(Arrays.asList(testRunSchedule, testRunSchedule2));

        testUpdatedRunSchedule.setId(testRunSchedule.getId());
        testUpdatedRunSchedule2.setId(testRunSchedule2.getId());

        runScheduleDao.updateRunSchedules(Arrays.asList(testUpdatedRunSchedule, testUpdatedRunSchedule2));

        loadAndAssertSchedule(testUpdatedRunSchedule);
        loadAndAssertSchedule(testUpdatedRunSchedule2);
    }

    @Test
    public void testLoadAllRunSchedules() {
        runScheduleDao.createRunSchedules(Arrays.asList(testRunSchedule, testRunSchedule2));
        final List<RunSchedule> runSchedules = runScheduleDao.loadAllRunSchedulesByRunId(testRunSchedule.getRunId());
        assertEquals(1, runSchedules.size());
    }

    @Test
    public void testDeleteRunSchedules() {
        runScheduleDao.createRunSchedules(Arrays.asList(testRunSchedule, testRunSchedule2));
        runScheduleDao.deleteRunSchedules(Arrays.asList(testRunSchedule.getId(), testRunSchedule2.getId()));
        final Optional<RunSchedule> runSchedule = runScheduleDao.loadRunSchedule(testRunSchedule.getId());
        assertFalse(runSchedule.isPresent());
        final Optional<RunSchedule> runSchedule2 = runScheduleDao.loadRunSchedule(testRunSchedule2.getId());
        assertFalse(runSchedule2.isPresent());
    }

    @Test
    public void testDeleteRunSchedulesForRun() {
        runScheduleDao.createRunSchedules(Arrays.asList(testRunSchedule, testUpdatedRunSchedule, testRunSchedule2));
        runScheduleDao.deleteRunSchedulesForRun(testRunSchedule.getRunId());
        final List<RunSchedule> runSchedules = runScheduleDao.loadAllRunSchedules();
        assertEquals(1, runSchedules.size());
        assertEquals(runSchedules.get(0).getId(), testRunSchedule2.getId());
    }

    @Test
    public void testScheduleRemovalAfterRunIsRemoved() {
        runScheduleDao.createRunSchedules(Arrays.asList(testRunSchedule, testUpdatedRunSchedule, testRunSchedule2));
        pipelineRunDao.deleteRunsByPipeline(testPipeline.getId());
        final List<RunSchedule> runSchedules = runScheduleDao.loadAllRunSchedules();
        assertEquals(0, runSchedules.size());
    }

    private RunSchedule getRunSchedule(final Long runId, final RunScheduledAction action, final String cronExpression) {
        final RunSchedule runSchedule = new RunSchedule();
        runSchedule.setRunId(runId);
        runSchedule.setAction(action);
        runSchedule.setCronExpression(cronExpression);
        runSchedule.setCreatedDate(DateUtils.now());
        runSchedule.setTimeZone(TimeZone.getTimeZone("UTC"));
        return runSchedule;
    }

    private PipelineRun createPipelineRun(final Long runId, final Long pipelineId) {
        final AbstractCloudRegion cloudRegion = ObjectCreatorUtils.getDefaultAwsRegion();
        regionDao.create(cloudRegion);
        final PipelineRun run = ObjectCreatorUtils.createPipelineRun(runId, pipelineId, null, cloudRegion.getId());
        pipelineRunDao.createPipelineRun(run);
        return run;
    }

    private void loadAndAssertSchedule(final RunSchedule schedule) {
        final Optional<RunSchedule> loadRunSchedule = runScheduleDao.loadRunSchedule(schedule.getId());
        assertTrue(loadRunSchedule.isPresent());
        assertEquals(schedule.getRunId(), loadRunSchedule.get().getRunId());
        assertEquals(schedule.getAction(), loadRunSchedule.get().getAction());
        assertEquals(schedule.getCronExpression(), loadRunSchedule.get().getCronExpression());
    }

}
