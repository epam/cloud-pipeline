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

import com.epam.pipeline.entity.quota.ConditionType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.quota.ComputeQuotaRule;
import com.epam.pipeline.entity.quota.ConditionExpression;
import com.epam.pipeline.entity.quota.PipelineRunField;
import com.epam.pipeline.entity.quota.SubjectEntityField;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates a {@link ComputeQuotaRule} against a subject of type {@code T} in memory by walking
 * the filter and exclude expression trees and delegating each LOGICAL leaf to the appropriate
 * {@link LeafEvaluationStrategy}.
 *
 * <p>Strategies are registered at construction time via a field-name → strategy map passed to
 * the constructor. To evaluate rules against a new subject type, build a registry for that type
 * and pass it in — no changes to this class are needed.
 *
 * <p>Use {@link #forPipelineRun(UserGroupHolder)} to obtain a ready-made instance for
 * {@link PipelineRun} evaluation.
 *
 * <p>A subject matches a rule when:
 * <ol>
 *   <li>its {@code filterExpression} evaluates to {@code true}, AND</li>
 *   <li>its {@code excludeExpression} is {@code null} or evaluates to {@code false}.</li>
 * </ol>
 *
 * @param <T> the subject type to evaluate rules against
 */
@Slf4j
public class ComputeQuotaRuleEvaluator<T> {

    private final Map<String, LeafEvaluationStrategy<T>> leafStrategies;

    public ComputeQuotaRuleEvaluator(final Map<String, LeafEvaluationStrategy<T>> leafStrategies) {
        this.leafStrategies = Collections.unmodifiableMap(new HashMap<>(leafStrategies));
    }

    /**
     * Creates an evaluator pre-configured for {@link PipelineRun} subjects, covering all
     * {@link PipelineRunField} entries with their corresponding strategies.
     */
    public static ComputeQuotaRuleEvaluator<PipelineRun> forPipelineRun(final UserGroupHolder userGroupHolder) {
        return new ComputeQuotaRuleEvaluator<>(buildPipelineRunRegistry(userGroupHolder));
    }

    /** Evaluates the rule against the subject using the current UTC time for duration checks. */
    public boolean matches(final ComputeQuotaRule rule, final T subject) {
        return matches(rule, subject, LocalDateTime.now(ZoneOffset.UTC));
    }

    public boolean matches(final ComputeQuotaRule rule, final T subject, final LocalDateTime now) {
        if (!evaluate(rule.getStatement(), subject, now)) {
            return false;
        }
        return Objects.isNull(rule.getFilter())
                || !evaluate(rule.getFilter(), subject, now);
    }

    private boolean evaluate(final ConditionExpression condition, final T subject, final LocalDateTime now) {
        if (Objects.isNull(condition)) {
            return false;
        }
        try {
            final ConditionType type = condition.getType();
            Assert.notNull(type, "type must not be null");
            switch (type) {
                case AND:     return children(condition).stream().allMatch(child -> evaluate(child, subject, now));
                case OR:      return children(condition).stream().anyMatch(child -> evaluate(child, subject, now));
                case LOGICAL: return evaluateLeaf(condition, subject, now);
                default: throw new IllegalArgumentException("Unknown type: '" + type + "'");
            }
        } catch (IllegalArgumentException e) {
            log.warn("Skipping expression condition: {}", e.getMessage());
            return false;
        }
    }

    private boolean evaluateLeaf(final ConditionExpression condition, final T subject, final LocalDateTime now) {
        final String fieldName = condition.getField();
        final LeafEvaluationStrategy<T> strategy = leafStrategies.get(fieldName);
        Assert.notNull(strategy, "Unknown quota rule field: '" + fieldName + "'");
        return strategy.evaluate(condition, subject, now);
    }

    private List<ConditionExpression> children(final ConditionExpression condition) {
        final List<ConditionExpression> exprs = condition.getExpressions();
        return exprs != null ? exprs : Collections.emptyList();
    }

    private static Map<String, LeafEvaluationStrategy<PipelineRun>> buildPipelineRunRegistry(
            final UserGroupHolder userGroupHolder) {
        final Map<String, LeafEvaluationStrategy<PipelineRun>> map = new HashMap<>();
        for (final PipelineRunField field : PipelineRunField.values()) {
            final LeafEvaluationStrategy<PipelineRun> strategy = resolveEntityFieldStrategy(field, userGroupHolder);
            for (final String name : field.getDisplayNames()) {
                map.put(name, strategy);
            }
        }
        return map;
    }

    private static LeafEvaluationStrategy<PipelineRun> resolveEntityFieldStrategy(
            final SubjectEntityField<PipelineRun> field, final UserGroupHolder userGroupHolder) {
        switch (field.getType()) {
            case STRING:  return new StringLeafEvaluationStrategy<>(field);
            case NUMERIC: return new NumericLeafEvaluationStrategy<>(field);
            case BOOLEAN: return new BooleanLeafEvaluationStrategy<>(field);
            case ENUM:    return new EnumLeafEvaluationStrategy<>(field);
            case TAGS:    return new TagLeafEvaluationStrategy<>(field, PipelineRun::getTags);
            case GROUPS:  return new UserGroupLeafEvaluationStrategy<>(userGroupHolder, PipelineRun::getOwner);
            default: throw new IllegalArgumentException(
                    "No leaf evaluation strategy registered for type: " + field.getType());
        }
    }
}
