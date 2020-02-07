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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.controller.vo.PipelineRunScheduleVO;
import com.epam.pipeline.controller.vo.PipelineUserVO;
import com.epam.pipeline.dao.configuration.RunConfigurationDao;
import com.epam.pipeline.dao.pipeline.PipelineDao;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.pipeline.run.ScheduleType;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.user.UserManager;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

@DirtiesContext
@Transactional
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
public class RunScheduleManagerTest extends AbstractManagerTest {

    private static final Long RUN_ID = 1L;
    private static final String CRON_EXPRESSION1 = "0 0 12 * * ?";
    private static final String CRON_EXPRESSION2 = "0 15 10 ? * *";
    private static final String TEST_NAME = "TEST";
    private static final String TEST_REPOSITORY = "///";
    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone("UTC");
    private static final String TEST_PIPELINE_REPO_SSH = "git@test";
    private static final String USER_OWNER = "OWNER";

    @Autowired
    private RunScheduleManager runScheduleManager;

    @Autowired
    private PipelineDao pipelineDao;

    @Autowired
    private PipelineRunDao pipelineRunDao;

    @Autowired
    private CloudRegionDao regionDao;

    @Autowired
    private RunConfigurationDao configurationDao;

    @Autowired
    private UserManager userManager;

    private PipelineRunScheduleVO testRunScheduleVO;
    private PipelineRunScheduleVO testRunScheduleVO2;
    private PipelineRunScheduleVO testRunScheduleVO3;
    private RunConfiguration runConfiguration;

    @Before
    public void setUp() {
        Pipeline testPipeline = new Pipeline();
        testPipeline.setName(TEST_NAME);
        testPipeline.setRepository(TEST_REPOSITORY);
        testPipeline.setOwner(TEST_NAME);
        testPipeline.setRepositorySsh(TEST_PIPELINE_REPO_SSH);
        pipelineDao.createPipeline(testPipeline);

        createPipelineRun(RUN_ID, testPipeline.getId());
        runConfiguration = createRunConfiguration();

        PipelineUserVO userVO = new PipelineUserVO();
        userVO.setUserName(USER_OWNER);
        userManager.createUser(userVO);

        testRunScheduleVO = getRunScheduleVO(RunScheduledAction.PAUSE, CRON_EXPRESSION1);
        testRunScheduleVO2 = getRunScheduleVO(RunScheduledAction.RESUME, CRON_EXPRESSION2);
        testRunScheduleVO3 = getRunScheduleVO(RunScheduledAction.RUN, CRON_EXPRESSION2);

    }

    @Test
    @WithMockUser(username = USER_OWNER)
    public void testCreateRunSchedules() {
        final List<RunSchedule> runSchedules = runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                    Arrays.asList(testRunScheduleVO, testRunScheduleVO2));
        runSchedules.forEach(this::loadAndAssertSchedule);

