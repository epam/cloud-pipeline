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
import org.elasticsearch.search.aggregations.PipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;


public class BillingChartCostDetailsLoaderTest {

    public static final int ZERO = 0;
    public static final int NUMBER_OF_AGGS_FOR_STORAGE_GROUPING = 24;

    // For each storage class 2 aggs for cost + 1 Terms agg for avg usage statistics by storage
    public static final int NUMBER_OF_SIMPLE_AGGS_FOR_NON_STORAGE_GROUPING =
            2 * StorageBillingCostDetailsLoader.STORAGE_CLASSES.size() + 1;

    // For each storage class 2 aggregations to get sum of avg stats by bucket
    public static final int NUMBER_OF_PIPELINE_AGGS_FOR_NON_STORAGE_GROUPING =
            2 * StorageBillingCostDetailsLoader.STORAGE_CLASSES.size();

    @Test
    public void checkThatBuildQueryWillBuildAggsIfCriteriaMatchByGrouping() {
        assertAggregations(
            agg -> BillingChartCostDetailsLoader.buildQuery(
                    BillingCostDetailsRequest.builder().enabled(true).grouping(BillingGrouping.STORAGE).build(), agg
            ),
            NUMBER_OF_AGGS_FOR_STORAGE_GROUPING, ZERO
        );

        assertAggregations(
            agg -> BillingChartCostDetailsLoader.buildQuery(
            BillingCostDetailsRequest.builder().enabled(true)
                    .grouping(BillingGrouping.STORAGE_TYPE).build(), agg),
                NUMBER_OF_SIMPLE_AGGS_FOR_NON_STORAGE_GROUPING,
                NUMBER_OF_PIPELINE_AGGS_FOR_NON_STORAGE_GROUPING
        );
    }

    @Test
    public void checkThatBuildQueryWillBuildAggsIfCriteriaMatchByFilters() {
        final HashMap<String, List<String>> filters = new HashMap<String, List<String>>() {{
                put("resource_type", Collections.singletonList("STORAGE"));
                put("storage_type", Collections.singletonList("OBJECT_STORAGE"));
            }};
        assertAggregations(
            agg -> BillingChartCostDetailsLoader.buildQuery(
                    BillingCostDetailsRequest.builder().enabled(true).filters(filters).build(), agg),
                NUMBER_OF_SIMPLE_AGGS_FOR_NON_STORAGE_GROUPING,
                NUMBER_OF_PIPELINE_AGGS_FOR_NON_STORAGE_GROUPING
        );
    }

    @Test
    public void checkThatBuildQueryWillNotBuildAggsIfDisabledMatch() {
        assertAggregations(
            agg -> BillingChartCostDetailsLoader.buildQuery(
                    BillingCostDetailsRequest.builder().enabled(false).build(), agg),
                ZERO, ZERO
        );
    }

    @Test
    public void checkThatBuildQueryWillNotBuildAggsIfCriteriaDontMatch() {
        assertAggregations(
            agg -> BillingChartCostDetailsLoader.buildQuery(
                    BillingCostDetailsRequest.builder().enabled(true)
                            .grouping(BillingGrouping.TOOL).build(), agg),
                ZERO, ZERO
        );

        final HashMap<String, List<String>> filters = new HashMap<String, List<String>>() {{
                put("resource_type", Arrays.asList("STORAGE", "COMPUTE"));
                put("storage_type", Collections.singletonList("OBJECT_STORAGE"));
            }};
        assertAggregations(
            agg -> BillingChartCostDetailsLoader.buildQuery(
                    BillingCostDetailsRequest.builder().enabled(true).filters(filters).build(), agg),
                ZERO, ZERO
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

    private static void assertAggregations(final Consumer<AggregationBuilder> aggs,
                                           final int numberOfSimpleAggs, final int numberOfPipelineAggs) {
        TermsAggregationBuilder topLevelAgg = Mockito.mock(TermsAggregationBuilder.class);
        aggs.accept(topLevelAgg);
        Mockito.verify(topLevelAgg, Mockito.times(numberOfSimpleAggs))
                .subAggregation(Mockito.any(AggregationBuilder.class));
        Mockito.verify(
                topLevelAgg,
                Mockito.times(numberOfPipelineAggs)
        ).subAggregation(Mockito.any(PipelineAggregationBuilder.class));
    }
}
