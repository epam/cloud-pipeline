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

/**
 * Evaluates {@link FieldType#BOOLEAN} leaf nodes (e.g. {@code run.spot}).
 * Rule values are expected to be {@code "true"} or {@code "false"} (case-insensitive).
 * Supports {@code =} and {@code !=}.
 *
 * @param <T> the subject type being evaluated
 */
class BooleanLeafEvaluationStrategy<T> extends AbstractLeafEvaluationStrategy<T> {

    BooleanLeafEvaluationStrategy(final SubjectEntityField<T> field) {
        super(field);
    }

    @Override
    protected boolean doEvaluate(final ConditionOperator op, final String subjectValue, final String expressionValue) {
        return (op == ConditionOperator.EQUALS)
                == (Boolean.parseBoolean(subjectValue) == Boolean.parseBoolean(expressionValue));
    }
}
