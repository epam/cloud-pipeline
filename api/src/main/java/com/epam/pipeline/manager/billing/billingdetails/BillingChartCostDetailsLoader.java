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

import com.epam.pipeline.controller.vo.billing.BillingCostDetailsRequest;
import com.epam.pipeline.entity.billing.BillingChartDetails;
import com.epam.pipeline.entity.billing.StorageBillingChartCostDetails;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import java.util.Collections;
import java.util.List;

/**
 * Helper class to encapsulate logic of loading cost details for specific billing entity,
 * currently it loads details for storages {@link StorageBillingChartCostDetails} only but can be easily expanded
 * */
public final class BillingChartCostDetailsLoader {

    private BillingChartCostDetailsLoader() {}

    public static List<AggregationBuilder> buildQuery(final BillingCostDetailsRequest request) {
        if (StorageBillingCostDetailsHelper.isStorageBillingDetailsShouldBeLoaded(request)) {
            return StorageBillingCostDetailsHelper.buildQuery();
        }
        return Collections.emptyList();
    }


    public static BillingChartDetails parseResponse(final BillingCostDetailsRequest request,
                                                    final Aggregations aggregations) {
        if (StorageBillingCostDetailsHelper.isStorageBillingDetailsShouldBeLoaded(request)) {
            return StorageBillingCostDetailsHelper.parseResponse(aggregations);
        }
        return null;
    }

}
