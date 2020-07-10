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

package com.epam.pipeline.manager.cloud;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.leakagepolicy.SensitiveStorageOperation;
import com.epam.pipeline.utils.CommonUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@SuppressWarnings("unchecked")
public class TemporaryCredentialsManagerImpl implements TemporaryCredentialsManager {

    private final Map<DataStorageType, TemporaryCredentialsGenerator> credentialsGenerators;
    private final MessageHelper messageHelper;
    private final DataStorageManager dataStorageManager;

    public TemporaryCredentialsManagerImpl(final List<TemporaryCredentialsGenerator> credentialsGenerators,
                                           final MessageHelper messageHelper,
                                           final DataStorageManager dataStorageManager) {
        this.credentialsGenerators = CommonUtils.groupByKey(credentialsGenerators,
                TemporaryCredentialsGenerator::getStorageType);
        this.messageHelper = messageHelper;
        this.dataStorageManager = dataStorageManager;
    }

    @SensitiveStorageOperation
    @Override
    public TemporaryCredentials generate(final List<DataStorageAction> actions) {
        final AbstractDataStorage dataStorage = ListUtils.emptyIfNull(actions)
                .stream()
                .findFirst()
                .map(action -> dataStorageManager.load(action.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Actions are not provided"));
        final TemporaryCredentialsGenerator credentialsGenerator = getCredentialsGenerator(dataStorage);

        final AbstractCloudRegion region = credentialsGenerator.getRegion(dataStorage);

        actions.forEach(action -> {
            AbstractDataStorage loadedDataStorage =
                    action.getId().equals(dataStorage.getId()) ? dataStorage : dataStorageManager.load(action.getId());
            action.setBucketName(loadedDataStorage.getRoot());
            action.setPath(loadedDataStorage.getPath());
            AbstractCloudRegion loadedRegion = credentialsGenerator.getRegion(loadedDataStorage);
            Assert.isTrue(Objects.equals(region.getId(), loadedRegion.getId()),
                    "Actions shall be requested for buckets from the same region");
        });

        return credentialsGenerator.generate(actions, dataStorage);
    }

    private TemporaryCredentialsGenerator getCredentialsGenerator(final AbstractDataStorage dataStorage) {
        return Optional.ofNullable(MapUtils.emptyIfNull(credentialsGenerators).get(dataStorage.getType()))
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NOT_SUPPORTED,
                                dataStorage.getName(), dataStorage.getType())));
    }
}
