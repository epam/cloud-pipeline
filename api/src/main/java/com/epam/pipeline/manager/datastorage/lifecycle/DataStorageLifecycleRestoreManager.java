/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage.lifecycle;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreAction;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreActionNotification;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreActionRequest;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreActionSearchFilter;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestorePath;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestorePathType;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreStatus;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.lifecycle.restore.StorageRestoreActionEntity;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.datastorage.StorageProviderManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.mapper.datastorage.lifecycle.StorageLifecycleEntityMapper;
import com.epam.pipeline.repository.datastorage.lifecycle.DataStorageRestoreActionRepository;
import com.epam.pipeline.utils.StreamUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataStorageLifecycleRestoreManager {

    public static final String EMPTY = "";
    private final MessageHelper messageHelper;
    private final StorageLifecycleEntityMapper lifecycleEntityMapper;
    private final DataStorageRestoreActionRepository dataStoragePathRestoreActionRepository;
    private final StorageProviderManager storageProviderManager;
    private final PreferenceManager preferenceManager;

    private final UserManager userManager;


    @Transactional
    public List<StorageRestoreAction> initiateStorageRestores(final AbstractDataStorage storage,
                                                              final StorageRestoreActionRequest request) {
        Assert.state(!CollectionUtils.isEmpty(request.getPaths()),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RESTORE_PATH_IS_NOT_SPECIFIED));
        request.getPaths().forEach(
            path -> Assert.state(path.getPath() != null && path.getType() != null,
                    messageHelper.getMessage(
                            MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RESTORE_PATH_IS_NOT_SPECIFIED)
            )
        );

        final StorageRestoreActionNotification notification = request.getNotification();
        final boolean notificationFormedCorrectly = notification != null
                && (BooleanUtils.isFalse(notification.getEnabled())
                || CollectionUtils.isNotEmpty(notification.getRecipients()));
        Assert.state(notificationFormedCorrectly,
                messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RESTORE_NOTIFICATION_CONFIGURED_INCORRECTLY));

        final Long effectiveDays = request.getDays() == null
                ? preferenceManager.getPreference(SystemPreferences.STORAGE_LIFECYCLE_DEFAULT_RESTORE_DAYS)
                : request.getDays();

        final boolean force = BooleanUtils.isTrue(request.getForce());
        final String restoreMode = storageProviderManager.verifyOrDefaultRestoreMode(storage, request);
        final boolean restoreVersions = BooleanUtils.isTrue(request.getRestoreVersions());
        final List<StorageRestoreActionEntity> actions = request.getPaths().stream()
                .sorted(Comparator.comparing(StorageRestorePath::getPath))
                .map(path -> {
                    path.setPath(getEffectivePathForRestoreAction(path, storage.getDelimiter()));
                    return buildStoragePathRestoreAction(storage, path, restoreMode, effectiveDays,
                            restoreVersions, force, notification);
                }).collect(Collectors.toList());
        final List<StorageRestoreAction> created = dataStoragePathRestoreActionRepository.save(actions).stream()
                .map(lifecycleEntityMapper::toDto).collect(Collectors.toList());
        created.forEach(a ->
                log.info(String.format(
                        "Storage Lifecycle Restore Action was created. Action: %s", a.toDescriptionString()))
        );
        return created;
    }

    @Transactional
    public StorageRestoreAction updateStorageRestoreAction(final StorageRestoreAction action) {
        final StorageRestoreActionEntity loaded = dataStoragePathRestoreActionRepository.findOne(action.getId());
        Assert.notNull(loaded,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_CANNOT_FIND_RESTORE,
                        action.getId()));
        Assert.state(!loaded.getStatus().isTerminal(),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RESTORE_IN_FINAL_STATUS));
        loaded.setStatus(action.getStatus());
        if (action.getStatus() == StorageRestoreStatus.SUCCEEDED) {
            loaded.setRestoredTill(action.getRestoredTill());
        }
        loaded.setUpdated(DateUtils.nowUTC());
        final StorageRestoreAction updated = lifecycleEntityMapper.toDto(loaded);
        log.info(String.format(
                "Storage Lifecycle Restore Action was updated. Action: %s", updated.toDescriptionString()));
        return updated;
    }


    public List<StorageRestoreAction> filterRestoreStorageActions(final AbstractDataStorage storage,
                                                                  final StorageRestoreActionSearchFilter filter) {
        if (filter.getPath() != null) {
            filter.getPath().setPath(getEffectivePathForRestoreAction(filter.getPath(), storage.getDelimiter()));
        }
        return dataStoragePathRestoreActionRepository.filterBy(filter)
                .stream().map(lifecycleEntityMapper::toDto)
                .sorted(Comparator.comparing(StorageRestoreAction::getStarted).reversed())
                .filter(BooleanUtils.isTrue(filter.getIsLatest())
                        ? StreamUtils.distinctByKeyPredicate(StorageRestoreAction::getPath)
                        : action -> true
                ).collect(Collectors.toList());
    }

    public StorageRestoreAction loadEffectiveRestoreStorageAction(final AbstractDataStorage storage,
                                                                  final StorageRestorePath path) {
        final List<StorageRestoreAction> relatedActions = filterRestoreStorageActions(
                storage,
                StorageRestoreActionSearchFilter.builder()
                        .datastorageId(storage.getId())
                        .path(path)
                        .searchType(StorageRestoreActionSearchFilter.SearchType.SEARCH_PARENT)
                        .statuses(StorageRestoreStatus.ACTIVE_STATUSES)
                        .build()
        );
        return relatedActions.stream()
                .filter(DataStorageLifecycleRestoreManager::isActionStillActive)
                .findFirst().orElse(relatedActions.stream().findFirst().orElse(null));
    }

    public List<StorageRestoreAction> loadEffectiveRestoreStorageActionHierarchy(final AbstractDataStorage storage,
                                                                                 final StorageRestorePath path,
                                                                                 final Boolean recursive,
                                                                                 final boolean showArchived) {
        final StorageRestoreAction root = loadEffectiveRestoreStorageAction(storage, path);
        final boolean rootActionIsActive = root != null && (isActionStillActive(root));
        final List<StorageRestoreAction> rootActions = buildRootActions(root, rootActionIsActive, showArchived);
        if (path.getType() == StorageRestorePathType.FILE) {
            return rootActions;
        }
        final List<StorageRestoreAction> relatedActions = filterRestoreStorageActions(
                storage,
                StorageRestoreActionSearchFilter.builder()
                        .datastorageId(storage.getId())
                        .path(path)
                        .isLatest(true)
                        .statuses(showArchived
                                ? StorageRestoreStatus.ACTIVE_STATUSES
                                : Collections.singletonList(StorageRestoreStatus.SUCCEEDED))
                        .searchType(recursive
                                ? StorageRestoreActionSearchFilter.SearchType.SEARCH_CHILD_RECURSIVELY
                                : StorageRestoreActionSearchFilter.SearchType.SEARCH_CHILD
                        ).build())
                .stream()
                // check if root action override child
                .filter(childAction -> childAction != root &&
                        (!rootActionIsActive || childAction.getStarted().isAfter(root.getStarted())))
                // check if child action still active
                .filter(DataStorageLifecycleRestoreManager::isActionStillActive).collect(Collectors.toList());

        return ListUtils.union(rootActions, relatedActions);
    }

    @Transactional
    public void deleteRestoreActions(final Long datastorageId) {
        dataStoragePathRestoreActionRepository.deleteByDatastorageId(datastorageId);
        dataStoragePathRestoreActionRepository.flush();
    }

    public List<StorageRestoreAction> loadSucceededRestoreActions(final AbstractDataStorage storage,
                                                                  final String path) {
        final List<StorageRestoreAction> restoredFolders = ListUtils.emptyIfNull(
                loadEffectiveRestoreStorageActionHierarchy(storage, StorageRestorePath.builder()
                        .path(path)
                        .type(StorageRestorePathType.FOLDER)
                        .build(), false, false));
        final List<StorageRestoreAction> restoredFiles = ListUtils.emptyIfNull(
                loadEffectiveRestoreStorageActionHierarchy(storage, StorageRestorePath.builder()
                        .path(path)
                        .type(StorageRestorePathType.FILE)
                        .build(), false, false));
        return ListUtils.union(restoredFiles, restoredFolders);
    }

    protected StorageRestoreActionEntity buildStoragePathRestoreAction(
            final AbstractDataStorage storage, final StorageRestorePath path,
            final String restoreMode, final Long days, final boolean restoreVersions, final boolean force,
            final StorageRestoreActionNotification notification) {
        storageProviderManager.verifyRestoreActionSupported(storage);

        final StorageRestoreAction effectiveRestore = filterRestoreStorageActions(
                storage,
                StorageRestoreActionSearchFilter.builder()
                        .datastorageId(storage.getId())
                        .path(path)
                        .searchType(StorageRestoreActionSearchFilter.SearchType.SEARCH_PARENT)
                        .statuses(StorageRestoreStatus.ACTIVE_STATUSES)
                        .build()
        ).stream().findFirst().orElse(null);

        final LocalDateTime nowUTC = DateUtils.nowUTC();
        if (effectiveRestore != null) {
            final StorageRestoreStatus effActionStatus = effectiveRestore.getStatus();
            log.debug(messageHelper.getMessage(MessageConstants.DEBUG_DATASTORAGE_LIFECYCLE_EXISTING_RESTORE,
                    effectiveRestore.getPath(), effActionStatus));
            Assert.state(
                    effActionStatus != StorageRestoreStatus.RUNNING
                            && effActionStatus != StorageRestoreStatus.INITIATED,
                    String.format("Can't create restore action, effective action for path %s is still running!",
                            effectiveRestore.getPath())
            );
            if (!force) {
                Assert.isTrue(!isActionStillActive(effectiveRestore),
                        messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_PATH_ALREADY_RESTORED,
                                storage.getPath(), effectiveRestore.getPath()));
            }
        }

        return StorageRestoreActionEntity.builder()
                .datastorageId(storage.getId())
                .userActor(userManager.getCurrentUser())
                .path(path.getPath())
                .type(path.getType())
                .restoreMode(restoreMode)
                .restoreVersions(restoreVersions)
                .status(StorageRestoreStatus.INITIATED)
                .days(days)
                .started(nowUTC)
                .updated(nowUTC)
                .notificationJson(parseRestoreNotification(notification))
                .build();
    }

    private static boolean isActionStillActive(final StorageRestoreAction action) {
        return action.getStatus() != StorageRestoreStatus.SUCCEEDED
                || action.getRestoredTill().isAfter(DateUtils.nowUTC());
    }

    @SneakyThrows
    private static String parseRestoreNotification(final StorageRestoreActionNotification notification) {
        return StorageLifecycleEntityMapper.restoreNotificationToJson(notification);
    }

    private static String getEffectivePathForRestoreAction(final StorageRestorePath path, final String delimiter) {
        final String result;
        if (StringUtils.isEmpty(path.getPath())) {
            result = delimiter;
        } else if (!path.getPath().startsWith(delimiter)) {
            result = delimiter + path.getPath();
        } else {
            result = path.getPath();
        }
        switch (path.getType()) {
            case FOLDER:
                if (EMPTY.equals(result)) {
                    return result;
                }
                return result.endsWith(delimiter) ? result : result + delimiter;
            case FILE:
            default:
                return result;
        }
    }

    private static List<StorageRestoreAction> buildRootActions(final StorageRestoreAction root,
                                                               final boolean rootActionIsActive,
                                                               final boolean showArchived) {
        return !rootActionIsActive || showArchived && !StorageRestoreStatus.SUCCEEDED.equals(root.getStatus())
                ? Collections.emptyList()
                : Collections.singletonList(root);
    }
}
