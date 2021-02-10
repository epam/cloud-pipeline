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

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.PipelineRunScheduleVO;
import com.epam.pipeline.controller.vo.PipelineUserVO;
import com.epam.pipeline.dao.pipeline.RunScheduleDao;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.pipeline.run.ScheduleType;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.scheduling.RunScheduler;
import com.epam.pipeline.manager.scheduling.ScheduleProviderManager;
import com.epam.pipeline.manager.scheduling.provider.PipelineRunScheduleProvider;
import com.epam.pipeline.manager.scheduling.provider.RunConfigurationScheduleProvider;
import com.epam.pipeline.manager.security.AuthManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.Matchers.any;

import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.eq;

public class RunScheduleManagerTest {

    private static final Long RUN_ID = 1L;
    private static final String CRON_EXPRESSION1 = "0 0 12 * * ?";
    private static final String CRON_EXPRESSION2 = "0 15 10 ? * *";
    private static final String TEST_NAME = "TEST";
    private static final String TEST_REPOSITORY = "///";
    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone("UTC");
    private static final String TEST_PIPELINE_REPO_SSH = "git@test";
    private static final String USER_OWNER = "OWNER";
    private static final String ENTRY_NAME = "entry";

    @InjectMocks
    private RunScheduleManager runScheduleManager;

    @Mock
    private RunScheduler mockRunScheduler;

    @Mock
    private ScheduleProviderManager mockScheduleProviderManager;

    @Mock
    private RunScheduleDao mockRunScheduleDao;

    @Mock
    private PipelineRunScheduleProvider mockPipelineRunScheduleProvider;

    @Mock
    private AuthManager mockAuthManager;

    @Mock
    private PipelineUser mockPipelineUser;

    @Mock
    private RunConfigurationScheduleProvider mockRunConfigurationScheduleProvider;

    @Mock
    private MessageHelper mockMessageHelper;

    @Mock
    private PipelineRunManager mockPipelineRunManager;


    private PipelineRunScheduleVO testRunScheduleVO;
    private PipelineRunScheduleVO testRunScheduleVO2;
    private PipelineRunScheduleVO testRunScheduleVO3;
    private RunConfiguration runConfiguration;
    private Pipeline testPipeline;

    @Before
    public void setUp() {
        testPipeline = new Pipeline();
        testPipeline.setName(TEST_NAME);
        testPipeline.setRepository(TEST_REPOSITORY);
        testPipeline.setOwner(TEST_NAME);
        testPipeline.setRepositorySsh(TEST_PIPELINE_REPO_SSH);

        createPipelineRun(RUN_ID, testPipeline.getId());

        runConfiguration = createRunConfiguration();
        runConfiguration.setId(2L);

        PipelineUserVO userVO = new PipelineUserVO();
        userVO.setUserName(USER_OWNER);

        testRunScheduleVO = getRunScheduleVO(RunScheduledAction.PAUSE, CRON_EXPRESSION1);
        testRunScheduleVO2 = getRunScheduleVO(RunScheduledAction.RESUME, CRON_EXPRESSION2);
        testRunScheduleVO3 = getRunScheduleVO(RunScheduledAction.RUN, CRON_EXPRESSION2);

        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCreateRunSchedules() {
        when(mockRunScheduleDao.loadAllRunSchedules()).thenReturn(Collections.emptyList());
        when(mockScheduleProviderManager.getProvider(ScheduleType.PIPELINE_RUN)).thenReturn(mockPipelineRunScheduleProvider);
        when(mockScheduleProviderManager.getProvider(ScheduleType.RUN_CONFIGURATION)).thenReturn(mockRunConfigurationScheduleProvider);
        when(mockAuthManager.getCurrentUser()).thenReturn(mockPipelineUser);

        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Arrays.asList(testRunScheduleVO, testRunScheduleVO2));

        runScheduleManager.createSchedules(runConfiguration.getId(),
                ScheduleType.RUN_CONFIGURATION, Collections.singletonList(testRunScheduleVO3));

        verify(mockRunScheduler, times(3)).scheduleRunSchedule(any());
        verify(mockRunScheduleDao, times(2)).createRunSchedules(anyList());
        verify(mockScheduleProviderManager, times(3)).getProvider(ScheduleType.PIPELINE_RUN);
        verify(mockScheduleProviderManager, times(2)).getProvider(ScheduleType.RUN_CONFIGURATION);
    }


