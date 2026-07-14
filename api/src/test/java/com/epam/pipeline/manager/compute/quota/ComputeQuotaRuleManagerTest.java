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

public class ComputeQuotaRuleManagerTest {

    private final ComputeQuotaRuleRepository repository = mock(ComputeQuotaRuleRepository.class);
    private final ComputeQuotaRuleMapper mapper = mock(ComputeQuotaRuleMapper.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final ComputeQuotaRuleManager manager =
            new ComputeQuotaRuleManager(repository, mapper, messageHelper);

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
}
