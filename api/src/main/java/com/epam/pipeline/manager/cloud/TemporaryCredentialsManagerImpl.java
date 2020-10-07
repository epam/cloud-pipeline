/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.leakagepolicy.SensitiveStorageOperation;
import com.epam.pipeline.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
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
        final List<AbstractDataStorage> storages = actions.stream()
                .filter(this::storageIdNotNull)
                .map(DataStorageAction::getId)
                .distinct()
                .map(dataStorageManager::load)
                .collect(Collectors.toList());
        Assert.state(CollectionUtils.isNotEmpty(storages), "No storages were specified");
        final TemporaryCredentialsGenerator credentialsGenerator = getCredentialsGenerator(storages);

        final Map<Long, AbstractDataStorage> storagesById = storages.stream()
                .collect(Collectors.toMap(AbstractDataStorage::getId, Function.identity()));
        actions.forEach(action -> prepareAction(action, storagesById));

        return credentialsGenerator.generate(actions, storages);
    }

    private TemporaryCredentialsGenerator getCredentialsGenerator(final List<AbstractDataStorage> storages) {
        final AbstractDataStorage storage = verifyAllTypesAreSameAngGetStorage(storages);
        return Optional.ofNullable(MapUtils.emptyIfNull(credentialsGenerators).get(storage.getType()))
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NOT_SUPPORTED,
                                storage.getName(), storage.getType())));
    }

    private void prepareAction(final DataStorageAction action, final Map<Long, AbstractDataStorage> storagesById) {
        final AbstractDataStorage loadedDataStorage = storagesById.get(action.getId());
        action.setBucketName(loadedDataStorage.getRoot());
        action.setPath(loadedDataStorage.getPath());
    }

    private AbstractDataStorage verifyAllTypesAreSameAngGetStorage(final List<AbstractDataStorage> storages) {
        Assert.state(storages.stream()
                .map(AbstractDataStorage::getType)
                .distinct()
                .count() <= 1, "Storage types shall be the same");
        return storages.get(0);
    }

    private boolean storageIdNotNull(final DataStorageAction action) {
        if (Objects.isNull(action.getId())) {
            log.debug("Storage ID was not specified for action. This action will be skipped.");
        }
        return Objects.nonNull(action.getId());
    }
}
