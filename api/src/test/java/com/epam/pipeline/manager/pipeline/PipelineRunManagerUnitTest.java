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

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.dao.pipeline.RunLogDao;
import com.epam.pipeline.entity.pipeline.DiskAttachRequest;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.docker.DockerContainerOperationManager;
import org.apache.commons.lang.time.DateUtils;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Predicate;

import static com.epam.pipeline.entity.utils.DateUtils.convertDateToLocalDateTime;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PipelineRunManagerUnitTest {

    private static final Long RUN_ID = 1L;
    private static final long NOT_EXISTING_RUN_ID = -1L;
    private static final String NODE_NAME = "node_name";
    private static final Long SIZE = 10L;
    private static final String PAUSE_TASK_NAME = "PausePipelineRun";
    private static final Date FIRST_DATE = new Date();
    private static final Date SECOND_DATE = DateUtils.addMinutes(FIRST_DATE, 1);
    private static final String TEST_NAME = "name";
    private static final Long TEST_ID = 3L;
    private static final List<String> TEST_NAMES = Collections.singletonList(TEST_NAME);

    @Mock
    private NodesManager nodesManager;

    @Mock
    private PipelineRunDao pipelineRunDao;

    @Mock
    @SuppressWarnings("PMD.UnusedPrivateField")
    private MessageHelper messageHelper;

    @Mock
    @SuppressWarnings("PMD.UnusedPrivateField")
    private PipelineRunCRUDService runCRUDService;

    @InjectMocks
    private PipelineRunManager pipelineRunManager;

    @Mock
    private RunLogDao runLogDao;

    @Mock
    private DockerContainerOperationManager dockerContainerOperationManager;

    @Mock
    private RunStatusManager runStatusManager;

    @Mock
    private ToolManager toolManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testTerminateNotExistingRun() {
        assertThrows(() -> pipelineRunManager.terminateRun(NOT_EXISTING_RUN_ID));
    }

    @Test
    public void testTerminateNotPausedRun() {
        when(pipelineRunDao.loadPipelineRun(eq(RUN_ID))).thenReturn(run(TaskStatus.RUNNING));

        assertThrows(() -> pipelineRunManager.terminateRun(RUN_ID));
    }

    @Test
    public void testTerminatePausedRunTerminatesInstanceNode() {
        when(pipelineRunDao.loadPipelineRun(eq(RUN_ID))).thenReturn(run(TaskStatus.PAUSED));

        pipelineRunManager.terminateRun(RUN_ID);

        verify(nodesManager).terminateRun(argThat(matches(run -> run.getId().equals(RUN_ID))));
    }

    @Test
    public void testTerminatePausedRunChangesRunStatusToStopped() {
        when(pipelineRunDao.loadPipelineRun(eq(RUN_ID))).thenReturn(run(TaskStatus.PAUSED));

        pipelineRunManager.terminateRun(RUN_ID);

        verify(runCRUDService).updateRunStatus(argThat(matches(run -> run.getStatus() == TaskStatus.STOPPED)));
    }

    @Test
    public void testAttachDiskToNotExistingRun() {
        assertAttachFails(NOT_EXISTING_RUN_ID, diskAttachRequest());
    }

    @Test
    public void testAttachDiskToInvalidRuns() {
        assertAttachFails(run(TaskStatus.STOPPED));
        assertAttachFails(run(TaskStatus.FAILURE));
        assertAttachFails(run(TaskStatus.SUCCESS));
    }

    @Test
    public void testAttachDiskWithInvalidSize() {
        assertAttachFails(diskAttachRequest(null));
        assertAttachFails(diskAttachRequest(-SIZE));
    }

    @Test
    public void testAttachDiskToValidRuns() {
        assertAttachSucceed(run(TaskStatus.RUNNING));
        assertAttachSucceed(run(TaskStatus.PAUSING));
        assertAttachSucceed(run(TaskStatus.PAUSED));
        assertAttachSucceed(run(TaskStatus.RESUMING));
    }

    @Test
    public void pauseRunShouldBeRelaunched() {
        final RunLog runningPause = pausingRunLog(FIRST_DATE, TaskStatus.RUNNING);
        final RunLog firstSuccess = pausingRunLog(FIRST_DATE, TaskStatus.SUCCESS);
        final RunLog lastSuccess = pausingRunLog(SECOND_DATE, TaskStatus.SUCCESS);
        final RunStatus firstPausingStatus = pausingRunStatus(convertDateToLocalDateTime(FIRST_DATE));
        final RunStatus lastPausingStatus = pausingRunStatus(convertDateToLocalDateTime(SECOND_DATE));
        final PipelineRun run = pipelineRun();

        when(pipelineRunDao.loadRunsByStatuses(Collections.singletonList(TaskStatus.PAUSING)))
                .thenReturn(Collections.singletonList(run));
        when(pipelineRunDao.loadRunsByStatuses(Collections.singletonList(TaskStatus.RESUMING)))
                .thenReturn(Collections.emptyList());
        when(runStatusManager.loadRunStatus(run.getId()))
                .thenReturn(Arrays.asList(firstPausingStatus, lastPausingStatus));
        when(runLogDao.loadAllLogsForTask(run.getId(), PAUSE_TASK_NAME))
                .thenReturn(Arrays.asList(runningPause, firstSuccess, lastSuccess));

        pipelineRunManager.rerunPauseAndResume();

        verify(dockerContainerOperationManager).pauseRun(run);
        verify(dockerContainerOperationManager, never()).resumeRun(any(), any());
    }

    @Test
    public void resumeRunShouldBeRelaunched() {
        final PipelineRun run = pipelineRun();
        run.setDockerImage(TEST_NAME);

        when(pipelineRunDao.loadRunsByStatuses(Collections.singletonList(TaskStatus.PAUSING)))
                .thenReturn(Collections.emptyList());
        when(pipelineRunDao.loadRunsByStatuses(Collections.singletonList(TaskStatus.RESUMING)))
                .thenReturn(Collections.singletonList(run));
        when(toolManager.loadByNameOrId(TEST_NAME)).thenReturn(tool());

        pipelineRunManager.rerunPauseAndResume();

        verify(toolManager).loadByNameOrId(TEST_NAME);
        verify(dockerContainerOperationManager).resumeRun(run, TEST_NAMES);
        verify(dockerContainerOperationManager, never()).pauseRun(any());
    }

    @Test
    public void pauseRunShouldNotRelaunch() {
        final RunLog runningPause = pausingRunLog(FIRST_DATE, TaskStatus.RUNNING);
        final RunLog firstSuccess = pausingRunLog(FIRST_DATE, TaskStatus.SUCCESS);
        final RunStatus firstPausingStatus = pausingRunStatus(convertDateToLocalDateTime(FIRST_DATE));
        final RunStatus lastPausingStatus = pausingRunStatus(convertDateToLocalDateTime(SECOND_DATE));
        final PipelineRun run = pipelineRun();

        when(pipelineRunDao.loadRunsByStatuses(Collections.singletonList(TaskStatus.PAUSING)))
                .thenReturn(Collections.singletonList(run));
        when(pipelineRunDao.loadRunsByStatuses(Collections.singletonList(TaskStatus.RESUMING)))
                .thenReturn(Collections.emptyList());
        when(runStatusManager.loadRunStatus(run.getId()))
                .thenReturn(Arrays.asList(firstPausingStatus, lastPausingStatus));
        when(runLogDao.loadAllLogsForTask(run.getId(), PAUSE_TASK_NAME))
                .thenReturn(Arrays.asList(runningPause, firstSuccess));

        pipelineRunManager.rerunPauseAndResume();

        verify(dockerContainerOperationManager, never()).pauseRun(run);
        verify(dockerContainerOperationManager, never()).resumeRun(any(), any());
    }

    @Test
    public void shouldNotFailResumeIfPauseFailed() {
        final RunLog runningPause = pausingRunLog(FIRST_DATE, TaskStatus.RUNNING);
        final RunLog firstSuccess = pausingRunLog(FIRST_DATE, TaskStatus.SUCCESS);
        final RunLog lastSuccess = pausingRunLog(SECOND_DATE, TaskStatus.SUCCESS);
        final RunStatus firstPausingStatus = pausingRunStatus(convertDateToLocalDateTime(FIRST_DATE));
        final RunStatus lastPausingStatus = pausingRunStatus(convertDateToLocalDateTime(SECOND_DATE));
        final PipelineRun runToPause = pipelineRun();
        final PipelineRun runToResume = pipelineRun();
        runToResume.setId(TEST_ID);
        runToResume.setDockerImage(TEST_NAME);

        when(pipelineRunDao.loadRunsByStatuses(Collections.singletonList(TaskStatus.PAUSING)))
                .thenReturn(Collections.singletonList(runToPause));
        when(runStatusManager.loadRunStatus(runToPause.getId()))
                .thenReturn(Arrays.asList(firstPausingStatus, lastPausingStatus));
        when(runLogDao.loadAllLogsForTask(runToPause.getId(), PAUSE_TASK_NAME))
                .thenReturn(Arrays.asList(runningPause, firstSuccess, lastSuccess));
        doThrow(new RuntimeException()).when(dockerContainerOperationManager).pauseRun(any());
        when(pipelineRunDao.loadRunsByStatuses(Collections.singletonList(TaskStatus.RESUMING)))
                .thenReturn(Collections.singletonList(runToResume));
        when(toolManager.loadByNameOrId(TEST_NAME)).thenReturn(tool());

        pipelineRunManager.rerunPauseAndResume();

        verify(dockerContainerOperationManager).pauseRun(any());
        verify(toolManager).loadByNameOrId(TEST_NAME);
        verify(dockerContainerOperationManager).resumeRun(runToResume, TEST_NAMES);
    }

    private void assertAttachFails(final DiskAttachRequest request) {
        assertAttachFails(RUN_ID, request);
    }

    private void assertAttachFails(final Long runId, final DiskAttachRequest request) {
        assertThrows(() -> pipelineRunManager.attachDisk(runId, request));
    }

    private void assertAttachFails(final PipelineRun run) {
        when(pipelineRunDao.loadPipelineRun(eq(RUN_ID))).thenReturn(run);
        assertAttachFails(diskAttachRequest());
    }

    private void assertAttachSucceed(final PipelineRun run) {
        when(pipelineRunDao.loadPipelineRun(eq(RUN_ID))).thenReturn(run);
        pipelineRunManager.attachDisk(RUN_ID, diskAttachRequest());
        verify(nodesManager).attachDisk(argThat(matches(r -> r.getStatus() == run.getStatus())),
                eq(diskAttachRequest()));
    }

    private PipelineRun run(final TaskStatus status) {
        final PipelineRun run = run();
        run.setStatus(status);
        return run;
    }

    private PipelineRun run() {
        final PipelineRun run = new PipelineRun();
        run.setId(RUN_ID);
        final RunInstance instance = new RunInstance();
        instance.setNodeName(NODE_NAME);
        run.setInstance(instance);
        return run;
    }

    private DiskAttachRequest diskAttachRequest() {
        return diskAttachRequest(SIZE);
    }

    private DiskAttachRequest diskAttachRequest(final Long size) {
        return new DiskAttachRequest(size);
    }

    private <T> BaseMatcher<T> matches(final Predicate<T> test) {
        return new BaseMatcher<T>() {

            @Override
            public void describeTo(final Description description) {
                description.appendText("custom matcher");
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean matches(final Object item) {
                return test.test((T) item);
            }
        };
    }

    private RunStatus pausingRunStatus(final LocalDateTime timestamp) {
        return RunStatus.builder()
                .timestamp(timestamp)
                .status(TaskStatus.PAUSING)
                .build();
    }

    private RunLog pausingRunLog(final Date date, final TaskStatus status) {
        return RunLog.builder()
                .date(date)
                .status(status)
                .taskName(PAUSE_TASK_NAME)
                .build();
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
