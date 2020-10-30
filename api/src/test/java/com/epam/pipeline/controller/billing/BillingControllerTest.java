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

package com.epam.pipeline.controller.billing;

import com.epam.pipeline.acl.billing.BillingApiService;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.billing.BillingChartRequest;
import com.epam.pipeline.entity.billing.BillingChartInfo;
import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.test.web.AbstractControllerTest;
import com.fasterxml.jackson.core.type.TypeReference;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = BillingController.class)
public class BillingControllerTest extends AbstractControllerTest {

    private static final long COST = 7367L;
    private static final String BILLING_URL = SERVLET_PATH + "/billing";
    private static final String GET_BILLING_CHART_INFO_URL = BILLING_URL + "/charts";
    private static final String GET_BILLING_CHART_INFO_PAGINATED_URL = GET_BILLING_CHART_INFO_URL + "/pagination";
    private static final String GET_BILLING_CENTERS = BILLING_URL + "/centers";
    private static final String REQUEST_JSON = "{\"from\":\"-999999999-01-01\"," +
                                                "\"to\":\"+999999999-12-31\"," +
                                                "\"filters\":{\"test\":[\"test\"]}," +
                                                "\"interval\":\"1d\"," +
                                                "\"grouping\":\"BILLING_CENTER\"," +
                                                "\"loadDetails\":true," +
                                                "\"pageSize\":5," +
                                                "\"pageNum\":1}";
    private final BillingChartInfo billingChartInfo = BillingChartInfo.builder().cost(COST).build();
    private final BillingChartRequest billingChartRequest = new BillingChartRequest(
            LocalDate.MIN, LocalDate.MAX, Collections.singletonMap("test", Collections.singletonList("test")),
            DateHistogramInterval.DAY, BillingGrouping.BILLING_CENTER, true, 5L, 1L
    );
    private final List<BillingChartInfo> billingChartInfos = Collections.singletonList(billingChartInfo);

    @Autowired
    private BillingApiService mockBillingApiService;

    @Test
    public void shouldFailGetBillingChartInfoForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(post(GET_BILLING_CHART_INFO_URL));
    }

    @Test
    @WithMockUser
    public void shouldReturnBillingChartInfo() throws Exception {
        Mockito.doReturn(billingChartInfos).when(mockBillingApiService).getBillingChartInfo(billingChartRequest);

        final MvcResult mvcResult = performRequest(post(GET_BILLING_CHART_INFO_URL).content(REQUEST_JSON));

        Mockito.verify(mockBillingApiService).getBillingChartInfo(billingChartRequest);

        assertResponse(mvcResult, billingChartInfos, new TypeReference<Result<List<BillingChartInfo>>>() { });
    }

    @Test
    public void shouldFailGetBillingChartInfoPaginatedForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(post(GET_BILLING_CHART_INFO_PAGINATED_URL));
    }

    @Test
    @WithMockUser
    public void shouldReturnBillingChartInfoPaginated() throws Exception {
        Mockito.doReturn(billingChartInfos).when(mockBillingApiService)
                .getBillingChartInfoPaginated(billingChartRequest);

        final MvcResult mvcResult = performRequest(post(GET_BILLING_CHART_INFO_PAGINATED_URL).content(REQUEST_JSON));

        Mockito.verify(mockBillingApiService).getBillingChartInfoPaginated(billingChartRequest);

        assertResponse(mvcResult, billingChartInfos, new TypeReference<Result<List<BillingChartInfo>>>() { });
    }

    @Test
    public void shouldFailGetAllBillingCentersForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(GET_BILLING_CENTERS));
    }

    @Test
    @WithMockUser
    public void shouldGetAllBillingCenters() throws Exception {
        final List<String> centers = Collections.singletonList("testCenter");

        Mockito.doReturn(centers).when(mockBillingApiService).getAllBillingCenters();

        final MvcResult mvcResult = performRequest(get(GET_BILLING_CENTERS));

        Mockito.verify(mockBillingApiService).getAllBillingCenters();

        assertResponse(mvcResult, centers, new TypeReference<Result<List<String>>>() { });
    }
}
