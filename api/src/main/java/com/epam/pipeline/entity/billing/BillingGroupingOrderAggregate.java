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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

@Getter
@RequiredArgsConstructor
public enum BillingGroupingOrderAggregate {

    DEFAULT(null, getCostSortOrder(), getCostSortOrder()),

    STORAGE(BillingGrouping.STORAGE, getCostSortOrder(), getSortOrder("usage_bytes")),
    STANDARD(BillingGrouping.STORAGE,
            getSortOrder("standard_total_cost"), getSortOrder("standard_total_usage_bytes")),
    GLACIER(BillingGrouping.STORAGE,
            getSortOrder("glacier_total_cost"), getSortOrder("glacier_total_usage_bytes")),
    GLACIER_IR(BillingGrouping.STORAGE,
            getSortOrder("glacier_ir_total_cost"), getSortOrder("glacier_ir_total_usage_bytes")),
    DEEP_ARCHIVE(BillingGrouping.STORAGE,
            getSortOrder("deep_archive_total_cost"), getSortOrder("deep_archive_total_usage_bytes"));

    private static ImmutablePair<String, String> getCostSortOrder() {
        return ImmutablePair.of("cost_sort_order", "cost");
    }

    private static ImmutablePair<String, String> getSortOrder(String field) {
        return ImmutablePair.of(field, field);
    }

    private final BillingGrouping group;
    private final Pair<String, String> costAggregate;
    private final Pair<String, String> usageAggregate;
}
