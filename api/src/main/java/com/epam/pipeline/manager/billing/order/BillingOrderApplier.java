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

package com.epam.pipeline.manager.billing.order;

import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.entity.billing.BillingGroupingOrderAggregate;
import com.epam.pipeline.entity.billing.BillingGroupingSortOrder;
import com.epam.pipeline.manager.billing.BillingUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.springframework.util.Assert;

public final class BillingOrderApplier {

    private BillingOrderApplier() {}

    public static BoolQueryBuilder applyOrder(final BillingGrouping grouping,
                                              final BillingGroupingSortOrder order,
                                              final BoolQueryBuilder query,
                                              final TermsAggregationBuilder terms) {
        final BillingGroupingOrderAggregate orderAggregate = order.getAggregate();
        Assert.isTrue(orderAggregate.getGroups() == null || orderAggregate.getGroups().contains(grouping),
                String.format("Grouping: %s and Grouping Order: %s, don't match.",
                        grouping.name(), orderAggregate.name()));

        // Apply additional filter to query to filter out docs that don't have value to sort by
        query.filter(QueryBuilders.boolQuery().must(QueryBuilders.existsQuery(order.getAggregateToOrderBy())));

        terms.subAggregation(order.getAggregation());
        terms.order(BucketOrder.aggregation(order.getAggregateToOrderBy() + BillingUtils.SORT_AGG_POSTFIX,
                order.isDesc()));

        return query;
    }
}
