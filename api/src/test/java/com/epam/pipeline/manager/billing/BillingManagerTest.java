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

package com.epam.pipeline.manager.billing;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.billing.BillingChartRequest;
import com.epam.pipeline.entity.billing.BillingChartInfo;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.utils.GlobalSearchElasticHelper;
import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedDateHistogram;
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.elasticsearch.search.aggregations.pipeline.ParsedSimpleValue;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.Whitebox;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BillingManagerTest extends AbstractManagerTest {

    private static final String COMMON_PREFIX = "cp-billing";
    private static final String EMPTY_VALUE = "unknown";
    private static final String BILLING_CENTER_KEY = "billing-center";
    private static final String USER_NAME = "admin";
    private static final String COST_AGG = "cost";
    private static final String ACCUMULATED_COST_AGG = "accumulatedCost";

    @Mock
    private AuthManager authManager;
    @Mock
    private MessageHelper messageHelper;
    @Mock
    private MetadataManager metadataManager;
    @Mock
    private GlobalSearchElasticHelper globalSearchElasticHelper;
    private RestClient restClient = mock(RestClient.class);
    private List<EntityBillingDetailsLoader> billingDetailsLoaders = Collections.emptyList();
    private BillingManager manager;

    @Before
    public void setup() {
        doReturn(StringUtils.EMPTY).when(messageHelper).getMessage(anyString());
        doReturn(restClient).when(globalSearchElasticHelper).buildLowLevelClient();
        doReturn(PipelineUser.builder()
                     .userName(USER_NAME)
                     .admin(true)
                     .groups(Collections.emptyList())
                     .build())
            .when(authManager).getCurrentUser();
        manager = spy(new BillingManager(authManager,
                                         messageHelper,
                                         metadataManager,
                                         globalSearchElasticHelper,
                                         COMMON_PREFIX,
                                         EMPTY_VALUE,
                                         BILLING_CENTER_KEY,
                                         billingDetailsLoaders));
    }

    @Test
    public void testBillingChartInfo() throws IOException {
        final LocalDate now = LocalDate.now();
        final int daysCount = 3;
        final BillingChartRequest request = createBillingChartRequest(now, 10L, daysCount);
        final List<BillingChartInfo> chartInfo = manager.getBillingChartInfo(request);
        Assert.assertEquals(daysCount, chartInfo.size());
    }

    @Test
    public void testBillingChartInfoRequestInactivePeriod() throws IOException {
        final LocalDate now = LocalDate.now();
        final BillingChartRequest request = createBillingChartRequest(now, 0L, 3);
        final List<BillingChartInfo> chartInfo = manager.getBillingChartInfo(request);
        Assert.assertEquals(0, chartInfo.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBillingChartInfoIncorrectIntervalOrder() {
        final BillingChartRequest request = BillingChartRequest.builder()
            .from(LocalDate.MAX)
            .to(LocalDate.MIN)
            .build();
        manager.getBillingChartInfo(request);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBillingChartOncomingPeriodDates() {
        final LocalDate now = LocalDate.now();
        final BillingChartRequest request = BillingChartRequest.builder()
            .from(now.plusMonths(1))
            .to(now.plusMonths(2))
            .build();
        manager.getBillingChartInfo(request);
    }

    private BillingChartRequest createBillingChartRequest(final LocalDate now, final long dailyCost,
                                                          final int days) throws IOException {
        final BillingChartRequest request = BillingChartRequest.builder()
            .from(now.minusDays(days))
            .to(now.minusDays(1))
            .interval(DateHistogramInterval.DAY)
            .build();
        mockBuckets(now, days, dailyCost);
        return request;
    }

    private void mockBuckets(final LocalDate now, final int days, final long dailyCost) throws IOException {
        final List<HistBucket> parsedBuckets = IntStream.range(1, days + 1)
            .mapToObj(i -> HistBucket.builder()
                .date(now.minusDays(i))
                .cost(dailyCost)
                .accumulatedCost((i) * dailyCost)
                .build())
            .collect(Collectors.toList());
        mockBuckets(parsedBuckets);
    }

    private void mockBuckets(final List<HistBucket> buckets) throws IOException {
        final ParsedDateHistogram parsedDateHistogram = mock(ParsedDateHistogram.class);
        final List<ParsedDateHistogram.ParsedBucket> parsedBuckets = buckets.stream()
            .map(this::createParsedBucket)
            .collect(Collectors.toList());
        doReturn(parsedBuckets).when(parsedDateHistogram).getBuckets();
        doReturn(Optional.of(parsedDateHistogram)).when(manager).getBillingHistogram(any(), any());
    }

    private ParsedDateHistogram.ParsedBucket createParsedBucket(final HistBucket bucket) {
        final ParsedDateHistogram.ParsedBucket bucketMock = mock(ParsedDateHistogram.ParsedBucket.class);
        final DateTime dateTime = new DateTime(bucket.getDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                                               DateTimeZone.UTC);
        doReturn(dateTime).when(bucketMock).getKey();

        final ParsedSum cost = mock(ParsedSum.class);
        doReturn(bucket.getCost().doubleValue()).when(cost).getValue();
        final ParsedSimpleValue accumulatedCost = mock(ParsedSimpleValue.class);
        doReturn(String.valueOf(bucket.getAccumulatedCost())).when(accumulatedCost).getValueAsString();
        final Map<String, Aggregation> aggregationsMap = new HashMap<>();
        aggregationsMap.put(COST_AGG, cost);
        aggregationsMap.put(ACCUMULATED_COST_AGG, accumulatedCost);

        final Aggregations aggregations = mock(Aggregations.class);
        Whitebox.setInternalState(aggregations, "aggregationsAsMap", aggregationsMap);
        Whitebox.setInternalState(aggregations, "aggregations", new ArrayList<>(aggregationsMap.values()));
        doReturn(aggregations).when(bucketMock).getAggregations();
        return bucketMock;
    }

    @Getter
    @Builder
    private static class HistBucket {

        private LocalDate date;
        private Long cost;
        private Long accumulatedCost;
    }
}
