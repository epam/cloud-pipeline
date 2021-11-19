/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.data.storage.UpdateDataStorageItemActionTypes;
import com.epam.pipeline.controller.vo.data.storage.UpdateDataStorageItemVO;
import com.epam.pipeline.entity.datastorage.*;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RunMountService {

    public static final String DEFAULT_PATTERN = "${RUN_ID}";
    private final PreferenceManager preferenceManager;
    private final DataStorageManager storageManager;
    private final MessageHelper messageHelper;
    private final FileShareMountManager fileShareMountManager;
    private final PipelineRunManager pipelineRunManager;

    public StorageMountPath getSharedFSSPathForRun(final Long runId, final boolean createFolder) {
        final AbstractDataStorage storage = Optional.ofNullable(
                preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_RUN_SHARED_STORAGE_NAME))
                .map(storageManager::loadByPathOrId)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_SHARED_STORAGE_IS_NOT_CONFIGURED)));
        final FileShareMount mount = Optional.ofNullable(storage.getFileShareMountId())
                .map(fileShareMountManager::load)
                .orElse(null);
        final String runPath = buildMountPath(runId);
        if (createFolder) {
            storageManager.updateDataStorageItems(storage.getId(),
                    Collections.singletonList(buildUpdateRequest(runPath)));
        }
        return new StorageMountPath(ProviderUtils.mergePaths(storage.getPath(), runPath), storage, mount);
    }

    private UpdateDataStorageItemVO buildUpdateRequest(final String path) {
        final UpdateDataStorageItemVO item = new UpdateDataStorageItemVO();
        item.setAction(UpdateDataStorageItemActionTypes.Create);
        item.setPath(path);
        item.setType(DataStorageItemType.Folder);
        return item;
    }

    private String buildMountPath(final Long runId) {
        final String folderPattern = Optional.ofNullable(
                preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_RUN_SHARED_FOLDER_PATTERN))
                .orElse(DEFAULT_PATTERN);
        final PipelineRun pipelineRun = pipelineRunManager.loadPipelineRun(runId);
        final List<PipelineRunParameter> resolved = pipelineRunManager.replaceParametersWithEnvVars(
                Collections.singletonList(new PipelineRunParameter("path", folderPattern)), pipelineRun.getEnvVars());
        return resolved.get(0).getResolvedValue();
    }
}
