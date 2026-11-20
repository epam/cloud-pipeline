/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.manager.scheduling.RunScheduler;
<<<<<<< ours
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.repository.run.PipelineRunServiceUrlRepository;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
||||||| base
import com.epam.pipeline.manager.scheduling.ScheduleProviderManager;
import com.epam.pipeline.manager.scheduling.provider.PipelineRunScheduleProvider;
import com.epam.pipeline.manager.scheduling.provider.RunConfigurationScheduleProvider;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Matchers.any;

import org.mockito.MockitoAnnotations;
=======
import com.epam.pipeline.manager.scheduling.ScheduleProviderManager;
import com.epam.pipeline.manager.scheduling.provider.PipelineRunScheduleProvider;
import com.epam.pipeline.manager.scheduling.provider.RunConfigurationScheduleProvider;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
>>>>>>> theirs

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

<<<<<<< ours
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
||||||| base
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
=======
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
>>>>>>> theirs
import static org.mockito.Mockito.doReturn;
<<<<<<< ours
||||||| base
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
=======
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.quality.Strictness.LENIENT;
>>>>>>> theirs

<<<<<<< ours
@DirtiesContext
@Transactional
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
public class RunScheduleManagerTest extends AbstractManagerTest {
||||||| base
public class RunScheduleManagerTest {
=======
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
public class RunScheduleManagerTest {
>>>>>>> theirs

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

    @Autowired
    private PipelineRunServiceUrlRepository pipelineRunServiceUrlRepository;

    @MockBean
    private RunScheduler runScheduler;

    private PipelineRunScheduleVO testRunScheduleVO;
    private PipelineRunScheduleVO testRunScheduleVO2;
    private PipelineRunScheduleVO testRunScheduleVO3;
    private RunConfiguration runConfiguration;
    private Pipeline testPipeline;

    @BeforeEach
    public void setUp() {
<<<<<<< ours
        testPipeline = new Pipeline();
        testPipeline.setName(TEST_NAME);
        testPipeline.setRepository(TEST_REPOSITORY);
        testPipeline.setOwner(TEST_NAME);
        testPipeline.setRepositorySsh(TEST_PIPELINE_REPO_SSH);
        pipelineDao.createPipeline(testPipeline);

        createPipelineRun(RUN_ID, testPipeline.getId());
        runConfiguration = createRunConfiguration();

        PipelineUserVO userVO = new PipelineUserVO();
        userVO.setUserName(USER_OWNER);
        userManager.create(userVO);

        testRunScheduleVO = getRunScheduleVO(RunScheduledAction.PAUSE, CRON_EXPRESSION1);
        testRunScheduleVO2 = getRunScheduleVO(RunScheduledAction.RESUME, CRON_EXPRESSION2);
        testRunScheduleVO3 = getRunScheduleVO(RunScheduledAction.RUN, CRON_EXPRESSION2);

        doReturn(Collections.emptyList()).when(pipelineRunServiceUrlRepository)
                .findByPipelineRunId(any());
        doReturn(Collections.emptyList()).when(pipelineRunServiceUrlRepository)
                .findByPipelineRunIdIn(Collections.singletonList(any()));
||||||| base
        MockitoAnnotations.initMocks(this);

        doReturn(mockPipelineRunScheduleProvider)
                .when(mockScheduleProviderManager).getProvider(ScheduleType.PIPELINE_RUN);
        doReturn(mockRunConfigurationScheduleProvider)
                .when(mockScheduleProviderManager).getProvider(ScheduleType.RUN_CONFIGURATION);
        doReturn(mockPipelineUser).when(mockAuthManager).getCurrentUser();
=======
        doReturn(mockPipelineRunScheduleProvider)
                .when(mockScheduleProviderManager).getProvider(ScheduleType.PIPELINE_RUN);
        doReturn(mockRunConfigurationScheduleProvider)
                .when(mockScheduleProviderManager).getProvider(ScheduleType.RUN_CONFIGURATION);
        doReturn(mockPipelineUser).when(mockAuthManager).getCurrentUser();
>>>>>>> theirs
    }

    @Test
    @WithMockUser(username = USER_OWNER)
    public void testCreateRunSchedules() {
        final List<RunSchedule> runSchedules = runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                    Arrays.asList(testRunScheduleVO, testRunScheduleVO2));
        runSchedules.forEach(this::loadAndAssertSchedule);

<<<<<<< ours
        List<RunSchedule> configSchedules = runScheduleManager.createSchedules(runConfiguration.getId(),
||||||| base
        verify(mockRunScheduler, times(2)).scheduleRunSchedule(any());
        verify(mockRunScheduleDao).createRunSchedules(anyListOf(RunSchedule.class));
    }


