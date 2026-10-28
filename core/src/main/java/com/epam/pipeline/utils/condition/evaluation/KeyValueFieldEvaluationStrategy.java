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
import com.epam.pipeline.utils.condition.ConditionOperator;
import com.epam.pipeline.utils.condition.FieldType;
import com.epam.pipeline.utils.condition.field.SubjectEntityField;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Evaluates {@link FieldType#KEY_VALUE} leaf nodes against a string-to-string map provided by
 * the field's {@link SubjectEntityField#extractMap} method.
 *
 * <p>The expression value is interpreted as:
 * <ul>
 *   <li>{@code <name>} — matches when the map contains key {@code name}
 *       (case-insensitive), regardless of its value.</li>
 *   <li>{@code <name>=<valuePattern>} — matches when the map contains key {@code name} and
 *       its value satisfies the case-insensitive wildcard pattern {@code valuePattern}
 *       ({@code *} expands to any sequence of characters).</li>
 * </ul>
 *
 * <p>{@code =} matches when the condition is satisfied; {@code !=} when not.
 * Supports {@code =} and {@code !=}.
 *
 * @param <T> the subject type being evaluated
 */
public class KeyValueFieldEvaluationStrategy<T> extends AbstractLeafEvaluationStrategy<T> {

    public KeyValueFieldEvaluationStrategy(final SubjectEntityField<T> field) {
        super(field);
    }

    @Override
    public boolean evaluate(final ConditionExpression condition, final T subject, final LocalDateTime now) {
        final ConditionOperator op = ConditionOperator.fromSymbol(condition.getOperand());
        if (!FieldType.KEY_VALUE.supports(op)) {
            throw new IllegalArgumentException(
                    "Operator '" + op.getSymbol() + "' not supported for field '" + condition.getField() + "'");
        }
        final String expressionValue = condition.getValue();
        final int sep = expressionValue.indexOf('=');
        final String name = sep < 0 ? expressionValue : expressionValue.substring(0, sep);
        final String valuePattern = sep < 0 ? null : expressionValue.substring(sep + 1);
        final Map<String, String> map = field.extractMap(subject);
        final String actualValue = findValue(map, name);
        final boolean positive = actualValue != null
                && (valuePattern == null || matchesWildcard(actualValue, valuePattern));
        return (op == ConditionOperator.EQUALS) == positive;
    }

    @Override
    protected boolean doEvaluate(final ConditionOperator op, final String subjectValue,
                                 final String expressionValue) {
        throw new UnsupportedOperationException("KeyValueFieldEvaluationStrategy overrides evaluate() directly");
    }

    private static String findValue(final Map<String, String> map, final String name) {
        if (map == null) {
            return null;
        }
        return map.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static boolean matchesWildcard(final String actual, final String pattern) {
        final String[] parts = pattern.split("\\*", -1);
        final StringBuilder regex = new StringBuilder("(?i)");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(parts[i]));
        }
        return actual.matches(regex.toString());
    }
}
