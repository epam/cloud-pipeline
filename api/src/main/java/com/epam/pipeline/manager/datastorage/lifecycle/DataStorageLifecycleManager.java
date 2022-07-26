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
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleNotification;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRuleTemplate;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRule;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRuleTransition;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRuleTransition.StorageLifecycleRuleTransitionBuilder;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecycleRuleTemplateEntity;
import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecycleRuleEntity;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.StorageProviderManager;
import com.epam.pipeline.mapper.datastorage.lifecycle.StorageLifecycleEntityMapper;
import com.epam.pipeline.repository.datastorage.lifecycle.DataStorageLifecycleRuleTemplateRepository;
import com.epam.pipeline.repository.datastorage.lifecycle.DataStorageLifecycleRuleRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
// TODO verefication for passed objects
public class DataStorageLifecycleManager {

    private final MessageHelper messageHelper;
    private final StorageLifecycleEntityMapper lifecycleEntityMapper;
    private final DataStorageLifecycleRuleTemplateRepository dataStorageLifecycleRuleTemplateRepository;
    private final DataStorageLifecycleRuleRepository dataStorageLifecycleRuleRepository;

    private final DataStorageManager storageManager;
    private final StorageProviderManager storageProviderManager;


    public List<StorageLifecycleRuleTemplate> listStorageLifecycleRuleTemplates(final Long storageId) {
        return ListUtils.emptyIfNull(dataStorageLifecycleRuleTemplateRepository.findByDatastorageId(storageId))
                .stream()
                .map(lifecycleEntityMapper::toDto)
                .collect(Collectors.toList());
    }

    public StorageLifecycleRuleTemplate loadStorageLifecycleRuleTemplate(final Long templateId) {
        return Optional.ofNullable(dataStorageLifecycleRuleTemplateRepository.findOne(templateId))
                .map(lifecycleEntityMapper::toDto)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Lifecycle policy with id '%d' cannot be found!", templateId)));
    }

    @Transactional
    public StorageLifecycleRuleTemplate createOrUpdateStorageLifecycleRuleTemplate(
            final StorageLifecycleRuleTemplate ruleTemplate) {
        verifyStorageLifecycleRuleTemplateObject(ruleTemplate);
        final StorageLifecycleRuleTemplateEntity saved = dataStorageLifecycleRuleTemplateRepository
                .save(lifecycleEntityMapper.toEntity(ruleTemplate));
        return lifecycleEntityMapper.toDto(saved);
    }

    @Transactional
    public StorageLifecycleRuleTemplate deleteStorageLifecycleRuleTemplate(final Long templateId) {
        final StorageLifecycleRuleTemplate loaded = loadStorageLifecycleRuleTemplate(templateId);

        if (loaded != null) {
            final List<StorageLifecycleRule> dependentRules = listStorageLifecyclePolicyRulesByTemplate(loaded.getId());
            if (!ListUtils.emptyIfNull(dependentRules).isEmpty()) {
                throw new IllegalStateException(
                        "There are lifecycle rules created from this template, before deletion of the template," +
                                " please delete rules first.");
            }
            dataStorageLifecycleRuleTemplateRepository.delete(loaded.getId());
            return loaded;
        } else {
            throw new IllegalArgumentException(
                    String.format("Can't load storage lifecycle policy by id: %s", templateId));
        }
    }

    public List<StorageLifecycleRule> listStorageLifecyclePolicyRules(final Long storageId) {
        return ListUtils.emptyIfNull(dataStorageLifecycleRuleRepository.findByDatastorageId(storageId))
                .stream()
                .map(lifecycleEntityMapper::toDto)
                .collect(Collectors.toList());
    }

    public List<StorageLifecycleRule> listStorageLifecyclePolicyRulesByTemplate(final Long templateId) {
        return ListUtils.emptyIfNull(dataStorageLifecycleRuleRepository.findByTemplateId(templateId))
                .stream()
                .map(lifecycleEntityMapper::toDto)
                .collect(Collectors.toList());
    }

    public StorageLifecycleRule loadStorageLifecyclePolicyRule(final Long ruleId) {
        return Optional.ofNullable(dataStorageLifecycleRuleRepository.findOne(ruleId))
                .map(lifecycleEntityMapper::toDto)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Lifecycle policy rule with id '%d' cannot be found!", ruleId)));
    }

    @Transactional
    public StorageLifecycleRule createStorageLifecyclePolicyRule(final StorageLifecycleRule rule) {
        final StorageLifecycleRule constructed = checkAndRebuildStorageLifecycleRuleObject(rule);
        final StorageLifecycleRuleEntity saved = dataStorageLifecycleRuleRepository
                .save(lifecycleEntityMapper.toEntity(constructed));
        return lifecycleEntityMapper.toDto(saved);
    }

