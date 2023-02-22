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

package com.epam.pipeline.acl.datastorage.lifecycle;

import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRule;
import com.epam.pipeline.dto.datastorage.lifecycle.execution.StorageLifecycleRuleExecution;
import com.epam.pipeline.dto.datastorage.lifecycle.execution.StorageLifecycleRuleExecutionStatus;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreAction;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreActionRequest;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreActionSearchFilter;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestorePath;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestorePathType;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.lifecycle.DataStorageLifecycleManager;
import com.epam.pipeline.manager.datastorage.lifecycle.DataStorageLifecycleRestoreManager;
import com.epam.pipeline.manager.security.storage.StoragePermissionManager;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
public class DataStorageLifecycleApiService {

    private final DataStorageManager storageManager;

    private final DataStorageLifecycleManager storageLifecycleManager;
    private final DataStorageLifecycleRestoreManager restoreManager;
    private final StoragePermissionManager permissionManager;

    @PreAuthorize(AclExpressions.STORAGE_LIFECYCLE_READER)
    public List<StorageLifecycleRule> listStorageLifecyclePolicyRules(final Long id, final String path) {
        return storageLifecycleManager.listStorageLifecyclePolicyRules(id, path);
    }

    @PreAuthorize(AclExpressions.STORAGE_LIFECYCLE_READER)
    public StorageLifecycleRule loadStorageLifecyclePolicyRule(final Long id, final Long ruleId) {
        return storageLifecycleManager.loadStorageLifecyclePolicyRule(id, ruleId);
    }

    @PreAuthorize(AclExpressions.STORAGE_LIFECYCLE_MANAGER)
    public StorageLifecycleRule createStorageLifecyclePolicyRule(final Long id,
                                                                 final StorageLifecycleRule rule) {
        return storageLifecycleManager.createStorageLifecyclePolicyRule(id, rule);
    }

    @PreAuthorize(AclExpressions.STORAGE_LIFECYCLE_MANAGER)
    public StorageLifecycleRule updateStorageLifecyclePolicyRule(final Long id,
                                                                 final StorageLifecycleRule rule) {
        return storageLifecycleManager.updateStorageLifecyclePolicyRule(id, rule);
    }

    @PreAuthorize(AclExpressions.STORAGE_LIFECYCLE_MANAGER)
    public StorageLifecycleRule prolongStorageLifecyclePolicyRule(final Long id,
                                                                  final Long ruleId, final String path,
                                                                  final Long daysToProlong,
                                                                  final Boolean force) {
        return storageLifecycleManager.prolongLifecyclePolicyRule(id, ruleId, path, daysToProlong, force);
    }
    @PreAuthorize(AclExpressions.STORAGE_LIFECYCLE_MANAGER)
    public StorageLifecycleRule deleteStorageLifecyclePolicyRule(final Long id, final Long ruleId) {
        return storageLifecycleManager.deleteStorageLifecyclePolicyRule(id, ruleId);
    }

    @PreAuthorize(AclExpressions.STORAGE_LIFECYCLE_MANAGER)
    public StorageLifecycleRuleExecution createStorageLifecyclePolicyRuleExecution(
            final Long id, final Long ruleId, final StorageLifecycleRuleExecution execution) {
        return storageLifecycleManager.createStorageLifecycleRuleExecution(ruleId, execution);
    }

    @PreAuthorize(AclExpressions.STORAGE_LIFECYCLE_MANAGER)
    public StorageLifecycleRuleExecution updateStorageLifecycleRuleExecutionStatus(
            final Long id, final Long executionId, final StorageLifecycleRuleExecutionStatus status) {
        return storageLifecycleManager.updateStorageLifecycleRuleExecutionStatus(executionId, status);
    }

    @PreAuthorize(AclExpressions.STORAGE_LIFECYCLE_MANAGER)
    public StorageLifecycleRuleExecution deleteStorageLifecycleRuleExecution(
            final Long id, final Long executionId) {
        return storageLifecycleManager.deleteStorageLifecycleRuleExecution(executionId);
    }

    @PreAuthorize(AclExpressions.STORAGE_LIFECYCLE_READER)
    public List<StorageLifecycleRuleExecution> listStorageLifecyclePolicyRuleExecutions(
            final Long id, final Long ruleId, final String path, final StorageLifecycleRuleExecutionStatus status) {
        return storageLifecycleManager.listStorageLifecycleRuleExecutionsForRuleAndPath(ruleId, path, status);
    }

    @PreAuthorize(AclExpressions.STORAGE_LIFECYCLE_MANAGER)
    public List<StorageRestoreAction> initiateStorageRestores(final Long id,
                                                              final StorageRestoreActionRequest request) {
        final AbstractDataStorage storage = storageManager.load(id);
        return restoreManager.initiateStorageRestores(storage, request);
    }

    @PreAuthorize(AclExpressions.STORAGE_LIFECYCLE_MANAGER)
    public StorageRestoreAction updateStorageRestoreAction(final Long id,
                                                           final StorageRestoreAction action) {
        return restoreManager.updateStorageRestoreAction(action);
    }

    @PreAuthorize(AclExpressions.STORAGE_LIFECYCLE_READER)
    public List<StorageRestoreAction> filterRestoreStorageActions(final Long id,
                                                                  final StorageRestoreActionSearchFilter filter) {
        if (filter.getDatastorageId() == null) {
            filter.setDatastorageId(id);
        }
        final AbstractDataStorage storage = storageManager.load(id);
        return restoreManager.filterRestoreStorageActions(storage, filter);
    }

    @PreAuthorize(AclExpressions.STORAGE_LIFECYCLE_READER)
    public StorageRestoreAction loadEffectiveRestoreStorageAction(final Long id, final String path,
                                                                  final StorageRestorePathType pathType) {
        final AbstractDataStorage storage = storageManager.load(id);
        return restoreManager.loadEffectiveRestoreStorageAction(
                storage, StorageRestorePath.builder().type(pathType).path(path).build());
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_READ)
    public List<StorageRestoreAction> loadEffectiveRestoreStorageActionHierarchy(final Long id,
                                                                                 final String path,
                                                                                 final StorageRestorePathType pathType,
                                                                                 final Boolean recursive) {
        final AbstractDataStorage storage = storageManager.load(id);
        final boolean showArchived = permissionManager.storageArchiveReadPermissions(storage);
        return restoreManager.loadEffectiveRestoreStorageActionHierarchy(
                storage, StorageRestorePath.builder().type(pathType).path(path).build(), recursive, showArchived);
    }
}
