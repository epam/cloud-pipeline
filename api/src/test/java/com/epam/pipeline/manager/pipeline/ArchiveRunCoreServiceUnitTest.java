/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.pipeline.ArchiveRunDao;
import com.epam.pipeline.dao.pipeline.EngineRunTaskDao;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.dao.pipeline.RestartRunDao;
import com.epam.pipeline.dao.pipeline.RunLogDao;
import com.epam.pipeline.dao.pipeline.RunStatusDao;
import com.epam.pipeline.dao.pipeline.StopServerlessRunDao;
import com.epam.pipeline.dao.run.RunServiceUrlDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.util.TestUtils;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.util.CustomAssertions.notInvoked;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ArchiveRunCoreServiceUnitTest {

    private static final String OWNER = "owner";
    private static final String POD = "pod-id";
    private static final Long RUN_ID_1 = 1L;
    private static final Long RUN_ID_2 = 2L;
    private static final Long CHILD_RUN_ID = 3L;

    private final ArchiveRunDao archiveRunDao = mock(ArchiveRunDao.class);
    private final PipelineRunDao pipelineRunDao = mock(PipelineRunDao.class);
    private final RunLogDao runLogDao = mock(RunLogDao.class);
    private final RestartRunDao restartRunDao = mock(RestartRunDao.class);
    private final RunServiceUrlDao runServiceUrlDao = mock(RunServiceUrlDao.class);
    private final RunStatusDao runStatusDao = mock(RunStatusDao.class);
    private final StopServerlessRunDao stopServerlessRunDao = mock(StopServerlessRunDao.class);
    private final EngineRunTaskDao engineRunTaskDao = mock(EngineRunTaskDao.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);

    private final ArchiveRunCoreService archiveRunCoreService = new ArchiveRunCoreService(
            archiveRunDao, pipelineRunDao, runLogDao, restartRunDao, runServiceUrlDao,
            runStatusDao, stopServerlessRunDao, engineRunTaskDao, messageHelper);

    @Test
    public void shouldNotArchiveByIdsIfCollectionIsEmpty() {
        archiveRunCoreService.archiveRunsByIds(Collections.emptyList());

        notInvoked(pipelineRunDao).loadRunByIdIn(any());
        notInvoked(archiveRunDao).batchInsertArchiveRuns(any());
    }

    @Test
    public void shouldArchiveMasterRunsById() {
        final PipelineRun run1 = run(RUN_ID_1);
        final PipelineRun run2 = run(RUN_ID_2);
        final List<Long> runIds = Arrays.asList(RUN_ID_1, RUN_ID_2);
        doReturn(Arrays.asList(run1, run2)).when(pipelineRunDao).loadRunByIdIn(runIds);
        doReturn(Collections.emptyList()).when(pipelineRunDao).loadRunsByParentRuns(runIds);
        doReturn(Collections.emptyList()).when(runStatusDao).loadRunStatus(runIds, false, false);

        archiveRunCoreService.archiveRunsByIds(runIds);

        final ArgumentCaptor<List<PipelineRun>> runsCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(archiveRunDao).batchInsertArchiveRuns(runsCaptor.capture());
        assertThat(runsCaptor.getValue()).extracting(PipelineRun::getId)
                .containsExactlyInAnyOrder(RUN_ID_1, RUN_ID_2);
        verify(pipelineRunDao).deleteRunByIdIn(eq(runIds), eq(false));
    }

    @Test
    public void shouldArchiveMasterRunsWithChildrenById() {
        final PipelineRun master = run(RUN_ID_1);
        final PipelineRun child = run(CHILD_RUN_ID);
        child.setParentRunId(RUN_ID_1);
        final List<Long> masterIds = Collections.singletonList(RUN_ID_1);
        doReturn(Collections.singletonList(master)).when(pipelineRunDao).loadRunByIdIn(masterIds);
        doReturn(Collections.singletonList(child)).when(pipelineRunDao).loadRunsByParentRuns(masterIds);
        final List<Long> allIds = Arrays.asList(RUN_ID_1, CHILD_RUN_ID);
        doReturn(Collections.emptyList()).when(runStatusDao).loadRunStatus(allIds, false, false);

        archiveRunCoreService.archiveRunsByIds(masterIds);

        final ArgumentCaptor<List<PipelineRun>> runsCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(archiveRunDao).batchInsertArchiveRuns(runsCaptor.capture());
        assertThat(runsCaptor.getValue()).extracting(PipelineRun::getId)
                .containsExactlyInAnyOrder(RUN_ID_1, CHILD_RUN_ID);
        final ArgumentCaptor<List<Long>> deleteCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(pipelineRunDao).deleteRunByIdIn(deleteCaptor.capture(), eq(false));
        assertThat(deleteCaptor.getValue()).containsExactlyInAnyOrder(RUN_ID_1, CHILD_RUN_ID);
    }

    @Test
    public void shouldArchiveRunStatusesById() {
        final PipelineRun run = run(RUN_ID_1);
        final RunStatus status = runStatus(RUN_ID_1);
        final List<Long> runIds = Collections.singletonList(RUN_ID_1);
        doReturn(Collections.singletonList(run)).when(pipelineRunDao).loadRunByIdIn(runIds);
        doReturn(Collections.emptyList()).when(pipelineRunDao).loadRunsByParentRuns(runIds);
        doReturn(Collections.singletonList(status)).when(runStatusDao).loadRunStatus(runIds, false, false);

        archiveRunCoreService.archiveRunsByIds(runIds);

        final ArgumentCaptor<List<RunStatus>> statusCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(archiveRunDao).batchInsertArchiveRunsStatusChange(statusCaptor.capture());
        assertThat(statusCaptor.getValue()).extracting(RunStatus::getRunId).containsExactly(RUN_ID_1);
    }

    @Test
    public void shouldDeleteAllDependentsById() {
        final PipelineRun run = run(RUN_ID_1);
        final List<Long> runIds = Collections.singletonList(RUN_ID_1);
        doReturn(Collections.singletonList(run)).when(pipelineRunDao).loadRunByIdIn(runIds);
        doReturn(Collections.emptyList()).when(pipelineRunDao).loadRunsByParentRuns(runIds);
        doReturn(Collections.emptyList()).when(runStatusDao).loadRunStatus(runIds, false, false);

        archiveRunCoreService.archiveRunsByIds(runIds);

        verify(pipelineRunDao).deleteRunSidsByRunIdIn(eq(runIds), eq(false));
        verify(runLogDao).deleteTaskByRunIdsIn(eq(runIds), eq(false));
        verify(restartRunDao).deleteRestartRunByIdsIn(eq(runIds), eq(false));
        verify(runServiceUrlDao).deleteByRunIdsIn(eq(runIds), eq(false));
        verify(runStatusDao).deleteRunStatusByRunIdsIn(eq(runIds), eq(false));
        verify(stopServerlessRunDao).deleteByRunIdIn(eq(runIds), eq(false));
        verify(engineRunTaskDao).deleteByRunIdIn(eq(runIds), eq(false));
        verify(pipelineRunDao).deleteRunByIdIn(eq(runIds), eq(false));
    }

    private PipelineRun run(final Long id) {
        final PipelineRun run = TestUtils.createPipelineRun(null, null, TaskStatus.STOPPED, OWNER,
                null, null, true, null, null, POD, 1L);
        run.setId(id);
        return run;
    }

    private RunStatus runStatus(final Long runId) {
        return RunStatus.builder()
                .runId(runId)
                .status(TaskStatus.FAILURE)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