    @Transactional
    public StorageLifecycleRule prolongStorageLifecyclePolicyRule(final Long ruleId, final Long daysToProlong) {
        final StorageLifecycleRule loaded = loadStorageLifecyclePolicyRule(ruleId);

        final Long effectiveDaysToProlong = daysToProlong != null
                ? daysToProlong
                : loaded.getNotification().getProlongDays();

        Assert.notNull(effectiveDaysToProlong, "Can't define days to prolong, please explicitly specify this value.");
        Assert.isTrue(effectiveDaysToProlong > 0, "Days to prolong should be > 0.");

        if (loaded.getProlongedDate() != null &&
                DateUtils.nowUTC().minus(effectiveDaysToProlong, ChronoUnit.DAYS).isBefore(loaded.getProlongedDate())) {
            throw new IllegalStateException(
                    String.format("This Rule was prolonged less then %d days. Will not prolonged again.",
                            daysToProlong));
        }

        loaded.setTransitions(loaded.getTransitions().stream().map(t -> {
            final StorageLifecycleRuleTransitionBuilder transitionBuilder = t.toBuilder();
            if (t.getTransitionDate() != null) {
                transitionBuilder.transitionDate(t.getTransitionDate().plus(effectiveDaysToProlong, ChronoUnit.DAYS));
            }
            if (t.getTransitionAfterDays() != null) {
                transitionBuilder.transitionAfterDays(t.getTransitionAfterDays() + effectiveDaysToProlong);
            }
            return transitionBuilder.build();
        }).collect(Collectors.toList()));
        loaded.setProlongedDate(DateUtils.nowUTC());
        return lifecycleEntityMapper.toDto(
                dataStorageLifecycleRuleRepository.save(lifecycleEntityMapper.toEntity(loaded)));
    }

    @Transactional
    public StorageLifecycleRule deleteStorageLifecyclePolicyRule(final Long ruleId) {
        final StorageLifecycleRule loaded = loadStorageLifecyclePolicyRule(ruleId);
        if (loaded != null) {
            dataStorageLifecycleRuleRepository.delete(loaded.getId());
            return loaded;
        } else {
            throw new IllegalArgumentException(
                    String.format("Can't load storage lifecycle policy by id: %s", ruleId));
        }
    }

    private void verifyStorageLifecycleRuleTemplateObject(final StorageLifecycleRuleTemplate ruleTemplate) {
        final Long datastorageId = ruleTemplate.getDatastorageId();
        final AbstractDataStorage dataStorage = storageManager.load(datastorageId);
        Assert.notNull(datastorageId, "datastorageId should be specified!");
        Assert.notNull(dataStorage, String.format("Can't find datastorage with id: '%d'", datastorageId));
        Assert.isTrue(!StringUtils.isEmpty(ruleTemplate.getPathRoot()), "Root path should be provided!");
        Assert.isTrue(!StringUtils.isEmpty(ruleTemplate.getObjectGlob()),
                "Path glob for objects should be provided!");
        Assert.notEmpty(ruleTemplate.getTransitions(), "At least one transition should be specified!");
        verifyNotification(ruleTemplate.getNotification());
        storageProviderManager.verifyStorageLifecycleRuleTemplate(dataStorage, ruleTemplate);
    }

    private StorageLifecycleRule checkAndRebuildStorageLifecycleRuleObject(final StorageLifecycleRule rule) {
        StorageLifecycleRule result = rule;
        Assert.isTrue(!StringUtils.isEmpty(rule.getPathRoot()), "Root path should be provided!");
        final Long datastorageId = rule.getDatastorageId();
        final AbstractDataStorage dataStorage = storageManager.load(datastorageId);
        Assert.notNull(datastorageId, "datastorageId should be specified!");
        Assert.notNull(dataStorage, String.format("Can't find datastorage with id: '%d'", datastorageId));

        if (rule.getTemplateId() != null) {
            final StorageLifecycleRuleTemplate ruleTemplate = loadStorageLifecycleRuleTemplate(rule.getTemplateId());
            result = StorageLifecycleRule.builder()
                    .datastorageId(ruleTemplate.getDatastorageId())
                    .objectGlob(ruleTemplate.getObjectGlob())
                    .pathRoot(rule.getPathRoot())
                    .templateId(ruleTemplate.getId())
                    .notification(ruleTemplate.getNotification())
                    .transitions(ruleTemplate.getTransitions())
                    .build();
        } else {
            Assert.isTrue(!StringUtils.isEmpty(rule.getObjectGlob()),
                    "Path glob for objects should be provided!");
            Assert.notEmpty(rule.getTransitions(), "At least one transition should be specified!");
        }
        verifyNotification(rule.getNotification());
        storageProviderManager.verifyStorageLifecycleRule(dataStorage, result);
        return result;
    }

    private void verifyNotification(final StorageLifecycleNotification notification) {
        if (notification == null) {
            return;
        }
        Assert.notNull(notification.getProlongDays(), "Number of days to prolong rule should be provided.");
        Assert.isTrue(notification.getProlongDays() > 0, "prolongDays should be > 0");
        Assert.notNull(notification.getNotifyBeforeDays(),
                "notifyBeforeDays - Number of days to notify user before action will take place. " +
                        "This value should be provided.");
        Assert.isTrue(notification.getNotifyBeforeDays() >= 0, "notifyBeforeDays should be >= 0");
        Assert.hasLength(notification.getSubject(), "Subject for notification message should be provided.");
        Assert.hasLength(notification.getBody(), "Body for notification message should be provided.");
    }
}
