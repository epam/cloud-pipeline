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

package com.epam.pipeline.acl.billing;

import com.epam.pipeline.controller.vo.billing.BillingChartRequest;
import com.epam.pipeline.entity.billing.BillingChartInfo;
import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.manager.billing.BillingManager;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class BillingApiServiceTest extends AbstractAclTest {

    @Autowired
    private BillingApiService billingApiService;

    @Autowired
    private BillingManager mockBillingManager;

    private final BillingChartInfo billingChartInfo = BillingChartInfo.builder().build();

    private List<BillingChartInfo> billingChartInfos;

    private List<String> billingCenters;

    private BillingChartRequest billingChartRequest;

    @Before
    public void setUp() {
        billingChartInfos = Collections.singletonList(billingChartInfo);

        billingCenters = Collections.singletonList("billing center");

        billingChartRequest = new BillingChartRequest(
                LocalDate.MIN,
                LocalDate.MAX,
                Collections.singletonMap("filter", Collections.singletonList("filter")),
                DateHistogramInterval.DAY,
                BillingGrouping.BILLING_CENTER,
                true,
                1L,
                1L
        );
    }

    @Test
    public void shouldReturnBillingChartInfoList() {
        when(mockBillingManager.getBillingChartInfo(billingChartRequest)).thenReturn(billingChartInfos);

        assertThat(billingApiService.getBillingChartInfo(billingChartRequest)).isEqualTo(billingChartInfos);
    }

    @Test
    public void shouldReturnPaginatedBillingChartInfoList() {
        when(mockBillingManager.getBillingChartInfoPaginated(billingChartRequest)).thenReturn(billingChartInfos);

        assertThat(billingApiService.getBillingChartInfoPaginated(billingChartRequest)).isEqualTo(billingChartInfos);
    }

    @Test
    public void shouldReturnAllBillingCenters() {
        when(mockBillingManager.getAllBillingCenters()).thenReturn(billingCenters);

        assertThat(billingApiService.getAllBillingCenters()).isEqualTo(billingCenters);
    }
}
