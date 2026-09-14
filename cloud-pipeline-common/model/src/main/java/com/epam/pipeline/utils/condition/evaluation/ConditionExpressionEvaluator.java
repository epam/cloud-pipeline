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

package com.epam.pipeline.utils.condition.evaluation;

import com.epam.pipeline.utils.condition.ConditionExpression;
import com.epam.pipeline.utils.condition.ConditionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates a {@link ConditionExpression} tree against a subject of type {@code T} by recursively
 * walking AND/OR nodes and delegating each LOGICAL leaf to the registered
 * {@link EntityConditionEvaluationStrategy}.
 *
 * <p>Strategies are registered at construction time via a field-name → strategy map.
 * Unknown fields and unsupported operators are caught and logged; the offending node
 * evaluates to {@code false}.
 *
 * @param <T> the subject type to evaluate conditions against
 */
@Slf4j
public class ConditionExpressionEvaluator<T> {

    private final Map<String, EntityConditionEvaluationStrategy<T>> conditionEvaluationStrategies;

    public ConditionExpressionEvaluator(
            final Map<String, EntityConditionEvaluationStrategy<T>> conditionEvaluationStrategies) {
        this.conditionEvaluationStrategies = Collections.unmodifiableMap(new HashMap<>(conditionEvaluationStrategies));
    }

    /**
     * Evaluates the expression tree against {@code subject}. Returns {@code false} when
     * {@code expression} is {@code null}.
     */
    public boolean evaluate(final ConditionExpression expression, final T subject, final LocalDateTime now) {
        if (Objects.isNull(expression)) {
            return false;
        }
        try {
            final ConditionType type = expression.getType();
            Assert.notNull(type, "type must not be null");
            switch (type) {
                case AND:
                    return children(expression).stream()
                        .allMatch(child -> evaluate(child, subject, now));
                case OR:
                    return children(expression).stream()
                        .anyMatch(child -> evaluate(child, subject, now));
                case LOGICAL:
                    return evaluateLeafCondition(expression, subject, now);
                default:
                    throw new IllegalArgumentException("Unknown type: '" + type + "'");
            }
        } catch (IllegalArgumentException e) {
            log.warn("Skipping expression condition: {}", e.getMessage());
            return false;
        }
    }

    private boolean evaluateLeafCondition(final ConditionExpression expression, final T subject,
                                          final LocalDateTime now) {
        final String fieldName = expression.getField();
        final EntityConditionEvaluationStrategy<T> strategy = conditionEvaluationStrategies.get(fieldName);
        Assert.notNull(strategy, "Unknown quota rule field: '" + fieldName + "'");
        return strategy.evaluate(expression, subject, now);
    }

    private List<ConditionExpression> children(final ConditionExpression expression) {
        final List<ConditionExpression> exprs = expression.getExpressions();
        return exprs != null ? exprs : Collections.emptyList();
    }
}
