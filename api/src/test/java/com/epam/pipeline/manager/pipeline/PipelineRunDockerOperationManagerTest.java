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
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.cluster.performancemonitoring.UsageMonitoringManager;
import com.epam.pipeline.manager.docker.DockerContainerOperationManager;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.mockito.Matchers.any;
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
    private final PipelineRunDockerOperationManager pipelineRunDockerOperationManager =
            new PipelineRunDockerOperationManager(
                    dockerContainerOperationManager,
                    pipelineRunManager,
                    dockerRegistryManager,
                    toolManager,
                    pipelineRunDao,
                    runCRUDService,
                    usageMonitoringManager,
                    messageHelper,
                    preferenceManager);

    @Test
    public void pauseRunShouldBeRelaunched() {
        final PipelineRun run = pipelineRun();

        when(pipelineRunManager.loadRunsByStatuses(Collections.singletonList(TaskStatus.PAUSING)))
                .thenReturn(Collections.singletonList(run));
        when(pipelineRunManager.loadRunsByStatuses(Collections.singletonList(TaskStatus.RESUMING)))
                .thenReturn(Collections.emptyList());

        pipelineRunDockerOperationManager.rerunPauseAndResume();

        verify(dockerContainerOperationManager).pauseRun(run);
        verify(dockerContainerOperationManager, never()).resumeRun(any(), any());
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
        verify(dockerContainerOperationManager, never()).pauseRun(any());
    }

    @Test
    public void shouldNotFailResumeIfPauseFailed() {
        final PipelineRun runToPause = pipelineRun();
        final PipelineRun runToResume = pipelineRun();
        runToResume.setId(TEST_ID);
        runToResume.setDockerImage(TEST_NAME);

        when(pipelineRunManager.loadRunsByStatuses(Collections.singletonList(TaskStatus.PAUSING)))
                .thenReturn(Collections.singletonList(runToPause));
        doThrow(new RuntimeException()).when(dockerContainerOperationManager).pauseRun(any());
        when(pipelineRunManager.loadRunsByStatuses(Collections.singletonList(TaskStatus.RESUMING)))
                .thenReturn(Collections.singletonList(runToResume));
        when(toolManager.loadByNameOrId(TEST_NAME)).thenReturn(tool());

        pipelineRunDockerOperationManager.rerunPauseAndResume();

        verify(dockerContainerOperationManager).pauseRun(any());
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
}
