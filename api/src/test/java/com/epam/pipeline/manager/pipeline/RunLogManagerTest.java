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
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
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
    private static final String POD_NAME = "pod-123";
    private static final int DURATION1 = 10000;
    private static final int DURATION2 = 5000;

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

    private static final String INIT_TASK_NAME = "InitializeEnvironment";

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(logManager, "consoleLogTask", CONSOLE_LOG_TASK);
        ReflectionTestUtils.setField(logManager, "initTaskName", INIT_TASK_NAME);
        ReflectionTestUtils.setField(logManager, "self", logManager);
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
            final Writer writer = invocation.getArgumentAt(1, Writer.class);
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

    @Test
    public void loadTasksByRunIdShouldNormalizeStorageTasks() {
        // Given: A final run with storage path and non-final task in storage
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.SUCCESS);
        run.setLogsStoragePath(LOGS_STORAGE_PREFIX + RUN_ID);
        final Date runStartDate = new Date(System.currentTimeMillis() - DURATION1);
        final Date runEndDate = new Date();
        run.setStartDate(runStartDate);
        run.setEndDate(runEndDate);
        run.setPodId(POD_NAME);

        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);

        // Task from storage with RUNNING status (should be normalized to SUCCESS)
        final PipelineTask storageTask = buildPipelineTask(TASK_NAME, TaskStatus.RUNNING);
        storageTask.setCreated(new Date(System.currentTimeMillis() - DURATION2));
        storageTask.setInstance(POD_NAME);
        when(runLogStorageManager.loadTasksFromStorage(RUN_ID))
                .thenReturn(Collections.singletonList(storageTask));

        // When
        final List<PipelineTask> result = logManager.loadTasksByRunId(RUN_ID);

        // Then: Task status should be normalized to match run status
        assertEquals(1, result.size());
        final PipelineTask normalizedTask = result.get(0);
        assertEquals(TaskStatus.SUCCESS, normalizedTask.getStatus());
        assertEquals(runEndDate, normalizedTask.getFinished());
        verify(runLogStorageManager).loadTasksFromStorage(RUN_ID);
        verify(runLogDao, never()).loadTasksForRun(anyLong());
    }

    @Test
    public void loadTasksByRunIdShouldNormalizeStorageTaskWithNullStatus() {
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.STOPPED);
        run.setLogsStoragePath(LOGS_STORAGE_PREFIX + RUN_ID);
        run.setStartDate(new Date(System.currentTimeMillis() - DURATION1));
        run.setEndDate(new Date());
        run.setPodId(POD_NAME);

        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);

        final PipelineTask storageTask = buildPipelineTask(TASK_NAME, null);
        storageTask.setCreated(new Date(System.currentTimeMillis() - DURATION2));
        storageTask.setInstance(POD_NAME);
        when(runLogStorageManager.loadTasksFromStorage(RUN_ID))
                .thenReturn(Collections.singletonList(storageTask));

        final List<PipelineTask> result = logManager.loadTasksByRunId(RUN_ID);

        assertEquals(1, result.size());
        assertEquals(TaskStatus.STOPPED, result.get(0).getStatus());
    }

    @Test
    public void loadTasksByRunIdShouldNormalizeDatabaseTasks() {
        // Given: A final run without storage path and non-final task in database
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.FAILURE);
        final Date runStartDate = new Date(System.currentTimeMillis() - 10000);
        final Date runEndDate = new Date();
        run.setStartDate(runStartDate);
        run.setEndDate(runEndDate);
        run.setPodId(POD_NAME);

        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);

        // Task from database with RUNNING status (should be normalized to FAILURE)
        final PipelineTask dbTask = buildPipelineTask(TASK_NAME, TaskStatus.RUNNING);
        dbTask.setCreated(new Date(System.currentTimeMillis() - DURATION2));
        dbTask.setInstance(POD_NAME);
        when(runLogDao.loadTasksForRun(RUN_ID)).thenReturn(Collections.singletonList(dbTask));

        // When
        final List<PipelineTask> result = logManager.loadTasksByRunId(RUN_ID);

        // Then: Task status should be normalized to match run status
        assertEquals(1, result.size());
        final PipelineTask normalizedTask = result.get(0);
        assertEquals(TaskStatus.FAILURE, normalizedTask.getStatus());
        assertEquals(runEndDate, normalizedTask.getFinished());
    }

    @Test
    public void loadTasksByRunIdShouldSetStartedDateWhenNull() {
        // Given: A final run and task without started date
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.SUCCESS);
        run.setLogsStoragePath(LOGS_STORAGE_PREFIX + RUN_ID);
        final Date runStartDate = new Date(System.currentTimeMillis() - DURATION1);
        final Date taskCreated = new Date(System.currentTimeMillis() - DURATION2);
        run.setStartDate(runStartDate);
        run.setPodId(POD_NAME);

        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);

        // Task without started date
        final PipelineTask storageTask = buildPipelineTask(TASK_NAME, TaskStatus.SUCCESS);
        storageTask.setCreated(taskCreated);
        storageTask.setInstance(POD_NAME);
        storageTask.setStarted(null);
        when(runLogStorageManager.loadTasksFromStorage(RUN_ID))
                .thenReturn(Collections.singletonList(storageTask));

        // When
        final List<PipelineTask> result = logManager.loadTasksByRunId(RUN_ID);

        // Then: Started date should be set to created date
        assertEquals(1, result.size());
        final PipelineTask normalizedTask = result.get(0);
        assertEquals(taskCreated, normalizedTask.getStarted());
    }

    @Test
    public void loadTasksByRunIdShouldAddConsoleTaskForRunningRun() {
        // Given: A running run
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.RUNNING);
        final Date runStartDate = new Date(System.currentTimeMillis() - DURATION1);
        run.setStartDate(runStartDate);

        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);

        final PipelineTask dbTask = buildPipelineTask(TASK_NAME, TaskStatus.RUNNING);
        dbTask.setCreated(new Date());
        when(runLogDao.loadTasksForRun(RUN_ID)).thenReturn(Collections.singletonList(dbTask));

        // When
        final List<PipelineTask> result = logManager.loadTasksByRunId(RUN_ID);

        // Then: Console task should be added
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(t -> CONSOLE_LOG_TASK.equals(t.getName())));
        final PipelineTask consoleTask = result.stream()
                .filter(t -> CONSOLE_LOG_TASK.equals(t.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(consoleTask);
        assertEquals(TaskStatus.RUNNING, consoleTask.getStatus());
        assertEquals(runStartDate, consoleTask.getStarted());
    }

    @Test
    public void loadTasksByRunIdShouldNotAddConsoleTaskForFinalRun() {
        // Given: A final run
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.SUCCESS);
        run.setStartDate(new Date(System.currentTimeMillis() - DURATION1));
        run.setEndDate(new Date());

        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);

        final PipelineTask dbTask = buildPipelineTask(TASK_NAME, TaskStatus.SUCCESS);
        dbTask.setCreated(new Date());
        when(runLogDao.loadTasksForRun(RUN_ID)).thenReturn(Collections.singletonList(dbTask));

        // When
        final List<PipelineTask> result = logManager.loadTasksByRunId(RUN_ID);

        // Then: Console task should NOT be added
        assertEquals(1, result.size());
        assertTrue(result.stream().noneMatch(t -> CONSOLE_LOG_TASK.equals(t.getName())));
    }

    @Test
    public void loadTasksByRunIdShouldClearFinishedDateForNonFinalTasks() {
        // Given: A running run with task that has finished date set
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.RUNNING);
        run.setStartDate(new Date(System.currentTimeMillis() - DURATION1));

        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);

        final PipelineTask dbTask = buildPipelineTask(TASK_NAME, TaskStatus.RUNNING);
        dbTask.setCreated(new Date());
        dbTask.setFinished(new Date()); // This should be cleared
        when(runLogDao.loadTasksForRun(RUN_ID)).thenReturn(Collections.singletonList(dbTask));

        // When
        final List<PipelineTask> result = logManager.loadTasksByRunId(RUN_ID);

        // Then: Finished date should be null for running task
        final PipelineTask task = result.stream()
                .filter(t -> TASK_NAME.equals(t.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(task);
        assertNull(task.getFinished());
    }

    @Test
    public void loadTasksByRunIdShouldHandlePipelineNamedTask() {
        // Given: A run with task matching pipeline name
        final String pipelineName = "mainPipeline";
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.SUCCESS);
        run.setPipelineName(pipelineName);
        final Date runStartDate = new Date(System.currentTimeMillis() - DURATION1);
        run.setStartDate(runStartDate);
        run.setEndDate(new Date());

        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);

        final PipelineTask pipelineTask = buildPipelineTask(pipelineName, TaskStatus.SUCCESS);
        final Date taskCreated = new Date(System.currentTimeMillis() - DURATION2);
        pipelineTask.setCreated(taskCreated);
        pipelineTask.setStarted(null);
        when(runLogDao.loadTasksForRun(RUN_ID)).thenReturn(Collections.singletonList(pipelineTask));

        // When
        final List<PipelineTask> result = logManager.loadTasksByRunId(RUN_ID);

        // Then: Task created should be set to run start date
        assertEquals(1, result.size());
        final PipelineTask normalizedTask = result.get(0);
        assertEquals(runStartDate, normalizedTask.getCreated());
        assertEquals(runStartDate, normalizedTask.getStarted());
    }

    @Test
    public void loadTasksByRunIdShouldNormalizeTasksFromStorageWhenFallbackToDao() {
        // Given: A run with storage path but empty storage, falling back to DAO
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.SUCCESS);
        run.setLogsStoragePath(LOGS_STORAGE_PREFIX + RUN_ID);
        final Date runEndDate = new Date();
        run.setStartDate(new Date(System.currentTimeMillis() - DURATION1));
        run.setEndDate(runEndDate);
        run.setPodId(POD_NAME);

        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);
        when(runLogStorageManager.loadTasksFromStorage(RUN_ID)).thenReturn(Collections.emptyList());

        // Task from database with non-final status
        final PipelineTask dbTask = buildPipelineTask(TASK_NAME, TaskStatus.RUNNING);
        dbTask.setCreated(new Date(System.currentTimeMillis() - DURATION2));
        dbTask.setInstance(POD_NAME);
        when(runLogDao.loadTasksForRun(RUN_ID)).thenReturn(Collections.singletonList(dbTask));

        // When
        final List<PipelineTask> result = logManager.loadTasksByRunId(RUN_ID);

        // Then: Task should be normalized even when loaded from DAO as fallback
        assertEquals(1, result.size());
        final PipelineTask normalizedTask = result.get(0);
        assertEquals(TaskStatus.SUCCESS, normalizedTask.getStatus());
        assertEquals(runEndDate, normalizedTask.getFinished());
        verify(runLogStorageManager).loadTasksFromStorage(RUN_ID);
        verify(runLogDao).loadTasksForRun(RUN_ID);
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
        if (status != null) {
            task.setStatus(status);
        }
        return task;
    }

    @Test
    public void saveLogShouldMarkRunInitializedOnInitTaskSuccess() {
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.RUNNING);
        run.setInitialized(false);
        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);
        when(runLogDao.loadTaskStatus(eq(RUN_ID), eq(INIT_TASK_NAME))).thenReturn(null);

        final RunLog log = buildRunLog(RUN_ID, INIT_TASK_NAME, TaskStatus.SUCCESS, "done");
        logManager.saveLog(log);

        verify(runCRUDServiceMock).updateRunInitialized(RUN_ID);
    }

    @Test
    public void saveLogShouldNotMarkInitializedWhenRunAlreadyInitialized() {
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.RUNNING);
        run.setInitialized(true);
        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);
        when(runLogDao.loadTaskStatus(eq(RUN_ID), eq(INIT_TASK_NAME))).thenReturn(null);

        final RunLog log = buildRunLog(RUN_ID, INIT_TASK_NAME, TaskStatus.SUCCESS, "done");
        logManager.saveLog(log);

        verify(runCRUDServiceMock, never()).updateRunInitialized(anyLong());
    }

    @Test
    public void saveLogShouldNotMarkInitializedForNonSuccessStatus() {
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.RUNNING);
        run.setInitialized(false);
        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);
        when(runLogDao.loadTaskStatus(eq(RUN_ID), eq(INIT_TASK_NAME))).thenReturn(null);

        final RunLog log = buildRunLog(RUN_ID, INIT_TASK_NAME, TaskStatus.RUNNING, "running");
        logManager.saveLog(log);

        verify(runCRUDServiceMock, never()).updateRunInitialized(anyLong());
    }

    @Test
    public void saveLogShouldNotMarkInitializedForOtherTask() {
        final PipelineRun run = buildRun(RUN_ID, TaskStatus.RUNNING);
        run.setInitialized(false);
        when(runCRUDServiceMock.loadRunById(RUN_ID)).thenReturn(run);
        when(runLogDao.loadTaskStatus(eq(RUN_ID), eq(TASK_NAME))).thenReturn(null);

        final RunLog log = buildRunLog(RUN_ID, TASK_NAME, TaskStatus.SUCCESS, "done");
        logManager.saveLog(log);

        verify(runCRUDServiceMock, never()).updateRunInitialized(anyLong());
    }

    private static PipelineRun buildRun(final Long runId, final TaskStatus status) {
        final PipelineRun run = new PipelineRun(runId, "");
        run.setStatus(status);
        run.setEndDate(new Date());
        return run;
    }
}
