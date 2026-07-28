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

package com.epam.pipeline.dto.credits;

import com.epam.pipeline.utils.condition.ConditionExpression;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PlatformUsageCreditsUpdateRule {

    private Long id;
    private String name;
    private String description;
    private PlatformUsageCreditsUpdateRuleType ruleType;
    private ConditionExpression statement;
    /** Optional: runs matching this expression are excluded even if they match filterExpression. */
    private ConditionExpression exclude;
    private PlatformUsageCreditsUpdateAction action;
    /** If null, the rule is applied per incident. If set, the rule
     *  fires at most once per user within the given number of hours, regardless of how many
     *  matching incidents exist. <p>Measured in hours.*/
    private Integer timeWindow;
}
