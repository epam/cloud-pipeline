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

import com.epam.pipeline.manager.billing.BillingUtils;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;

public record BillingGroupingSortOrder(BillingGroupingSortMetric metric,
                                       BillingGroupingOrderAggregate aggregate,
                                       boolean desc) {

    public static final BillingGroupingSortOrder DEFAULT_SORT_ORDER = new BillingGroupingSortOrder(
            BillingGroupingSortMetric.COST,
            BillingGroupingOrderAggregate.DEFAULT,
            false
    );

    public enum BillingGroupingSortMetric {
        COST, USAGE, USAGE_RUNS, COUNT_RUNS
    }

    public String getAggregateToOrderBy() {
        return switch (metric) {
            case COST -> aggregate.getCostField();
            case USAGE, USAGE_RUNS -> aggregate.getUsageField();
            case COUNT_RUNS -> aggregate.getCountField();
            default -> BillingGroupingOrderAggregate.DEFAULT.getCostField();
        };

    }

    public AggregationBuilder getAggregation() {
        final String aggregateField = getAggregateToOrderBy();
        return switch (metric) {
            case USAGE -> AggregationBuilders.avg(aggregateField + BillingUtils.SORT_AGG_POSTFIX)
                    .field(aggregateField);
            case COUNT_RUNS -> AggregationBuilders.count(aggregateField + BillingUtils.SORT_AGG_POSTFIX)
                    .field(aggregateField);
            default -> AggregationBuilders.sum(aggregateField + BillingUtils.SORT_AGG_POSTFIX)
                    .field(aggregateField);
        };
    }
}

