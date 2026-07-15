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

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.FilterFieldVO;
import com.epam.pipeline.dto.compute.quota.ComputeQuotaRule;
import com.epam.pipeline.dto.compute.quota.ComputeQuotaStrategyType;
import com.epam.pipeline.dto.compute.quota.FilterExpressionType;
import com.epam.pipeline.dto.compute.quota.QuotaFilterExpression;
import com.epam.pipeline.dto.compute.quota.RunField;
import com.epam.pipeline.entity.compute.quota.ComputeQuotaRuleEntity;
import com.epam.pipeline.mapper.compute.quota.ComputeQuotaRuleMapper;
import com.epam.pipeline.repository.compute.quota.ComputeQuotaRuleRepository;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.epam.pipeline.test.creator.compute.quota.ComputeQuotaRuleCreatorsUtils.ID;
import static com.epam.pipeline.test.creator.compute.quota.ComputeQuotaRuleCreatorsUtils.computeQuotaRule;
import static com.epam.pipeline.test.creator.compute.quota.ComputeQuotaRuleCreatorsUtils.computeQuotaRuleEntity;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ComputeQuotaRuleServiceTest {

    private final ComputeQuotaRuleRepository repository = mock(ComputeQuotaRuleRepository.class);
    private final ComputeQuotaRuleMapper mapper = mock(ComputeQuotaRuleMapper.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final ComputeQuotaRuleService manager =
            new ComputeQuotaRuleService(repository, mapper, messageHelper);

    @Test
    public void shouldLoadAll() {
        final ComputeQuotaRuleEntity entity = computeQuotaRuleEntity();
        final ComputeQuotaRule rule = computeQuotaRule();
        doReturn(Collections.singletonList(entity)).when(repository).findAll();
        doReturn(rule).when(mapper).toDto(entity);

        final List<ComputeQuotaRule> result = manager.loadAll();

        assertThat(result.size(), is(1));
        assertThat(result.get(0), is(rule));
    }

    @Test
    public void shouldLoad() {
        final ComputeQuotaRuleEntity entity = computeQuotaRuleEntity();
        final ComputeQuotaRule rule = computeQuotaRule();
        doReturn(entity).when(repository).findOne(ID);
        doReturn(rule).when(mapper).toDto(entity);

        assertThat(manager.load(ID), is(rule));
    }

    @Test
    public void shouldFailLoadIfNotFound() {
        doReturn(null).when(repository).findOne(ID);

        assertThrows(IllegalArgumentException.class, () -> manager.load(ID));
    }

    @Test
    public void shouldCreate() {
        final ComputeQuotaRule rule = computeQuotaRule();
        final ComputeQuotaRuleEntity entity = computeQuotaRuleEntity();
        doReturn(entity).when(mapper).toEntity(rule);
        doReturn(entity).when(repository).save(any(ComputeQuotaRuleEntity.class));
        doReturn(rule).when(mapper).toDto(entity);

        final ComputeQuotaRule result = manager.create(rule);

        final ArgumentCaptor<ComputeQuotaRuleEntity> captor =
                ArgumentCaptor.forClass(ComputeQuotaRuleEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId(), nullValue());
        assertThat(captor.getValue().getCreatedDate(), notNullValue());
        assertThat(captor.getValue().getModifiedDate(), notNullValue());
        assertThat(result, is(rule));
    }

    @Test
    public void shouldSetDefaultStrategyTypeOnCreate() {
        final ComputeQuotaRule rule = computeQuotaRule();
        rule.setStrategyType(null);
        final ComputeQuotaRuleEntity entity = computeQuotaRuleEntity();
        entity.setStrategyType(null);
        doReturn(entity).when(mapper).toEntity(rule);
        doReturn(entity).when(repository).save(any(ComputeQuotaRuleEntity.class));
        doReturn(rule).when(mapper).toDto(entity);

        manager.create(rule);

        final ArgumentCaptor<ComputeQuotaRuleEntity> captor =
                ArgumentCaptor.forClass(ComputeQuotaRuleEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStrategyType(), is(ComputeQuotaStrategyType.RUN_STATE));
    }

    @Test
    public void shouldFailCreateIfNameIsBlank() {
        final ComputeQuotaRule rule = computeQuotaRule();
        rule.setName("");

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    @Test
    public void shouldFailCreateIfFilterExpressionIsNull() {
        final ComputeQuotaRule rule = computeQuotaRule();
        rule.setFilterExpression(null);

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    @Test
    public void shouldFailCreateIfActionIsNull() {
        final ComputeQuotaRule rule = computeQuotaRule();
        rule.setAction(null);

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    @Test
    public void shouldUpdate() {
        final ComputeQuotaRule rule = computeQuotaRule();
        final ComputeQuotaRuleEntity existing = computeQuotaRuleEntity();
        final ComputeQuotaRuleEntity entity = computeQuotaRuleEntity();
        doReturn(existing).when(repository).findOne(ID);
        doReturn(entity).when(mapper).toEntity(rule);
        doReturn(entity).when(repository).save(any(ComputeQuotaRuleEntity.class));
        doReturn(rule).when(mapper).toDto(entity);

        final ComputeQuotaRule result = manager.update(ID, rule);

        final ArgumentCaptor<ComputeQuotaRuleEntity> captor =
                ArgumentCaptor.forClass(ComputeQuotaRuleEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId(), is(ID));
        assertThat(captor.getValue().getStrategyType(), is(existing.getStrategyType()));
        assertThat(captor.getValue().getCreatedDate(), is(existing.getCreatedDate()));
        assertThat(captor.getValue().getModifiedDate(), notNullValue());
        assertThat(result, is(rule));
    }

    @Test
    public void shouldFailUpdateIfNotFound() {
        doReturn(null).when(repository).findOne(ID);

        assertThrows(IllegalArgumentException.class, () -> manager.update(ID, computeQuotaRule()));
    }

    @Test
    public void shouldDelete() {
        doReturn(true).when(repository).exists(ID);

        manager.delete(ID);

        verify(repository).delete(ID);
    }

    @Test
    public void shouldFailDeleteIfNotFound() {
        doReturn(false).when(repository).exists(ID);

        assertThrows(IllegalStateException.class, () -> manager.delete(ID));
    }

    @Test
    public void shouldGetKeywordsForAllRunFields() {
        final List<FilterFieldVO> keywords = manager.getKeywords();

        final List<String> expectedNames = Arrays.stream(RunField.values())
                .flatMap(f -> f.getDisplayNames().stream())
                .collect(Collectors.toList());
        final List<String> actualNames = keywords.stream()
                .map(FilterFieldVO::getFieldName)
                .collect(Collectors.toList());
        assertThat(actualNames, is(expectedNames));
    }

    @Test
    public void shouldGetKeywordsWithSupportedOperands() {
        final List<FilterFieldVO> keywords = manager.getKeywords();

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
        final ComputeQuotaRule rule = computeQuotaRule();
        rule.setFilterExpression(leafExpression("unknown.field", "=", null));

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    @Test
    public void shouldFailCreateIfExcludeExpressionHasUnknownField() {
        final ComputeQuotaRule rule = computeQuotaRule();
        rule.setExcludeExpression(leafExpression("unknown.field", "=", null));

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    @Test
    public void shouldFailCreateIfOperandIsUnknownSymbol() {
        final ComputeQuotaRule rule = computeQuotaRule();
        // BOOLEAN field run.spot only supports = and !=; "??" is not a known operator at all
        rule.setFilterExpression(leafExpression("run.spot", "??", null));

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    @Test
    public void shouldFailCreateIfOperandIsNotSupportedForFieldType() {
        final ComputeQuotaRule rule = computeQuotaRule();
        // BOOLEAN field run.spot only supports = and !=; ">" is valid syntax but not for BOOLEAN
        rule.setFilterExpression(leafExpression("run.spot", ">", null));

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    @Test
    public void shouldFailCreateIfDurationSetOnNonDurationField() {
        final ComputeQuotaRule rule = computeQuotaRule();
        // run.status is ENUM and does NOT support duration
        rule.setFilterExpression(leafExpression("run.status", "=", 24));

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    @Test
    public void shouldCreateWithDurationOnTagField() {
        final ComputeQuotaRule rule = computeQuotaRule();
        // run.tag is the only field that supports duration
        rule.setFilterExpression(leafExpression("run.tag", "=", 48));
        final ComputeQuotaRuleEntity entity = computeQuotaRuleEntity();
        doReturn(entity).when(mapper).toEntity(rule);
        doReturn(entity).when(repository).save(any(ComputeQuotaRuleEntity.class));
        doReturn(rule).when(mapper).toDto(entity);

        manager.create(rule);

        verify(repository).save(any(ComputeQuotaRuleEntity.class));
    }

    @Test
    public void shouldValidateLeafNodesInsideAndExpression() {
        final ComputeQuotaRule rule = computeQuotaRule();
        final QuotaFilterExpression unknown = leafExpression("unknown.field", "=", null);
        final QuotaFilterExpression valid = leafExpression("run.tag", "=", null);
        final QuotaFilterExpression and = new QuotaFilterExpression();
        and.setFilterExpressionType(FilterExpressionType.AND);
        and.setExpressions(Arrays.asList(valid, unknown));
        rule.setFilterExpression(and);

        assertThrows(IllegalArgumentException.class, () -> manager.create(rule));
    }

    // --- field normalization ---

    @Test
    public void shouldNormalizeFieldToLowerCaseOnCreate() {
        final ComputeQuotaRule rule = computeQuotaRule();
        rule.setFilterExpression(leafExpression("RUN.TAG", "=", 48));
        final ComputeQuotaRuleEntity entity = computeQuotaRuleEntity();
        doReturn(entity).when(mapper).toEntity(rule);
        doReturn(entity).when(repository).save(any(ComputeQuotaRuleEntity.class));
        doReturn(rule).when(mapper).toDto(entity);

        manager.create(rule);

        assertThat(rule.getFilterExpression().getField(), is("run.tag"));
    }

    @Test
    public void shouldNormalizeExcludeExpressionFieldToLowerCase() {
        final ComputeQuotaRule rule = computeQuotaRule();
        rule.setExcludeExpression(leafExpression("Node.Type", "=", null));
        final ComputeQuotaRuleEntity entity = computeQuotaRuleEntity();
        doReturn(entity).when(mapper).toEntity(rule);
        doReturn(entity).when(repository).save(any(ComputeQuotaRuleEntity.class));
        doReturn(rule).when(mapper).toDto(entity);

        manager.create(rule);

        assertThat(rule.getExcludeExpression().getField(), is("node.type"));
    }

    @Test
    public void shouldNormalizeFieldsInsideAndExpression() {
        final ComputeQuotaRule rule = computeQuotaRule();
        final QuotaFilterExpression leaf1 = leafExpression("RUN.OWNER", "=", null);
        final QuotaFilterExpression leaf2 = leafExpression("RUN.SPOT", "=", null);
        final QuotaFilterExpression and = new QuotaFilterExpression();
        and.setFilterExpressionType(FilterExpressionType.AND);
        and.setExpressions(Arrays.asList(leaf1, leaf2));
        rule.setFilterExpression(and);
        final ComputeQuotaRuleEntity entity = computeQuotaRuleEntity();
        doReturn(entity).when(mapper).toEntity(rule);
        doReturn(entity).when(repository).save(any(ComputeQuotaRuleEntity.class));
        doReturn(rule).when(mapper).toDto(entity);

        manager.create(rule);

        assertThat(leaf1.getField(), is("run.owner"));
        assertThat(leaf2.getField(), is("run.spot"));
    }

    private static QuotaFilterExpression leafExpression(final String field,
                                                        final String operand,
                                                        final Integer duration) {
        final QuotaFilterExpression expr = new QuotaFilterExpression();
        expr.setFilterExpressionType(FilterExpressionType.LOGICAL);
        expr.setField(field);
        expr.setOperand(operand);
        expr.setValue("someValue");
        expr.setDuration(duration);
        return expr;
    }
}
