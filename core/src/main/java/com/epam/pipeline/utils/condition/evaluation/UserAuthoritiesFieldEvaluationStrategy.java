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
import java.util.Collection;
import java.util.function.Function;

public class UserAuthoritiesFieldEvaluationStrategy<T> extends AbstractLeafEvaluationStrategy<T> {

    private final Function<String, Collection<String>> authoritiesResolver;

    public UserAuthoritiesFieldEvaluationStrategy(final SubjectEntityField<T> field,
                                                  final Function<String, Collection<String>> authoritiesResolver) {
        super(field);
        this.authoritiesResolver = authoritiesResolver;
    }

    @Override
    public boolean evaluate(final ConditionExpression condition, final T subject, final LocalDateTime now) {
        final ConditionOperator op = ConditionOperator.fromSymbol(condition.getOperand());
        if (!FieldType.USER_AUTHORITIES.supports(op)) {
            throw new IllegalArgumentException(
                    "Operator '" + op.getSymbol() + "' not supported for field '" + condition.getField() + "'");
        }
        return doEvaluate(op, field.extract(subject), condition.getValue());
    }

    @Override
    protected boolean doEvaluate(final ConditionOperator op, final String subjectValue,
                                 final String expressionValue) {
        return (op == ConditionOperator.EQUALS)
                == contains(authoritiesResolver.apply(subjectValue), expressionValue);
    }

    private static boolean contains(final Collection<String> names, final String value) {
        return names != null && names.stream().anyMatch(value::equalsIgnoreCase);
    }
}
