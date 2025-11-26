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
import com.epam.pipeline.entity.pipeline.run.runtime.RunSyncRuntimeEvalType;
import com.epam.pipeline.manager.pipeline.runtime.PipelineRunRuntimeDataExtractor;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.nio.file.Paths;
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


        final String runtimeDataDirPath;
        final RunSyncRuntimeEvalType evalType = dataSyncEntry.getEvalType();
        if (evalType == RunSyncRuntimeEvalType.HASH || type.equals(RunSyncRuntimeDataType.NF_TRACE)) {
            if (evalType == RunSyncRuntimeEvalType.HASH) {
                validateParams(RunSyncRuntimeEvalType.HASH.getValue(), parameters, evalType);
            }
            runtimeDataDirPath = Stream.of(
                            dataSyncEntry.getRunFolderPathPrefix(),
                            String.valueOf(runId),
                            dataSyncEntry.getDataPathPrefix(),
                            MapUtils.emptyIfNull(parameters).get(RunSyncRuntimeEvalType.HASH.getValue())
                    ).filter(StringUtils::isNotBlank)
                    .map(this::cleanupPath)
                    .collect(Collectors.joining(PATH_DELIMITER));

        } else if (evalType == RunSyncRuntimeEvalType.WORKDIR) {
            validateParams(RunSyncRuntimeEvalType.WORKDIR.getValue(), parameters, evalType);
            runtimeDataDirPath = cleanupPath(parameters.get(RunSyncRuntimeEvalType.WORKDIR.getValue()));
        } else {
            throw new IllegalArgumentException(String.format("Incorrect data evaluation type '%s' for '%s' " +
                            "system preference. Should be HASH or WORKDIR",
                    evalType, SystemPreferences.LAUNCH_RUN_SYNC_RUNTIME_DATA.getKey()));
        }

        final String runtimeDataFilePath = cleanupRuntimeDataStoragePath(
                String.join(PATH_DELIMITER, runtimeDataDirPath, dataExtractor.getDataFilePath(parameters))
        );
        final AbstractDataStorage dataStorage = dataStorageManager.loadByPathOrId(runtimeDataFilePath);
        final String runtimeDataFileStoragePath = cleanupPath(
                runtimeDataFilePath.replace(dataStorage.getPath(), StringUtils.EMPTY)
        );

        log.debug("Constructed file path '{}' in storage {}, to get runtime data for the run {}.",
                runtimeDataFileStoragePath, dataStorage.getId(), runId);

        dataStorageManager.checkDataStorageObjectExists(dataStorage, runtimeDataFileStoragePath, null);
        final DataStorageStreamingContent fileContent = dataStorageManager
                .getStreamingContent(dataStorage.getId(), runtimeDataFileStoragePath, null);

        return dataExtractor.parseData(parameters, fileContent.getContent());
    }

    private static void validateParams(final String parameterKey,
                                       final Map<String, String> parameters,
                                       final RunSyncRuntimeEvalType evalType) {
        Assert.isTrue(parameters.containsKey(parameterKey),
                String.format("Invalid parameters for '%s' preference: " +
                                "in case of '%s' evalType '%s' key should be specified",
                        SystemPreferences.LAUNCH_RUN_SYNC_RUNTIME_DATA.getKey(), evalType, parameterKey));
    }

    private static String cleanupRuntimeDataStoragePath(String runtimeDataFilePath) {
        if (runtimeDataFilePath.matches("\\w+://.*")) {
            runtimeDataFilePath = runtimeDataFilePath.replaceFirst("\\w+://", "");
        }
        return runtimeDataFilePath;
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
