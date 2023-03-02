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
import com.epam.pipeline.dto.datastorage.lifecycle.execution.StorageLifecycleRuleExecution;
import com.epam.pipeline.dto.datastorage.lifecycle.execution.StorageLifecycleRuleExecutionStatus;
import com.epam.pipeline.dto.datastorage.lifecycle.transition.StorageLifecycleTransitionCriterion;
import com.epam.pipeline.dto.datastorage.lifecycle.transition.StorageLifecycleTransitionMethod;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecycleRuleEntity;
import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecycleRuleExecutionEntity;
import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecycleRuleProlongationEntity;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.StorageProviderManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.mapper.datastorage.lifecycle.StorageLifecycleEntityMapper;
import com.epam.pipeline.repository.datastorage.lifecycle.DataStorageLifecycleRuleExecutionRepository;
import com.epam.pipeline.repository.datastorage.lifecycle.DataStorageLifecycleRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataStorageLifecycleManager {

    private static final String EMPTY = "";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    public static final String PATH_SEPARATOR = "/";
    private final MessageHelper messageHelper;
    private final StorageLifecycleEntityMapper lifecycleEntityMapper;
    private final DataStorageLifecycleRuleRepository dataStorageLifecycleRuleRepository;
    private final DataStorageLifecycleRuleExecutionRepository dataStorageLifecycleRuleExecutionRepository;
    private final DataStorageManager storageManager;
    private final StorageProviderManager storageProviderManager;
    private final PreferenceManager preferenceManager;
    private final UserManager userManager;

    public List<StorageLifecycleRule> listStorageLifecyclePolicyRules(final Long storageId, final String path) {
        final String formattedPath = StringUtils.hasText(path) ? formatGlobPath(path) : path;
        return StreamSupport.stream(
                        dataStorageLifecycleRuleRepository.findByDatastorageId(storageId).spliterator(),
                        false
                ).map(lifecycleEntityMapper::toDto)
                .filter(rule -> !StringUtils.hasText(formattedPath)
                        || PATH_MATCHER.match(rule.getPathGlob(), formattedPath))
                .collect(Collectors.toList());
    }

    public List<StorageLifecycleRule> listStorageLifecyclePolicyRules(final Long storageId) {
        return listStorageLifecyclePolicyRules(storageId, null);
    }

    @Transactional(readOnly = true)
    public List<Long> listStorageIdsWithLifecycle() {
        return dataStorageLifecycleRuleRepository.findAll()
                .stream().map(StorageLifecycleRuleEntity::getDatastorageId).distinct()
                .collect(Collectors.toList());
    }

    public StorageLifecycleRule loadStorageLifecyclePolicyRule(final Long datastorageId, final Long ruleId) {
        final StorageLifecycleRuleEntity lifecycleRuleEntity = loadLifecycleRuleEntity(ruleId);
        Assert.isTrue(lifecycleRuleEntity.getDatastorageId().equals(datastorageId),
                messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_ASSIGNED_TO_ANOTHER_DATASTORAGE,
                        lifecycleRuleEntity.getDatastorageId()));
        return lifecycleEntityMapper.toDto(lifecycleRuleEntity);
    }

    @Transactional
    public StorageLifecycleRule createStorageLifecyclePolicyRule(final Long datastorageId,
                                                                 final StorageLifecycleRule rule) {
        rule.setDatastorageId(datastorageId);
        verifyStorageLifecycleRuleObject(rule);
        final StorageLifecycleRuleEntity savedEntity = dataStorageLifecycleRuleRepository
                .save(lifecycleEntityMapper.toEntity(rule));
        final StorageLifecycleRule saved = lifecycleEntityMapper.toDto(savedEntity);
        log.info("Storage lifecycle rule was created. Rule: {}", saved.toDescriptionString());
        return saved;
    }

    @Transactional
    public StorageLifecycleRule updateStorageLifecyclePolicyRule(final Long datastorageId,
                                                                 final StorageLifecycleRule rule) {
        Assert.notNull(rule.getId(),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_ID_IS_NOT_SPECIFIED));
        rule.setDatastorageId(datastorageId);
        final StorageLifecycleRuleEntity loadedRuleEntity = loadLifecycleRuleEntity(rule.getId());
        verifyStorageLifecycleRuleObject(rule);
        final StorageLifecycleRuleEntity updatedRuleEntity = lifecycleEntityMapper.toEntity(rule);
        Assert.isTrue(loadedRuleEntity.getDatastorageId().equals(rule.getDatastorageId()),
                messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_ASSIGNED_TO_ANOTHER_DATASTORAGE,
                        loadedRuleEntity.getDatastorageId()));
        final StorageLifecycleRule saved = lifecycleEntityMapper.toDto(
                dataStorageLifecycleRuleRepository.save(mergeRulesEntity(loadedRuleEntity, updatedRuleEntity)));
        log.info("Storage lifecycle rule was updated. Rule: {}", saved.toDescriptionString());
        return saved;
    }

    @Transactional
    public StorageLifecycleRule prolongLifecyclePolicyRule(final Long datastorageId, final Long ruleId,
                                                           final String path, final Long daysToProlong,
                                                           final Boolean force) {
        final LocalDateTime now = DateUtils.nowUTC();
        final StorageLifecycleRuleEntity lifecycleRuleEntity = loadLifecycleRuleEntity(ruleId);
        Assert.isTrue(PATH_MATCHER.match(lifecycleRuleEntity.getPathGlob(), path),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_PATH_NOT_MATCH_GLOB));
        Assert.isTrue(lifecycleRuleEntity.getDatastorageId().equals(datastorageId),
                messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_ASSIGNED_TO_ANOTHER_DATASTORAGE,
                        lifecycleRuleEntity.getDatastorageId()));
        final Long effectiveDaysToProlong = getEffectiveDaysToProlong(daysToProlong, lifecycleRuleEntity);
        final Long effectiveNotifyBeforeDays = getEffectiveNotifyBeforeDays(lifecycleRuleEntity);

        Assert.notNull(effectiveDaysToProlong,
                messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_CANNOT_DEFINE_DAYS_TO_PROLONG));
        Assert.isTrue(effectiveDaysToProlong > 0,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_WRONG_DAYS_TO_PROLONG));

        if (!force) {
            final List<StorageLifecycleRuleExecution> executionsWithNotificationSent =
                    listStorageLifecycleRuleExecutionsForRuleAndPath(
                            ruleId, path, StorageLifecycleRuleExecutionStatus.NOTIFICATION_SENT);
            Assert.notEmpty(
                    executionsWithNotificationSent,
                    messageHelper.getMessage(
                            MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_CANT_BE_PROLONGED, ruleId, path)
            );
        }

        final StorageLifecycleRuleProlongationEntity lastProlongation =
                ListUtils.emptyIfNull(lifecycleRuleEntity.getProlongations())
                        .stream().filter(p -> p.getPath().equals(path))
                        .max(Comparator.comparing(StorageLifecycleRuleProlongationEntity::getProlongedDate))
                        .orElseGet(() -> {
                            final StorageLifecycleRuleProlongationEntity prolongationEntity =
                                    StorageLifecycleRuleProlongationEntity.builder()
                                            .path(path).days(0L).build();
                            lifecycleRuleEntity.getProlongations().add(prolongationEntity);
                            return prolongationEntity;
                        });

        if (lastProlongation.getProlongedDate() != null && !force) {
            final LocalDateTime nextNotificationDate = lastProlongation.getProlongedDate()
                    .plus(Math.max(1, effectiveDaysToProlong - effectiveNotifyBeforeDays), ChronoUnit.DAYS);
            if (nextNotificationDate.isAfter(now)) {
                throw new IllegalStateException(
                        messageHelper.getMessage(
                                MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_WAS_PROLONGED_BEFORE,
                                lastProlongation.getProlongedDate(), nextNotificationDate));
            }
        }

        lastProlongation.setDays(lastProlongation.getDays() + effectiveDaysToProlong);
        lastProlongation.setProlongedDate(now);
        lastProlongation.setLifecycleRule(lifecycleRuleEntity);
        lastProlongation.setUserId(userManager.getCurrentUser().getId());
        final StorageLifecycleRule prolonged = lifecycleEntityMapper.toDto(
                dataStorageLifecycleRuleRepository.save(lifecycleRuleEntity));
        log.info("Storage lifecycle rule was prolonged. Id: {}, datastorageId: {}, path: {}, days: {}",
                prolonged.getId(), prolonged.getDatastorageId(),
                lastProlongation.getPath(), lastProlongation.getDays());
        return prolonged;
    }

    @Transactional
    public StorageLifecycleRule deleteStorageLifecyclePolicyRule(final Long datastorageId, final Long ruleId) {
        final StorageLifecycleRule loaded = loadStorageLifecyclePolicyRule(datastorageId, ruleId);
        if (loaded != null) {
            dataStorageLifecycleRuleExecutionRepository.deleteByRuleId(ruleId);
            dataStorageLifecycleRuleRepository.delete(loaded.getId());
            log.info("Storage lifecycle rule was deleted. Rule: {}", loaded.toDescriptionString());
            return loaded;
        } else {
            throw new IllegalArgumentException(
                    messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_NOT_FOUND, ruleId));
        }
    }

    @Transactional
    public void deleteStorageLifecyclePolicyRules(final Long datastorageId) {
        final List<StorageLifecycleRuleEntity> loaded = StreamSupport.stream(
                dataStorageLifecycleRuleRepository.findByDatastorageId(datastorageId).spliterator(),
                false
        ).collect(Collectors.toList());
        loaded.forEach(rule -> dataStorageLifecycleRuleExecutionRepository.deleteByRuleId(rule.getId()));
        dataStorageLifecycleRuleRepository.delete(loaded);
        loaded.stream().map(lifecycleEntityMapper::toDto)
                .forEach(r -> log.info("Storage lifecycle rule was deleted. Rule: {}", r.toDescriptionString()));
        dataStorageLifecycleRuleRepository.flush();
    }

    @Transactional
    public StorageLifecycleRuleExecution createStorageLifecycleRuleExecution(
            final Long ruleId, final StorageLifecycleRuleExecution execution) {
        final StorageLifecycleRuleEntity ruleEntity = loadLifecycleRuleEntity(ruleId);
        execution.setRuleId(ruleEntity.getId());
        execution.setUpdated(DateUtils.nowUTC());
        verifyLifecycleRuleExecutionObject(execution, ruleEntity);
        final StorageLifecycleRuleExecutionEntity saved =
                dataStorageLifecycleRuleExecutionRepository.save(lifecycleEntityMapper.toEntity(execution));
        log.info("Storage lifecycle rule execution was created. ExecutionId: {}, RuleId: {}, datastorageId: {}, " +
                "Path: '{}', StorageClass: {}, Status: {}", saved.getId(), saved.getRuleId(),
                ruleEntity.getDatastorageId(), saved.getPath(), saved.getStorageClass(), saved.getStatus());
        return lifecycleEntityMapper.toDto(saved);
    }

    @Transactional
    public StorageLifecycleRuleExecution updateStorageLifecycleRuleExecutionStatus(
            final Long executionId, final StorageLifecycleRuleExecutionStatus status) {
        final StorageLifecycleRuleExecutionEntity executionEntity =
                dataStorageLifecycleRuleExecutionRepository.findOne(executionId);
        Assert.notNull(executionEntity,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_EXECUTION_NOT_FOUND,
                        executionId));
        final StorageLifecycleRuleEntity ruleEntity = loadLifecycleRuleEntity(executionEntity.getRuleId());
        executionEntity.setStatus(status);
        executionEntity.setUpdated(DateUtils.nowUTC());
        final StorageLifecycleRuleExecutionEntity saved =
                dataStorageLifecycleRuleExecutionRepository.save(executionEntity);
        log.info("Storage lifecycle rule execution status was updated.  ExecutionId: {}, RuleId: {}, " +
                        "datastorageId: {}, Path: '{}', StorageClass: {}, Status: {}",
                saved.getId(), saved.getRuleId(), ruleEntity.getDatastorageId(),
                saved.getPath(), saved.getStorageClass(), saved.getStatus());
        return lifecycleEntityMapper.toDto(saved);
    }

    @Transactional
    public StorageLifecycleRuleExecution deleteStorageLifecycleRuleExecution(final Long executionId) {
        final StorageLifecycleRuleExecutionEntity execution =
                dataStorageLifecycleRuleExecutionRepository.findOne(executionId);
        dataStorageLifecycleRuleExecutionRepository.delete(execution.getId());
        return lifecycleEntityMapper.toDto(execution);
    }

    public List<StorageLifecycleRuleExecution> listStorageLifecycleRuleExecutionsForRuleAndPath(
            final Long ruleId, final String path, final StorageLifecycleRuleExecutionStatus status) {
        final StorageLifecycleRuleEntity lifecycleRuleEntity = loadLifecycleRuleEntity(ruleId);
        return StreamSupport.stream(
                        dataStorageLifecycleRuleExecutionRepository
                                .findByRuleIdPathAndStatus(lifecycleRuleEntity.getId(), path, status).spliterator(),
                        false
                ).map(lifecycleEntityMapper::toDto)
                .collect(Collectors.toList());
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
                if (notification != null && notification.getProlongDays() != null) {
                    return notification.getProlongDays();
                }
            } catch (final IOException e) {
                throw new IllegalStateException(messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_CANNOT_PARSE_NOTIFICATION), e);
            }
        }
        return preferenceManager.getPreference(SystemPreferences.STORAGE_LIFECYCLE_PROLONG_DAYS);
    }

    private Long getEffectiveNotifyBeforeDays(final StorageLifecycleRuleEntity ruleEntity) {
        final String notificationJson = ruleEntity.getNotificationJson();
        if (!StringUtils.isEmpty(notificationJson)) {
            try {
                final StorageLifecycleNotification notification =
                        StorageLifecycleEntityMapper.notificationJsonToDto(notificationJson);
                if (notification != null && notification.getNotifyBeforeDays() != null) {
                    return notification.getNotifyBeforeDays();
                }
            } catch (final IOException e) {
                throw new IllegalStateException(messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_CANNOT_PARSE_NOTIFICATION), e);
            }
        }
        return preferenceManager.getPreference(SystemPreferences.STORAGE_LIFECYCLE_NOTIFY_BEFORE_DAYS);
    }

    private StorageLifecycleRuleEntity mergeRulesEntity(final StorageLifecycleRuleEntity loadedRuleEntity,
                                                        final StorageLifecycleRuleEntity updatedRuleEntity) {
        loadedRuleEntity.setTransitionMethod(updatedRuleEntity.getTransitionMethod());
        loadedRuleEntity.setTransitionsJson(updatedRuleEntity.getTransitionsJson());
        loadedRuleEntity.setNotificationJson(updatedRuleEntity.getNotificationJson());
        loadedRuleEntity.setTransitionCriterionJson(updatedRuleEntity.getTransitionCriterionJson());
        return loadedRuleEntity;
    }

    private StorageLifecycleRuleEntity loadLifecycleRuleEntity(final Long ruleId) {
        return Optional.ofNullable(dataStorageLifecycleRuleRepository.findOne(ruleId))
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_NOT_FOUND, ruleId)));
    }

    private void verifyStorageLifecycleRuleObject(final StorageLifecycleRule rule) {
        final Long datastorageId = rule.getDatastorageId();
        verifyLifecycleRuleTransitionCriterion(rule);
        Assert.isTrue(
                listStorageLifecyclePolicyRules(datastorageId).stream().noneMatch(existingRule -> {
                    if (existingRule.getId().equals(rule.getId())) {
                        return false;
                    }

                    final String existingRuleObjectGlob = !StringUtils.isEmpty(existingRule.getObjectGlob())
                            ? existingRule.getObjectGlob() : EMPTY;
                    final String newObjectGlob = !StringUtils.isEmpty(rule.getObjectGlob())
                            ? rule.getObjectGlob() : EMPTY;

                    return existingRule.getPathGlob().equals(rule.getPathGlob())
                            && existingRuleObjectGlob.equals(newObjectGlob)
                            && existingRule.getTransitionCriterion().equals(rule.getTransitionCriterion());
                }), messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_ALREADY_EXISTS));

        final boolean ifTransitionMethodIsOneByOneThenCriterionIsDefault =
                !rule.getTransitionMethod().equals(StorageLifecycleTransitionMethod.ONE_BY_ONE)
                        || Optional.ofNullable(rule.getTransitionCriterion())
                        .map(StorageLifecycleTransitionCriterion::getType)
                        .orElse(StorageLifecycleTransitionCriterion.StorageLifecycleTransitionCriterionType.DEFAULT)
                        .equals(StorageLifecycleTransitionCriterion.StorageLifecycleTransitionCriterionType.DEFAULT);
        Assert.isTrue(ifTransitionMethodIsOneByOneThenCriterionIsDefault,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_ONE_BY_ONE_WRONG_CRITERION));
        Assert.isTrue(!StringUtils.isEmpty(rule.getDatastorageId()),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_DATASTORAGE_ID_NOT_SPECIFIED));
        Assert.isTrue(!StringUtils.isEmpty(rule.getPathGlob()),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_ROOT_PATH_NOT_SPECIFIED));
        rule.setPathGlob(formatGlobPath(rule.getPathGlob()));
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
        verifyNotification(rule);
        storageProviderManager.verifyStorageLifecycleRule(dataStorage, rule);
    }

    private void verifyLifecycleRuleTransitionCriterion(final StorageLifecycleRule rule) {
        final StorageLifecycleTransitionCriterion transitionCriterion = rule.getTransitionCriterion();
        if (transitionCriterion == null) {
            rule.setTransitionCriterion(
                    StorageLifecycleTransitionCriterion.builder().type(
                            StorageLifecycleTransitionCriterion.StorageLifecycleTransitionCriterionType.DEFAULT
                    ).build()
            );
            return;
        }
        switch (transitionCriterion.getType()) {
            case MATCHING_FILES:
                Assert.notNull(
                        transitionCriterion.getValue(),
                        messageHelper.getMessage(
                                MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_TRANSITION_CRITERION_VALUE_NOT_PROVIDED));
                break;
            case DEFAULT:
                // clean up value for default criterion
                rule.setTransitionCriterion(
                        rule.getTransitionCriterion().toBuilder().value(null).build()
                );
            default:
                break;
        }
    }

    private void verifyLifecycleRuleExecutionObject(
            final StorageLifecycleRuleExecution execution, final StorageLifecycleRuleEntity ruleEntity) {
        Assert.notNull(execution.getPath(),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_PATH_NOT_PROVIDED));
        Assert.isTrue(PATH_MATCHER.match(ruleEntity.getPathGlob(), execution.getPath()),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_PATH_NOT_MATCH_GLOB));
        final AbstractDataStorage dataStorage = storageManager.load(ruleEntity.getDatastorageId());
        storageProviderManager.verifyStorageLifecycleRuleExecution(dataStorage, execution);
    }

    private void verifyNotification(final StorageLifecycleRule rule) {
        final StorageLifecycleNotification notification = rule.getNotification();

        final boolean ifTransitionMethodIsOneByOneThenNotificationDisabled =
                !rule.getTransitionMethod().equals(StorageLifecycleTransitionMethod.ONE_BY_ONE)
                        || rule.getNotification() != null && !rule.getNotification().getEnabled();
        Assert.isTrue(ifTransitionMethodIsOneByOneThenNotificationDisabled,
                messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_ONE_BY_ONE_NOTIFICATION_ENABLED));

        if (notification == null) {
            return;
        }

        Assert.notNull(notification.getEnabled(),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_ENABLE_FLAG_NOT_PROVIDED));
        if (!notification.getEnabled()) {
            return;
        }

        Assert.isTrue(CollectionUtils.isNotEmpty(notification.getRecipients()) ||
                        Boolean.TRUE.equals(notification.getNotifyUsers()),
                messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_NOTIFICATION_RECIPIENTS_NOT_PROVIDED));
        Assert.isTrue(notification.getProlongDays() == null || notification.getProlongDays() > 0,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_RULE_WRONG_DAYS_TO_PROLONG));
        Assert.isTrue(notification.getNotifyBeforeDays() == null || notification.getNotifyBeforeDays() > 0,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_LIFECYCLE_WRONG_NOTIFY_BEFORE_DAYS));
    }

    private String formatGlobPath(final String path) {
        if (!StringUtils.hasText(path)) {
            return PATH_SEPARATOR;
        }
        final String formattedPath = !path.startsWith(PATH_SEPARATOR) ? PATH_SEPARATOR + path : path;
        if (formattedPath.endsWith(PATH_SEPARATOR) && !formattedPath.equals(PATH_SEPARATOR)) {
            return formattedPath.substring(0, formattedPath.length() - PATH_SEPARATOR.length());
        } else {
            return formattedPath;
        }
    }
}
