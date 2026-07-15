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

import com.epam.pipeline.entity.quota.ConditionOperator;
import com.epam.pipeline.entity.quota.FieldType;
import com.epam.pipeline.entity.quota.SubjectEntityField;

import java.util.regex.Pattern;

/**
 * Evaluates {@link FieldType#STRING} leaf nodes using case-insensitive wildcard matching.
 *
 * <p>{@code *} in the rule value expands to any sequence of characters, consistent with the
 * wildcard convention used in the run Advanced Filter (e.g. {@code node.type = m5.*}).
 * Supports {@code =} and {@code !=}.
 *
 * @param <T> the subject type being evaluated
 */
class StringLeafEvaluationStrategy<T> extends AbstractLeafEvaluationStrategy<T> {

    StringLeafEvaluationStrategy(final SubjectEntityField<T> field) {
        super(field);
    }

    @Override
    protected boolean doEvaluate(final ConditionOperator op, final String subjectValue, final String expressionValue) {
        return (op == ConditionOperator.EQUALS) == matchesWildcard(subjectValue, expressionValue);
    }

    /**
     * Case-insensitive wildcard match. {@code Pattern.quote()} is used per segment so that
     * special regex characters inside literal parts are escaped, then segments are joined with
     * {@code .*} to cover the {@code *} wildcards.
     */
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
