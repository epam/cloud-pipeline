/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;

@Setter
@AllArgsConstructor
public class BillingGroupingSortOrder {

    public static final BillingGroupingSortOrder DEFAULT_SORT_ORDER = new BillingGroupingSortOrder(
            BillingGroupingSortOrder.BillingGroupingSortField.COST,
            BillingDetailsOrderAggregate.DEFAULT,
            true
    );

    @Getter
    @RequiredArgsConstructor
    public enum BillingGroupingSortField {
        COST, USAGE
    }

    private BillingGroupingSortField field;
    private BillingDetailsOrderAggregate detailsAggregate;
    private boolean desc;

    public Pair<String, String> getAggregateToOrderBy() {
        switch (field) {
            case COST:
                return detailsAggregate.getCostAggregate();
            case USAGE:
                return detailsAggregate.getUsageAggregate();
            default:
                return BillingDetailsOrderAggregate.DEFAULT.getCostAggregate();
        }

    }

    public BillingDetailsOrderAggregate getDetailsAggregate() {
        return detailsAggregate;
    }

    public boolean isDesc() {
        return desc;
    }

}
