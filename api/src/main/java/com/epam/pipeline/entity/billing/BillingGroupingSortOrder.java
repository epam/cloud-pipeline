/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.billing;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@EqualsAndHashCode
public class BillingGroupingSortOrder {

    public static final BillingGroupingSortOrder DEFAULT_SORT_ORDER = new BillingGroupingSortOrder(
            BillingGroupingSortMetric.COST,
            BillingGroupingOrderAggregate.DEFAULT,
            false
    );

    public enum BillingGroupingSortMetric {
        COST, USAGE
    }

    private final BillingGroupingSortMetric metric;
    private final BillingGroupingOrderAggregate aggregate;
    private final boolean desc;

    public String getAggregateToOrderBy() {
        switch (metric) {
            case COST:
                return aggregate.getCostField();
            case USAGE:
                return aggregate.getUsageField();
            default:
                return BillingGroupingOrderAggregate.DEFAULT.getCostField();
        }

    }
}

