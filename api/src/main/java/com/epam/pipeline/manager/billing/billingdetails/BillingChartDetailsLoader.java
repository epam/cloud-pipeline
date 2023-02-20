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

package com.epam.pipeline.manager.billing.billingdetails;

import com.epam.pipeline.controller.vo.billing.CostDetailsRequest;
import com.epam.pipeline.entity.billing.BillingChartDetails;
import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.entity.billing.StorageBillingChartDetails;
import org.apache.commons.collections4.MapUtils;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Helper class to encapsulate logic of loading cost details for specific billing entity,
 * currently it loads details for storages {@link StorageBillingChartDetails} only but can be easily expanded
 * */
public final class BillingChartDetailsLoader {

    private BillingChartDetailsLoader() {}

    public static List<AggregationBuilder> buildQuery(final CostDetailsRequest request) {
        if (isStorageBillingDetailsShouldBeLoaded(request)) {
            return StorageBillingDetailsHelper.buildQuery();
        }
        return Collections.emptyList();
    }

    private static boolean isStorageBillingDetailsShouldBeLoaded(final CostDetailsRequest request) {
        if (!request.isEnabled()) {
            return false;
        }
        final BillingGrouping grouping = request.getGrouping();
        if (BillingGrouping.STORAGE.equals(grouping) || BillingGrouping.STORAGE_TYPE.equals(grouping)) {
            return true;
        } else {
            final Map<String, List<String>> filters = MapUtils.emptyIfNull(request.getFilters());
            Boolean onlyStorageRTypeRequestedOrNothing = Optional.ofNullable(filters.get("resource_type"))
                    .map(values -> (values.contains("STORAGE") && values.size() == 1) || values.isEmpty())
                    .orElse(false);
            Boolean onlyObjectStorageSTypeRequested = Optional.ofNullable(filters.get("storage_type"))
                    .map(values -> values.contains("OBJECT_STORAGE") && values.size() == 1).orElse(false);
            return onlyObjectStorageSTypeRequested && onlyStorageRTypeRequestedOrNothing;
        }
    }


    public static BillingChartDetails parseResponse(final CostDetailsRequest request,
                                                    final Aggregations aggregations) {
        if (isStorageBillingDetailsShouldBeLoaded(request)) {
            return StorageBillingDetailsHelper.parseResponse(aggregations);
        }
        return null;
    }

}
