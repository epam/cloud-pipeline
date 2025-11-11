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
import com.epam.pipeline.dao.pipeline.RunScheduleDao;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.pipeline.run.ScheduleType;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.configuration.RunConfigurationManager;
import com.epam.pipeline.manager.scheduling.RunScheduler;
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

import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.security.access.AccessDeniedException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.quality.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
public class RunScheduleManagerTest {

    private static final Long RUN_ID = 1L;
    private static final Long PAUSE_SCHEDULE_ID = 1L;
    private static final Long RESUME_SCHEDULE_ID = 2L;
    private static final String CRON_EXPRESSION1 = "0 0 12 * * ?";
    private static final String CRON_EXPRESSION2 = "0 15 10 ? * *";
    private static final String TEST_NAME = "TEST";
    private static final String TIME_ZONE = "Coordinated Universal Time";

    private final RunConfiguration runConfiguration = ConfigurationCreatorUtils.getRunConfiguration(2L, TEST_NAME);
    private final Pipeline testPipeline = PipelineCreatorUtils.getPipeline(RUN_ID, TEST_NAME);
    private final PipelineRunScheduleVO testRunScheduleVO =
            PipelineCreatorUtils.getPipelineRunScheduleVO(RunScheduledAction.PAUSE, CRON_EXPRESSION1, TIME_ZONE);
    private final PipelineRunScheduleVO testRunScheduleVO2 =
            PipelineCreatorUtils.getPipelineRunScheduleVO(RunScheduledAction.RESUME, CRON_EXPRESSION2, TIME_ZONE);
    private final PipelineRunScheduleVO testRunScheduleVO3 =
            PipelineCreatorUtils.getPipelineRunScheduleVO(RunScheduledAction.RUN, CRON_EXPRESSION2, TIME_ZONE);
    private final RunSchedule runPauseSchedule =
            createRunSchedule(PAUSE_SCHEDULE_ID, RunScheduledAction.PAUSE, CRON_EXPRESSION1);
    private final RunSchedule runResumeSchedule =
            createRunSchedule(RESUME_SCHEDULE_ID, RunScheduledAction.RESUME, CRON_EXPRESSION2);

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
    @SuppressWarnings("PMD.UnusedPrivateField")
    private MessageHelper mockMessageHelper;

    @Mock
    private PipelineRunManager mockPipelineRunManager;

    @Mock
    private RunConfigurationManager mockRunConfigurationManager;

    @BeforeEach
    public void setUp() {
        doReturn(mockPipelineRunScheduleProvider)
                .when(mockScheduleProviderManager).getProvider(ScheduleType.PIPELINE_RUN);
        doReturn(mockRunConfigurationScheduleProvider)
                .when(mockScheduleProviderManager).getProvider(ScheduleType.RUN_CONFIGURATION);
        doReturn(mockPipelineUser).when(mockAuthManager).getCurrentUser();
    }


    @Test
    public void testCreatePipelineRunSchedules() {
        runScheduleManager.createSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Arrays.asList(testRunScheduleVO, testRunScheduleVO2));

