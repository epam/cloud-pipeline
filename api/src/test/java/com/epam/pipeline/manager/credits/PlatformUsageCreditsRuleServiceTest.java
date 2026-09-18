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

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.FilterFieldVO;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateRuleType;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateRule;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateRuleEntity;
import com.epam.pipeline.mapper.credits.PlatformUsageCreditsRuleMapper;
import com.epam.pipeline.repository.credits.PlatformUsageCreditsRuleRepository;
import com.epam.pipeline.utils.condition.ConditionExpression;
import com.epam.pipeline.utils.condition.ConditionType;
import com.epam.pipeline.utils.condition.field.PipelineRunField;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsRuleCreatorsUtils.ID;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsRuleCreatorsUtils.platformUsageCreditsRule;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsRuleCreatorsUtils.platformUsageCreditsRuleEntity;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class PlatformUsageCreditsRuleServiceTest {

    private static final int DURATION = 24;
    private final PlatformUsageCreditsRuleRepository repository = mock(PlatformUsageCreditsRuleRepository.class);
    private final PlatformUsageCreditsRuleMapper mapper = mock(PlatformUsageCreditsRuleMapper.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final PlatformUsageCreditsRuleService manager =
            new PlatformUsageCreditsRuleService(repository, mapper, messageHelper);

    @Test
    public void shouldLoadAll() {
        final PlatformUsageCreditsUpdateRuleEntity entity = platformUsageCreditsRuleEntity();
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        doReturn(Collections.singletonList(entity)).when(repository).findAll();
        doReturn(rule).when(mapper).toDto(entity);

        final List<PlatformUsageCreditsUpdateRule> result = manager.loadAll();

        assertThat(result.size(), is(1));
        assertThat(result.get(0), is(rule));
    }

    @Test
    public void shouldLoad() {
        final PlatformUsageCreditsUpdateRuleEntity entity = platformUsageCreditsRuleEntity();
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        doReturn(Optional.of(entity)).when(repository).findById(ID);
        doReturn(rule).when(mapper).toDto(entity);

        assertThat(manager.load(ID), is(rule));
    }

    @Test
    public void shouldFailLoadIfNotFound() {
        doReturn(Optional.empty()).when(repository).findById(ID);

        assertThrows(IllegalArgumentException.class, () -> manager.load(ID));
    }

    @Test
    public void shouldCreate() {
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        final PlatformUsageCreditsUpdateRuleEntity entity = platformUsageCreditsRuleEntity();
        doReturn(entity).when(mapper).toEntity(rule);
        doReturn(entity).when(repository).save(any(PlatformUsageCreditsUpdateRuleEntity.class));
        doReturn(rule).when(mapper).toDto(entity);

        final PlatformUsageCreditsUpdateRule result = manager.create(rule);

        final ArgumentCaptor<PlatformUsageCreditsUpdateRuleEntity> captor =
                ArgumentCaptor.forClass(PlatformUsageCreditsUpdateRuleEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId(), nullValue());
        assertThat(captor.getValue().getCreatedDate(), notNullValue());
        assertThat(captor.getValue().getModifiedDate(), notNullValue());
        assertThat(result, is(rule));
    }

    @Test
    public void shouldFailCreateIfNameIsBlank() {
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        rule.setName("");

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    @Test
    public void shouldFailCreateIfFilterExpressionIsNull() {
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        rule.setStatement(null);

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    @Test
    public void shouldFailCreateIfActionIsNull() {
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        rule.setAction(null);

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    @Test
    public void shouldUpdate() {
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        final PlatformUsageCreditsUpdateRuleEntity existing = platformUsageCreditsRuleEntity();
        final PlatformUsageCreditsUpdateRuleEntity entity = platformUsageCreditsRuleEntity();
        doReturn(Optional.of(existing)).when(repository).findById(ID);
        doReturn(entity).when(mapper).toEntity(rule);
        doReturn(entity).when(repository).save(any(PlatformUsageCreditsUpdateRuleEntity.class));
        doReturn(rule).when(mapper).toDto(entity);

        final PlatformUsageCreditsUpdateRule result = manager.update(ID, rule);

        final ArgumentCaptor<PlatformUsageCreditsUpdateRuleEntity> captor =
                ArgumentCaptor.forClass(PlatformUsageCreditsUpdateRuleEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId(), is(ID));
        assertThat(captor.getValue().getRuleType(), is(existing.getRuleType()));
        assertThat(captor.getValue().getCreatedDate(), is(existing.getCreatedDate()));
        assertThat(captor.getValue().getModifiedDate(), notNullValue());
        assertThat(result, is(rule));
    }

    @Test
    public void shouldFailUpdateIfNotFound() {
        doReturn(Optional.empty()).when(repository).findById(ID);

        assertThrows(IllegalArgumentException.class, () -> manager.update(ID, platformUsageCreditsRule()));
    }

    @Test
    public void shouldDelete() {
        doReturn(true).when(repository).existsById(ID);

        manager.delete(ID);

        verify(repository).deleteById(ID);
    }

    @Test
    public void shouldFailDeleteIfNotFound() {
        doReturn(false).when(repository).existsById(ID);

        assertThrows(IllegalStateException.class, () -> manager.delete(ID));
    }

    @Test
    public void shouldGetKeywordsForAllStrategyTypes() {
        final Map<String, List<FilterFieldVO>> keywords = manager.getKeywords();

        assertThat(keywords.containsKey(PlatformUsageCreditsUpdateRuleType.RUN_STATE.name()), is(true));
    }

    @Test
    public void shouldGetKeywordsForAllRunFields() {
        final List<FilterFieldVO> keywords =
                manager.getKeywords().get(PlatformUsageCreditsUpdateRuleType.RUN_STATE.name());

        final List<String> expectedNames = Arrays.stream(PipelineRunField.values())
                .flatMap(f -> f.getDisplayNames().stream())
                .collect(Collectors.toList());
        final List<String> actualNames = keywords.stream()
                .map(FilterFieldVO::getFieldName)
                .collect(Collectors.toList());
        assertThat(actualNames, is(expectedNames));
    }

    @Test
    public void shouldGetKeywordsWithSupportedOperands() {
        final List<FilterFieldVO> keywords =
                manager.getKeywords().get(PlatformUsageCreditsUpdateRuleType.RUN_STATE.name());

        keywords.forEach(kw -> assertThat(
                "Expected non-empty operands for field: " + kw.getFieldName(),
                kw.getSupportedOperands(), notNullValue()));
        keywords.forEach(kw -> assertThat(
                "Expected non-empty operands for field: " + kw.getFieldName(),
                kw.getSupportedOperands().isEmpty(), is(false)));
    }

    // --- expression validation ---

    @Test
    public void shouldFailCreateIfFilterExpressionHasUnknownField() {
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        rule.setStatement(leafExpression("unknown.field", "=", null));

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    @Test
    public void shouldFailCreateIfExcludeExpressionHasUnknownField() {
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        rule.setExclude(leafExpression("unknown.field", "=", null));

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    @Test
    public void shouldFailCreateIfOperandIsUnknownSymbol() {
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        // BOOLEAN field run.spot only supports = and !=; "??" is not a known operator at all
        rule.setStatement(leafExpression("run.spot", "??", null));

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    @Test
    public void shouldFailCreateIfOperandIsNotSupportedForFieldType() {
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        // BOOLEAN field run.spot only supports = and !=; ">" is valid syntax but not for BOOLEAN
        rule.setStatement(leafExpression("run.spot", ">", null));

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    @Test
    public void shouldFailCreateIfTimeWindowIsZero() {
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        rule.setTimeWindow(0);

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    @Test
    public void shouldFailCreateIfTimeWindowIsNegative() {
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        rule.setTimeWindow(-1);

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    @Test
    public void shouldFailUpdateIfTimeWindowIsZero() {
        final PlatformUsageCreditsUpdateRuleEntity existing = platformUsageCreditsRuleEntity();
        doReturn(Optional.of(existing)).when(repository).findById(ID);
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        rule.setTimeWindow(0);

        assertThrows(IllegalArgumentException.class, () -> manager.update(ID, rule));
    }

    @Test
    public void shouldFailCreateIfDurationSetOnNonDurationField() {
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        // run.status is ENUM and does NOT support duration
        rule.setStatement(leafExpression("run.status", "=", DURATION));

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    @Test
    public void shouldCreateWithDurationOnTagField() {
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        // run.tag is the only field that supports duration
        rule.setStatement(leafExpression("run.tag", "=", DURATION));
        final PlatformUsageCreditsUpdateRuleEntity entity = platformUsageCreditsRuleEntity();
        doReturn(entity).when(mapper).toEntity(rule);
        doReturn(entity).when(repository).save(any(PlatformUsageCreditsUpdateRuleEntity.class));
        doReturn(rule).when(mapper).toDto(entity);

        manager.create(rule);

        verify(repository).save(any(PlatformUsageCreditsUpdateRuleEntity.class));
    }

    @Test
    public void shouldValidateLeafNodesInsideAndExpression() {
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        final ConditionExpression unknown = leafExpression("unknown.field", "=", null);
        final ConditionExpression valid = leafExpression("run.tag", "=", null);
        final ConditionExpression and = new ConditionExpression();
        and.setType(ConditionType.AND);
        and.setExpressions(Arrays.asList(valid, unknown));
        rule.setStatement(and);

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    // --- field normalization ---

    @Test
    public void shouldNormalizeFieldToLowerCaseOnCreate() {
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        rule.setStatement(leafExpression("RUN.TAG", "=", DURATION));
        final PlatformUsageCreditsUpdateRuleEntity entity = platformUsageCreditsRuleEntity();
        doReturn(entity).when(mapper).toEntity(rule);
        doReturn(entity).when(repository).save(any(PlatformUsageCreditsUpdateRuleEntity.class));
        doReturn(rule).when(mapper).toDto(entity);

        manager.create(rule);

        assertThat(rule.getStatement().getField(), is("run.tag"));
    }

    @Test
    public void shouldNormalizeExcludeExpressionFieldToLowerCase() {
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        rule.setExclude(leafExpression("Node.Type", "=", null));
        final PlatformUsageCreditsUpdateRuleEntity entity = platformUsageCreditsRuleEntity();
        doReturn(entity).when(mapper).toEntity(rule);
        doReturn(entity).when(repository).save(any(PlatformUsageCreditsUpdateRuleEntity.class));
        doReturn(rule).when(mapper).toDto(entity);

        manager.create(rule);

        assertThat(rule.getExclude().getField(), is("node.type"));
    }

    @Test
    public void shouldNormalizeFieldsInsideAndExpression() {
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        final ConditionExpression leaf1 = leafExpression("RUN.OWNER", "=", null);
        final ConditionExpression leaf2 = leafExpression("RUN.SPOT", "=", null);
        final ConditionExpression and = new ConditionExpression();
        and.setType(ConditionType.AND);
        and.setExpressions(Arrays.asList(leaf1, leaf2));
        rule.setStatement(and);
        final PlatformUsageCreditsUpdateRuleEntity entity = platformUsageCreditsRuleEntity();
        doReturn(entity).when(mapper).toEntity(rule);
        doReturn(entity).when(repository).save(any(PlatformUsageCreditsUpdateRuleEntity.class));
        doReturn(rule).when(mapper).toDto(entity);

        manager.create(rule);

        assertThat(leaf1.getField(), is("run.owner"));
        assertThat(leaf2.getField(), is("run.spot"));
    }

    private static ConditionExpression leafExpression(final String field,
                                                      final String operand,
                                                      final Integer duration) {
        final ConditionExpression expr = new ConditionExpression();
        expr.setType(ConditionType.LOGICAL);
        expr.setField(field);
        expr.setOperand(operand);
        expr.setValue("someValue");
        expr.setDuration(duration);
        return expr;
    }
}