        List<RunSchedule> configSchedules = runScheduleManager.createSchedules(runConfiguration.getId(),
                ScheduleType.RUN_CONFIGURATION, Collections.singletonList(testRunScheduleVO3));
        configSchedules.forEach(this::loadAndAssertSchedule);

    }

    @Test(expected = IllegalArgumentException.class)
    @WithMockUser(username = USER_OWNER)
    public void testCreateRunScheduleWithExistentCronExpression() {
        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Collections.singletonList(testRunScheduleVO));
        testRunScheduleVO.setAction(RunScheduledAction.RESUME);
        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Collections.singletonList(testRunScheduleVO));
    }

    @Test(expected = IllegalArgumentException.class)
    @WithMockUser(username = USER_OWNER)
    public void  createSchedulesWithIdenticalExpressions() {
        final PipelineRunScheduleVO runScheduleVO = getRunScheduleVO(RunScheduledAction.PAUSE, CRON_EXPRESSION1);
        final PipelineRunScheduleVO runScheduleVO2 = getRunScheduleVO(RunScheduledAction.RESUME, CRON_EXPRESSION1);
        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Arrays.asList(runScheduleVO, runScheduleVO2));
    }

    @Test(expected = IllegalArgumentException.class)
    @WithMockUser(username = USER_OWNER)
    public void  createSchedulesWithWrongAction() {
        final PipelineRunScheduleVO runScheduleVO = getRunScheduleVO(RunScheduledAction.RESUME, CRON_EXPRESSION1);
        runScheduleManager.createSchedules(RUN_ID, ScheduleType.RUN_CONFIGURATION,
                Collections.singletonList(runScheduleVO));
    }

    @Test
    @WithMockUser(username = USER_OWNER)
    public void testUpdateRunSchedules() {
        final List<RunSchedule> schedules =
            runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                    Arrays.asList(testRunScheduleVO, testRunScheduleVO2));

        testRunScheduleVO.setScheduleId(schedules.get(0).getId());
        testRunScheduleVO.setCronExpression(CRON_EXPRESSION2);
        testRunScheduleVO2.setScheduleId(schedules.get(1).getId());
        testRunScheduleVO2.setCronExpression(CRON_EXPRESSION1);

        final List<RunSchedule> updatedSchedules =
            runScheduleManager.updateSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                    Arrays.asList(testRunScheduleVO, testRunScheduleVO2));
        updatedSchedules.forEach(this::loadAndAssertSchedule);

        final List<RunSchedule> loadRunSchedule = runScheduleManager
                .loadAllSchedulesBySchedulableId(RUN_ID, ScheduleType.PIPELINE_RUN);
        assertEquals(2, loadRunSchedule.size());
    }

    @Test(expected = IllegalArgumentException.class)
    @WithMockUser(username = USER_OWNER)
    public void testUpdateRunSchedulesFailsWithoutScheduleId() {
        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                        Collections.singletonList(testRunScheduleVO));

        testRunScheduleVO.setCronExpression(CRON_EXPRESSION2);

        runScheduleManager.updateSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                        Collections.singletonList(testRunScheduleVO));
    }

    @Test
    @WithMockUser(username = USER_OWNER)
    public void testUpdateRunSchedulesWithIdenticalCron() {
        final List<RunSchedule> schedules =
            runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                    Arrays.asList(testRunScheduleVO, testRunScheduleVO2));

        testRunScheduleVO.setScheduleId(schedules.get(0).getId());
        testRunScheduleVO.setCronExpression(CRON_EXPRESSION2);
        testRunScheduleVO2.setScheduleId(schedules.get(1).getId());
        testRunScheduleVO2.setCronExpression(CRON_EXPRESSION1);

        final List<RunSchedule> updatedSchedules =
            runScheduleManager.updateSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                    Arrays.asList(testRunScheduleVO, testRunScheduleVO2));
        updatedSchedules.forEach(this::loadAndAssertSchedule);

        final List<RunSchedule> loadRunSchedule = runScheduleManager
                .loadAllSchedulesBySchedulableId(RUN_ID, ScheduleType.PIPELINE_RUN);
        assertEquals(2, loadRunSchedule.size());
    }

    @Test
    @WithMockUser(username = USER_OWNER)
    public void testDeleteRunSchedules() {
        final List<RunSchedule> schedules =
            runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                    Arrays.asList(testRunScheduleVO, testRunScheduleVO2));
        final List<Long> ids = schedules.stream().map(RunSchedule::getId).collect(Collectors.toList());
        runScheduleManager.deleteSchedules(RUN_ID, ScheduleType.PIPELINE_RUN, ids);
        final List<RunSchedule> loadRunSchedule = runScheduleManager
                .loadAllSchedulesBySchedulableId(RUN_ID, ScheduleType.PIPELINE_RUN);
        assertEquals(0, loadRunSchedule.size());
    }

    @Test(expected = IllegalArgumentException.class)
    @WithMockUser(username = USER_OWNER)
    public void loadRunScheduleByWrongId() {
        final RunSchedule runSchedule =
            runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                    Collections.singletonList(testRunScheduleVO)).get(0);
        runScheduleManager.loadSchedule(runSchedule.getSchedulableId());
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

    private RunConfiguration createRunConfiguration() {
        //create
        RunConfigurationEntry entry =
                ObjectCreatorUtils.createConfigEntry("entry", true, null);

        RunConfiguration configuration =
                ObjectCreatorUtils.createConfiguration(TEST_NAME, null, null,
                        USER_OWNER, Collections.singletonList(entry));
        return configurationDao.create(configuration);
    }


    private void loadAndAssertSchedule(final RunSchedule schedule) {
        final RunSchedule loadRunSchedule = runScheduleManager.loadSchedule(schedule.getId());
        assertEquals(schedule.getSchedulableId(), loadRunSchedule.getSchedulableId());
        assertEquals(schedule.getType(), loadRunSchedule.getType());
        assertEquals(schedule.getCronExpression(), loadRunSchedule.getCronExpression());
        assertEquals(schedule.getAction(), loadRunSchedule.getAction());
        assertEquals(schedule.getUser(), loadRunSchedule.getUser());
    }
}
