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
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class RunLogStorageManager {

    private static final String LOG_FILE_NAME = "log";
    private static final String METADATA_FILE_NAME = "metadata";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<RunLog>> RUN_LOG_LIST_TYPE = new TypeReference<List<RunLog>>() {};

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

    public String buildLogsStoragePath(final Long runId) {
        final AbstractDataStorage storage = resolveStorage();
        if (storage == null) {
            return null;
        }
        return storage.getPathMask() + "/" + resolvePathPrefix() + runId;
    }

    public void saveLogsToStorage(final Long runId,
                                  final Map<PipelineTask, List<RunLog>> logsByTask) {
        final AbstractDataStorage storage = resolveStorage();
        final String runFolder = resolvePathPrefix() + runId;

        for (Map.Entry<PipelineTask, List<RunLog>> entry : logsByTask.entrySet()) {
            final PipelineTask task = entry.getKey();
            final List<RunLog> taskLogs = entry.getValue();
            final String taskFolder = runFolder + "/"
                    + PipelineTask.buildTaskId(task.getName(), task.getParameters());

            final byte[] logContent = serializeLogs(taskLogs);
            dataStorageManager.createDataStorageFile(
                    storage.getId(), taskFolder, LOG_FILE_NAME, new ByteArrayInputStream(logContent));

            final byte[] metaContent = serializeTaskMetadata(task);
            dataStorageManager.createDataStorageFile(
                    storage.getId(), taskFolder, METADATA_FILE_NAME, new ByteArrayInputStream(metaContent));
        }
    }

    public List<RunLog> loadLogsFromStorage(final Long runId) {
        final AbstractDataStorage storage = resolveStorage();
        if (storage == null) {
            return Collections.emptyList();
        }
        final String runFolder = resolvePathPrefix() + runId;
        return loadAllTaskLogs(storage, runFolder);
    }

    public List<RunLog> loadTaskLogsFromStorage(final Long runId, final String taskName) {
        final AbstractDataStorage storage = resolveStorage();
        if (storage == null) {
            return Collections.emptyList();
        }
        final String logPath = resolvePathPrefix() + runId + "/" + taskName + "/" + LOG_FILE_NAME;
        return loadLogsFromFile(storage, logPath);
    }

    public List<PipelineTask> loadTasksFromStorage(final Long runId) {
        final AbstractDataStorage storage = resolveStorage();
        if (storage == null) {
            return Collections.emptyList();
        }
        final String runLogStorageFolder = resolvePathPrefix() + runId;
        try {
            final DataStorageListing listing = dataStorageManager.getDataStorageItems(
                    storage.getId(), runLogStorageFolder, false, null, null, false);
            if (listing == null || CollectionUtils.isEmpty(listing.getResults())) {
                return Collections.emptyList();
            }
            return listing.getResults().stream()
                    .filter(item -> DataStorageItemType.Folder.equals(item.getType()))
                    .map(item -> loadTaskMetadata(storage, runLogStorageFolder + "/" + item.getName()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to load tasks from storage for run {}: {}", runId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<RunLog> loadAllTaskLogs(final AbstractDataStorage storage, final String runFolder) {
        try {
            final DataStorageListing listing = dataStorageManager.getDataStorageItems(
                    storage.getId(), runFolder, false, null, null, false);
            if (listing == null || CollectionUtils.isEmpty(listing.getResults())) {
                return Collections.emptyList();
            }
            return listing.getResults().stream()
                    .filter(item -> DataStorageItemType.Folder.equals(item.getType()))
                    .flatMap(item -> loadLogsFromFile(
                            storage, runFolder + "/" + item.getName() + "/" + LOG_FILE_NAME).stream())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to list logs from storage folder {}: {}", runFolder, e.getMessage());
            return Collections.emptyList();
        }
    }

    private PipelineTask loadTaskMetadata(final AbstractDataStorage storage, final String taskFolder) {
        final String metaPath = taskFolder + "/" + METADATA_FILE_NAME;
        try {
            final DataStorageItemContent content = dataStorageManager.getDataStorageItemContent(
                    storage.getId(), metaPath, null);
            return deserializeTaskMetadata(content.getContent());
        } catch (Exception e) {
            log.warn("Failed to load task metadata from {}: {}", metaPath, e.getMessage());
            return null;
        }
    }

    private List<RunLog> loadLogsFromFile(final AbstractDataStorage storage, final String logPath) {
        try {
            final DataStorageItemContent content = dataStorageManager.getDataStorageItemContent(
                    storage.getId(), logPath, null);
            return deserializeLogs(content.getContent());
        } catch (Exception e) {
            log.warn("Failed to load logs from {}: {}", logPath, e.getMessage());
            return Collections.emptyList();
        }
    }

    private byte[] serializeLogs(final List<RunLog> logs) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(logs);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize run logs to JSON", e);
        }
    }

    private List<RunLog> deserializeLogs(final byte[] content) {
        try {
            return OBJECT_MAPPER.readValue(content, RUN_LOG_LIST_TYPE);
        } catch (IOException e) {
            log.warn("Failed to deserialize run logs from storage: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private byte[] serializeTaskMetadata(final PipelineTask task) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(task);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize task metadata to JSON", e);
        }
    }

    private PipelineTask deserializeTaskMetadata(final byte[] content) {
        try {
            return OBJECT_MAPPER.readValue(content, PipelineTask.class);
        } catch (IOException e) {
            log.warn("Failed to deserialize task metadata from storage: {}", e.getMessage());
            return new PipelineTask();
        }
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
