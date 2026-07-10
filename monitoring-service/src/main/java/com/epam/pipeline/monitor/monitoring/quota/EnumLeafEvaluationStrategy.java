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
import com.epam.pipeline.entity.quota.RunField;

import java.util.EnumSet;
import java.util.Set;

/**
 * Evaluates {@link FieldType#ENUM} leaf nodes using case-insensitive name equality
 * (e.g. {@code run.status = RUNNING} matches regardless of capitalisation).
 * Supports {@code =} and {@code !=}.
 */
class EnumLeafEvaluationStrategy extends StandardLeafEvaluationStrategy {

    EnumLeafEvaluationStrategy(final RunField field) {
        super(field);
    }

    @Override
    protected boolean doEvaluate(final ConditionOperator op, final String runValue, final String expressionValue) {
        return (op == ConditionOperator.EQUALS) == runValue.equalsIgnoreCase(expressionValue);
    }
}
