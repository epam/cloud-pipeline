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
import com.epam.pipeline.entity.quota.FieldType;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.function.Function;

/**
 * Evaluates {@link FieldType#GROUPS} leaf nodes by checking whether the subject's owner
 * belongs to a given group or role.
 *
 * <p>Group membership cannot be derived from the subject alone — it requires the pre-loaded
 * user-group cache in {@link UserGroupHolder}. The owner username is extracted via the
 * injected {@code ownerExtractor}, keeping this strategy free of any subject-type coupling.
 *
 * <p>{@code =} matches when the owner is a member of the named group; {@code !=} when not.
 * Comparison is case-insensitive. Supports {@code =} and {@code !=}.
 *
 * @param <T> the subject type being evaluated
 */
@RequiredArgsConstructor
class UserGroupLeafEvaluationStrategy<T> implements LeafEvaluationStrategy<T> {

    private final UserGroupHolder userGroupHolder;
    private final Function<T, String> ownerExtractor;

    @Override
    public boolean evaluate(final ConditionExpression node, final T subject, final LocalDateTime now) {
        final ConditionOperator op = ConditionOperator.fromSymbol(node.getOperand());
        if (!FieldType.GROUPS.supports(op)) {
            throw new IllegalArgumentException(
                    "Operator '" + op.getSymbol() + "' not supported for field '" + node.getField() + "'");
        }
        final String groups = String.join(",", userGroupHolder.getGroupsForUser(ownerExtractor.apply(subject)));
        return (op == ConditionOperator.EQUALS) == containsKey(groups, node.getValue());
    }

    private static boolean containsKey(final String commaSeparatedKeys, final String group) {
        return Arrays.stream(commaSeparatedKeys.split(",", -1))
                .anyMatch(k -> k.equalsIgnoreCase(group));
    }
}
