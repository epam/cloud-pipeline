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

import java.io.ByteArrayOutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.ResultWriter;
import com.epam.pipeline.controller.vo.run.OffsetPagingFilter;
import com.epam.pipeline.dao.pipeline.RunLogDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RunLogManagerTest extends AbstractManagerTest {

    private static final Long RUN_ID = 1L;
    private static final int LOG_LIMIT = 100;
    private static final String TASK_NAME = "testTask";
    private static final String CONSOLE_LOG_TASK = "Console";
    private static final String LOGS_STORAGE_PREFIX = "s3://bucket/logs/runs/";
    private static final String DB_LOG_TEXT = "db log";

    @Mock
    private PipelineRunCRUDService runCRUDServiceMock;

    @Mock
    private RunLogExporter logExporter;

    @Mock
    private RunLogDao runLogDao;

    @Mock
    private RunLogStorageManager runLogStorageManager;

    @Mock
    private MessageHelper messageHelper;

    @Mock
    private PreferenceManager preferenceManager;

    @InjectMocks
    private RunLogManager logManager;

    @BeforeEach    public void setup() {
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(logManager, "consoleLogTask", CONSOLE_LOG_TASK);
        when(messageHelper.getMessage(any(String.class))).thenReturn("test message");
        when(messageHelper.getMessage(any(String.class), any())).thenReturn("test message");
        when(preferenceManager.findPreference(SystemPreferences.SYSTEM_LIMIT_LOG_LINES))
                .thenReturn(Optional.of(LOG_LIMIT));
    }

    @Test
    public void downloadLogs() throws Exception {
        PipelineRun run = new PipelineRun(RUN_ID, "");
        Mockito.doReturn(run).when(runCRUDServiceMock).loadRunById(run.getId());
        Mockito.doAnswer((Answer<Void>) invocation -> {
            final Writer writer = invocation.getArgument(1, Writer.class);
            writer.write("First task Log1");
            writer.write("Second task Log1");
            writer.write("First task log2");
            return null;
        }).when(logExporter).export(any(), any());
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ResultWriter writer = logManager.exportLogs(run.getId());
        writer.write(os);
        String result = os.toString();
        assertNotNull(result);
        assertTrue(!result.isEmpty());
    }

    @Test
    public void loadLogsByRunIdShouldReturnStorageLogsForFinalRun() {
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.SUCCESS);
        run.setLogsStoragePath(LOGS_STORAGE_PREFIX + RUN_ID);
        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);

        final List<RunLog> storageLogs = Collections.singletonList(
                buildRunLog(RUN_ID, TASK_NAME, TaskStatus.SUCCESS, "storage log"));
        when(runLogStorageManager.loadLogsFromStorage(RUN_ID)).thenReturn(storageLogs);

        final OffsetPagingFilter filter = OffsetPagingFilter.builder()
                .offset(0).limit(LOG_LIMIT).build();
        final List<RunLog> result = logManager.loadLogsByRunId(RUN_ID, filter);

        assertEquals(1, result.size());
        assertEquals("storage log", result.get(0).getLogText());
        verify(runLogDao, never()).loadLogsForRun(anyLong(), any());
    }

    @Test
    public void loadLogsByRunIdShouldReturnDaoLogsForRunningRun() {
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.RUNNING);
        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);

        final List<RunLog> daoLogs = Collections.singletonList(
                buildRunLog(RUN_ID, TASK_NAME, TaskStatus.RUNNING, DB_LOG_TEXT));
        when(runLogDao.loadLogsForRun(eq(RUN_ID), any())).thenReturn(daoLogs);

        final OffsetPagingFilter filter = OffsetPagingFilter.builder()
                .offset(0).limit(LOG_LIMIT).build();
        final List<RunLog> result = logManager.loadLogsByRunId(RUN_ID, filter);

        assertEquals(1, result.size());
        assertEquals(DB_LOG_TEXT, result.get(0).getLogText());
        verify(runLogStorageManager, never()).loadLogsFromStorage(anyLong());
    }

    @Test
    public void loadLogsByRunIdShouldFallBackToDaoWhenStorageEmpty() {
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.SUCCESS);
        run.setLogsStoragePath(LOGS_STORAGE_PREFIX + RUN_ID);
        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);
        when(runLogStorageManager.loadLogsFromStorage(RUN_ID)).thenReturn(Collections.emptyList());

        final List<RunLog> daoLogs = Collections.singletonList(
                buildRunLog(RUN_ID, TASK_NAME, TaskStatus.SUCCESS, DB_LOG_TEXT));
        when(runLogDao.loadLogsForRun(eq(RUN_ID), any())).thenReturn(daoLogs);

        final OffsetPagingFilter filter = OffsetPagingFilter.builder()
                .offset(0).limit(LOG_LIMIT).build();
        final List<RunLog> result = logManager.loadLogsByRunId(RUN_ID, filter);

        assertEquals(1, result.size());
        assertEquals(DB_LOG_TEXT, result.get(0).getLogText());
    }

    @Test
    public void loadLogsForTaskShouldReturnStorageLogsForFinalRun() {
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.SUCCESS);
        run.setLogsStoragePath(LOGS_STORAGE_PREFIX + RUN_ID);
        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);

        final List<RunLog> storageLogs = Collections.singletonList(
                buildRunLog(RUN_ID, TASK_NAME, TaskStatus.SUCCESS, "storage task log"));
        when(runLogStorageManager.loadTaskLogsFromStorage(RUN_ID, TASK_NAME)).thenReturn(storageLogs);

        final List<RunLog> result = logManager.loadLogsForTask(RUN_ID, TASK_NAME);

        assertEquals(1, result.size());
        assertEquals("storage task log", result.get(0).getLogText());
        verify(runLogDao, never()).loadLogsForTask(anyLong(), any(), any());
    }

    @Test
    public void loadLogsForTaskShouldFallBackToDaoWhenStorageEmpty() {
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.SUCCESS);
        run.setLogsStoragePath(LOGS_STORAGE_PREFIX + RUN_ID);
        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);
        when(runLogStorageManager.loadTaskLogsFromStorage(RUN_ID, TASK_NAME))
                .thenReturn(Collections.emptyList());

        final List<RunLog> daoLogs = Collections.singletonList(
                buildRunLog(RUN_ID, TASK_NAME, TaskStatus.SUCCESS, "db task log"));
        when(runLogDao.loadLogsForTask(eq(RUN_ID), eq(TASK_NAME), any())).thenReturn(daoLogs);

        final List<RunLog> result = logManager.loadLogsForTask(RUN_ID, TASK_NAME);

        assertEquals(1, result.size());
        assertEquals("db task log", result.get(0).getLogText());
    }

    @Test
    public void loadTasksByRunIdShouldReturnStorageTasksWhenDbEmpty() {
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.SUCCESS);
        run.setLogsStoragePath(LOGS_STORAGE_PREFIX + RUN_ID);
        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);

        final PipelineTask storageTask = buildPipelineTask(TASK_NAME, TaskStatus.SUCCESS);
        when(runLogStorageManager.loadTasksFromStorage(RUN_ID))
                .thenReturn(Collections.singletonList(storageTask));

        final List<PipelineTask> result = logManager.loadTasksByRunId(RUN_ID);

        assertEquals(1, result.size());
        assertEquals(TASK_NAME, result.get(0).getName());
    }

    @Test
    public void loadTasksByRunIdShouldFallBackToDbWhenStorageEmpty() {
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.SUCCESS);
        run.setLogsStoragePath(LOGS_STORAGE_PREFIX + RUN_ID);
        run.setPipelineName("pipeline");
        run.setStartDate(new Date());
        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);
        when(runLogStorageManager.loadTasksFromStorage(RUN_ID)).thenReturn(Collections.emptyList());

        final PipelineTask dbTask = buildPipelineTask(TASK_NAME, TaskStatus.SUCCESS);
        dbTask.setCreated(new Date());
        dbTask.setFinished(new Date());
        when(runLogDao.loadTasksForRun(RUN_ID)).thenReturn(Collections.singletonList(dbTask));

        final List<PipelineTask> result = logManager.loadTasksByRunId(RUN_ID);

        assertEquals(1, result.size());
        assertEquals(TASK_NAME, result.get(0).getName());
        verify(runLogStorageManager).loadTasksFromStorage(RUN_ID);
        verify(runLogDao).loadTasksForRun(RUN_ID);
    }

    @Test
    public void loadTasksByRunIdShouldNotCheckStorageForRunningRun() {
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.RUNNING);
        run.setStartDate(new Date());
        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);
        when(runLogDao.loadTasksForRun(RUN_ID)).thenReturn(new ArrayList<>());

        final List<PipelineTask> result = logManager.loadTasksByRunId(RUN_ID);

        assertTrue(result.stream().anyMatch(t -> CONSOLE_LOG_TASK.equals(t.getName())));
        verify(runLogStorageManager, never()).loadTasksFromStorage(anyLong());
    }

    private static RunLog buildRunLog(final Long runId, final String taskName,
                                      final TaskStatus status, final String logText) {
        return RunLog.builder()
                .runId(runId)
                .date(new Date())
                .status(status)
                .taskName(taskName)
                .logText(logText)
                .build();
    }

    private static PipelineTask buildPipelineTask(final String name, final TaskStatus status) {
        final PipelineTask task = new PipelineTask();
        task.setName(name);
        task.setStatus(status);
        return task;
    }

    private static PipelineRun buildRun(final Long runId, final TaskStatus status) {
        final PipelineRun run = new PipelineRun(runId, "");
        run.setStatus(status);
        run.setEndDate(new Date());
        return run;
    }
}
