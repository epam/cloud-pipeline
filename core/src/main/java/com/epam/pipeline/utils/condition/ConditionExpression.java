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

package com.epam.pipeline.utils.condition;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * A node in a compute-quota filter or exclude expression tree.
 *
 * <p>The tree is evaluated recursively:
 * <ul>
 *   <li>{@link ConditionType#LOGICAL} — leaf node carrying a single
 *       {@code field}/{@code operand}/{@code value} predicate. Optionally gated by
 *       {@link #duration} for fields that support it (currently {@code run.tag}).</li>
 *   <li>{@link ConditionType#AND} — all child expressions must be {@code true}.</li>
 *   <li>{@link ConditionType#OR} — at least one child expression must be {@code true}.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class ConditionExpression {

    private String field;
    private String value;
    private String operand;
    private ConditionType conditionType;
    private List<ConditionExpression> expressions;

    /**
     * Minimum hours the condition must have been continuously true before the rule fires.
     * {@code null} means no time gate. Only valid on {@code LOGICAL} leaves where the field
     * supports duration (currently only {@code run.tag} with operator {@code =}).
     */
    private Integer duration;
}