    @Test
    public void testCreateRunConfigurationSchedules() {
        doReturn(runConfiguration).when(mockRunConfigurationManager).load(anyLong());

        runScheduleManager.createSchedules(runConfiguration.getId(),
=======
        verify(mockRunScheduler, times(2)).scheduleRunSchedule(any());
        verify(mockRunScheduleDao).createRunSchedules(anyList());
    }


    @Test
    public void testCreateRunConfigurationSchedules() {
        doReturn(runConfiguration).when(mockRunConfigurationManager).load(anyLong());

        runScheduleManager.createSchedules(runConfiguration.getId(),
>>>>>>> theirs
                ScheduleType.RUN_CONFIGURATION, Collections.singletonList(testRunScheduleVO3));
        configSchedules.forEach(this::loadAndAssertSchedule);

        Mockito.verify(runScheduler, Mockito.times(3)).scheduleRunSchedule(any());

<<<<<<< ours
||||||| base
        verify(mockRunScheduler).scheduleRunSchedule(any());
        verify(mockRunScheduleDao).createRunSchedules(anyListOf(RunSchedule.class));
=======
        verify(mockRunScheduler).scheduleRunSchedule(any());
        verify(mockRunScheduleDao).createRunSchedules(anyList());
>>>>>>> theirs
    }

<<<<<<< ours
    @Test(expected = IllegalArgumentException.class)
    @WithMockUser(username = USER_OWNER)
||||||| base
    @Test(expected = IllegalArgumentException.class)
=======
    @Test
>>>>>>> theirs
    public void testCreateRunScheduleWithExistentCronExpression() {
<<<<<<< ours
        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Collections.singletonList(testRunScheduleVO));
        testRunScheduleVO.setAction(RunScheduledAction.RESUME);
        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Collections.singletonList(testRunScheduleVO));
||||||| base
        doReturn(Collections
                .singletonList(runPauseSchedule))
                .when(mockRunScheduleDao).loadAllRunSchedulesBySchedulableIdAndType(RUN_ID, ScheduleType.PIPELINE_RUN);

        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Collections.singletonList(testRunScheduleVO));
=======
        doReturn(Collections
                .singletonList(runPauseSchedule))
                .when(mockRunScheduleDao).loadAllRunSchedulesBySchedulableIdAndType(RUN_ID, ScheduleType.PIPELINE_RUN);

