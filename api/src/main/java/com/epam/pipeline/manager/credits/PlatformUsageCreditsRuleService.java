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

package com.epam.pipeline.manager.credits;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.FilterFieldVO;
import com.epam.pipeline.dto.credits.*;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateRuleEntity;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.mapper.credits.PlatformUsageCreditsRuleMapper;
import com.epam.pipeline.repository.credits.PlatformUsageCreditsRuleRepository;
import com.epam.pipeline.utils.CommonUtils;
import com.epam.pipeline.utils.condition.ConditionExpression;
import com.epam.pipeline.utils.condition.ConditionOperator;
import com.epam.pipeline.utils.condition.FieldType;
import com.epam.pipeline.utils.condition.ConditionType;
import com.epam.pipeline.utils.condition.field.PipelineRunField;
import com.epam.pipeline.utils.condition.field.SubjectEntityField;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Business-logic layer for compute quota rules.
 *
 * <p>A quota rule describes a condition evaluated against run attributes (field, operand, value)
 * and an associated action (income or deduction) applied to the owner's quota balance when the
 * condition is met. Rules are evaluated periodically by the monitoring service; this service
 * is responsible only for their lifecycle management.
 *
 * <p><b>Example — penalise idle runs:</b>
 * <pre>{@code
 * {
 *   "name": "Idle Run Penalty",
 *   "filterExpression": { "filterExpressionType": "LOGICAL",
 *                         "field": "run.tag", "operand": "=",
 *                         "value": "IDLE", "duration": 48 },
 *   "excludeExpression": { "filterExpressionType": "LOGICAL",
 *                          "field": "run.spot", "operand": "=", "value": "true" },
 *   "action": { "type": "DEDUCTION", "value": 100, "perIncident": true }
 * }
 * }</pre>
 * Deducts 100 CPU credits each time a non-spot run has carried the {@code IDLE} tag for
 * at least 48 hours.
 *
 * <p><b>Example — reward spot usage:</b>
 * <pre>{@code
 * {
 *   "name": "Spot Usage Reward",
 *   "filterExpression": { "filterExpressionType": "LOGICAL",
 *                         "field": "run.spot", "operand": "=", "value": "true" },
 *   "action": { "type": "INCOME", "value": 200, "perIncident": false }
 * }
 * }</pre>
 * Awards 200 CPU credits once per spot run regardless of how often the rule fires.
 *
 * <p>Before any rule is persisted, the service:
 * <ul>
 *   <li><b>Normalises</b> all field names in the filter/exclude expression tree to lower-case,
 *       so lookups remain case-insensitive at evaluation time.</li>
 *   <li><b>Validates</b> that every leaf node references a known {@link PipelineRunField} display name,
 *       uses an operand supported by that field's {@link FieldType},
 *       and only sets a duration gate on fields that declare {@code supportsDuration = true}
 *       (currently only {@code run.tag}).</li>
 * </ul>
 *
 * <p>{@code strategyType} defaults to {@link PlatformUsageCreditsStrategyType#RUN_STATE} when not
 * supplied by the caller and is preserved on updates so it cannot be changed after creation.
 */
@Service
@RequiredArgsConstructor
public class PlatformUsageCreditsRuleService {

    private final PlatformUsageCreditsRuleRepository repository;
    private final PlatformUsageCreditsRuleMapper mapper;
    private final MessageHelper messageHelper;

    public List<PlatformUsageCreditsUpdateRule> loadAll() {
        return CommonUtils.toList(repository.findAll()).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    public PlatformUsageCreditsUpdateRule load(final Long id) {
        final PlatformUsageCreditsUpdateRuleEntity entity = repository.findOne(id);
        Assert.notNull(entity,
                messageHelper.getMessage(MessageConstants.ERROR_PLATFORM_USAGE_CREDITS_RULE_NOT_FOUND, id));
        return mapper.toDto(entity);
    }

    @Transactional
    public PlatformUsageCreditsUpdateRule create(final PlatformUsageCreditsUpdateRule rule) {
        normalize(rule);
        validate(rule);
        final LocalDateTime now = DateUtils.nowUTC();
        final PlatformUsageCreditsUpdateRuleEntity entity = mapper.toEntity(rule);
        entity.setId(null);
        entity.setCreatedDate(now);
        entity.setModifiedDate(now);
        return mapper.toDto(repository.save(entity));
    }

    @Transactional
    public PlatformUsageCreditsUpdateRule update(final Long id, final PlatformUsageCreditsUpdateRule rule) {
        final PlatformUsageCreditsUpdateRuleEntity existing = repository.findOne(id);
        Assert.notNull(existing,
                messageHelper.getMessage(MessageConstants.ERROR_PLATFORM_USAGE_CREDITS_RULE_NOT_FOUND, id));
        normalize(rule);
        validate(rule);
        final PlatformUsageCreditsUpdateRuleEntity entity = mapper.toEntity(rule);
        entity.setId(id);
        entity.setStrategyType(existing.getStrategyType());
        entity.setCreatedDate(existing.getCreatedDate());
        entity.setModifiedDate(DateUtils.nowUTC());
        return mapper.toDto(repository.save(entity));
    }

    @Transactional
    public void delete(final Long id) {
        Assert.state(repository.exists(id),
                messageHelper.getMessage(MessageConstants.ERROR_PLATFORM_USAGE_CREDITS_RULE_NOT_FOUND, id));
        repository.delete(id);
    }

    public Map<String, List<FilterFieldVO>> getKeywords() {
        return Arrays.stream(PlatformUsageCreditsStrategyType.values())
                .collect(Collectors.toMap(PlatformUsageCreditsStrategyType::name,
                        t -> SubjectEntityField.forSubjectType(t.getEntityClass()).stream()
                                .flatMap(f -> f.getDisplayNames().stream()
                                        .map(name -> toKeyword(name, f)))
                                .collect(Collectors.toList())));
    }

    private void validate(final PlatformUsageCreditsUpdateRule rule) {
        Assert.isTrue(StringUtils.isNotBlank(rule.getName()),
                messageHelper.getMessage(MessageConstants.ERROR_PLATFORM_USAGE_CREDITS_RULE_NAME_EMPTY));
        Assert.notNull(rule.getFilterExpression(),
                messageHelper.getMessage(MessageConstants.ERROR_PLATFORM_USAGE_CREDITS_RULE_FILTER_EMPTY));
        Assert.notNull(rule.getAction(),
                messageHelper.getMessage(MessageConstants.ERROR_PLATFORM_USAGE_CREDITS_RULE_ACTION_EMPTY));
        final Map<String, ? extends SubjectEntityField<? extends AbstractSecuredEntity>> displayNames =
                SubjectEntityField.byDisplayNames(rule.getStrategyType().getEntityClass());
        validateExpression(rule.getFilterExpression(), displayNames);
        validateExpression(rule.getExcludeExpression(), displayNames);
    }

    private void normalize(final PlatformUsageCreditsUpdateRule rule) {
        if (Objects.isNull(rule.getStrategyType())) {
            rule.setStrategyType(PlatformUsageCreditsStrategyType.RUN_STATE);
        }
        normalizeExpression(rule.getFilterExpression());
        normalizeExpression(rule.getExcludeExpression());
    }

    private void normalizeExpression(final ConditionExpression expression) {
        if (Objects.isNull(expression)) {
            return;
        }
        if (ConditionType.LOGICAL.equals(expression.getConditionType())) {
            if (StringUtils.isNotBlank(expression.getField())) {
                expression.setField(expression.getField().toLowerCase());
            }
        } else {
            ListUtils.emptyIfNull(expression.getExpressions()).forEach(this::normalizeExpression);
        }
    }

    private void validateExpression(final ConditionExpression expression,
                                    final Map<String, ? extends SubjectEntityField<? extends AbstractSecuredEntity>>
                                            displayNames) {
        if (Objects.isNull(expression)) {
            return;
        }
        if (ConditionType.LOGICAL.equals(expression.getConditionType())) {
            validateLeafExpression(expression, displayNames);
        } else {
            ListUtils.emptyIfNull(expression.getExpressions())
                    .forEach(e -> validateExpression(e,  displayNames));
        }
    }

    private void validateLeafExpression(final ConditionExpression leaf,
                                        final Map<String, ? extends SubjectEntityField<? extends AbstractSecuredEntity>>
                                                displayNames) {
        final String fieldName = leaf.getField();
        final SubjectEntityField<? extends AbstractSecuredEntity> field =
                Optional.ofNullable(displayNames.get(fieldName))
                        .orElseThrow(() -> new IllegalArgumentException(
                                messageHelper.getMessage(
                                        MessageConstants.ERROR_PLATFORM_USAGE_CREDITS_RULE_UNKNOWN_FIELD, fieldName)));

        final ConditionOperator operator = ConditionOperator.fromSymbol(leaf.getOperand());
        Assert.isTrue(Objects.nonNull(operator) && field.getType().supports(operator),
                messageHelper.getMessage(MessageConstants.ERROR_PLATFORM_USAGE_CREDITS_RULE_UNSUPPORTED_OPERAND,
                        leaf.getOperand(), fieldName));

        Assert.isTrue(Objects.isNull(leaf.getDuration()) || field.isSupportsDuration(),
                messageHelper.getMessage(
                        MessageConstants.ERROR_PLATFORM_USAGE_CREDITS_RULE_DURATION_NOT_SUPPORTED, fieldName));
    }

    private FilterFieldVO toKeyword(final String name,
                                    final SubjectEntityField<? extends AbstractSecuredEntity> field) {
        final FilterFieldVO vo = new FilterFieldVO();
        vo.setFieldName(name);
        vo.setSupportedOperands(field.getType().getSupportedOperators().stream()
                .map(ConditionOperator::getSymbol)
                .sorted()
                .collect(Collectors.toList()));
        return vo;
    }
}