    @Test(expected = IllegalArgumentException.class)
    public void testCreateRunScheduleWithExistentCronExpression() {
        when(mockScheduleProviderManager.getProvider(ScheduleType.PIPELINE_RUN)).thenReturn(mockPipelineRunScheduleProvider);
        when(mockAuthManager.getCurrentUser()).thenReturn(mockPipelineUser);

        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Collections.singletonList(testRunScheduleVO));
        testRunScheduleVO.setAction(RunScheduledAction.RESUME);

        doReturn(Collections.singletonList(createRunSchedule())).
                when(mockRunScheduleDao).loadAllRunSchedulesBySchedulableIdAndType(RUN_ID, ScheduleType.PIPELINE_RUN);

        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Collections.singletonList(testRunScheduleVO));
    }

    @Test(expected = IllegalArgumentException.class)
    public void createSchedulesWithIdenticalExpressions() {
        final PipelineRunScheduleVO runScheduleVO = getRunScheduleVO(RunScheduledAction.PAUSE, CRON_EXPRESSION1);
        final PipelineRunScheduleVO runScheduleVO2 = getRunScheduleVO(RunScheduledAction.RESUME, CRON_EXPRESSION1);
        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Arrays.asList(runScheduleVO, runScheduleVO2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void createSchedulesWithWrongAction() {
        doThrow(IllegalArgumentException.class).when(mockScheduleProviderManager).getProvider(ScheduleType.RUN_CONFIGURATION);
        final PipelineRunScheduleVO runScheduleVO = getRunScheduleVO(RunScheduledAction.RESUME, CRON_EXPRESSION1);
        runScheduleManager.createSchedules(RUN_ID, ScheduleType.RUN_CONFIGURATION,
                Collections.singletonList(runScheduleVO));
    }

    @Test
    public void testUpdateRunSchedules() {
        when(mockScheduleProviderManager.getProvider(ScheduleType.PIPELINE_RUN)).thenReturn(mockPipelineRunScheduleProvider);
        when(mockAuthManager.getCurrentUser()).thenReturn(mockPipelineUser);

        final List<RunSchedule> schedules =
                runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                        Arrays.asList(testRunScheduleVO, testRunScheduleVO2));

        schedules.get(0).setId(1L);
        schedules.get(1).setId(2L);

        testRunScheduleVO.setScheduleId(schedules.get(0).getId());
        testRunScheduleVO.setCronExpression(CRON_EXPRESSION2);
        testRunScheduleVO2.setScheduleId(schedules.get(1).getId());
        testRunScheduleVO2.setCronExpression(CRON_EXPRESSION1);

        when(mockRunScheduleDao.loadAllRunSchedulesBySchedulableIdAndType(RUN_ID, ScheduleType.PIPELINE_RUN)).thenReturn(schedules);

        runScheduleManager.updateSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Arrays.asList(testRunScheduleVO, testRunScheduleVO2));

        final List<RunSchedule> loadRunSchedule = runScheduleManager
                .loadAllSchedulesBySchedulableId(RUN_ID, ScheduleType.PIPELINE_RUN);

        assertEquals(2, loadRunSchedule.size());
        verify(mockRunScheduler, times(2)).unscheduleRunSchedule(any());
        // 2 for creation and 2 from update
        verify(mockRunScheduler, times(4)).scheduleRunSchedule(any());
        verify(mockRunScheduleDao, times(1)).createRunSchedules(anyList());
        verify(mockRunScheduleDao, times(1)).updateRunSchedules(anyList());
        verify(mockRunScheduleDao, times(4)).loadAllRunSchedulesBySchedulableIdAndType(anyLong(), eq(ScheduleType.PIPELINE_RUN));
        verify(mockScheduleProviderManager, times(6)).getProvider(ScheduleType.PIPELINE_RUN);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUpdateRunSchedulesFailsWithoutScheduleId() {
        when(mockScheduleProviderManager.getProvider(ScheduleType.PIPELINE_RUN)).thenReturn(mockPipelineRunScheduleProvider);
        when(mockAuthManager.getCurrentUser()).thenReturn(mockPipelineUser);
        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Collections.singletonList(testRunScheduleVO));

        testRunScheduleVO.setCronExpression(CRON_EXPRESSION2);

        runScheduleManager.updateSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Collections.singletonList(testRunScheduleVO));
    }

    @Test
    public void testUpdateRunSchedulesWithIdenticalCron() {
        when(mockScheduleProviderManager.getProvider(ScheduleType.PIPELINE_RUN)).thenReturn(mockPipelineRunScheduleProvider);
        when(mockAuthManager.getCurrentUser()).thenReturn(mockPipelineUser);

        final List<RunSchedule> schedules =
                runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                        Arrays.asList(testRunScheduleVO, testRunScheduleVO2));

        testRunScheduleVO.setScheduleId(schedules.get(0).getSchedulableId());
        testRunScheduleVO.setCronExpression(CRON_EXPRESSION2);
        testRunScheduleVO2.setScheduleId(schedules.get(1).getSchedulableId());
        testRunScheduleVO2.setCronExpression(CRON_EXPRESSION1);

        schedules.get(0).setId(1L);
        schedules.get(1).setId(2L);

        when(mockRunScheduleDao.loadAllRunSchedulesBySchedulableIdAndType(RUN_ID, ScheduleType.PIPELINE_RUN)).thenReturn(schedules);

        runScheduleManager.updateSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Arrays.asList(testRunScheduleVO, testRunScheduleVO2));

        final List<RunSchedule> loadRunSchedule = runScheduleManager
                .loadAllSchedulesBySchedulableId(RUN_ID, ScheduleType.PIPELINE_RUN);
        assertEquals(2, loadRunSchedule.size());
    }

    @Test
    public void testDeleteRunSchedules() {
        when(mockScheduleProviderManager.getProvider(ScheduleType.PIPELINE_RUN)).thenReturn(mockPipelineRunScheduleProvider);
        when(mockAuthManager.getCurrentUser()).thenReturn(mockPipelineUser);

        final List<RunSchedule> schedules =
                runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                        Arrays.asList(testRunScheduleVO, testRunScheduleVO2));

        schedules.get(0).setId(1L);
        schedules.get(1).setId(2L);

        final List<Long> ids = schedules.stream().map(RunSchedule::getId).collect(Collectors.toList());
        Mockito.verify(mockRunScheduler, Mockito.times(2)).scheduleRunSchedule(any());

        when(mockRunScheduleDao.loadRunSchedule(1L)).thenReturn(Optional.of(createRunSchedule(1L, RUN_ID, RunScheduledAction.PAUSE, CRON_EXPRESSION1)));
        when(mockRunScheduleDao.loadRunSchedule(2L)).thenReturn(Optional.of(createRunSchedule(2L, RUN_ID, RunScheduledAction.RESUME, CRON_EXPRESSION2)));

        runScheduleManager.deleteSchedules(RUN_ID, ScheduleType.PIPELINE_RUN, ids);

        when(mockRunScheduleDao.loadAllRunSchedulesBySchedulableIdAndType(RUN_ID, ScheduleType.PIPELINE_RUN)).thenReturn(new ArrayList<>());
        final List<RunSchedule> loadRunSchedule = runScheduleManager
                .loadAllSchedulesBySchedulableId(RUN_ID, ScheduleType.PIPELINE_RUN);

        assertEquals(0, loadRunSchedule.size());
        verify(mockRunScheduler, times(2)).unscheduleRunSchedule(any());
        verify(mockScheduleProviderManager, times(3)).getProvider(ScheduleType.PIPELINE_RUN);
        verify(mockRunScheduleDao, times(2)).loadRunSchedule(anyLong());
        verify(mockRunScheduleDao, times(3)).loadAllRunSchedulesBySchedulableIdAndType(anyLong(), eq(ScheduleType.PIPELINE_RUN));

    }

    @Test
    public void testDeleteAllRunSchedules() {
        when(mockScheduleProviderManager.getProvider(ScheduleType.PIPELINE_RUN)).thenReturn(mockPipelineRunScheduleProvider);
        when(mockAuthManager.getCurrentUser()).thenReturn(mockPipelineUser);

        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Arrays.asList(testRunScheduleVO, testRunScheduleVO2));
        verify(mockRunScheduler, times(2)).scheduleRunSchedule(any());

        runScheduleManager.deleteSchedules(RUN_ID, ScheduleType.PIPELINE_RUN);

        when(mockRunScheduleDao.loadAllRunSchedulesBySchedulableIdAndType(RUN_ID, ScheduleType.PIPELINE_RUN)).thenReturn(new ArrayList<>());
        final List<RunSchedule> loadRunSchedule = runScheduleManager
                .loadAllSchedulesBySchedulableId(RUN_ID, ScheduleType.PIPELINE_RUN);
        assertEquals(0, loadRunSchedule.size());
    }

    @Test
    public void testDeleteRunSchedulesForPipeline() {
        when(mockScheduleProviderManager.getProvider(ScheduleType.PIPELINE_RUN)).thenReturn(mockPipelineRunScheduleProvider);
        when(mockAuthManager.getCurrentUser()).thenReturn(mockPipelineUser);

        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Arrays.asList(testRunScheduleVO, testRunScheduleVO2));

        when(mockPipelineRunManager.loadAllRunsByPipeline(testPipeline.getId())).thenReturn(Arrays.asList(createPipelineRun(RUN_ID, testPipeline.getId())));

        runScheduleManager.deleteSchedulesForRunByPipeline(testPipeline.getId());

        when(mockRunScheduleDao.loadAllRunSchedulesBySchedulableIdAndType(RUN_ID, ScheduleType.PIPELINE_RUN)).thenReturn(new ArrayList<>());

        final List<RunSchedule> loadRunSchedule = runScheduleManager
                .loadAllSchedulesBySchedulableId(RUN_ID, ScheduleType.PIPELINE_RUN);

        assertEquals(0, loadRunSchedule.size());

        verify(mockScheduleProviderManager, times(3)).getProvider(ScheduleType.PIPELINE_RUN);
        verify(mockPipelineRunManager, times(1)).loadAllRunsByPipeline(anyLong());
        verify(mockRunScheduleDao, times(4)).loadAllRunSchedulesBySchedulableIdAndType(anyLong(), eq(ScheduleType.PIPELINE_RUN));
    }

    @Test(expected = IllegalArgumentException.class)
    public void loadRunScheduleByWrongId() {
        when(mockRunScheduleDao.loadAllRunSchedules()).thenReturn(Collections.emptyList());
        when(mockScheduleProviderManager.getProvider(ScheduleType.PIPELINE_RUN)).thenReturn(mockPipelineRunScheduleProvider);
        when(mockAuthManager.getCurrentUser()).thenReturn(mockPipelineUser);

        final RunSchedule runSchedule =
                runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                        Collections.singletonList(testRunScheduleVO)).get(0);
        doThrow(IllegalArgumentException.class).when(mockRunScheduleDao).loadRunSchedule(runSchedule.getSchedulableId());

        runScheduleManager.loadSchedule(runSchedule.getSchedulableId());

        verify(mockScheduleProviderManager, times(1)).getProvider(ScheduleType.PIPELINE_RUN);
        verify(mockPipelineRunManager, times(2)).loadAllRunsByPipeline(anyLong());
        verify(mockRunScheduleDao.loadAllRunSchedulesBySchedulableIdAndType(anyLong(), ScheduleType.PIPELINE_RUN), times(3));

    }


    private PipelineRunScheduleVO getRunScheduleVO(final RunScheduledAction action, final String cronExpression) {
        PipelineRunScheduleVO runScheduleVO = new PipelineRunScheduleVO();
        runScheduleVO.setTimeZone(TIME_ZONE.getDisplayName());
        runScheduleVO.setCronExpression(cronExpression);
        runScheduleVO.setAction(action);
        return runScheduleVO;
    }

    private PipelineRun createPipelineRun(final Long runId, final Long pipelineId) {
        final AbstractCloudRegion cloudRegion = ObjectCreatorUtils.getDefaultAwsRegion();
        PipelineRun run = ObjectCreatorUtils.createPipelineRun(runId, pipelineId, null, cloudRegion.getId());
        run.setStatus(TaskStatus.RUNNING);
        run.getInstance().setSpot(false);
        return run;
    }

    private RunConfiguration createRunConfiguration() {
        RunConfigurationEntry entry =
                ObjectCreatorUtils.createConfigEntry(ENTRY_NAME, true, null);

        RunConfiguration configuration =
                ObjectCreatorUtils.createConfiguration(TEST_NAME, null, null,
                        USER_OWNER, Collections.singletonList(entry));
        return configuration;
    }

    private RunSchedule createRunSchedule() {
        final RunSchedule runSchedule = new RunSchedule();
        runSchedule.setUser(USER_OWNER);
        runSchedule.setAction(RunScheduledAction.PAUSE);
        runSchedule.setSchedulableId(RUN_ID);
        runSchedule.setType(ScheduleType.PIPELINE_RUN);
        runSchedule.setCronExpression(CRON_EXPRESSION1);
        runSchedule.setCreatedDate(new Date());
        runSchedule.setTimeZone(TimeZone.getTimeZone("GMT"));
        return runSchedule;
    }

    private RunSchedule createRunSchedule(final Long id, final Long schedulableId,
                                          final RunScheduledAction action, final String cronExpression) {
        final RunSchedule runSchedule = new RunSchedule();
        runSchedule.setUser(USER_OWNER);
        runSchedule.setAction(action);
        runSchedule.setId(id);
        runSchedule.setSchedulableId(schedulableId);
        runSchedule.setType(ScheduleType.PIPELINE_RUN);
        runSchedule.setCronExpression(cronExpression);
        runSchedule.setCreatedDate(new Date());
        runSchedule.setTimeZone(TIME_ZONE);
        return runSchedule;
    }


}