        assertThrows(IllegalArgumentException.class, () -> runScheduleManager.createSchedules(RUN_ID,
            ScheduleType.PIPELINE_RUN, Collections.singletonList(testRunScheduleVO)));
>>>>>>> theirs
    }

<<<<<<< ours
    @Test(expected = IllegalArgumentException.class)
    @WithMockUser(username = USER_OWNER)
    public void  createSchedulesWithIdenticalExpressions() {
        final PipelineRunScheduleVO runScheduleVO = getRunScheduleVO(RunScheduledAction.PAUSE, CRON_EXPRESSION1);
        final PipelineRunScheduleVO runScheduleVO2 = getRunScheduleVO(RunScheduledAction.RESUME, CRON_EXPRESSION1);
        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Arrays.asList(runScheduleVO, runScheduleVO2));
||||||| base
    @Test(expected = IllegalArgumentException.class)
    public void createSchedulesWithIdenticalExpressions() {
        final PipelineRunScheduleVO runScheduleVO =
                PipelineCreatorUtils.getPipelineRunScheduleVO(RunScheduledAction.PAUSE, CRON_EXPRESSION1, TIME_ZONE);
        final PipelineRunScheduleVO runScheduleVO2 =
                PipelineCreatorUtils.getPipelineRunScheduleVO(RunScheduledAction.RESUME, CRON_EXPRESSION1, TIME_ZONE);
        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Arrays.asList(runScheduleVO, runScheduleVO2));
=======
    @Test
    public void createSchedulesWithIdenticalExpressions() {
        final PipelineRunScheduleVO runScheduleVO =
                PipelineCreatorUtils.getPipelineRunScheduleVO(RunScheduledAction.PAUSE, CRON_EXPRESSION1, TIME_ZONE);
        final PipelineRunScheduleVO runScheduleVO2 =
                PipelineCreatorUtils.getPipelineRunScheduleVO(RunScheduledAction.RESUME, CRON_EXPRESSION1, TIME_ZONE);
        assertThrows(IllegalArgumentException.class, () -> runScheduleManager.createSchedules(RUN_ID,
            ScheduleType.PIPELINE_RUN, Arrays.asList(runScheduleVO, runScheduleVO2)));
>>>>>>> theirs
    }

<<<<<<< ours
    @Test(expected = IllegalArgumentException.class)
    @WithMockUser(username = USER_OWNER)
    public void  createSchedulesWithWrongAction() {
        final PipelineRunScheduleVO runScheduleVO = getRunScheduleVO(RunScheduledAction.RESUME, CRON_EXPRESSION1);
        runScheduleManager.createSchedules(RUN_ID, ScheduleType.RUN_CONFIGURATION,
                Collections.singletonList(runScheduleVO));
||||||| base
    @Test(expected = IllegalArgumentException.class)
    public void createInvalidSchedules() {
        final PipelineRunScheduleVO runScheduleVO =
                PipelineCreatorUtils.getPipelineRunScheduleVO(RunScheduledAction.RESUME, CRON_EXPRESSION1, TIME_ZONE);
        doThrow(IllegalArgumentException.class)
                .when(mockRunConfigurationScheduleProvider)
                .verifyScheduleVO(RUN_ID, runScheduleVO);

        runScheduleManager.createSchedules(RUN_ID, ScheduleType.RUN_CONFIGURATION,
                Collections.singletonList(runScheduleVO));
=======
    @Test
    public void createInvalidSchedules() {
        final PipelineRunScheduleVO runScheduleVO =
                PipelineCreatorUtils.getPipelineRunScheduleVO(RunScheduledAction.RESUME, CRON_EXPRESSION1, TIME_ZONE);
        doThrow(IllegalArgumentException.class)
                .when(mockRunConfigurationScheduleProvider)
                .verifyScheduleVO(RUN_ID, runScheduleVO);
        assertThrows(IllegalArgumentException.class, () -> runScheduleManager.createSchedules(RUN_ID,
            ScheduleType.RUN_CONFIGURATION, Collections.singletonList(runScheduleVO)));
>>>>>>> theirs
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

<<<<<<< ours
        final List<RunSchedule> updatedSchedules =
            runScheduleManager.updateSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                    Arrays.asList(testRunScheduleVO, testRunScheduleVO2));
        updatedSchedules.forEach(this::loadAndAssertSchedule);

        final List<RunSchedule> loadRunSchedule = runScheduleManager
                .loadAllSchedulesBySchedulableId(RUN_ID, ScheduleType.PIPELINE_RUN);
        assertEquals(2, loadRunSchedule.size());
        Mockito.verify(runScheduler, Mockito.times(2)).unscheduleRunSchedule(any());
        // 2 for creation and 2 from update
        Mockito.verify(runScheduler, Mockito.times(4)).scheduleRunSchedule(any());
||||||| base
        doReturn(Arrays.asList(runPauseSchedule, runResumeSchedule))
                .when(mockRunScheduleDao).loadAllRunSchedulesBySchedulableIdAndType(RUN_ID, ScheduleType.PIPELINE_RUN);

        runScheduleManager.updateSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Arrays.asList(testRunScheduleVO, testRunScheduleVO2));

        verify(mockRunScheduler, times(2)).scheduleRunSchedule(any());
        verify(mockRunScheduler, times(2)).unscheduleRunSchedule(any());
        verify(mockRunScheduleDao).updateRunSchedules(anyListOf(RunSchedule.class));
=======
        doReturn(Arrays.asList(runPauseSchedule, runResumeSchedule))
                .when(mockRunScheduleDao).loadAllRunSchedulesBySchedulableIdAndType(RUN_ID, ScheduleType.PIPELINE_RUN);

        runScheduleManager.updateSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Arrays.asList(testRunScheduleVO, testRunScheduleVO2));

        verify(mockRunScheduler, times(2)).scheduleRunSchedule(any());
        verify(mockRunScheduler, times(2)).unscheduleRunSchedule(any());
        verify(mockRunScheduleDao).updateRunSchedules(anyList());
>>>>>>> theirs
    }

<<<<<<< ours
    @Test(expected = IllegalArgumentException.class)
    @WithMockUser(username = USER_OWNER)
||||||| base
    @Test(expected = IllegalArgumentException.class)
=======
    @Test
>>>>>>> theirs
    public void testUpdateRunSchedulesFailsWithoutScheduleId() {
<<<<<<< ours
        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                        Collections.singletonList(testRunScheduleVO));

        testRunScheduleVO.setCronExpression(CRON_EXPRESSION2);

        runScheduleManager.updateSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                        Collections.singletonList(testRunScheduleVO));
