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
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RunMountService {

    private final PreferenceManager preferenceManager;
    private final DataStorageManager storageManager;
    private final MessageHelper messageHelper;
    private final FileShareMountManager fileShareMountManager;

    public DataStorageWithShareMount getSharedFSStorage() {
        return Optional.ofNullable(
                preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_SYSTEM_DATA_STORAGE_NAME))
                .map(storageManager::loadByPathOrId)
                .map(storage -> Optional.ofNullable(storage.getFileShareMountId())
                        .map(fileShareId ->
                                new DataStorageWithShareMount(storage, fileShareMountManager.load(fileShareId)))
                        .orElse(new DataStorageWithShareMount(storage, null)))
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_SHARED_STORAGE_IS_NOT_CONFIGURED)));
    }
}
