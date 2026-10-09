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
import com.epam.pipeline.utils.condition.field.SubjectEntityField;

import java.time.LocalDateTime;

public abstract class AbstractLeafEvaluationStrategy<T> implements EntityConditionEvaluationStrategy<T> {

    protected final SubjectEntityField<T> field;

    public AbstractLeafEvaluationStrategy(final SubjectEntityField<T> field) {
        this.field = field;
    }

    @Override
    public boolean evaluate(final ConditionExpression condition, final T subject, final LocalDateTime now) {
        final ConditionOperator op = ConditionOperator.fromSymbol(condition.getOperand());
        if (!field.getType().supports(op)) {
            throw new IllegalArgumentException(
                    "Operator '" + op.getSymbol() + "' not supported for field '" + condition.getField() + "'");
        }
        final String subjectValue = field.extract(subject);
        if (subjectValue == null) {
            return false;
        }
        return doEvaluate(op, subjectValue, condition.getValue());
    }

    protected abstract boolean doEvaluate(ConditionOperator op, String subjectValue, String expressionValue);
}
