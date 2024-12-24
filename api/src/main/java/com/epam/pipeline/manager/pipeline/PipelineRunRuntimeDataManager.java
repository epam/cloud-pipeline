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
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        final RunSyncRuntimeDataConfig runSyncRuntimeDataConfig = preferenceManager.getObjectPreferenceAs(
                SystemPreferences.LAUNCH_RUN_SYNC_RUNTIME_DATA, new TypeReference<RunSyncRuntimeDataConfig>() {}
        );
        final RunSyncRuntimeDataConfigEntry dataSyncEntry = MapUtils.emptyIfNull(
                runSyncRuntimeDataConfig.getData()).get(type);

        if (dataSyncEntry == null) {
            throw new IllegalArgumentException(
                    String.format("There is not runtime data of type %s for run %d", type, runId));
        }

        final PipelineRunRuntimeDataExtractor dataExtractor = dataExtractors.stream()
                .filter(de -> de.getDataType() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("There is not runtime dataExtractor defined of type %s", type))
                );

        final String runFolderStoragePathPrefix = dataSyncEntry.getRunFolderPathPrefix();
        final AbstractDataStorage dataStorage = dataStorageManager.loadByPathOrId(runFolderStoragePathPrefix);

        final String runFolderPathPrefix = runFolderStoragePathPrefix.replace(dataStorage.getPath(), StringUtils.EMPTY);
        final String dataPathPrefix = dataSyncEntry.getDataPathPrefix();
        final String dataFileName = dataExtractor.getDataFilePath(parameters);

        final String dataFilePath = Stream.of(runFolderPathPrefix, String.valueOf(runId), dataPathPrefix, dataFileName)
                .filter(Objects::nonNull).collect(Collectors.joining(PATH_DELIMITER));

        final DataStorageStreamingContent fileContent = dataStorageManager
                .getStreamingContent(dataStorage.getId(), dataFilePath, null);

        return dataExtractor.parseData(fileContent.getContent());
    }
}
