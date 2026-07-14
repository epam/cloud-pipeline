/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.compute.quota;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.FilterFieldVO;
import com.epam.pipeline.dto.compute.quota.ComputeQuotaRule;
import com.epam.pipeline.dto.compute.quota.ComputeQuotaStrategyType;
import com.epam.pipeline.dto.compute.quota.ConditionOperator;
import com.epam.pipeline.dto.compute.quota.RunField;
import com.epam.pipeline.entity.compute.quota.ComputeQuotaRuleEntity;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.mapper.compute.quota.ComputeQuotaRuleMapper;
import com.epam.pipeline.repository.compute.quota.ComputeQuotaRuleRepository;
import com.epam.pipeline.utils.CommonUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComputeQuotaRuleManager {

    private final ComputeQuotaRuleRepository repository;
    private final ComputeQuotaRuleMapper mapper;
    private final MessageHelper messageHelper;

    public List<ComputeQuotaRule> loadAll() {
        return CommonUtils.toList(repository.findAll()).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    public ComputeQuotaRule load(final Long id) {
        final ComputeQuotaRuleEntity entity = repository.findOne(id);
        Assert.notNull(entity,
                messageHelper.getMessage(MessageConstants.ERROR_COMPUTE_QUOTA_RULE_NOT_FOUND, id));
        return mapper.toDto(entity);
    }

    @Transactional
    public ComputeQuotaRule create(final ComputeQuotaRule rule) {
        validate(rule);
        final LocalDateTime now = DateUtils.nowUTC();
        final ComputeQuotaRuleEntity entity = mapper.toEntity(rule);
        entity.setId(null);
        entity.setCreatedDate(now);
        entity.setModifiedDate(now);
        if (Objects.isNull(entity.getStrategyType())) {
            entity.setStrategyType(ComputeQuotaStrategyType.RUN_STATE);
        }
        return mapper.toDto(repository.save(entity));
    }

    @Transactional
    public ComputeQuotaRule update(final Long id, final ComputeQuotaRule rule) {
        final ComputeQuotaRuleEntity existing = repository.findOne(id);
        Assert.notNull(existing,
                messageHelper.getMessage(MessageConstants.ERROR_COMPUTE_QUOTA_RULE_NOT_FOUND, id));
        validate(rule);
        final ComputeQuotaRuleEntity entity = mapper.toEntity(rule);
        entity.setId(id);
        entity.setStrategyType(existing.getStrategyType());
        entity.setCreatedDate(existing.getCreatedDate());
        entity.setModifiedDate(DateUtils.nowUTC());
        return mapper.toDto(repository.save(entity));
    }

    @Transactional
    public void delete(final Long id) {
        Assert.state(repository.exists(id),
                messageHelper.getMessage(MessageConstants.ERROR_COMPUTE_QUOTA_RULE_NOT_FOUND, id));
        repository.delete(id);
    }

    public List<FilterFieldVO> getKeywords() {
        return Arrays.stream(RunField.values())
                .flatMap(f -> f.getDisplayNames().stream()
                        .map(name -> toKeyword(name, f)))
                .collect(Collectors.toList());
    }

    private void validate(final ComputeQuotaRule rule) {
        Assert.isTrue(StringUtils.isNotBlank(rule.getName()),
                messageHelper.getMessage(MessageConstants.ERROR_COMPUTE_QUOTA_RULE_NAME_EMPTY));
        Assert.notNull(rule.getFilterExpression(),
                messageHelper.getMessage(MessageConstants.ERROR_COMPUTE_QUOTA_RULE_FILTER_EMPTY));
        Assert.notNull(rule.getAction(),
                messageHelper.getMessage(MessageConstants.ERROR_COMPUTE_QUOTA_RULE_ACTION_EMPTY));
    }

    private FilterFieldVO toKeyword(final String name, final RunField field) {
        final FilterFieldVO vo = new FilterFieldVO();
        vo.setFieldName(name);
        vo.setSupportedOperands(field.getType().getSupportedOperators().stream()
                .map(ConditionOperator::getSymbol)
                .sorted()
                .collect(Collectors.toList()));
        return vo;
    }
}
