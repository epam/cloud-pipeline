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

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.cluster.performancemonitoring.UsageMonitoringManager;
import com.epam.pipeline.manager.docker.DockerContainerOperationManager;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import org.apache.commons.lang.time.DateUtils;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.epam.pipeline.entity.utils.DateUtils.convertDateToLocalDateTime;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PipelineRunDockerOperationManagerTest {
    private static final Long RUN_ID = 1L;
    private static final String TEST_NAME = "name";
    private static final Long TEST_ID = 3L;
    private static final List<String> TEST_NAMES = Collections.singletonList(TEST_NAME);
    private static final String PAUSE_TASK_NAME = "PausePipelineRun";
    private static final Date DATE_1 = new Date();
    private static final Date DATE_2 = DateUtils.addMinutes(DATE_1, 1);
    private static final Date DATE_3 = DateUtils.addMinutes(DATE_2, 1);

    private final DockerContainerOperationManager dockerContainerOperationManager =
            mock(DockerContainerOperationManager.class);
    private final PipelineRunManager pipelineRunManager = mock(PipelineRunManager.class);
    private final DockerRegistryManager dockerRegistryManager = mock(DockerRegistryManager.class);
    private final ToolManager toolManager = mock(ToolManager.class);
    private final PipelineRunDao pipelineRunDao = mock(PipelineRunDao.class);
    private final PipelineRunCRUDService runCRUDService = mock(PipelineRunCRUDService.class);
    private final UsageMonitoringManager usageMonitoringManager = mock(UsageMonitoringManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final RunLogManager runLogManager = mock(RunLogManager.class);
    private final RunStatusManager runStatusManager = mock(RunStatusManager.class);
    private final PipelineRunDockerOperationManager pipelineRunDockerOperationManager =
            new PipelineRunDockerOperationManager(
                    dockerContainerOperationManager,
                    pipelineRunManager,
                    dockerRegistryManager,
                    toolManager,
                    pipelineRunDao,
                    runCRUDService,
                    usageMonitoringManager,
                    runLogManager,
                    runStatusManager,
                    messageHelper,
                    preferenceManager);

    @Test
    public void pauseRunShouldBeRelaunched() {
        final PipelineRun run = pausingRun();

        when(pipelineRunManager.loadRunsByStatuses(Collections.singletonList(TaskStatus.PAUSING)))
                .thenReturn(Collections.singletonList(run));
        when(pipelineRunManager.loadRunsByStatuses(Collections.singletonList(TaskStatus.RESUMING)))
                .thenReturn(Collections.emptyList());
        mockNeedToRerunPauseSituation();

        pipelineRunDockerOperationManager.rerunPauseAndResume();

        verify(dockerContainerOperationManager).pauseRun(run, true);
        verify(dockerContainerOperationManager, never()).resumeRun(any(), any());
    }

    @Test
    public void pauseRunShouldNotBeRelaunched() {
        final PipelineRun run = pausingRun();

        when(pipelineRunManager.loadRunsByStatuses(Collections.singletonList(TaskStatus.PAUSING)))
                .thenReturn(Collections.singletonList(run));
        when(pipelineRunManager.loadRunsByStatuses(Collections.singletonList(TaskStatus.RESUMING)))
                .thenReturn(Collections.emptyList());
        mockNoNeedToRerunPauseSituation();

        pipelineRunDockerOperationManager.rerunPauseAndResume();

        verify(dockerContainerOperationManager, never()).pauseRun(run, true);
    }

    @Test
    public void resumeRunShouldBeRelaunched() {
        final PipelineRun run = pipelineRun();
        run.setDockerImage(TEST_NAME);

        when(pipelineRunManager.loadRunsByStatuses(Collections.singletonList(TaskStatus.PAUSING)))
                .thenReturn(Collections.emptyList());
        when(pipelineRunManager.loadRunsByStatuses(Collections.singletonList(TaskStatus.RESUMING)))
                .thenReturn(Collections.singletonList(run));
        when(toolManager.loadByNameOrId(TEST_NAME)).thenReturn(tool());

        pipelineRunDockerOperationManager.rerunPauseAndResume();

        verify(toolManager).loadByNameOrId(TEST_NAME);
        verify(dockerContainerOperationManager).resumeRun(run, TEST_NAMES);
        verify(dockerContainerOperationManager, never()).pauseRun(any(), anyBoolean());
    }

    @Test
    public void shouldNotFailResumeIfPauseFailed() {
        final PipelineRun runToPause = pausingRun();
        final PipelineRun runToResume = resumingRun();
        runToResume.setId(TEST_ID);
        runToResume.setDockerImage(TEST_NAME);

        when(pipelineRunManager.loadRunsByStatuses(Collections.singletonList(TaskStatus.PAUSING)))
                .thenReturn(Collections.singletonList(runToPause));
        doThrow(new RuntimeException()).when(dockerContainerOperationManager).pauseRun(any(), anyBoolean());
        when(pipelineRunManager.loadRunsByStatuses(Collections.singletonList(TaskStatus.RESUMING)))
                .thenReturn(Collections.singletonList(runToResume));
        when(toolManager.loadByNameOrId(TEST_NAME)).thenReturn(tool());
        mockNeedToRerunPauseSituation();

        pipelineRunDockerOperationManager.rerunPauseAndResume();

        verify(dockerContainerOperationManager).pauseRun(any(), anyBoolean());
        verify(toolManager).loadByNameOrId(TEST_NAME);
        verify(dockerContainerOperationManager).resumeRun(runToResume, TEST_NAMES);
    }

    private PipelineRun pipelineRun() {
        return ObjectCreatorUtils.createPipelineRun(RUN_ID, null, null, TEST_ID);
    }

    private Tool tool() {
        final Tool tool = ObjectCreatorUtils.createTool(TEST_NAME, TEST_ID);
        tool.setEndpoints(TEST_NAMES);
        return tool;
    }

    private PipelineRun pausingRun() {
        final PipelineRun run = pipelineRun();
        run.setStatus(TaskStatus.PAUSING);
        return run;
    }

    private PipelineRun resumingRun() {
        final PipelineRun run = pipelineRun();
        run.setStatus(TaskStatus.RESUMING);
        return run;
    }

    private RunLog pauseRunLog(final Date date, final TaskStatus status) {
        return RunLog.builder()
                .date(date)
                .status(status)
                .taskName(PAUSE_TASK_NAME)
                .build();
    }

    private RunStatus pausingRunStatus(final LocalDateTime timestamp) {
        return RunStatus.builder()
                .timestamp(timestamp)
                .status(TaskStatus.PAUSING)
                .build();
    }

    private void mockNeedToRerunPauseSituation() {
        when(runStatusManager.loadRunStatus(RUN_ID)).thenReturn(Arrays.asList(
                pausingRunStatus(convertDateToLocalDateTime(DATE_1)),
                pausingRunStatus(convertDateToLocalDateTime(DATE_2))));
        when(runLogManager.loadAllLogsForTask(RUN_ID, PAUSE_TASK_NAME)).thenReturn(Arrays.asList(
                pauseRunLog(DATE_1, TaskStatus.SUCCESS), pauseRunLog(DATE_3, TaskStatus.SUCCESS)));
    }

    private void mockNoNeedToRerunPauseSituation() {
        when(runStatusManager.loadRunStatus(RUN_ID)).thenReturn(Collections.singletonList(
                pausingRunStatus(convertDateToLocalDateTime(DATE_1))));
        when(runLogManager.loadAllLogsForTask(RUN_ID, PAUSE_TASK_NAME)).thenReturn(Collections.emptyList());
    }
}
