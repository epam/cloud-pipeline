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

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RunLogStorageManager {

    private static final String[] CSV_HEADER = {"runId", "date", "status", "taskName", "instance", "logText"};
    private static final int CSV_COL_RUN_ID = 0;
    private static final int CSV_COL_DATE = 1;
    private static final int CSV_COL_STATUS = 2;
    private static final int CSV_COL_TASK_NAME = 3;
    private static final int CSV_COL_INSTANCE = 4;
    private static final int CSV_COL_LOG_TEXT = 5;
    private static final int CSV_COLUMN_COUNT = 6;

    private final DataStorageManager dataStorageManager;
    private final PreferenceManager preferenceManager;

    public boolean isRunLogMigrationConfigured() {
        final String systemStorageName = preferenceManager.getPreference(
                SystemPreferences.DATA_STORAGE_SYSTEM_DATA_STORAGE_NAME);
        final String pathPrefix = preferenceManager.getPreference(
                SystemPreferences.DATA_STORAGE_SYSTEM_RUN_LOGS_PATH_PREFIX);
        final Boolean migrationEnabled = preferenceManager.getPreference(
                SystemPreferences.DATA_STORAGE_SYSTEM_RUN_LOGS_TRANSFER_ENABLE);
        return migrationEnabled &&
                StringUtils.isNotBlank(systemStorageName) && StringUtils.isNotBlank(pathPrefix);
    }

    public void saveLogsToStorage(final Long runId, final Map<String, List<RunLog>> logsByTask) {
        final AbstractDataStorage storage = resolveStorage();
        final String runFolder = resolvePathPrefix() + runId;

        for (Map.Entry<String, List<RunLog>> entry : logsByTask.entrySet()) {
            final String taskName = entry.getKey();
            final List<RunLog> taskLogs = entry.getValue();
            final byte[] content = serializeLogs(taskLogs);
            dataStorageManager.createDataStorageFile(
                    storage.getId(), runFolder, taskName, new ByteArrayInputStream(content));
        }
    }

    public List<RunLog> loadLogsFromStorage(final Long runId) {
        final AbstractDataStorage storage = resolveStorage();
        if (storage == null) {
            return Collections.emptyList();
        }
        final String runFolder = resolvePathPrefix() + runId;
        return loadAllTaskLogsFromFolder(storage, runFolder);
    }

    public List<RunLog> loadTaskLogsFromStorage(final Long runId, final String taskName) {
        final AbstractDataStorage storage = resolveStorage();
        if (storage == null) {
            return Collections.emptyList();
        }
        final String runFolder = resolvePathPrefix() + runId;
        return loadTaskLogsFromFile(storage, runFolder, taskName);
    }

    public List<PipelineTask> loadTasksFromStorage(final Long runId) {
        final AbstractDataStorage storage = resolveStorage();
        if (storage == null) {
            return Collections.emptyList();
        }
        final String runFolder = resolvePathPrefix() + runId;
        try {
            final DataStorageListing listing = dataStorageManager.getDataStorageItems(
                    storage.getId(), runFolder, false, null, null, false);
            if (listing == null || CollectionUtils.isEmpty(listing.getResults())) {
                return Collections.emptyList();
            }
            return listing.getResults().stream()
                    .filter(item -> DataStorageItemType.File.equals(item.getType()))
                    .map(item -> {
                        final List<RunLog> taskLogs = loadTaskLogsFromFile(storage, runFolder, item.getName());
                        return buildPipelineTask(item.getName(), taskLogs);
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to load tasks from storage for run {}: {}", runId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private PipelineTask buildPipelineTask(final String taskName, final List<RunLog> logs) {
        final PipelineTask task = new PipelineTask(taskName);
        if (!logs.isEmpty()) {
            task.setStatus(logs.get(logs.size() - 1).getStatus());
            task.setCreated(logs.get(0).getDate());
            task.setStarted(logs.get(0).getDate());
            task.setFinished(logs.get(logs.size() - 1).getDate());
            if (StringUtils.isNotBlank(logs.get(0).getInstance())) {
                task.setInstance(logs.get(0).getInstance());
            }
        }
        return task;
    }

    private List<RunLog> loadAllTaskLogsFromFolder(final AbstractDataStorage storage,
                                                   final String runFolder) {
        try {
            final DataStorageListing listing = dataStorageManager.getDataStorageItems(
                    storage.getId(), runFolder, false, null, null, false);
            if (listing == null || CollectionUtils.isEmpty(listing.getResults())) {
                return Collections.emptyList();
            }
            return listing.getResults().stream()
                    .filter(item -> DataStorageItemType.File.equals(item.getType()))
                    .flatMap(item -> loadTaskLogsFromFile(storage, runFolder, item.getName()).stream())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to list logs from storage folder {}: {}", runFolder, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<RunLog> loadTaskLogsFromFile(final AbstractDataStorage storage,
                                              final String runFolder,
                                              final String taskName) {
        try {
            final String filePath = runFolder + "/" + taskName;
            final DataStorageItemContent content = dataStorageManager.getDataStorageItemContent(
                    storage.getId(), filePath, null);
            return deserializeLogs(content.getContent());
        } catch (Exception e) {
            log.warn("Failed to load logs for task {} from storage: {}", taskName, e.getMessage());
            return Collections.emptyList();
        }
    }

    private byte[] serializeLogs(final List<RunLog> logs) {
        final StringWriter stringWriter = new StringWriter();
        try (CSVWriter csvWriter = new CSVWriter(stringWriter)) {
            csvWriter.writeNext(CSV_HEADER);
            for (RunLog logEntry : logs) {
                csvWriter.writeNext(new String[]{
                        String.valueOf(logEntry.getRunId()),
                        logEntry.getDate() != null ? String.valueOf(logEntry.getDate().getTime()) : "",
                        logEntry.getStatus() != null ? logEntry.getStatus().name() : "",
                        StringUtils.defaultString(logEntry.getTaskName()),
                        StringUtils.defaultString(logEntry.getInstance()),
                        StringUtils.defaultString(logEntry.getLogText())
                });
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize run logs to CSV", e);
        }
        return stringWriter.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<RunLog> deserializeLogs(final byte[] content) {
        final List<RunLog> result = new ArrayList<>();
        try (CSVReader csvReader = new CSVReader(
                new StringReader(new String(content, StandardCharsets.UTF_8)))) {
            String[] header = csvReader.readNext();
            if (header == null) {
                return result;
            }
            String[] line;
            while ((line = csvReader.readNext()) != null) {
                if (line.length < CSV_COLUMN_COUNT) {
                    continue;
                }
                final RunLog logEntry = new RunLog();
                logEntry.setRunId(Long.parseLong(line[CSV_COL_RUN_ID]));
                if (StringUtils.isNotBlank(line[CSV_COL_DATE])) {
                    logEntry.setDate(new Date(Long.parseLong(line[CSV_COL_DATE])));
                }
                if (StringUtils.isNotBlank(line[CSV_COL_STATUS])) {
                    logEntry.setStatus(TaskStatus.getByName(line[CSV_COL_STATUS]));
                }
                logEntry.setTaskName(StringUtils.trimToNull(line[CSV_COL_TASK_NAME]));
                logEntry.setInstance(StringUtils.trimToNull(line[CSV_COL_INSTANCE]));
                logEntry.setLogText(line[CSV_COL_LOG_TEXT]);
                if (StringUtils.isNotBlank(logEntry.getTaskName())) {
                    logEntry.setTask(new PipelineTask(logEntry.getTaskName()));
                }
                result.add(logEntry);
            }
        } catch (IOException e) {
            log.warn("Failed to deserialize run logs from storage: {}", e.getMessage());
            return Collections.emptyList();
        }
        return result;
    }

    private AbstractDataStorage resolveStorage() {
        final String systemStorageName = preferenceManager.getPreference(
                SystemPreferences.DATA_STORAGE_SYSTEM_DATA_STORAGE_NAME);
        if (StringUtils.isBlank(systemStorageName)) {
            return null;
        }
        try {
            return dataStorageManager.loadByNameOrId(systemStorageName);
        } catch (Exception e) {
            log.warn("Failed to resolve system storage: {}", e.getMessage());
            return null;
        }
    }

    private String resolvePathPrefix() {
        return StringUtils.defaultString(
                preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_SYSTEM_RUN_LOGS_PATH_PREFIX),
                "logs/runs/");
    }
}
