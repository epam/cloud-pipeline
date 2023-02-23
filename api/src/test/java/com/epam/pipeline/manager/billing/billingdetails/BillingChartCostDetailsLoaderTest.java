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
import com.epam.pipeline.entity.billing.BillingGrouping;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;


public class BillingChartCostDetailsLoaderTest {

    @Test
    public void checkThatBuildQueryWillBuildAggsIfCriteriaMatchByGrouping() {
        assertAggregations(
            BillingChartCostDetailsLoader.buildQuery(
                BillingCostDetailsRequest.builder().enabled(true)
                        .grouping(BillingGrouping.STORAGE).build()
            )
        );

        assertAggregations(
            BillingChartCostDetailsLoader.buildQuery(
                BillingCostDetailsRequest.builder().enabled(true)
                        .grouping(BillingGrouping.STORAGE_TYPE).build()
            )
        );
    }

    @Test
    public void checkThatBuildQueryWillBuildAggsIfCriteriaMatchByFilters() {
        final HashMap<String, List<String>> filters = new HashMap<String, List<String>>() {{
                put("resource_type", Collections.singletonList("STORAGE"));
                put("storage_type", Collections.singletonList("OBJECT_STORAGE"));
            }};
        assertAggregations(
            BillingChartCostDetailsLoader.buildQuery(
                BillingCostDetailsRequest.builder().enabled(true).filters(filters).build()
            )
        );
    }

    @Test
    public void checkThatBuildQueryWillNotBuildAggsIfDisabledMatch() {
        Assert.assertTrue(
                BillingChartCostDetailsLoader.buildQuery(
                        BillingCostDetailsRequest.builder().enabled(false).build()
                ).isEmpty()
        );
    }

    @Test
    public void checkThatBuildQueryWillNotBuildAggsIfCriteriaDontMatch() {
        Assert.assertTrue(
                BillingChartCostDetailsLoader.buildQuery(
                        BillingCostDetailsRequest.builder().enabled(true)
                                .grouping(BillingGrouping.TOOL).build()
                ).isEmpty()
        );
        final HashMap<String, List<String>> filters = new HashMap<String, List<String>>() {{
                put("resource_type", Arrays.asList("STORAGE", "COMPUTE"));
                put("storage_type", Collections.singletonList("OBJECT_STORAGE"));
            }};
        Assert.assertTrue(
            BillingChartCostDetailsLoader.buildQuery(
                BillingCostDetailsRequest.builder().enabled(true).filters(filters).build()
            ).isEmpty()
        );
    }

    @Test
    public void parseResponseReturnDataIfCriteriaMatch() {
        BillingChartDetails details = BillingChartCostDetailsLoader.parseResponse(
                BillingCostDetailsRequest.builder().enabled(true)
                        .grouping(BillingGrouping.STORAGE).build(), null);
        Assert.assertNotNull(details);
    }

    @Test
    public void parseResponseReturnNullIfCriteriaDontMatch() {
        Assert.assertNull(
                BillingChartCostDetailsLoader.parseResponse(
                        BillingCostDetailsRequest.builder().enabled(false).build(), null)
        );
    }

    private static void assertAggregations(List<AggregationBuilder> aggs) {
        Assert.assertEquals(aggs.size(), 4 * StorageBillingCostDetailsHelper.S3_STORAGE_CLASSES.size());
        // For each storage class there should be 4 aggs (cost, cost old version, size, size old versions)
        for (String storageClass : StorageBillingCostDetailsHelper.S3_STORAGE_CLASSES) {
            for (String template : StorageBillingCostDetailsHelper.STORAGE_CLASS_AGGREGATION_TEMPLATES) {
                Assert.assertTrue(
                        aggs.stream().anyMatch(agg -> agg.getName().equals(
                                String.format(template, storageClass.toLowerCase(Locale.ROOT)))
                        )
                );
            }
        }
    }
}