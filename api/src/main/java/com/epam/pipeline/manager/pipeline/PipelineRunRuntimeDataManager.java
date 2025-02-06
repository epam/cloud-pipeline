/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.pipeline.run.runtime.RunRuntimeData;
import com.epam.pipeline.entity.pipeline.run.runtime.RunSyncRuntimeDataConfig;
import com.epam.pipeline.entity.pipeline.run.runtime.RunSyncRuntimeDataConfigEntry;
import com.epam.pipeline.entity.pipeline.run.runtime.RunSyncRuntimeDataType;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.pipeline.runtime.PipelineRunRuntimeDataExtractor;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Slf4j
@Service
@AllArgsConstructor
public class PipelineRunRuntimeDataManager {


    private static final String PATH_DELIMITER = "/";

    @Autowired
    private final PreferenceManager preferenceManager;

    @Autowired
    private final DataStorageManager dataStorageManager;

    @Autowired
    private final List<PipelineRunRuntimeDataExtractor> dataExtractors;

    public RunRuntimeData getPipelineRunRuntimeData(final Long runId, final RunSyncRuntimeDataType type,
                                                    final Map<String, String> parameters) {
        log.debug("For the run {} runtime data {} was requested, with parameters {}.", runId, type, parameters);

        final RunSyncRuntimeDataConfigEntry dataSyncEntry = Optional.ofNullable(
                preferenceManager.getPreference(SystemPreferences.LAUNCH_RUN_SYNC_RUNTIME_DATA))
                .map(RunSyncRuntimeDataConfig::getData)
                .map(dataTypeConfigs -> dataTypeConfigs.get(type))
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format(
                                "There is no configuration for data of type %s in %s preference",
                                type, SystemPreferences.LAUNCH_RUN_SYNC_RUNTIME_DATA.getKey()))
                );

        final PipelineRunRuntimeDataExtractor dataExtractor = dataExtractors.stream()
                .filter(de -> de.getDataType() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("There is not runtime dataExtractor defined of type %s", type))
                );

        final String runFolderStoragePathPrefix = defineRunFolderStoragePathPrefix(dataSyncEntry, type);
        final AbstractDataStorage dataStorage = dataStorageManager.loadByPathOrId(runFolderStoragePathPrefix);

        final String runFolderPathPrefix = runFolderStoragePathPrefix
                .replace(dataStorage.getPath(), StringUtils.EMPTY);
        final String dataPathPrefix = dataSyncEntry.getDataPathPrefix();
        final String dataFileName = dataExtractor.getDataFilePath(parameters);

        final String dataFilePath = Stream.of(runFolderPathPrefix, String.valueOf(runId), dataPathPrefix, dataFileName)
                .filter(StringUtils::isNotBlank)
                .map(this::cleanupPath)
                .collect(Collectors.joining(PATH_DELIMITER));

        log.debug("Constructed file path '{}' in storage {}, to get runtime data for the run {}.",
                dataFileName, dataStorage.getId(), runId);

        dataStorageManager.checkDataStorageObjectExists(dataStorage, dataFilePath, null);
        final DataStorageStreamingContent fileContent = dataStorageManager
                .getStreamingContent(dataStorage.getId(), dataFilePath, null);

        return dataExtractor.parseData(parameters, fileContent.getContent());
    }

    private static String defineRunFolderStoragePathPrefix(final RunSyncRuntimeDataConfigEntry dataSyncEntry,
                                                           final RunSyncRuntimeDataType type) {
        String runFolderStoragePathPrefix = dataSyncEntry.getRunFolderPathPrefix();
        Assert.notNull(runFolderStoragePathPrefix,
                String.format("Invalid configuration in %s for datatype %s: runFolderPathPrefix should be specified",
                        SystemPreferences.LAUNCH_RUN_SYNC_RUNTIME_DATA.getKey(), type));
        if (runFolderStoragePathPrefix.matches("\\w+://.*")) {
            runFolderStoragePathPrefix = runFolderStoragePathPrefix.replaceFirst("\\w+://", "");
        }
        return runFolderStoragePathPrefix;
    }

    private String cleanupPath(final String path) {
        String result = path;
        if (path.startsWith(PATH_DELIMITER)) {
            result = result.substring(1);
        }
        if (path.endsWith(PATH_DELIMITER)) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