        verify(mockRunScheduler, times(2)).scheduleRunSchedule(any());
        verify(mockRunScheduleDao).createRunSchedules(anyList());
    }


    @Test
    public void testCreateRunConfigurationSchedules() {
        doReturn(runConfiguration).when(mockRunConfigurationManager).load(anyLong());

        runScheduleManager.createSchedules(runConfiguration.getId(),
                ScheduleType.RUN_CONFIGURATION, Collections.singletonList(testRunScheduleVO3));

        verify(mockRunScheduler).scheduleRunSchedule(any());
        verify(mockRunScheduleDao).createRunSchedules(anyList());
    }

    @Test
    public void testCreateRunScheduleWithExistentCronExpression() {
        doReturn(Collections
                .singletonList(runPauseSchedule))
                .when(mockRunScheduleDao).loadAllRunSchedulesBySchedulableIdAndType(RUN_ID, ScheduleType.PIPELINE_RUN);

        assertThrows(IllegalArgumentException.class, () -> runScheduleManager.createSchedules(RUN_ID,
            ScheduleType.PIPELINE_RUN, Collections.singletonList(testRunScheduleVO)));
    }

    @Test
    public void createSchedulesWithIdenticalExpressions() {
        final PipelineRunScheduleVO runScheduleVO =
                PipelineCreatorUtils.getPipelineRunScheduleVO(RunScheduledAction.PAUSE, CRON_EXPRESSION1, TIME_ZONE);
        final PipelineRunScheduleVO runScheduleVO2 =
                PipelineCreatorUtils.getPipelineRunScheduleVO(RunScheduledAction.RESUME, CRON_EXPRESSION1, TIME_ZONE);
        assertThrows(IllegalArgumentException.class, () -> runScheduleManager.createSchedules(RUN_ID,
            ScheduleType.PIPELINE_RUN, Arrays.asList(runScheduleVO, runScheduleVO2)));
    }

    @Test
    public void createInvalidSchedules() {
        final PipelineRunScheduleVO runScheduleVO =
                PipelineCreatorUtils.getPipelineRunScheduleVO(RunScheduledAction.RESUME, CRON_EXPRESSION1, TIME_ZONE);
        doThrow(IllegalArgumentException.class)
                .when(mockRunConfigurationScheduleProvider)
                .verifyScheduleVO(RUN_ID, runScheduleVO);
        assertThrows(IllegalArgumentException.class, () -> runScheduleManager.createSchedules(RUN_ID,
            ScheduleType.RUN_CONFIGURATION, Collections.singletonList(runScheduleVO)));
    }

    @Test
    public void testUpdateRunSchedules() {
        testRunScheduleVO.setScheduleId(PAUSE_SCHEDULE_ID);
        testRunScheduleVO2.setScheduleId(RESUME_SCHEDULE_ID);
        testRunScheduleVO.setCronExpression(CRON_EXPRESSION2);
        testRunScheduleVO2.setCronExpression(CRON_EXPRESSION1);

        doReturn(Arrays.asList(runPauseSchedule, runResumeSchedule))
                .when(mockRunScheduleDao).loadAllRunSchedulesBySchedulableIdAndType(RUN_ID, ScheduleType.PIPELINE_RUN);

        runScheduleManager.updateSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Arrays.asList(testRunScheduleVO, testRunScheduleVO2));

        verify(mockRunScheduler, times(2)).scheduleRunSchedule(any());
        verify(mockRunScheduler, times(2)).unscheduleRunSchedule(any());
        verify(mockRunScheduleDao).updateRunSchedules(anyList());
    }

    @Test
    public void testUpdateRunSchedulesFailsWithoutScheduleId() {
        assertThrows(IllegalArgumentException.class,
            () -> runScheduleManager.updateSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Collections.singletonList(testRunScheduleVO)));
    }

    @Test
    public void testUpdateRunSchedulesWithIdenticalCron() {
        testRunScheduleVO.setScheduleId(RUN_ID);
        testRunScheduleVO2.setScheduleId(RUN_ID);
        testRunScheduleVO.setCronExpression(CRON_EXPRESSION2);
        testRunScheduleVO2.setCronExpression(CRON_EXPRESSION1);

        doReturn(
                Arrays.asList(runPauseSchedule, runResumeSchedule))
                .when(mockRunScheduleDao).loadAllRunSchedulesBySchedulableIdAndType(RUN_ID, ScheduleType.PIPELINE_RUN);

        runScheduleManager.updateSchedules(RUN_ID, ScheduleType.PIPELINE_RUN,
                Arrays.asList(testRunScheduleVO, testRunScheduleVO2));

        verify(mockRunScheduler, times(2)).scheduleRunSchedule(any());
        verify(mockRunScheduler, times(2)).unscheduleRunSchedule(any());
        verify(mockRunScheduleDao).updateRunSchedules(anyList());
    }

    @Test
    public void testDeleteRunSchedules() {
        doReturn(Optional.of(runPauseSchedule))
                .when(mockRunScheduleDao).loadRunSchedule(PAUSE_SCHEDULE_ID);
        doReturn(Optional.of(runResumeSchedule))
                .when(mockRunScheduleDao).loadRunSchedule(RESUME_SCHEDULE_ID);

        runScheduleManager.deleteSchedules(
                RUN_ID, ScheduleType.PIPELINE_RUN, Arrays.asList(PAUSE_SCHEDULE_ID, RESUME_SCHEDULE_ID));

        verify(mockRunScheduler, times(2)).unscheduleRunSchedule(any());
    }

    @Test
    public void testDeleteAllRunSchedules() {
        doReturn(
                Arrays.asList(runPauseSchedule, runResumeSchedule))
                .when(mockRunScheduleDao).loadAllRunSchedulesBySchedulableIdAndType(RUN_ID, ScheduleType.PIPELINE_RUN);

        runScheduleManager.deleteSchedules(RUN_ID, ScheduleType.PIPELINE_RUN);

        verify(mockRunScheduleDao).deleteRunSchedules(anyLong(), any());
        verify(mockRunScheduler, times(2)).unscheduleRunSchedule(any());
    }

    @Test
    public void testDeleteRunSchedulesForPipeline() {
        doReturn(
                Arrays.asList(runPauseSchedule, runResumeSchedule))
                .when(mockRunScheduleDao).loadAllRunSchedulesBySchedulableIdAndType(RUN_ID, ScheduleType.PIPELINE_RUN);

        doReturn(Collections.singletonList(
                ObjectCreatorUtils.createPipelineRun(RUN_ID, testPipeline.getId(), null, null)))
                .when(mockPipelineRunManager).loadAllRunsByPipeline(testPipeline.getId());

        runScheduleManager.deleteSchedulesForRunByPipeline(testPipeline.getId());

        verify(mockRunScheduler, times(2)).unscheduleRunSchedule(any());
    }

    @Test
    public void loadRunScheduleByWrongId() {
        doReturn(Optional.empty()).when(mockRunScheduleDao).loadRunSchedule(anyLong());

        assertThrows(IllegalArgumentException.class, () -> runScheduleManager.loadSchedule(RUN_ID));
    }

    private RunSchedule createRunSchedule(final Long id, final RunScheduledAction action, final String cronExpression) {
        final RunSchedule runSchedule = new RunSchedule();
        runSchedule.setAction(action);
        runSchedule.setId(id);
        runSchedule.setSchedulableId(RUN_ID);
        runSchedule.setType(ScheduleType.PIPELINE_RUN);
        runSchedule.setCronExpression(cronExpression);
        runSchedule.setCreatedDate(new Date());
        runSchedule.setTimeZone(TimeZone.getTimeZone("GMT"));
        return runSchedule;
    }

}
