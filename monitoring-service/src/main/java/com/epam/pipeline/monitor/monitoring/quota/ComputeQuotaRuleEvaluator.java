/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.monitor.monitoring.quota;

import com.epam.pipeline.entity.filter.FilterExpressionType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.quota.ComputeQuotaRule;
import com.epam.pipeline.entity.quota.QuotaFilterExpression;
import com.epam.pipeline.entity.quota.RunField;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates a {@link ComputeQuotaRule} against a {@link PipelineRun} in memory by walking
 * the filter and exclude expression trees and delegating each LOGICAL leaf to the appropriate
 * {@link LeafEvaluationStrategy}.
 *
 * <p>Strategies are registered at construction time in a field-name → strategy map, so adding
 * support for a new field requires only a new {@link LeafEvaluationStrategy} implementation
 * and a registration entry — no changes here.
 *
 * <p>A run matches a rule when:
 * <ol>
 *   <li>its {@code filterExpression} evaluates to {@code true}, AND</li>
 *   <li>its {@code excludeExpression} is {@code null} or evaluates to {@code false}.</li>
 * </ol>
 */
@Slf4j
@Component
public class ComputeQuotaRuleEvaluator {

    private final Map<String, LeafEvaluationStrategy> leafStrategies;

    public ComputeQuotaRuleEvaluator(final UserGroupHolder userGroupHolder) {
        this.leafStrategies = buildRegistry(userGroupHolder);
    }

    /** Evaluates the rule against the run using the current UTC time for duration checks. */
    public boolean matches(final ComputeQuotaRule rule, final PipelineRun run) {
        return matches(rule, run, LocalDateTime.now(ZoneOffset.UTC));
    }

    public boolean matches(final ComputeQuotaRule rule, final PipelineRun run, final LocalDateTime now) {
        if (!evaluate(rule.getFilterExpression(), run, now)) {
            return false;
        }
        return Objects.isNull(rule.getExcludeExpression()) || !evaluate(rule.getExcludeExpression(), run, now);
    }

    private boolean evaluate(final QuotaFilterExpression node, final PipelineRun run, final LocalDateTime now) {
        if (Objects.isNull(node)) {
            return false;
        }
        try {
            return dispatch(node, run, now);
        } catch (IllegalArgumentException e) {
            log.warn("Skipping expression node for run {}: {}", run.getId(), e.getMessage());
            return false;
        }
    }

    private boolean dispatch(final QuotaFilterExpression node, final PipelineRun run, final LocalDateTime now) {
        final FilterExpressionType type = node.getFilterExpressionType();
        Assert.notNull(type, "filterExpressionType must not be null");
        switch (type) {
            case AND:     return children(node).stream().allMatch(child -> evaluate(child, run, now));
            case OR:      return children(node).stream().anyMatch(child -> evaluate(child, run, now));
            case LOGICAL: return evaluateLeaf(node, run, now);
            default: throw new IllegalArgumentException("Unknown filterExpressionType: '" + type + "'");
        }
    }

    private boolean evaluateLeaf(final QuotaFilterExpression node, final PipelineRun run, final LocalDateTime now) {
        final String fieldName = node.getField();
        final LeafEvaluationStrategy strategy = leafStrategies.get(fieldName);
        Assert.notNull(strategy, "Unknown quota rule field: '" + fieldName + "'");
        return strategy.evaluate(node, run, now);
    }

    private List<QuotaFilterExpression> children(final QuotaFilterExpression node) {
        final List<QuotaFilterExpression> exprs = node.getExpressions();
        return exprs != null ? exprs : Collections.emptyList();
    }

    private static Map<String, LeafEvaluationStrategy> buildRegistry(final UserGroupHolder userGroupHolder) {
        final Map<String, LeafEvaluationStrategy> map = new HashMap<>();
        for (final RunField field : RunField.values()) {
            final LeafEvaluationStrategy strategy = resolveStrategy(field, userGroupHolder);
            for (final String name : field.getDisplayNames()) {
                map.put(name, strategy);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static LeafEvaluationStrategy resolveStrategy(final RunField field,
                                                          final UserGroupHolder userGroupHolder) {
        switch (field.getType()) {
            case STRING:  return new StringLeafEvaluationStrategy(field);
            case NUMERIC: return new NumericLeafEvaluationStrategy(field);
            case BOOLEAN: return new BooleanLeafEvaluationStrategy(field);
            case ENUM:    return new EnumLeafEvaluationStrategy(field);
            case TAGS:    return new TagLeafEvaluationStrategy(field);
            case GROUPS:  return new UserGroupLeafEvaluationStrategy(userGroupHolder);
            default: throw new IllegalArgumentException(
                    "No leaf evaluation strategy registered for type: " + field.getType());
        }
    }
}
