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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleNotification;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRuleTemplate;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRule;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
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
                        messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_TEMPLATE_NOT_FOUND,
                                templateId)));
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
                throw new IllegalStateException(messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_EXISTING_LIFECYCLE_RULE));
            }
            dataStorageLifecycleRuleTemplateRepository.delete(loaded.getId());
            return loaded;
        } else {
            throw new IllegalArgumentException(
                    String.format( messageHelper.getMessage(
                            MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_TEMPLATE_NOT_FOUND, templateId)));
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
                        messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_NOT_FOUND, ruleId)));
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

        Assert.notNull(effectiveDaysToProlong,
                messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_CANNOT_DEFINE_DAYS_TO_PROLONG));
        Assert.isTrue(effectiveDaysToProlong > 0,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_WRONG_DAYS_TO_PROLONG));

        if (loaded.getProlongedDate() != null &&
                DateUtils.nowUTC().minus(effectiveDaysToProlong, ChronoUnit.DAYS).isBefore(loaded.getProlongedDate())) {
            throw new IllegalStateException(
                    String.format(
                            messageHelper.getMessage(
                                    MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_WAS_PROLONGED_BEFORE,
                                    effectiveDaysToProlong
                            ), daysToProlong));
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
                    messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_NOT_FOUND, ruleId));
        }
    }

    private void verifyStorageLifecycleRuleTemplateObject(final StorageLifecycleRuleTemplate ruleTemplate) {
        final Long datastorageId = ruleTemplate.getDatastorageId();
        final AbstractDataStorage dataStorage = storageManager.load(datastorageId);
        Assert.notNull(datastorageId,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_DATASTORAGE_ID_NOT_SPECIFIED));
        Assert.notNull(dataStorage,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NOT_FOUND, datastorageId));
        Assert.isTrue(!StringUtils.isEmpty(ruleTemplate.getPathRoot()),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_ROOT_PATH_NOT_SPECIFIED));
        Assert.notEmpty(ruleTemplate.getTransitions(),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_TRANSITIONS_NOT_SPECIFIED));
        verifyNotification(ruleTemplate.getNotification());
        storageProviderManager.verifyStorageLifecycleRuleTemplate(dataStorage, ruleTemplate);
    }

    private StorageLifecycleRule checkAndRebuildStorageLifecycleRuleObject(final StorageLifecycleRule rule) {
        StorageLifecycleRule result = rule;
        Assert.isTrue(!StringUtils.isEmpty(rule.getPathRoot()),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_ROOT_PATH_NOT_SPECIFIED));
        final Long datastorageId = rule.getDatastorageId();
        final AbstractDataStorage dataStorage = storageManager.load(datastorageId);
        Assert.notNull(datastorageId,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_DATASTORAGE_ID_NOT_SPECIFIED));
        Assert.notNull(dataStorage,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NOT_FOUND, datastorageId));

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
            Assert.notEmpty(rule.getTransitions(),
                    messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_TRANSITIONS_NOT_SPECIFIED));
        }
        verifyNotification(rule.getNotification());
        storageProviderManager.verifyStorageLifecycleRule(dataStorage, result);
        return result;
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
