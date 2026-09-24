/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.entity.cluster.capacityreservation;

import com.epam.pipeline.utils.condition.ConditionExpression;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One rule in the capacity reservation approval policy: which requests it targets, and what happens to
 * them.
 *
 * <p>Held as a list in a system preference rather than a table of its own, so administrators can change
 * the rules without a release. There is deliberately no provider field - scope a rule to one provider by
 * testing {@code cloud.provider} inside {@link #statement}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CapacityReservationPolicy {

    private CapacityReservationPolicyAction action;

    /**
     * Which requests this rule applies to. A rule whose statement does not match is simply skipped.
     */
    private ConditionExpression statement;

    /**
     * Requests excluded from this rule even when {@link #statement} matches. Optional.
     */
    private ConditionExpression exclude;
}
