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
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.leakagepolicy.SensitiveStorageOperation;
import com.epam.pipeline.manager.datastorage.permissions.StoragePathPermissionsService;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.storage.StoragePermissionManager;
import com.epam.pipeline.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
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
    private final StoragePathPermissionsService storagePathPermissionsService;
    private final StoragePermissionManager storagePermissionManager;
    private final AuthManager authManager;

    public TemporaryCredentialsManagerImpl(final List<TemporaryCredentialsGenerator> credentialsGenerators,
                                           final MessageHelper messageHelper,
                                           final DataStorageManager dataStorageManager,
                                           final StoragePathPermissionsService storagePathPermissionsService,
                                           final StoragePermissionManager storagePermissionManager,
                                           final AuthManager authManager) {
        this.credentialsGenerators = CommonUtils.groupByKey(credentialsGenerators,
                TemporaryCredentialsGenerator::getStorageType);
        this.messageHelper = messageHelper;
        this.dataStorageManager = dataStorageManager;
        this.storagePathPermissionsService = storagePathPermissionsService;
        this.storagePermissionManager = storagePermissionManager;
        this.authManager = authManager;
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
        Assert.state(CollectionUtils.isNotEmpty(storages),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGES_NOT_FOUND));
        final TemporaryCredentialsGenerator credentialsGenerator = getCredentialsGenerator(storages);

        final Map<Long, AbstractDataStorage> storagesById = storages.stream()
                .collect(Collectors.toMap(AbstractDataStorage::getId, Function.identity()));
        final List<DataStorageAction> preparedActions = actions.stream()
                .peek(action -> validatePathAction(action, storagesById.get(action.getId())))
                .filter(action -> filterNotAllowedAction(storagesById.get(action.getId()), action))
                .map(action -> prepareAction(action, storagesById)).collect(Collectors.toList());

        return credentialsGenerator.generate(preparedActions, storages);
    }

    private TemporaryCredentialsGenerator getCredentialsGenerator(final List<AbstractDataStorage> storages) {
        final AbstractDataStorage storage = verifyAllTypesAreSameAngGetStorage(storages);
        return Optional.ofNullable(MapUtils.emptyIfNull(credentialsGenerators).get(storage.getType()))
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NOT_SUPPORTED,
                                storage.getName(), storage.getType())));
    }

    private DataStorageAction prepareAction(final DataStorageAction action,
                                            final Map<Long, AbstractDataStorage> storagesById) {
        final AbstractDataStorage loadedDataStorage = storagesById.get(action.getId());
        action.setBucketName(loadedDataStorage.getRoot());
        action.setPath(loadedDataStorage.getPath());
        return action;
    }

    private AbstractDataStorage verifyAllTypesAreSameAngGetStorage(final List<AbstractDataStorage> storages) {
        Assert.state(storages.stream()
                .map(AbstractDataStorage::getType)
                .distinct()
                .count() <= 1, messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGES_TYPES_NOT_SAME));
        return storages.get(0);
    }

    private boolean storageIdNotNull(final DataStorageAction action) {
        if (Objects.isNull(action.getId())) {
            log.debug("Storage ID was not specified for action. This action will be skipped.");
        }
        return Objects.nonNull(action.getId());
    }

    private void validatePathAction(final DataStorageAction action, final AbstractDataStorage storage) {
        Assert.state(DataStorageType.S3.equals(storage.getType()) || StringUtils.isBlank(action.getItemPath()),
                String.format("Item path not supported for %s storage yet.", storage.getType()));
        if (!DataStorageType.S3.equals(storage.getType()) || StringUtils.isBlank(action.getItemPath())) {
            // no path action check need
            return;
        }
        Assert.state(Objects.nonNull(action.getItemType()), "Item type is required for specified item path.");
    }

    private boolean filterNotAllowedAction(final AbstractDataStorage storage, final DataStorageAction action) {
        if (StringUtils.isBlank(action.getItemPath())) {
            return true;
        }
        if (!storage.isPathPermissionsEnabled()) {
            action.setItemPath(null);
            action.setItemType(null);
            return true;
        }
        if (storagePermissionManager.isStorageAdmin()
                || storage.getOwner().equalsIgnoreCase(authManager.getAuthorizedUser())) {
            return true;
        }
        return DataStorageItemType.File.equals(action.getItemType())
                ? isActionAllowedForFilePath(storage.getId(), action)
                : isActionAllowedForFolderPath(storage.getId(), action);
    }

    private boolean isActionAllowedForFilePath(final Long storageId, final DataStorageAction action) {
        return action.isWrite()
                ? storagePathPermissionsService.isWriteAllowedToFile(storageId, action.getItemPath())
                : storagePathPermissionsService.isReadAllowedToFile(storageId, action.getItemPath());
    }

    private boolean isActionAllowedForFolderPath(final Long storageId, final DataStorageAction action) {
        return action.isWrite()
                ? storagePathPermissionsService.isWriteAllowedToFolder(storageId, action.getItemPath())
                : storagePathPermissionsService.isReadAllowedToFolder(storageId, action.getItemPath());
    }
}
