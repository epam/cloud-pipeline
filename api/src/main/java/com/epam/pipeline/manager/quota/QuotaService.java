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

import com.epam.pipeline.dto.quota.Quota;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

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

    @Transactional
    public Quota create(final Quota quota) {
        Assert.notNull(quota.getQuotaGroup(), "Quota group cannot be empty");
        Assert.notNull(quota.getValue(), "Quota cannot be empty");
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
        Assert.notNull(loaded, String.format("No quota with ID '%d' found for update", id));
        Assert.notNull(quota.getValue(), "Quota cannot be empty");
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

    private void prepareQuotaAction(final QuotaEntity quotaEntity, final QuotaActionEntity quotaActionEntity) {
        Assert.state(CollectionUtils.isNotEmpty(quotaActionEntity.getActions()),
                "At least one quota action shall be specified");
        if (Objects.isNull(quotaActionEntity.getThreshold())) {
            quotaActionEntity.setThreshold(0);
        }
        quotaActionEntity.setQuota(quotaEntity);
    }

    private void prepareQuotaActions(final QuotaEntity entity) {
        if (QuotaGroup.GLOBAL.equals(entity.getQuotaGroup())) {
            entity.setActions(null);
        }
        ListUtils.emptyIfNull(entity.getActions()).forEach(action -> prepareQuotaAction(entity, action));
    }

    private QuotaPeriod prepareQuotaPeriod(final QuotaEntity entity) {
        if (QuotaGroup.GLOBAL.equals(entity.getQuotaGroup()) && Objects.isNull(entity.getPeriod())) {
            return QuotaPeriod.MONTH;
        }
        return QuotaGroup.GLOBAL.equals(entity.getQuotaGroup()) ? entity.getPeriod() : null;
    }

    private void validateUniqueness(final Quota quota) {
        if (QuotaGroup.GLOBAL.equals(quota.getQuotaGroup())) {
            Assert.isNull(quotaRepository.findByQuotaGroup(QuotaGroup.GLOBAL), "Global quota already exists");
            return;
        }
        Assert.isNull(quotaRepository.findByTypeAndNameAndQuotaGroup(quota.getType(), quota.getName(),
                quota.getQuotaGroup()), String.format("'%s' quota with type '%s' and name '%s' already exists",
                quota.getQuotaGroup().name(), quota.getType().name(), quota.getName()));
    }

    private void validateQuotaName(final Quota quota) {
        if (!QuotaGroup.GLOBAL.equals(quota.getQuotaGroup())) {
            Assert.state(StringUtils.isNotBlank(quota.getName()), "Quota entity name cannot be empty");
            return;
        }
        quota.setName(null);
    }

    private void validateQuotaType(final Quota quota) {
        if (QuotaGroup.GLOBAL.equals(quota.getQuotaGroup())) {
            quota.setType(null);
            return;
        }
        Assert.notNull(quota.getType(), "Quota type cannot be empty");
    }
}
