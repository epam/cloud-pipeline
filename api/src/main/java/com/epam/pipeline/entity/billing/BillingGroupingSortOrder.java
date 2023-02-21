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

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;

@Setter
@AllArgsConstructor
@EqualsAndHashCode
public class BillingGroupingSortOrder {

    public static final BillingGroupingSortOrder DEFAULT_SORT_ORDER = new BillingGroupingSortOrder(
            BillingGroupingSortOrder.BillingGroupingSortField.COST,
            BillingGroupingOrderAggregate.DEFAULT,
            false
    );

    @Getter
    @RequiredArgsConstructor
    public enum BillingGroupingSortField {
        COST, USAGE
    }

    private BillingGroupingSortField field;
    private BillingGroupingOrderAggregate orderAggregate;
    private boolean desc;

    public Pair<String, String> getAggregateToOrderBy() {
        switch (field) {
            case COST:
                return orderAggregate.getCostAggregate();
            case USAGE:
                return orderAggregate.getUsageAggregate();
            default:
                return BillingGroupingOrderAggregate.DEFAULT.getCostAggregate();
        }

    }

    public BillingGroupingOrderAggregate getOrderAggregate() {
        return orderAggregate;
    }

    public boolean isDesc() {
        return desc;
    }

}
