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
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleNotification;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRule;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecycleRuleEntity;
import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecycleRuleProlongationEntity;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.StorageProviderManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.mapper.datastorage.lifecycle.StorageLifecycleEntityMapper;
import com.epam.pipeline.repository.datastorage.lifecycle.DataStorageLifecycleRuleRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataStorageLifecycleManager {

    private final MessageHelper messageHelper;
    private final StorageLifecycleEntityMapper lifecycleEntityMapper;
    private final DataStorageLifecycleRuleRepository dataStorageLifecycleRuleRepository;

    private final DataStorageManager storageManager;
    private final StorageProviderManager storageProviderManager;

    private final PreferenceManager preferenceManager;


    public List<StorageLifecycleRule> listStorageLifecyclePolicyRules(final Long storageId) {
        return ListUtils.emptyIfNull(dataStorageLifecycleRuleRepository.findByDatastorageId(storageId))
                .stream()
                .map(lifecycleEntityMapper::toDto)
                .collect(Collectors.toList());
    }

    public StorageLifecycleRule loadStorageLifecyclePolicyRule(final Long datastorageId, final Long ruleId) {
        final StorageLifecycleRuleEntity lifecycleRuleEntity = loadLifecycleRuleEntity(ruleId);
        Assert.notNull(lifecycleRuleEntity,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_NOT_FOUND, ruleId));
        Assert.isTrue(lifecycleRuleEntity.getDatastorageId().equals(datastorageId),
                "Lifecycle rule assign to another datastorage with id: " + datastorageId);
        return lifecycleEntityMapper.toDto(lifecycleRuleEntity);
    }

    @Transactional
    public StorageLifecycleRule createStorageLifecyclePolicyRule(final Long datastorageId,
                                                                 final StorageLifecycleRule rule) {
        //TODO verify that there is no such rule yet
        verifyStorageLifecycleRuleObject(datastorageId, rule);
        final StorageLifecycleRuleEntity saved = dataStorageLifecycleRuleRepository
                .save(lifecycleEntityMapper.toEntity(rule));
        return lifecycleEntityMapper.toDto(saved);
    }

    @Transactional
    public StorageLifecycleRule updateStorageLifecyclePolicyRule(final Long datastorageId,
                                                                 final StorageLifecycleRule rule) {
        Assert.notNull(rule.getId(), "Rule id should be provided!");
        verifyStorageLifecycleRuleObject(datastorageId, rule);
        final StorageLifecycleRuleEntity saved = dataStorageLifecycleRuleRepository
                .save(lifecycleEntityMapper.toEntity(rule));
        return lifecycleEntityMapper.toDto(saved);
    }

    @Transactional
    public StorageLifecycleRule prolongLifecyclePolicyRule(final Long datastorageId, final Long ruleId,
                                                           final String path, final Long daysToProlong) {
        final StorageLifecycleRuleEntity lifecycleRuleEntity = loadLifecycleRuleEntity(ruleId);
        Assert.isTrue(lifecycleRuleEntity.getDatastorageId().equals(datastorageId),
                "Lifecycle rule assign to another datastorage with id: " + datastorageId);
        final Long effectiveDaysToProlong = getEffectiveDaysToProlong(daysToProlong, lifecycleRuleEntity);

        Assert.notNull(effectiveDaysToProlong,
                messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_CANNOT_DEFINE_DAYS_TO_PROLONG));
        Assert.isTrue(effectiveDaysToProlong > 0,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_WRONG_DAYS_TO_PROLONG));

        final StorageLifecycleRuleProlongationEntity prolongation =
                ListUtils.emptyIfNull(lifecycleRuleEntity.getProlongations())
                        .stream().filter(p -> p.getPath().equals(path))
                        .findFirst().orElseGet(() -> {
                            final StorageLifecycleRuleProlongationEntity prolongationEntity =
                                    StorageLifecycleRuleProlongationEntity.builder().days(0L).build();
                            lifecycleRuleEntity.setProlongations(Collections.singletonList(prolongationEntity));
                            return prolongationEntity;
                        });

        if (prolongation.getProlongedDate() != null &&
                DateUtils.nowUTC().minus(effectiveDaysToProlong, ChronoUnit.DAYS)
                        .isBefore(prolongation.getProlongedDate())) {
            throw new IllegalStateException(
                    String.format(
                            messageHelper.getMessage(
                                    MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_WAS_PROLONGED_BEFORE,
                                    effectiveDaysToProlong
                            ), daysToProlong));
        }

        prolongation.setDays(prolongation.getDays() + daysToProlong);
        prolongation.setProlongedDate(DateUtils.nowUTC());
        return lifecycleEntityMapper.toDto(
                dataStorageLifecycleRuleRepository.save(lifecycleRuleEntity));
    }

    @Transactional
    public StorageLifecycleRule deleteStorageLifecyclePolicyRule(final Long datastorageId, final Long ruleId) {
        final StorageLifecycleRule loaded = loadStorageLifecyclePolicyRule(datastorageId, ruleId);
        if (loaded != null) {
            dataStorageLifecycleRuleRepository.delete(loaded.getId());
            return loaded;
        } else {
            throw new IllegalArgumentException(
                    messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_NOT_FOUND, ruleId));
        }
    }

    private Long getEffectiveDaysToProlong(final Long daysToProlong, final StorageLifecycleRuleEntity ruleEntity) {
        if (daysToProlong != null) {
            return daysToProlong;
        }

        final String notificationJson = ruleEntity.getNotificationJson();
        if (!StringUtils.isEmpty(notificationJson)) {
            try {
                final StorageLifecycleNotification notification =
                        StorageLifecycleEntityMapper.notificationJsonToDto(notificationJson);
                if (notification.getProlongDays() != null) {
                    return notification.getProlongDays();
                }
            } catch (final IOException e) {
                throw new IllegalStateException("Can't parse notification json from lifecycle rule!", e);
            }
        }
        return preferenceManager.getPreference(SystemPreferences.STORAGE_LIFECYCLE_PROLONG_DAYS);
    }

    private StorageLifecycleRuleEntity loadLifecycleRuleEntity(final Long ruleId) {
        return Optional.ofNullable(dataStorageLifecycleRuleRepository.findOne(ruleId))
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_NOT_FOUND, ruleId)));
    }

    private void verifyStorageLifecycleRuleObject(final Long datastorageId, final StorageLifecycleRule rule) {
        Assert.isTrue(!StringUtils.isEmpty(rule.getPathGlob()),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_ROOT_PATH_NOT_SPECIFIED));
        final AbstractDataStorage dataStorage = storageManager.load(datastorageId);
        Assert.notNull(datastorageId,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_DATASTORAGE_ID_NOT_SPECIFIED));
        Assert.notNull(dataStorage,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NOT_FOUND, datastorageId));

        Assert.notEmpty(rule.getTransitions(),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_TRANSITIONS_NOT_SPECIFIED));
        Assert.notNull(rule.getTransitionMethod(),
                messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_TRANSITION_METHOD_NOT_SPECIFIED));
        verifyNotification(rule.getNotification());
        storageProviderManager.verifyStorageLifecycleRule(dataStorage, rule);
    }

    private void verifyNotification(final StorageLifecycleNotification notification) {
        if (notification == null) {
            return;
        }
        Assert.notNull(notification.getProlongDays(),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_PROLONG_DAYS_NOT_SPECIFIED));
        Assert.isTrue(notification.getProlongDays() > 0,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_WRONG_DAYS_TO_PROLONG));
        Assert.notNull(notification.getNotifyBeforeDays(),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_NOTIFY_BEFORE_DAYS_NOT_SPECIFIED)
        );
        Assert.isTrue(notification.getNotifyBeforeDays() >= 0,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_WRONG_NOTIFY_BEFORE_DAYS));
        Assert.hasLength(notification.getSubject(),
                messageHelper.getMessage(MessageConstants.ERROR_NOTIFICATION_SUBJECT_NOT_SPECIFIED));
        Assert.hasLength(notification.getBody(),
                messageHelper.getMessage(MessageConstants.ERROR_NOTIFICATION_BODY_NOT_SPECIFIED));
    }
}
