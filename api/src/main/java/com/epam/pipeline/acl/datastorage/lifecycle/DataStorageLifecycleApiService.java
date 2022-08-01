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
import com.epam.pipeline.manager.datastorage.lifecycle.DataStorageLifecycleManager;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
public class DataStorageLifecycleApiService {

    private final DataStorageLifecycleManager storageLifecycleManager;

    @PreAuthorize(AclExpressions.STORAGE_ID_READ)
    public List<StorageLifecycleRule> listStorageLifecyclePolicyRules(final Long id) {
        return storageLifecycleManager.listStorageLifecyclePolicyRules(id);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_READ)
    public StorageLifecycleRule loadStorageLifecyclePolicyRule(final Long id, final Long ruleId) {
        return storageLifecycleManager.loadStorageLifecyclePolicyRule(id, ruleId);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_WRITE)
    public StorageLifecycleRule createStorageLifecyclePolicyRule(final Long id,
                                                                 final StorageLifecycleRule rule) {
        return storageLifecycleManager.createStorageLifecyclePolicyRule(id, rule);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_WRITE)
    public StorageLifecycleRule updateStorageLifecyclePolicyRule(final Long id,
                                                                 final StorageLifecycleRule rule) {
        return storageLifecycleManager.updateStorageLifecyclePolicyRule(id, rule);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_WRITE)
    public StorageLifecycleRule prolongStorageLifecyclePolicyRule(final Long id,
                                                                  final Long ruleId, final String path,
                                                                  final Long daysToProlong) {
        return storageLifecycleManager.prolongLifecyclePolicyRule(id, ruleId, path, daysToProlong);
    }
    @PreAuthorize(AclExpressions.STORAGE_ID_WRITE)
    public StorageLifecycleRule deleteStorageLifecyclePolicyRule(final Long id, final Long ruleId) {
        return storageLifecycleManager.deleteStorageLifecyclePolicyRule(id, ruleId);
    }
}
