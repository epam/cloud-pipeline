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

import com.epam.pipeline.entity.quota.ConditionExpression;
import com.epam.pipeline.entity.quota.ConditionOperator;
import com.epam.pipeline.entity.quota.SubjectEntityField;

import java.time.LocalDateTime;

/**
 * Abstract base for all {@link SubjectEntityField}-based {@link LeafEvaluationStrategy} implementations.
 *
 * <p>Handles the common plumbing for every leaf node backed by a {@link SubjectEntityField}:
 * <ol>
 *   <li>Parses the operator symbol.</li>
 *   <li>Validates it against the operator set declared by the field's {@link SubjectEntityField#getType()}.</li>
 *   <li>Extracts the field value from the subject.</li>
 *   <li>Short-circuits to {@code false} when the extracted value is {@code null}.</li>
 *   <li>Delegates the actual comparison to {@link #doEvaluate}.</li>
 * </ol>
 *
 * <p>Subclasses provide only the type-specific comparison logic in {@link #doEvaluate}.
 * Subclasses that need additional runtime context (e.g. duration gate) may override
 * {@link #evaluate} and call {@code super.evaluate} for the boolean part.
 *
 * @param <T> the subject type being evaluated
 */
abstract class AbstractLeafEvaluationStrategy<T> implements LeafEvaluationStrategy<T> {

    protected final SubjectEntityField<T> field;

    AbstractLeafEvaluationStrategy(final SubjectEntityField<T> field) {
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

    /**
     * Performs the type-specific comparison. Called only after the operator has been
     * validated and {@code subjectValue} has been confirmed non-null.
     */
    protected abstract boolean doEvaluate(ConditionOperator op, String subjectValue, String expressionValue);
}
