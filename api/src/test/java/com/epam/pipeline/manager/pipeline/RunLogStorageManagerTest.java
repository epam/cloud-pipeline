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

import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RunLogStorageManagerTest {

    private static final Long STORAGE_ID = 1L;
    private static final String SYSTEM_STORAGE_NAME = "testStorage";
    private static final String PATH_PREFIX = "logs/runs/";
    private static final Long RUN_ID = 100L;
    private static final String TASK_1 = "task1";
    private static final String TASK_2 = "task2";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private DataStorageManager dataStorageManager;
    @Mock
    private PreferenceManager preferenceManager;

    private RunLogStorageManager runLogStorageManager;
    private S3bucketDataStorage testStorage;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        runLogStorageManager = new RunLogStorageManager(dataStorageManager, preferenceManager);
        testStorage = new S3bucketDataStorage(STORAGE_ID, SYSTEM_STORAGE_NAME, "test");

        when(preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_SYSTEM_DATA_STORAGE_NAME))
                .thenReturn(SYSTEM_STORAGE_NAME);
        when(preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_SYSTEM_RUN_LOGS_PATH_PREFIX))
                .thenReturn(PATH_PREFIX);
        when(preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_SYSTEM_RUN_LOGS_TRANSFER_ENABLE))
                .thenReturn(true);
        when(dataStorageManager.loadByNameOrId(SYSTEM_STORAGE_NAME)).thenReturn(testStorage);
    }

    @Test
    public void isRunLogMigrationConfiguredShouldReturnTrueWhenFullyConfigured() {
        assertTrue(runLogStorageManager.isRunLogMigrationConfigured());
    }

    @Test
    public void isRunLogMigrationConfiguredShouldReturnFalseWhenStorageNameBlank() {
        when(preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_SYSTEM_DATA_STORAGE_NAME))
                .thenReturn(null);
        assertFalse(runLogStorageManager.isRunLogMigrationConfigured());
    }

    @Test
    public void isRunLogMigrationConfiguredShouldReturnFalseWhenPathPrefixBlank() {
        when(preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_SYSTEM_RUN_LOGS_PATH_PREFIX))
                .thenReturn(null);
        assertFalse(runLogStorageManager.isRunLogMigrationConfigured());
    }

    @Test
    public void isRunLogMigrationConfiguredShouldReturnFalseWhenMigrationDisabled() {
        when(preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_SYSTEM_RUN_LOGS_TRANSFER_ENABLE))
                .thenReturn(false);
        assertFalse(runLogStorageManager.isRunLogMigrationConfigured());
    }

    @Test
    public void buildLogsStoragePathShouldReturnFullCloudPath() {
        final String result = runLogStorageManager.buildLogsStoragePath(RUN_ID);

        assertEquals(testStorage.getPathMask() + "/" + PATH_PREFIX + RUN_ID, result);
    }

    @Test
    public void buildLogsStoragePathShouldReturnNullWhenStorageNotResolved() {
        when(preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_SYSTEM_DATA_STORAGE_NAME))
                .thenReturn(null);

        assertNull(runLogStorageManager.buildLogsStoragePath(RUN_ID));
    }

    @Test
    public void saveLogsToStorageShouldCreateLogAndMetadataFiles() {
        final RunLog log1 = buildRunLog(RUN_ID, TASK_1, TaskStatus.RUNNING, "Log line 1");
        final RunLog log2 = buildRunLog(RUN_ID, TASK_1, TaskStatus.SUCCESS, "Log line 2");
        final PipelineTask task = buildPipelineTask(TASK_1, TaskStatus.SUCCESS);

        final Map<PipelineTask, List<RunLog>> logsByTask = Collections.singletonMap(
                task, Arrays.asList(log1, log2));

        runLogStorageManager.saveLogsToStorage(RUN_ID, logsByTask);

        final String expectedTaskFolder = PATH_PREFIX + RUN_ID + "/" + TASK_1;
        verify(dataStorageManager).createDataStorageFile(
                eq(STORAGE_ID), eq(expectedTaskFolder), eq("log"), any(InputStream.class));
        verify(dataStorageManager).createDataStorageFile(
                eq(STORAGE_ID), eq(expectedTaskFolder), eq("metadata"), any(InputStream.class));
    }

    @Test
    public void saveLogsToStorageShouldHandleMultipleTasks() {
        final RunLog log1 = buildRunLog(RUN_ID, TASK_1, TaskStatus.SUCCESS, "Log from task 1");
        final RunLog log2 = buildRunLog(RUN_ID, TASK_2, TaskStatus.SUCCESS, "Log from task 2");
        final PipelineTask task1 = buildPipelineTask(TASK_1, TaskStatus.SUCCESS);
        final PipelineTask task2 = buildPipelineTask(TASK_2, TaskStatus.SUCCESS);

        final Map<PipelineTask, List<RunLog>> logsByTask = new HashMap<>();
        logsByTask.put(task1, Collections.singletonList(log1));
        logsByTask.put(task2, Collections.singletonList(log2));

        runLogStorageManager.saveLogsToStorage(RUN_ID, logsByTask);

        verify(dataStorageManager, times(4)).createDataStorageFile(
                eq(STORAGE_ID), any(String.class), any(String.class), any(InputStream.class));
    }

    @Test
    public void loadLogsFromStorageShouldReturnDeserializedLogs() {
        mockTaskFolderListing(TASK_1);
        mockLogFileContent(TASK_1, buildLogJsonBytes(RUN_ID, TASK_1, TaskStatus.RUNNING, "Hello log"));

        final List<RunLog> result = runLogStorageManager.loadLogsFromStorage(RUN_ID);

        assertEquals(1, result.size());
        assertEquals(RUN_ID, result.get(0).getRunId());
        assertEquals(TASK_1, result.get(0).getTaskName());
        assertEquals(TaskStatus.RUNNING, result.get(0).getStatus());
        assertEquals("Hello log", result.get(0).getLogText());
    }

    @Test
    public void loadLogsFromStorageShouldCombineLogsFromMultipleTasks() {
        mockTaskFolderListing(TASK_1, TASK_2);
        mockLogFileContent(TASK_1, buildLogJsonBytes(RUN_ID, TASK_1, TaskStatus.RUNNING, "Log 1"));
        mockLogFileContent(TASK_2, buildLogJsonBytes(RUN_ID, TASK_2, TaskStatus.SUCCESS, "Log 2"));

        final List<RunLog> result = runLogStorageManager.loadLogsFromStorage(RUN_ID);

        assertEquals(2, result.size());
    }

    @Test
    public void loadLogsFromStorageShouldReturnEmptyWhenStorageNotResolved() {
        when(preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_SYSTEM_DATA_STORAGE_NAME))
                .thenReturn(null);

        final List<RunLog> result = runLogStorageManager.loadLogsFromStorage(RUN_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    public void loadLogsFromStorageShouldReturnEmptyWhenListingEmpty() {
        when(dataStorageManager.getDataStorageItems(
                eq(STORAGE_ID), eq(PATH_PREFIX + RUN_ID), eq(false),
                isNull(Integer.class), isNull(String.class), eq(false)))
                .thenReturn(new DataStorageListing(null, null, Collections.emptyList()));

        final List<RunLog> result = runLogStorageManager.loadLogsFromStorage(RUN_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    public void loadTaskLogsFromStorageShouldReturnLogsForSpecificTask() {
        final byte[] jsonContent = buildLogJsonBytes(RUN_ID, TASK_1, TaskStatus.SUCCESS, "Task log");
        final DataStorageItemContent content = new DataStorageItemContent();
        content.setContent(jsonContent);

        when(dataStorageManager.getDataStorageItemContent(
                eq(STORAGE_ID), eq(PATH_PREFIX + RUN_ID + "/" + TASK_1 + "/log"), isNull(String.class)))
                .thenReturn(content);

        final List<RunLog> result = runLogStorageManager.loadTaskLogsFromStorage(RUN_ID, TASK_1);

        assertEquals(1, result.size());
        assertEquals(TASK_1, result.get(0).getTaskName());
        assertEquals(TaskStatus.SUCCESS, result.get(0).getStatus());
        assertEquals("Task log", result.get(0).getLogText());
    }

    @Test
    public void loadTaskLogsFromStorageShouldReturnEmptyWhenStorageNotResolved() {
        when(preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_SYSTEM_DATA_STORAGE_NAME))
                .thenReturn(null);

        final List<RunLog> result = runLogStorageManager.loadTaskLogsFromStorage(RUN_ID, TASK_1);

        assertTrue(result.isEmpty());
    }

    @Test
    public void loadTasksFromStorageShouldReturnDeserializedTasks() throws IOException {
        mockTaskFolderListing(TASK_1);
        mockMetadataFileContent(TASK_1, buildPipelineTask(TASK_1, TaskStatus.SUCCESS));

        final List<PipelineTask> result = runLogStorageManager.loadTasksFromStorage(RUN_ID);

        assertEquals(1, result.size());
        assertEquals(TASK_1, result.get(0).getName());
        assertEquals(TaskStatus.SUCCESS, result.get(0).getStatus());
    }

    @Test
    public void loadTasksFromStorageShouldReturnMultipleTasks() throws IOException {
        mockTaskFolderListing(TASK_1, TASK_2);
        mockMetadataFileContent(TASK_1, buildPipelineTask(TASK_1, TaskStatus.SUCCESS));
        mockMetadataFileContent(TASK_2, buildPipelineTask(TASK_2, TaskStatus.FAILURE));

        final List<PipelineTask> result = runLogStorageManager.loadTasksFromStorage(RUN_ID);

        assertEquals(2, result.size());
    }

    @Test
    public void loadTasksFromStorageShouldReturnEmptyWhenStorageNotResolved() {
        when(preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_SYSTEM_DATA_STORAGE_NAME))
                .thenReturn(null);

        final List<PipelineTask> result = runLogStorageManager.loadTasksFromStorage(RUN_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    public void serializeDeserializeLogsRoundTrip() {
        final Date now = new Date();
        final RunLog original = RunLog.builder()
                .runId(RUN_ID)
                .date(now)
                .status(TaskStatus.RUNNING)
                .taskName(TASK_1)
                .instance("instance-1")
                .logText("Line with \"quotes\" and,commas\nand newlines")
                .build();

        final ArgumentCaptor<InputStream> streamCaptor = ArgumentCaptor.forClass(InputStream.class);

        final Map<PipelineTask, List<RunLog>> logsByTask = Collections.singletonMap(
                buildPipelineTask(TASK_1, TaskStatus.RUNNING), Collections.singletonList(original));

        runLogStorageManager.saveLogsToStorage(RUN_ID, logsByTask);

        verify(dataStorageManager).createDataStorageFile(
                eq(STORAGE_ID), eq(PATH_PREFIX + RUN_ID + "/" + TASK_1), eq("log"),
                streamCaptor.capture());

        final byte[] capturedBytes = readStream(streamCaptor.getValue());
        mockLogFileContent(TASK_1, capturedBytes);
        mockTaskFolderListing(TASK_1);

        final List<RunLog> result = runLogStorageManager.loadLogsFromStorage(RUN_ID);

        assertEquals(1, result.size());
        final RunLog loaded = result.get(0);
        assertEquals(original.getRunId(), loaded.getRunId());
        assertEquals(original.getDate(), loaded.getDate());
        assertEquals(original.getStatus(), loaded.getStatus());
        assertEquals(original.getTaskName(), loaded.getTaskName());
        assertEquals(original.getInstance(), loaded.getInstance());
        assertEquals(original.getLogText(), loaded.getLogText());
    }

    @Test
    public void serializeDeserializeTaskMetadataRoundTrip() throws IOException {
        final PipelineTask original = new PipelineTask();
        original.setName(TASK_1);
        original.setStatus(TaskStatus.SUCCESS);
        original.setInstance("instance-1");
        original.setCreated(new Date());
        original.setStarted(new Date());
        original.setFinished(new Date());

        final ArgumentCaptor<InputStream> streamCaptor = ArgumentCaptor.forClass(InputStream.class);

        final Map<PipelineTask, List<RunLog>> logsByTask = Collections.singletonMap(
                original, Collections.singletonList(buildRunLog(RUN_ID, TASK_1, TaskStatus.SUCCESS, "log")));

        runLogStorageManager.saveLogsToStorage(RUN_ID, logsByTask);

        verify(dataStorageManager).createDataStorageFile(
                eq(STORAGE_ID), eq(PATH_PREFIX + RUN_ID + "/" + TASK_1), eq("metadata"),
                streamCaptor.capture());

        final byte[] capturedBytes = readStream(streamCaptor.getValue());
        final DataStorageItemContent metaContent = new DataStorageItemContent();
        metaContent.setContent(capturedBytes);

        mockTaskFolderListing(TASK_1);
        when(dataStorageManager.getDataStorageItemContent(
                eq(STORAGE_ID), eq(PATH_PREFIX + RUN_ID + "/" + TASK_1 + "/metadata"), isNull(String.class)))
                .thenReturn(metaContent);

        final List<PipelineTask> result = runLogStorageManager.loadTasksFromStorage(RUN_ID);

        assertEquals(1, result.size());
        final PipelineTask loaded = result.get(0);
        assertEquals(original.getName(), loaded.getName());
        assertEquals(original.getStatus(), loaded.getStatus());
        assertEquals(original.getInstance(), loaded.getInstance());
    }

    @Test
    public void saveAndLoadRoundTripWithParameterizedTask() throws IOException {
        final String taskName = "mainTask";
        final String taskParams = "param1";
        final String taskId = PipelineTask.buildTaskId(taskName, taskParams);

        final PipelineTask task = new PipelineTask();
        task.setName(taskName);
        task.setParameters(taskParams);
        task.setStatus(TaskStatus.SUCCESS);
        task.setInstance("instance-1");
        task.setCreated(new Date());
        task.setStarted(new Date());
        task.setFinished(new Date());

        final RunLog log1 = buildRunLog(RUN_ID, taskId, TaskStatus.RUNNING, "Starting task");
        final RunLog log2 = buildRunLog(RUN_ID, taskId, TaskStatus.SUCCESS, "Task completed");

        final ArgumentCaptor<InputStream> logCaptor = ArgumentCaptor.forClass(InputStream.class);
        final ArgumentCaptor<InputStream> metaCaptor = ArgumentCaptor.forClass(InputStream.class);

        final String expectedFolder = PATH_PREFIX + RUN_ID + "/" + taskId;
        final Map<PipelineTask, List<RunLog>> logsByTask = Collections.singletonMap(
                task, Arrays.asList(log1, log2));

        runLogStorageManager.saveLogsToStorage(RUN_ID, logsByTask);

        verify(dataStorageManager).createDataStorageFile(
                eq(STORAGE_ID), eq(expectedFolder), eq("log"), logCaptor.capture());
        verify(dataStorageManager).createDataStorageFile(
                eq(STORAGE_ID), eq(expectedFolder), eq("metadata"), metaCaptor.capture());

        final byte[] savedLogBytes = readStream(logCaptor.getValue());
        final byte[] savedMetaBytes = readStream(metaCaptor.getValue());

        mockTaskFolderListing(taskId);

        final DataStorageItemContent logContent = new DataStorageItemContent();
        logContent.setContent(savedLogBytes);
        when(dataStorageManager.getDataStorageItemContent(
                eq(STORAGE_ID), eq(expectedFolder + "/log"), isNull(String.class)))
                .thenReturn(logContent);

        final DataStorageItemContent metaContent = new DataStorageItemContent();
        metaContent.setContent(savedMetaBytes);
        when(dataStorageManager.getDataStorageItemContent(
                eq(STORAGE_ID), eq(expectedFolder + "/metadata"), isNull(String.class)))
                .thenReturn(metaContent);

        final List<RunLog> loadedLogs = runLogStorageManager.loadLogsFromStorage(RUN_ID);
        assertEquals(2, loadedLogs.size());
        assertEquals(taskId, loadedLogs.get(0).getTaskName());
        assertEquals("Starting task", loadedLogs.get(0).getLogText());
        assertEquals("Task completed", loadedLogs.get(1).getLogText());

        final List<PipelineTask> loadedTasks = runLogStorageManager.loadTasksFromStorage(RUN_ID);
        assertEquals(1, loadedTasks.size());
        assertEquals(taskName, loadedTasks.get(0).getName());
        assertEquals(taskParams, loadedTasks.get(0).getParameters());
        assertEquals(TaskStatus.SUCCESS, loadedTasks.get(0).getStatus());
        assertEquals(task.getInstance(), loadedTasks.get(0).getInstance());
    }

    private void mockTaskFolderListing(final String... taskNames) {
        final List<AbstractDataStorageItem> folders = new java.util.ArrayList<>();
        for (String taskName : taskNames) {
            final DataStorageFolder folder = new DataStorageFolder();
            folder.setName(taskName);
            folders.add(folder);
        }
        when(dataStorageManager.getDataStorageItems(
                eq(STORAGE_ID), eq(PATH_PREFIX + RUN_ID), eq(false),
                isNull(Integer.class), isNull(String.class), eq(false)))
                .thenReturn(new DataStorageListing(null, null, folders));
    }

    private void mockLogFileContent(final String taskName, final byte[] jsonBytes) {
        final DataStorageItemContent content = new DataStorageItemContent();
        content.setContent(jsonBytes);
        when(dataStorageManager.getDataStorageItemContent(
                eq(STORAGE_ID), eq(PATH_PREFIX + RUN_ID + "/" + taskName + "/log"), isNull(String.class)))
                .thenReturn(content);
    }

    private void mockMetadataFileContent(final String taskName, final PipelineTask task) throws IOException {
        final DataStorageItemContent content = new DataStorageItemContent();
        content.setContent(OBJECT_MAPPER.writeValueAsBytes(task));
        when(dataStorageManager.getDataStorageItemContent(
                eq(STORAGE_ID), eq(PATH_PREFIX + RUN_ID + "/" + taskName + "/metadata"), isNull(String.class)))
                .thenReturn(content);
    }

    private static byte[] buildLogJsonBytes(final Long runId, final String taskName,
                                            final TaskStatus status, final String logText) {
        try {
            final RunLog log = buildRunLog(runId, taskName, status, logText);
            return OBJECT_MAPPER.writeValueAsBytes(Collections.singletonList(log));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    private static byte[] readStream(final InputStream stream) {
        try {
            final byte[] buffer = new byte[8192];
            final java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            int read;
            while ((read = stream.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