||||||| base

        runScheduleManager.updateSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Collections.singletonList(testRunScheduleVO));
=======
        assertThrows(IllegalArgumentException.class,
            () -> runScheduleManager.updateSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Collections.singletonList(testRunScheduleVO)));
>>>>>>> theirs
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

<<<<<<< ours
        final List<RunSchedule> loadRunSchedule = runScheduleManager
                .loadAllSchedulesBySchedulableId(RUN_ID, ScheduleType.PIPELINE_RUN);
        assertEquals(2, loadRunSchedule.size());
||||||| base
        verify(mockRunScheduler, times(2)).scheduleRunSchedule(any());
        verify(mockRunScheduler, times(2)).unscheduleRunSchedule(any());
        verify(mockRunScheduleDao).updateRunSchedules(anyListOf(RunSchedule.class));
=======
        verify(mockRunScheduler, times(2)).scheduleRunSchedule(any());
        verify(mockRunScheduler, times(2)).unscheduleRunSchedule(any());
        verify(mockRunScheduleDao).updateRunSchedules(anyList());
>>>>>>> theirs
    }

    @Test
    @WithMockUser(username = USER_OWNER)
    public void testDeleteRunSchedules() {
        final List<RunSchedule> schedules =
            runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                    Arrays.asList(testRunScheduleVO, testRunScheduleVO2));
        final List<Long> ids = schedules.stream().map(RunSchedule::getId).collect(Collectors.toList());
        Mockito.verify(runScheduler, Mockito.times(2)).scheduleRunSchedule(any());


        runScheduleManager.deleteSchedules(RUN_ID, ScheduleType.PIPELINE_RUN, ids);
        final List<RunSchedule> loadRunSchedule = runScheduleManager
                .loadAllSchedulesBySchedulableId(RUN_ID, ScheduleType.PIPELINE_RUN);
        assertEquals(0, loadRunSchedule.size());
        Mockito.verify(runScheduler, Mockito.times(2)).unscheduleRunSchedule(any());
    }

    @Test
    @WithMockUser(username = USER_OWNER)
    public void testDeleteAllRunSchedules() {
        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                        Arrays.asList(testRunScheduleVO, testRunScheduleVO2));
        Mockito.verify(runScheduler, Mockito.times(2)).scheduleRunSchedule(any());

        runScheduleManager.deleteSchedules(RUN_ID, ScheduleType.PIPELINE_RUN);
        final List<RunSchedule> loadRunSchedule = runScheduleManager
                .loadAllSchedulesBySchedulableId(RUN_ID, ScheduleType.PIPELINE_RUN);
        assertEquals(0, loadRunSchedule.size());
        Mockito.verify(runScheduler, Mockito.times(2)).unscheduleRunSchedule(any());

    }

    @Test
    @WithMockUser(username = USER_OWNER)
    public void shouldUnscheduleRunSchedulesForPipeline() {
        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Arrays.asList(testRunScheduleVO, testRunScheduleVO2));
        Mockito.verify(runScheduler, Mockito.times(2)).scheduleRunSchedule(any());

        runScheduleManager.deleteSchedulesForRunByPipeline(testPipeline.getId());

        Mockito.verify(runScheduler, Mockito.times(2)).unscheduleRunSchedule(any());
    }

<<<<<<< ours
    @Test(expected = IllegalArgumentException.class)
    @WithMockUser(username = USER_OWNER)
||||||| base
    @Test(expected = IllegalArgumentException.class)
=======
    @Test
>>>>>>> theirs
    public void loadRunScheduleByWrongId() {
        final RunSchedule runSchedule =
            runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                    Collections.singletonList(testRunScheduleVO)).get(0);
        runScheduleManager.loadSchedule(runSchedule.getSchedulableId());
    }

<<<<<<< ours
    private PipelineRunScheduleVO getRunScheduleVO(final RunScheduledAction action, final String cronExpression) {
        PipelineRunScheduleVO runScheduleVO = new PipelineRunScheduleVO();
        runScheduleVO.setTimeZone(TIME_ZONE.getDisplayName());
        runScheduleVO.setCronExpression(cronExpression);
        runScheduleVO.setAction(action);
        return runScheduleVO;
||||||| base
        runScheduleManager.loadSchedule(RUN_ID);
=======
        assertThrows(IllegalArgumentException.class, () -> runScheduleManager.loadSchedule(RUN_ID));
>>>>>>> theirs
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
