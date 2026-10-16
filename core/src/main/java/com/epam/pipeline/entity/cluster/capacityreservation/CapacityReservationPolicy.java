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
 * One rule of the {@code cluster.capacity.reservation.policies} preference: a condition over a capacity reservation
 * request, and what becomes of a request that matches it.
 *
 * <p>{@code statement} is evaluated against the request's
 * {@link com.epam.pipeline.utils.condition.field.CapacityReservationField}s. {@code exclude}, when set, takes a
 * request back out of the policy it would otherwise match.
 *
 * <p><b>Example - deny a long AWS commitment:</b>
 * <pre>{@code
 * {
 *   "action": "DENY",
 *   "statement": {
 *     "type": "AND",
 *     "expressions": [
 *       {"type": "LOGICAL", "field": "cloud.provider", "operand": "=", "value": "AWS"},
 *       {"type": "LOGICAL", "field": "commitment.duration", "operand": ">", "value": "1209600"}
 *     ]
 *   }
 * }
 * }</pre>
 * Refuses anything committed for longer than 14 days ({@code commitment.duration} is in seconds).
 *
 * <p><b>Example - approve a group's own GPU requests without a human:</b>
 * <pre>{@code
 * {
 *   "action": "AUTO_APPROVE",
 *   "statement": {
 *     "type": "AND",
 *     "expressions": [
 *       {"type": "LOGICAL", "field": "instance.type", "operand": "=", "value": "p5.*"},
 *       {"type": "LOGICAL", "field": "owner.authorities", "operand": "=", "value": "capacity_block_requester"}
 *     ]
 *   }
 * }
 * }</pre>
 *
 * <p>An expression naming a field the platform does not know is skipped with a warning and evaluates as no match,
 * so a policy written for an older version stops taking effect rather than failing loudly.
 *
 * @see CapacityReservationPolicyAction
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CapacityReservationPolicy {

    private CapacityReservationPolicyAction action;

    private ConditionExpression statement;

    private ConditionExpression exclude;
}
