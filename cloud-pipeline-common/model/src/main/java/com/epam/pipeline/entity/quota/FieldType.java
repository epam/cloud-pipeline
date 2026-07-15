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

package com.epam.pipeline.entity.quota;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Declares the value type of a {@link PipelineRunField} and the set of {@link ConditionOperator}s
 * that are valid for it.
 *
 * <p>Evaluation logic (how to compare two values) lives in the dedicated
 * {@code LeafEvaluationStrategy} implementations in the monitoring service, not here.
 *
 * <ul>
 *   <li>{@link #STRING}  — free-form text; supports case-insensitive wildcard matching ({@code *}).</li>
 *   <li>{@link #NUMERIC} — numeric value; supports all six comparison operators.</li>
 *   <li>{@link #BOOLEAN} — {@code true}/{@code false}; supports equality operators only.</li>
 *   <li>{@link #ENUM}    — named constant (e.g. run status); case-insensitive equality operators.</li>
 *   <li>{@link #TAGS}    — set of run tag keys; key-containment check, optional duration gate.</li>
 *   <li>{@link #GROUPS}  — set of user group/role names; group-membership check.</li>
 * </ul>
 */
public enum FieldType {

    /** Free-form string; supports {@code =} and {@code !=} with wildcard ({@code *}) patterns. */
    STRING(EnumSet.of(ConditionOperator.EQUALS, ConditionOperator.NOT_EQUALS)),

    /** Numeric value; supports {@code =}, {@code !=}, {@code >}, {@code >=}, {@code <}, {@code <=}. */
    NUMERIC(EnumSet.allOf(ConditionOperator.class)),

    /** Boolean ({@code true}/{@code false}); supports {@code =} and {@code !=}. */
    BOOLEAN(EnumSet.of(ConditionOperator.EQUALS, ConditionOperator.NOT_EQUALS)),

    /** Named constant compared case-insensitively; supports {@code =} and {@code !=}. */
    ENUM(EnumSet.of(ConditionOperator.EQUALS, ConditionOperator.NOT_EQUALS)),

    /**
     * Set of run tag keys. {@code =} matches when the tag key is present; {@code !=} when absent.
     * When {@link com.epam.pipeline.entity.quota.ConditionExpression#getDuration()} is set on the
     * leaf node a companion {@code <tagName>_date} tag is used to verify how long the tag has
     * been continuously present.
     */
    TAGS(EnumSet.of(ConditionOperator.EQUALS, ConditionOperator.NOT_EQUALS)),

    /**
     * Set of group and role names the run owner belongs to, resolved at monitoring time.
     * {@code =} matches when the owner is a member; {@code !=} when not.
     */
    GROUPS(EnumSet.of(ConditionOperator.EQUALS, ConditionOperator.NOT_EQUALS));

    private final Set<ConditionOperator> supportedOperators;

    FieldType(final Set<ConditionOperator> supportedOperators) {
        this.supportedOperators = Collections.unmodifiableSet(supportedOperators);
    }

    /** Returns {@code true} when {@code op} is valid for this type. */
    public boolean supports(final ConditionOperator op) {
        return supportedOperators.contains(op);
    }
}
