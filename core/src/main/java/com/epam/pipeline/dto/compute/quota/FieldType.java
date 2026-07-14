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

package com.epam.pipeline.dto.compute.quota;

import lombok.Getter;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Declares the value type of a rule filter field and the set of {@link ConditionOperator}s
 * that are valid for it.
 */
@Getter
public enum FieldType {

    /** Free-form string; supports {@code =} and {@code !=} with wildcard ({@code *}) patterns. */
    STRING(EnumSet.of(ConditionOperator.EQUALS, ConditionOperator.NOT_EQUALS)),

    /** Numeric value; supports all six comparison operators. */
    NUMERIC(EnumSet.allOf(ConditionOperator.class)),

    /** Boolean ({@code true}/{@code false}); supports {@code =} and {@code !=}. */
    BOOLEAN(EnumSet.of(ConditionOperator.EQUALS, ConditionOperator.NOT_EQUALS)),

    /** Named constant compared case-insensitively; supports {@code =} and {@code !=}. */
    ENUM(EnumSet.of(ConditionOperator.EQUALS, ConditionOperator.NOT_EQUALS)),

    /** Set of run tag keys; supports {@code =} and {@code !=} with optional duration gate. */
    TAGS(EnumSet.of(ConditionOperator.EQUALS, ConditionOperator.NOT_EQUALS)),

    /** Set of group/role names the run owner belongs to; supports {@code =} and {@code !=}. */
    GROUPS(EnumSet.of(ConditionOperator.EQUALS, ConditionOperator.NOT_EQUALS));

    private final Set<ConditionOperator> supportedOperators;

    FieldType(final Set<ConditionOperator> supportedOperators) {
        this.supportedOperators = Collections.unmodifiableSet(supportedOperators);
    }

    public boolean supports(final ConditionOperator op) {
        return supportedOperators.contains(op);
    }
}
