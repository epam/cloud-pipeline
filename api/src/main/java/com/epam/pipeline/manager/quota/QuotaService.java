/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.quota;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.quota.ActiveAction;
import com.epam.pipeline.dto.quota.AppliedQuota;
import com.epam.pipeline.dto.quota.Quota;
import com.epam.pipeline.dto.quota.QuotaAction;
import com.epam.pipeline.dto.quota.QuotaActionType;
import com.epam.pipeline.dto.quota.QuotaGroup;
import com.epam.pipeline.dto.quota.QuotaPeriod;
import com.epam.pipeline.dto.quota.QuotaType;
import com.epam.pipeline.entity.quota.AppliedQuotaEntity;
import com.epam.pipeline.entity.quota.QuotaActionEntity;
import com.epam.pipeline.entity.quota.QuotaEntity;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.billing.BillingUtils;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.mapper.quota.QuotaMapper;
import com.epam.pipeline.repository.quota.AppliedQuotaRepository;
import com.epam.pipeline.repository.quota.AppliedQuotaSpecification;
import com.epam.pipeline.repository.quota.QuotaActionRepository;
import com.epam.pipeline.repository.quota.QuotaRepository;
import com.epam.pipeline.utils.CommonUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QuotaService {
    private final QuotaRepository quotaRepository;
    private final QuotaActionRepository quotaActionRepository;
    private final AppliedQuotaRepository appliedQuotaRepository;
    private final MetadataManager metadataManager;
    private final QuotaMapper quotaMapper;
    private final MessageHelper messageHelper;
    private final String billingCenterKey;

    public QuotaService(final QuotaRepository quotaRepository,
                        final QuotaActionRepository quotaActionRepository,
                        final AppliedQuotaRepository appliedQuotaRepository,
                        final MetadataManager metadataManager,
                        final QuotaMapper quotaMapper,
                        final MessageHelper messageHelper,
                        final @Value("${billing.center.key:}") String billingCenterKey) {
        this.quotaRepository = quotaRepository;
        this.quotaActionRepository = quotaActionRepository;
        this.appliedQuotaRepository = appliedQuotaRepository;
        this.metadataManager = metadataManager;
        this.quotaMapper = quotaMapper;
        this.messageHelper = messageHelper;
        this.billingCenterKey = billingCenterKey;
    }

    @Transactional
    public Quota create(final Quota quota) {
        Assert.notNull(quota.getQuotaGroup(), messageHelper.getMessage(MessageConstants.ERROR_QUOTA_GROUP_EMPTY));
        Assert.notNull(quota.getValue(), messageHelper.getMessage(MessageConstants.ERROR_QUOTA_VALUE_EMPTY));
        validateQuotaType(quota);
        validateQuotaName(quota, quota.getType());
        validateUniqueness(quota);

        final QuotaEntity entity = quotaMapper.quotaToEntity(quota);
        entity.setId(null);
        entity.setPeriod(prepareQuotaPeriod(entity));
        prepareQuotaActions(entity);

        return quotaMapper.quotaToDto(quotaRepository.save(entity));
    }

    public Quota get(final Long id) {
        return quotaMapper.quotaToDto(quotaRepository.findById(id).orElse(null));
    }

    @Transactional
    public Quota update(final Long id, final Quota quota) {
        final QuotaEntity loaded = quotaRepository.findById(id).orElse(null);
        Assert.notNull(loaded, messageHelper.getMessage(MessageConstants.ERROR_QUOTA_NOT_FOUND_BY_ID, id));
        Assert.notNull(quota.getValue(), messageHelper.getMessage(MessageConstants.ERROR_QUOTA_VALUE_EMPTY));
        validateQuotaName(quota, loaded.getType());
        final QuotaEntity entity = quotaMapper.quotaToEntity(quota);

        deleteObsoleteActions(loaded, entity);

        entity.setId(id);
        entity.setQuotaGroup(loaded.getQuotaGroup());
        entity.setType(loaded.getType());
        entity.setPeriod(prepareQuotaPeriod(entity));
        prepareQuotaActions(entity);

        return quotaMapper.quotaToDto(quotaRepository.save(entity));
    }

    @Transactional
    public void delete(final Long id) {
        Assert.state(quotaRepository.existsById(id),
                messageHelper.getMessage(MessageConstants.ERROR_QUOTA_NOT_FOUND_BY_ID, id));
        appliedQuotaRepository.deleteAllByAction_Quota_Id(id);
        quotaRepository.deleteById(id);
    }

    @Transactional
    public List<Quota> getAll() {
        return getAll(false);
    }

    @Transactional
    public List<Quota> getAll(final boolean loadActive) {
        final List<Quota> quotas = CommonUtils.toList(quotaRepository.findAll())
                .stream()
                .map(quotaMapper::quotaToDto)
                .collect(Collectors.toList());
        if (loadActive) {
            appendActiveQuotas(quotas);
        }
        return quotas;
    }

    @Transactional
    public AppliedQuota createAppliedQuota(final AppliedQuota appliedQuota) {
        final AppliedQuotaEntity entity = quotaMapper.appliedQuotaToEntity(appliedQuota);
        entity.setModified(DateUtils.nowUTC());
        return quotaMapper.appliedQuotaToDto(appliedQuotaRepository.save(entity));
    }

    @Transactional
    public void deleteAppliedQuota(final Long id) {
        Assert.state(appliedQuotaRepository.existsById(id),
                messageHelper.getMessage(MessageConstants.ERROR_APPLIED_QUOTA_NOT_FOUND_BY_ID, id));
        appliedQuotaRepository.deleteById(id);
    }

    @Transactional
    public void deleteAppliedQuotas(final List<AppliedQuota> quotas) {
        appliedQuotaRepository.deleteAll(ListUtils.emptyIfNull(quotas).stream()
                .map(quotaMapper::appliedQuotaToEntity)
                .collect(Collectors.toList()));
    }

    @Transactional
    public List<AppliedQuota> findActiveQuotaForAction(final Long actionId) {
        return ListUtils.emptyIfNull(appliedQuotaRepository.findAll(
                        AppliedQuotaSpecification.activeQuotas(actionId)))
                .stream()
                .map(quotaMapper::appliedQuotaToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<Quota> loadActiveQuotasForUser(final PipelineUser user) {
        final List<AppliedQuota> activeQuotasForUser = findActiveQuotasForUser(user);
        final Map<Long, List<AppliedQuota>> groupedQuotas = ListUtils.emptyIfNull(activeQuotasForUser)
                .stream()
                .collect(Collectors.groupingBy(active -> active.getQuota().getId()));
        return groupedQuotas.values()
                .stream()
                .map(active -> {
                    final Quota quota = active.get(0).getQuota();
                    quota.setActions(active.stream().map(a -> {
                        final QuotaAction action = a.getAction();
                        action.setActiveAction(buildActiveAction(a));
                        return action;
                    })
                        .collect(Collectors.toList()));
                    return quota;
                }).collect(Collectors.toList());
    }

    @Transactional
    public List<AppliedQuota> findActiveQuotasForUser(final PipelineUser user) {
        return ListUtils.emptyIfNull(appliedQuotaRepository.findAll(
                        AppliedQuotaSpecification.userActiveQuotas(user,
                                BillingUtils.getUserBillingCenter(user, billingCenterKey, metadataManager))))
                .stream()
                .map(quotaMapper::appliedQuotaToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public Optional<AppliedQuota> findActiveActionForUser(final PipelineUser user,
                                                          final QuotaActionType actionType) {
        return findActiveQuotasForUser(user)
                .stream()
                .filter(quota -> quota.getAction().getActions().contains(actionType))
                .findAny();
    }

    @Transactional
    public Optional<AppliedQuota> findActiveActionForUser(final PipelineUser user,
                                                          final QuotaActionType actionType,
                                                          final QuotaGroup group) {
        return findActiveQuotasForUser(user)
                .stream()
                .filter(quota -> group.equals(quota.getQuota().getQuotaGroup()) &&
                        quota.getAction().getActions().contains(actionType))
                .findAny();
    }

    @Transactional
    public void deleteObsoleteQuotas(final LocalDate stamp) {
        appliedQuotaRepository.deleteByToBefore(stamp);
    }

    @Transactional
    public void attachQuotaInfo(Collection<PipelineUser> pipelineUsers) {
        final List<Quota> quotas = getAll(true);
        pipelineUsers.forEach(user -> findActiveQuotas(user, quotas));
    }

    private void deleteObsoleteActions(final QuotaEntity loaded, final QuotaEntity entity) {
        final List<Long> idsToUpdate = ListUtils.emptyIfNull(entity.getActions()).stream()
                .map(QuotaActionEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        ListUtils.emptyIfNull(loaded.getActions()).stream()
                .map(QuotaActionEntity::getId)
                .filter(actionId -> !idsToUpdate.contains(actionId))
                .forEach(this::deleteAction);
    }

    private void deleteAction(final Long actionId) {
        appliedQuotaRepository.deleteAllByAction_Id(actionId);
        quotaActionRepository.deleteById(actionId);
    }

    private void validateQuotaAction(final List<QuotaActionType> allowedActions,
                                     final QuotaActionType action, final QuotaGroup quotaGroup) {
        Assert.state(allowedActions.contains(action),
                messageHelper.getMessage(MessageConstants.ERROR_QUOTA_ACTION_NOT_ALLOWED,
                        action.name(), quotaGroup.name(), allowedActions.stream()
                                .map(QuotaActionType::name)
                                .collect(Collectors.joining(", "))));
    }

    private void prepareQuotaAction(final QuotaEntity quotaEntity, final QuotaActionEntity quotaActionEntity) {
        if (CollectionUtils.isEmpty(quotaActionEntity.getActions())) {
            quotaActionEntity.setActions(Collections.singletonList(QuotaActionType.NOTIFY));
        }
        if (Objects.isNull(quotaActionEntity.getThreshold())) {
            quotaActionEntity.setThreshold(NumberUtils.DOUBLE_ZERO);
        }
        quotaActionEntity.setQuota(quotaEntity);
        final List<QuotaActionType> allowedActions = quotaEntity.getQuotaGroup().getAllowedActions();
        quotaActionEntity.getActions()
                .forEach(action -> validateQuotaAction(allowedActions, action, quotaEntity.getQuotaGroup()));
    }

    private void prepareQuotaActions(final QuotaEntity entity) {
        ListUtils.emptyIfNull(entity.getActions()).forEach(action -> prepareQuotaAction(entity, action));
    }

    private QuotaPeriod prepareQuotaPeriod(final QuotaEntity entity) {
        return Objects.isNull(entity.getPeriod()) ? QuotaPeriod.MONTH : entity.getPeriod();
    }

    private void validateUniqueness(final Quota quota) {
        if (QuotaGroup.GLOBAL.equals(quota.getQuotaGroup())) {
            Assert.isNull(quotaRepository.findByQuotaGroupAndPeriod(QuotaGroup.GLOBAL, quota.getPeriod()),
                    messageHelper.getMessage(MessageConstants.ERROR_QUOTA_GLOBAL_ALREADY_EXISTS, quota.getPeriod()));
            return;
        }
        if (QuotaType.OVERALL.equals(quota.getType())) {
            Assert.isNull(quotaRepository.findByQuotaGroupAndTypeAndPeriod(
                            quota.getQuotaGroup(), QuotaType.OVERALL, quota.getPeriod()),
                    messageHelper.getMessage(MessageConstants.ERROR_QUOTA_OVERALL_ALREADY_EXISTS,
                            quota.getQuotaGroup(), quota.getPeriod()));
            return;
        }
        Assert.isNull(quotaRepository.findByTypeAndSubjectAndQuotaGroupAndPeriod(quota.getType(), quota.getSubject(),
                        quota.getQuotaGroup(), quota.getPeriod()),
                messageHelper.getMessage(MessageConstants.ERROR_QUOTA_ALREADY_EXISTS,
                        quota.getQuotaGroup().name(), quota.getType().name(), quota.getSubject(), quota.getPeriod()));
    }

    private void validateQuotaName(final Quota quota, final QuotaType type) {
        if (QuotaGroup.GLOBAL.equals(quota.getQuotaGroup()) || QuotaType.OVERALL.equals(type)) {
            quota.setSubject(null);
            return;
        }
        Assert.state(StringUtils.isNotBlank(quota.getSubject()),
                messageHelper.getMessage(MessageConstants.ERROR_QUOTA_SUBJECT_EMPTY));
    }

    private void validateQuotaType(final Quota quota) {
        if (QuotaGroup.GLOBAL.equals(quota.getQuotaGroup())) {
            quota.setType(QuotaType.OVERALL);
            return;
        }
        Assert.notNull(quota.getType(), messageHelper.getMessage(MessageConstants.ERROR_QUOTA_TYPE_EMPTY));
    }

    private void appendActiveQuotas(List<Quota> quotas) {
        final Map<Long, AppliedQuotaEntity> active = CommonUtils.toList(appliedQuotaRepository.findAll())
                .stream()
                .collect(Collectors.toMap(q -> q.getAction().getId(), Function.identity(),
                    (q1, q2) -> q1.getTo().isAfter(q2.getTo()) ? q1 : q2));
        quotas.forEach(quota -> ListUtils.emptyIfNull(quota.getActions())
                .forEach(action -> {
                    final AppliedQuotaEntity appliedQuota = active.get(action.getId());
                    if (Objects.nonNull(appliedQuota)) {
                        action.setActiveAction(
                                buildActiveAction(appliedQuota));
                    }
                }));
    }

    private ActiveAction buildActiveAction(final AppliedQuotaEntity appliedQuota) {
        return ActiveAction.builder()
                .expense(appliedQuota.getExpense())
                .from(appliedQuota.getFrom())
                .to(appliedQuota.getTo())
                .build();
    }

    private ActiveAction buildActiveAction(final AppliedQuota appliedQuota) {
        return ActiveAction.builder()
                .expense(appliedQuota.getExpense())
                .from(appliedQuota.getFrom())
                .to(appliedQuota.getTo())
                .build();
    }

    private void findActiveQuotas(final PipelineUser user, final List<Quota> quotas) {
        if (user.isAdmin()) {
            return;
        }
        user.setActiveQuotas(ListUtils.emptyIfNull(quotas)
                .stream()
                .peek(quota -> {
                    final List<QuotaAction> active = ListUtils.emptyIfNull(quota.getActions())
                            .stream()
                            .filter(action -> Objects.nonNull(action.getActiveAction()))
                            .collect(Collectors.toList());
                    quota.setActions(active);
                })
                .filter(quota -> CollectionUtils.isNotEmpty(quota.getActions()) && affectsUser(user, quota))
                .collect(Collectors.toList()));
    }

    private boolean affectsUser(final PipelineUser user, final Quota quota) {
        final QuotaType type = quota.getType();
        if (quota.getQuotaGroup().equals(QuotaGroup.GLOBAL) || type.equals(QuotaType.OVERALL)) {
            return true;
        }
        final String subject = quota.getSubject();
        if (type.equals(QuotaType.USER)) {
            return subject.equalsIgnoreCase(user.getUserName());
        }
        if (type.equals(QuotaType.BILLING_CENTER)) {
            return subject.equalsIgnoreCase(
                    BillingUtils.getUserBillingCenter(user, billingCenterKey, metadataManager));
        }
        if (type.equals(QuotaType.GROUP)) {
            return SetUtils.emptyIfNull(user.getAuthorities()).stream()
                    .anyMatch(subject::equalsIgnoreCase);
        }
        return false;
    }
}