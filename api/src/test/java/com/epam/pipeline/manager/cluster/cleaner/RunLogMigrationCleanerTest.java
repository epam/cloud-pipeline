/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.cleaner;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.pipeline.RunLogDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.pipeline.RunLogManager;
import com.epam.pipeline.manager.pipeline.RunLogStorageManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class RunLogMigrationCleanerTest {

    private static final Long MASTER_RUN_ID = 1L;
    private static final Long CHILD_RUN_ID_1 = 2L;
    private static final Long CHILD_RUN_ID_2 = 3L;
    private static final String STORAGE_PATH = "s3://bucket/logs/runs/1";
    private static final String CONSOLE_LOG_TASK = "Console";

    @Mock
    private RunLogManager runLogManager;
    @Mock
    private RunLogDao runLogDao;
    @Mock
    private RunLogStorageManager runLogStorageManager;
    @Mock
    private PipelineRunCRUDService runCRUDService;
    @Mock
    private MessageHelper messageHelper;

    private RunLogMigrationCleaner cleaner;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        cleaner = new RunLogMigrationCleaner(runLogManager, runLogDao,
                runLogStorageManager, runCRUDService, messageHelper);
        ReflectionTestUtils.setField(cleaner, "consoleLogTask", CONSOLE_LOG_TASK);

        when(runLogStorageManager.isRunLogMigrationConfigured()).thenReturn(true);
        when(messageHelper.getMessage(any())).thenReturn("test message");
        when(runLogManager.loadTasksByRunId(anyLong())).thenReturn(Collections.emptyList());
        when(runLogStorageManager.saveLogsToStorage(anyLong(), anyMap(), anyBoolean())).thenReturn(STORAGE_PATH);
    }

    @Test
    public void cleanResourcesShouldMigrateLogsForChildRunsWhenMasterRun() {
        final PipelineRun masterRun = buildRun(MASTER_RUN_ID, 2, null);
        final PipelineRun childRun1 = buildRun(CHILD_RUN_ID_1, null, MASTER_RUN_ID);
        final PipelineRun childRun2 = buildRun(CHILD_RUN_ID_2, null, MASTER_RUN_ID);

        when(runCRUDService.loadRunsByParentRuns(Collections.singleton(MASTER_RUN_ID)))
                .thenReturn(buildChildRunsMap(Arrays.asList(childRun1, childRun2)));
        mockRunWithLogs(masterRun);
        mockRunWithLogs(childRun1);
        mockRunWithLogs(childRun2);

        cleaner.cleanResources(masterRun);

        verify(runLogDao, times(3)).deleteTaskByRunIdsIn(any(), eq(false));
    }

    @Test
    public void cleanResourcesShouldNotLoadChildRunsWhenNotClusterRun() {
        final PipelineRun plainRun = buildRun(MASTER_RUN_ID, null, null);
        mockRunWithLogs(plainRun);

        cleaner.cleanResources(plainRun);

        verify(runCRUDService, never()).loadRunsByParentRuns(any());
    }

    @Test
    public void cleanResourcesShouldMigrateOnlyMasterWhenNoChildRuns() {
        final PipelineRun masterRun = buildRun(MASTER_RUN_ID, 2, null);

        when(runCRUDService.loadRunsByParentRuns(Collections.singleton(MASTER_RUN_ID)))
                .thenReturn(Collections.emptyMap());
        mockRunWithLogs(masterRun);

        cleaner.cleanResources(masterRun);

        verify(runLogDao, times(1)).deleteTaskByRunIdsIn(any(), eq(false));
    }

    @Test
    public void cleanResourcesShouldSkipMasterAndContinueChildWhenMasterAlreadyMigrated() {
        final PipelineRun masterRun = buildRun(MASTER_RUN_ID, 2, null);
        masterRun.setLogsStoragePath(STORAGE_PATH);
        final PipelineRun childRun = buildRun(CHILD_RUN_ID_1, null, MASTER_RUN_ID);

        when(runCRUDService.loadRunById(MASTER_RUN_ID)).thenReturn(masterRun);
        when(runLogDao.loadAllLogsForRun(MASTER_RUN_ID)).thenReturn(Collections.emptyList());
        when(runCRUDService.loadRunsByParentRuns(Collections.singleton(MASTER_RUN_ID)))
                .thenReturn(buildChildRunsMap(Collections.singletonList(childRun)));
        mockRunWithLogs(childRun);

        cleaner.cleanResources(masterRun);

        // master has no DB logs; child is migrated
        verify(runLogDao, times(1)).deleteTaskByRunIdsIn(any(), eq(false));
    }

    @Test
    public void migrateRunLogsToStorageShouldPassMergeWhenLogsStoragePathAlreadySet() {
        final PipelineRun run = buildRun(MASTER_RUN_ID, null, null);
        run.setLogsStoragePath(STORAGE_PATH);

        when(runCRUDService.loadRunById(MASTER_RUN_ID)).thenReturn(run);
        when(runLogDao.loadAllLogsForRun(MASTER_RUN_ID))
                .thenReturn(Collections.singletonList(buildRunLog(MASTER_RUN_ID)));

        cleaner.cleanResources(run);

        verify(runLogStorageManager).saveLogsToStorage(eq(MASTER_RUN_ID), anyMap(), eq(true));
    }

    private void mockRunWithLogs(final PipelineRun run) {
        when(runCRUDService.loadRunById(run.getId())).thenReturn(run);
        when(runLogDao.loadAllLogsForRun(run.getId()))
                .thenReturn(Collections.singletonList(buildRunLog(run.getId())));
    }

    private static PipelineRun buildRun(final Long id, final Integer nodeCount, final Long parentRunId) {
        final PipelineRun run = new PipelineRun();
        run.setId(id);
        run.setNodeCount(nodeCount);
        run.setParentRunId(parentRunId);
        return run;
    }

    private static RunLog buildRunLog(final Long runId) {
        return RunLog.builder()
                .runId(runId)
                .date(new Date())
                .status(TaskStatus.SUCCESS)
                .taskName(CONSOLE_LOG_TASK)
                .logText("log line")
                .build();
    }

    private static Map<Long, List<PipelineRun>> buildChildRunsMap(final List<PipelineRun> children) {
        final Map<Long, List<PipelineRun>> result = new HashMap<>();
        result.put(MASTER_RUN_ID, children);
        return result;
    }
}
