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
    public static final int NUMBER_OF_TERM_AGGREGATIONS_FOR_COST_DETAILS = 1;

    @Test
    public void checkThatBuildQueryWillBuildAggsIfCriteriaMatchByGrouping() {
        assertAggregations(
            agg -> BillingChartCostDetailsLoader.buildQuery(
                    BillingCostDetailsRequest.builder().enabled(true)
                            .grouping(BillingGrouping.STORAGE).build(), agg),
            true
        );

        assertAggregations(
            agg -> BillingChartCostDetailsLoader.buildQuery(
            BillingCostDetailsRequest.builder().enabled(true)
                    .grouping(BillingGrouping.STORAGE_TYPE).build(), agg),
            true
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
            true
        );
    }

    @Test
    public void checkThatBuildQueryWillNotBuildAggsIfDisabledMatch() {
        assertAggregations(
            agg -> BillingChartCostDetailsLoader.buildQuery(
                    BillingCostDetailsRequest.builder().enabled(false).build(), agg),
            false
        );
    }

    @Test
    public void checkThatBuildQueryWillNotBuildAggsIfCriteriaDontMatch() {
        assertAggregations(
            agg -> BillingChartCostDetailsLoader.buildQuery(
                    BillingCostDetailsRequest.builder().enabled(true)
                            .grouping(BillingGrouping.TOOL).build(), agg),
            false
        );

        final HashMap<String, List<String>> filters = new HashMap<String, List<String>>() {{
                put("resource_type", Arrays.asList("STORAGE", "COMPUTE"));
                put("storage_type", Collections.singletonList("OBJECT_STORAGE"));
            }};
        assertAggregations(
            agg -> BillingChartCostDetailsLoader.buildQuery(
                    BillingCostDetailsRequest.builder().enabled(true).filters(filters).build(), agg),
            false
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

    private static void assertAggregations(Consumer<AggregationBuilder> aggs, boolean shouldAccept) {
        TermsAggregationBuilder topLevelAgg = Mockito.mock(TermsAggregationBuilder.class);
        aggs.accept(topLevelAgg);
        if (shouldAccept) {
            Mockito.verify(topLevelAgg, Mockito.times(NUMBER_OF_TERM_AGGREGATIONS_FOR_COST_DETAILS))
                    .subAggregation(Mockito.any(TermsAggregationBuilder.class));
            Mockito.verify(
                    topLevelAgg,
                    Mockito.times(4 * StorageBillingCostDetailsHelper.S3_STORAGE_CLASSES.size())
            ).subAggregation(Mockito.any(PipelineAggregationBuilder.class));
        } else {
            Mockito.verify(topLevelAgg, Mockito.times(ZERO))
                    .subAggregation(Mockito.any(TermsAggregationBuilder.class));
            Mockito.verify(topLevelAgg, Mockito.times(ZERO))
                    .subAggregation(Mockito.any(PipelineAggregationBuilder.class));
        }
    }
}
