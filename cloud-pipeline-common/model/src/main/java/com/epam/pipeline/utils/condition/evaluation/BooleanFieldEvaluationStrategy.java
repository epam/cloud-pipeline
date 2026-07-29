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


import com.epam.pipeline.utils.condition.ConditionOperator;
import com.epam.pipeline.utils.condition.FieldType;
import com.epam.pipeline.utils.condition.field.SubjectEntityField;

/**
 * Evaluates {@link FieldType#BOOLEAN} leaf nodes (e.g. {@code run.spot}).
 * Rule values are expected to be {@code "true"} or {@code "false"} (case-insensitive).
 * Supports {@code =} and {@code !=}.
 *
 * @param <T> the subject type being evaluated
 */
public class BooleanFieldEvaluationStrategy<T> extends AbstractLeafEvaluationStrategy<T> {

    public BooleanFieldEvaluationStrategy(final SubjectEntityField<T> field) {
        super(field);
    }

    @Override
    protected boolean doEvaluate(final ConditionOperator op, final String subjectValue, final String expressionValue) {
        return (op == ConditionOperator.EQUALS)
                == (Boolean.parseBoolean(subjectValue) == Boolean.parseBoolean(expressionValue));
    }
}
