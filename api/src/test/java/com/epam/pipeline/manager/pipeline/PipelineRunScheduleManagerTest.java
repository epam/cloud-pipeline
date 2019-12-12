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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.controller.vo.PipelineRunScheduleVO;
import com.epam.pipeline.dao.pipeline.PipelineDao;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

@Transactional
public class PipelineRunScheduleManagerTest extends AbstractSpringTest {

    private static final Long RUN_ID = 1L;
    private static final String CRON_EXPRESSION1 = "0 0 12 * * ?";
    private static final String CRON_EXPRESSION2 = "0 15 10 ? * *";
    private static final String TEST_NAME = "TEST";
    private static final String TEST_REPOSITORY = "///";
    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone("UTC");
    private static final String TEST_PIPELINE_REPO_SSH = "git@test";

    @Autowired
    private PipelineRunScheduleManager runScheduleManager;

    @Autowired
    private PipelineDao pipelineDao;

    @Autowired
    private PipelineRunDao pipelineRunDao;

    @Autowired
    private CloudRegionDao regionDao;

    private PipelineRunScheduleVO testRunScheduleVO;

    @Before
    public void setUp() {
        Pipeline testPipeline = new Pipeline();
        testPipeline.setName(TEST_NAME);
        testPipeline.setRepository(TEST_REPOSITORY);
        testPipeline.setOwner(TEST_NAME);
        testPipeline.setRepositorySsh(TEST_PIPELINE_REPO_SSH);
        pipelineDao.createPipeline(testPipeline);

        createPipelineRun(RUN_ID, testPipeline.getId());

        testRunScheduleVO = getRunScheduleVO(RunScheduledAction.PAUSE, CRON_EXPRESSION1);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testCreateRunSchedule() {
        final RunSchedule runSchedule = runScheduleManager.createRunSchedule(RUN_ID, testRunScheduleVO);
        final RunSchedule loadRunSchedule = runScheduleManager.loadRunSchedule(runSchedule.getId());
        assertEquals(runSchedule.getRunId(), loadRunSchedule.getRunId());
        assertEquals(runSchedule.getCronExpression(), loadRunSchedule.getCronExpression());
        assertEquals(runSchedule.getAction(), loadRunSchedule.getAction());
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testCreateRunScheduleWithIdenticalCronExpression() {
        runScheduleManager.createRunSchedule(RUN_ID, testRunScheduleVO);
        testRunScheduleVO.setAction(RunScheduledAction.RESUME);
        runScheduleManager.createRunSchedule(RUN_ID, testRunScheduleVO);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testUpdateRunSchedule() {
        final RunSchedule runSchedule = runScheduleManager.createRunSchedule(RUN_ID, testRunScheduleVO);
        testRunScheduleVO.setScheduleId(runSchedule.getId());
        testRunScheduleVO.setCronExpression(CRON_EXPRESSION2);
        final RunSchedule updatedRunSchedule = runScheduleManager.updateRunSchedule(RUN_ID, testRunScheduleVO);
        final List<RunSchedule> loadRunSchedule = runScheduleManager.loadAllRunSchedulesByRunId(runSchedule.getRunId());
        assertEquals(1, loadRunSchedule.size());
        assertEquals(updatedRunSchedule.getRunId(), loadRunSchedule.get(0).getRunId());
        assertEquals(updatedRunSchedule.getCronExpression(), loadRunSchedule.get(0).getCronExpression());
        assertEquals(updatedRunSchedule.getAction(), loadRunSchedule.get(0).getAction());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testDeleteRunSchedule() {
        final RunSchedule runSchedule = runScheduleManager.createRunSchedule(RUN_ID, testRunScheduleVO);
        runScheduleManager.deleteRunSchedule(RUN_ID, runSchedule.getId());
        final List<RunSchedule> loadRunSchedule = runScheduleManager.loadAllRunSchedulesByRunId(runSchedule.getRunId());
        assertEquals(0, loadRunSchedule.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void loadRunSchedule() {
        final RunSchedule runSchedule = runScheduleManager.createRunSchedule(RUN_ID, testRunScheduleVO);
        runScheduleManager.loadRunSchedule(runSchedule.getRunId());
    }

    private PipelineRunScheduleVO getRunScheduleVO(final RunScheduledAction action, final String cronExpression) {
        PipelineRunScheduleVO runScheduleVO = new PipelineRunScheduleVO();
        runScheduleVO.setTimeZone(TIME_ZONE.getDisplayName());
        runScheduleVO.setCronExpression(cronExpression);
        runScheduleVO.setAction(action);
        return runScheduleVO;
    }

    private void createPipelineRun(final Long runId, final Long pipelineId) {
        final AbstractCloudRegion cloudRegion = ObjectCreatorUtils.getDefaultAwsRegion();
        regionDao.create(cloudRegion);
        PipelineRun run = ObjectCreatorUtils.createPipelineRun(runId, pipelineId, null, cloudRegion.getId());
        run.setStatus(TaskStatus.RUNNING);
        run.getInstance().setSpot(false);
        pipelineRunDao.createPipelineRun(run);
    }

}
