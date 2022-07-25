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

package com.epam.pipeline.manager.datastorage.lifecycle;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecyclePolicy;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecyclePolicyRule;
import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecyclePolicyEntity;
import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecyclePolicyRuleEntity;
import com.epam.pipeline.mapper.datastorage.lifecycle.StorageLifecycleEntityMapper;
import com.epam.pipeline.repository.datastorage.lifecycle.DataStorageLifecyclePolicyRepository;
import com.epam.pipeline.repository.datastorage.lifecycle.DataStorageLifecycleRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
// TODO verefication for passed objects
public class DataStorageLifecycleManager {

    private final StorageLifecycleEntityMapper lifecycleEntityMapper;
    private final MessageHelper messageHelper;
    private final DataStorageLifecyclePolicyRepository dataStorageLifecyclePolicyRepository;
    private final DataStorageLifecycleRuleRepository dataStorageLifecycleRuleRepository;


    public List<StorageLifecyclePolicy> listStorageLifecyclePolicies(final Long storageId) {
        return dataStorageLifecyclePolicyRepository.findByDatastorageId(storageId)
                .stream()
                .map(lifecycleEntityMapper::toDto)
                .collect(Collectors.toList());
    }

    public StorageLifecyclePolicy loadStorageLifecyclePolicy(final Long id) {
        return lifecycleEntityMapper.toDto(dataStorageLifecyclePolicyRepository.findOne(id));
    }

    public StorageLifecyclePolicy createOrUpdateStorageLifecyclePolicy(final StorageLifecyclePolicy policy) {
        final StorageLifecyclePolicyEntity saved = dataStorageLifecyclePolicyRepository
                .save(lifecycleEntityMapper.toEntity(policy));
        return lifecycleEntityMapper.toDto(saved);
    }

    public StorageLifecyclePolicy deleteStorageLifecyclePolicy(final Long policyId) {
        final StorageLifecyclePolicy loaded = loadStorageLifecyclePolicy(policyId);
        if (loaded != null) {
            dataStorageLifecyclePolicyRepository.delete(loaded.getId());
            return loaded;
        } else {
            throw new IllegalArgumentException(
                    String.format("Can't load storage lifecycle policy by id: %s", policyId));
        }
    }

    public List<StorageLifecyclePolicyRule> listStorageLifecyclePolicyRules(final Long storageId) {
        return dataStorageLifecycleRuleRepository.findByDatastorageId(storageId)
                .stream()
                .map(lifecycleEntityMapper::toDto)
                .collect(Collectors.toList());
    }

    public StorageLifecyclePolicyRule loadStorageLifecyclePolicyRule(final Long id) {
        return lifecycleEntityMapper.toDto(dataStorageLifecycleRuleRepository.findOne(id));
    }

    public StorageLifecyclePolicyRule createOrUpdateStorageLifecyclePolicyRule(final StorageLifecyclePolicyRule rule) {
        final StorageLifecyclePolicyRuleEntity saved = dataStorageLifecycleRuleRepository
                .save(lifecycleEntityMapper.toEntity(rule));
        return lifecycleEntityMapper.toDto(saved);
    }

    public StorageLifecyclePolicyRule deleteStorageLifecyclePolicyRule(final Long ruleId) {
        final StorageLifecyclePolicyRule loaded = loadStorageLifecyclePolicyRule(ruleId);
        if (loaded != null) {
            dataStorageLifecycleRuleRepository.delete(loaded.getId());
            return loaded;
        } else {
            throw new IllegalArgumentException(
                    String.format("Can't load storage lifecycle policy by id: %s", ruleId));
        }
    }

}
