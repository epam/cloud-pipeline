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
import com.epam.pipeline.dto.quota.Quota;
import com.epam.pipeline.dto.quota.QuotaActionType;
import com.epam.pipeline.dto.quota.QuotaGroup;
import com.epam.pipeline.dto.quota.QuotaPeriod;
import com.epam.pipeline.entity.quota.QuotaActionEntity;
import com.epam.pipeline.entity.quota.QuotaEntity;
import com.epam.pipeline.mapper.quota.QuotaMapper;
import com.epam.pipeline.repository.quota.QuotaActionRepository;
import com.epam.pipeline.repository.quota.QuotaRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class QuotaService {
    private final QuotaRepository quotaRepository;
    private final QuotaActionRepository quotaActionRepository;
    private final QuotaMapper quotaMapper;
    private final MessageHelper messageHelper;

    @Transactional
    public Quota create(final Quota quota) {
        Assert.notNull(quota.getQuotaGroup(), messageHelper.getMessage(MessageConstants.ERROR_QUOTA_GROUP_EMPTY));
        Assert.notNull(quota.getValue(), messageHelper.getMessage(MessageConstants.ERROR_QUOTA_VALUE_EMPTY));
        validateQuotaType(quota);
        validateQuotaName(quota);
        validateUniqueness(quota);

        final QuotaEntity entity = quotaMapper.quotaToEntity(quota);
        entity.setId(null);
        entity.setPeriod(prepareQuotaPeriod(entity));
        prepareQuotaActions(entity);

        return quotaMapper.quotaToDto(quotaRepository.save(entity));
    }

    public Quota get(final Long id) {
        return quotaMapper.quotaToDto(quotaRepository.findOne(id));
    }

    @Transactional
    public Quota update(final Long id, final Quota quota) {
        final QuotaEntity loaded = quotaRepository.findOne(id);
        Assert.notNull(loaded, messageHelper.getMessage(MessageConstants.ERROR_QUOTA_NOT_FOUND_BY_ID, id));
        Assert.notNull(quota.getValue(), messageHelper.getMessage(MessageConstants.ERROR_QUOTA_VALUE_EMPTY));
        validateQuotaName(quota);
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
        Assert.state(quotaRepository.exists(id),
                messageHelper.getMessage(MessageConstants.ERROR_QUOTA_NOT_FOUND_BY_ID, id));
        quotaRepository.delete(id);
    }

    public List<Quota> getAll() {
        return StreamSupport.stream(quotaRepository.findAll().spliterator(), false)
                .map(quotaMapper::quotaToDto)
                .collect(Collectors.toList());
    }

    private void deleteObsoleteActions(final QuotaEntity loaded, final QuotaEntity entity) {
        final List<Long> idsToUpdate = ListUtils.emptyIfNull(entity.getActions()).stream()
                .map(QuotaActionEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        ListUtils.emptyIfNull(loaded.getActions()).stream()
                .map(QuotaActionEntity::getId)
                .filter(actionId -> !idsToUpdate.contains(actionId))
                .forEach(quotaActionRepository::delete);
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
        if (QuotaGroup.GLOBAL.equals(entity.getQuotaGroup())) {
            entity.setActions(null);
        }
        ListUtils.emptyIfNull(entity.getActions()).forEach(action -> prepareQuotaAction(entity, action));
    }

    private QuotaPeriod prepareQuotaPeriod(final QuotaEntity entity) {
        return Objects.isNull(entity.getPeriod()) ? QuotaPeriod.MONTH : entity.getPeriod();
    }

    private void validateUniqueness(final Quota quota) {
        if (QuotaGroup.GLOBAL.equals(quota.getQuotaGroup())) {
            Assert.isNull(quotaRepository.findByQuotaGroup(QuotaGroup.GLOBAL),
                    messageHelper.getMessage(MessageConstants.ERROR_QUOTA_GLOBAL_ALREADY_EXISTS));
            return;
        }
        Assert.isNull(quotaRepository.findByTypeAndSubjectAndQuotaGroup(quota.getType(), quota.getSubject(),
                quota.getQuotaGroup()), messageHelper.getMessage(MessageConstants.ERROR_QUOTA_ALREADY_EXISTS,
                quota.getQuotaGroup().name(), quota.getType().name(), quota.getSubject()));
    }

    private void validateQuotaName(final Quota quota) {
        if (QuotaGroup.GLOBAL.equals(quota.getQuotaGroup())) {
            quota.setSubject(null);
            return;
        }
        Assert.state(StringUtils.isNotBlank(quota.getSubject()),
                messageHelper.getMessage(MessageConstants.ERROR_QUOTA_SUBJECT_EMPTY));
    }

    private void validateQuotaType(final Quota quota) {
        if (QuotaGroup.GLOBAL.equals(quota.getQuotaGroup())) {
            quota.setType(null);
            return;
        }
        Assert.notNull(quota.getType(), messageHelper.getMessage(MessageConstants.ERROR_QUOTA_TYPE_EMPTY));
    }
}
