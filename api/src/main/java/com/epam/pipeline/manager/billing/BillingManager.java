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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.billing.BillingChartRequest;
import com.epam.pipeline.entity.billing.BillingChartInfo;
import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.exception.search.SearchException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedDateHistogram;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.ParsedSimpleValue;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class BillingManager {

    private static final String COST_FIELD = "cost";
    private static final String ACCUMULATED_COST = "accumulatedCost";
    private static final String USAGE_FIELD = "usage";
    private static final String ID_FIELD = "id";
    private static final String UNIQUE_RUNS = "runs";
    private static final String BILLING_DATE_FIELD = "created_date";
    private static final String HISTOGRAM_AGGREGATION_NAME = "hist_agg";
    private static final String ES_MONTHLY_DATE_REGEXP = "%d-%02d-*";
    private static final String ES_BILLABLE_RESOURCE_WILDCARD = "*";

    private final PreferenceManager preferenceManager;
    private final AuthManager authManager;
    private final MessageHelper messageHelper;
    private final String billingIndicesMonthlyPattern;
    private final Map<DateHistogramInterval, TemporalAdjuster> periodAdjusters;
    private final List<DateHistogramInterval> validIntervals;
    private final SumAggregationBuilder costAggregation;
    private final SumAggregationBuilder usageAggregation;
    private final TermsAggregationBuilder idAggregation;
    private final Map<BillingGrouping, EntityBillingDetailsLoader> billingDetailsLoaders;
    @Value("${billing.empty.report.value:unknown}")
    private String emptyValue;

    @Autowired
    public BillingManager(final AuthManager authManager,
                          final PreferenceManager preferenceManager,
                          final MessageHelper messageHelper,
                          final @Value("${billing.index.common.prefix}") String commonPrefix,
                          final List<EntityBillingDetailsLoader> billingDetailsLoaders) {
        this.preferenceManager = preferenceManager;
        this.authManager = authManager;
        this.messageHelper = messageHelper;
        this.billingIndicesMonthlyPattern = String.join("-",
                                                        commonPrefix,
                                                        ES_BILLABLE_RESOURCE_WILDCARD,
                                                        ES_MONTHLY_DATE_REGEXP);
        this.periodAdjusters = new HashMap<DateHistogramInterval, TemporalAdjuster>() {{
                put(DateHistogramInterval.MONTH, TemporalAdjusters.lastDayOfMonth());
                put(DateHistogramInterval.YEAR, TemporalAdjusters.lastDayOfYear());
            }
        };
        this.validIntervals = Arrays.asList(DateHistogramInterval.DAY,
                                            DateHistogramInterval.MONTH,
                                            DateHistogramInterval.YEAR);
        this.costAggregation = AggregationBuilders.sum(COST_FIELD).field(COST_FIELD);
        this.usageAggregation = AggregationBuilders.sum(USAGE_FIELD).field(USAGE_FIELD);
        this.idAggregation = AggregationBuilders.terms(UNIQUE_RUNS).field(ID_FIELD);
        this.billingDetailsLoaders = billingDetailsLoaders.stream()
            .collect(Collectors.toMap(EntityBillingDetailsLoader::getGrouping,
                                      Function.identity()));
    }

    public List<BillingChartInfo> getBillingChartInfo(final BillingChartRequest request) {
        verifyRequest(request);
        final RestHighLevelClient elasticsearchClient = buildClient(preferenceManager);
        final LocalDate from = request.getFrom();
        final LocalDate to = request.getTo();
        final BillingGrouping grouping = request.getGrouping();
        final DateHistogramInterval interval = request.getInterval();
        final Map<String, List<String>> filters = MapUtils.emptyIfNull(request.getFilters());
        setAuthorizationFilters(filters);
        if (interval != null) {
            return getBillingStats(elasticsearchClient, from, to, filters, interval);
        } else {
            return getBillingStats(elasticsearchClient, from, to, filters, grouping, request.isLoadDetails(),
                                   request.getPageNum(), request.getPageSize());
        }
    }

    private void verifyRequest(final BillingChartRequest request) {
        final DateHistogramInterval interval = request.getInterval();
        final BillingGrouping grouping = request.getGrouping();
        if (interval != null
            && grouping != null) {
            throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_BILLING_FIELD_DATE_GROUPING_NOT_SUPPORTED));
        }
        final boolean shouldLoadDetails = request.isLoadDetails();
        if (shouldLoadDetails
            && grouping == null) {
            throw new IllegalArgumentException(messageHelper
                                                   .getMessage(MessageConstants.ERROR_BILLING_DETAILS_NOT_SUPPORTED));
        }
        final Long pageSize = request.getPageSize();
        final Long pageNum = request.getPageNum();
        if (pageNum != null && pageNum < 0
            || pageSize != null && pageSize <= 0) {
            throw new IllegalArgumentException(messageHelper
                                                   .getMessage(MessageConstants.ERROR_ILLEGAL_PAGING_PARAMETERS));
        }
    }

    private RestHighLevelClient buildClient(final PreferenceManager preferenceManager) {
        final String elasticHost = preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_HOST);
        final Integer elasticPort = preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_PORT);
        final String elasticScheme = preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_SCHEME);
        if (StringUtils.isBlank(elasticHost) || elasticPort == null ||  StringUtils.isBlank(elasticScheme)) {
            throw new IllegalArgumentException("Missing search engine configuration. Billing info is not available.");
        }
        final RestClient lowLevelClient =
                RestClient.builder(new HttpHost(elasticHost, elasticPort, elasticScheme))
                        .build();
        return new RestHighLevelClient(lowLevelClient);
    }

    private void setAuthorizationFilters(final Map<String, List<String>> filters) {
        final PipelineUser authorizedUser = authManager.getCurrentUser();
        if (authorizedUser.isAdmin()) {
            return;
        }
        filters.put("owner", Collections.singletonList(authorizedUser.getUserName()));
        filters.put("groups", authorizedUser.getGroups());
    }

    private List<BillingChartInfo> getBillingStats(final RestHighLevelClient elasticsearchClient,
                                                   final LocalDate from, final LocalDate to,
                                                   final Map<String, List<String>> filters,
                                                   final DateHistogramInterval interval) {
        if (!validIntervals.contains(interval)) {
            throw new IllegalArgumentException(messageHelper
                                                   .getMessage(MessageConstants.ERROR_BILLING_INTERVAL_NOT_SUPPORTED));
        }
        final SearchRequest searchRequest = new SearchRequest();
        final SearchSourceBuilder searchSource = new SearchSourceBuilder();

        final AggregationBuilder intervalAgg = AggregationBuilders.dateHistogram(
            HISTOGRAM_AGGREGATION_NAME)
            .field(BILLING_DATE_FIELD)
            .dateHistogramInterval(interval)
            .subAggregation(costAggregation)
            .subAggregation(PipelineAggregatorBuilders.cumulativeSum(ACCUMULATED_COST, COST_FIELD));

        searchSource.aggregation(intervalAgg);

        setFiltersAndPeriodForSearchRequest(from, to, filters, searchSource, searchRequest);

        try {
            final SearchResponse searchResponse = elasticsearchClient.search(searchRequest);
            final ParsedDateHistogram histogram = searchResponse.getAggregations().get(HISTOGRAM_AGGREGATION_NAME);
            return parseHistogram(interval, histogram);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new SearchException(e.getMessage(), e);
        }
    }

    private List<BillingChartInfo> getBillingStats(final RestHighLevelClient elasticsearchClient,
                                                   final LocalDate from, final LocalDate to,
                                                   final Map<String, List<String>> filters,
                                                   final BillingGrouping grouping,
                                                   final boolean isLoadDetails,
                                                   final Long pageNum,
                                                   final Long pageSize) {
        final SearchRequest searchRequest = new SearchRequest();
        final SearchSourceBuilder searchSource = new SearchSourceBuilder();
        if (grouping != null) {
            filters.forEach(
                (key, value) -> grouping.getRequiredDefaultFilters().merge(key, value, (l1, l2) -> {
                    l1.addAll(l2);
                    return l1;
                }));
            final AggregationBuilder fieldAgg = AggregationBuilders.terms(grouping.getCorrespondingField())
                .field(grouping.getCorrespondingField())
                .order(Terms.Order.aggregation(COST_FIELD, false))
                .size(Integer.MAX_VALUE);
            fieldAgg.subAggregation(costAggregation);
            if (grouping.usageDetailsRequired()) {
                fieldAgg.subAggregation(usageAggregation);
                fieldAgg.subAggregation(idAggregation);
            }
            searchSource.aggregation(fieldAgg);
        }
        searchSource.aggregation(costAggregation);

        setFiltersAndPeriodForSearchRequest(from, to, filters, searchSource, searchRequest);

        try {
            final SearchResponse searchResponse = elasticsearchClient.search(searchRequest);
            final List<BillingChartInfo> billingChartInfoForGrouping =
                getBillingChartInfoForGrouping(from, to, grouping, searchResponse, isLoadDetails);
            return finalizeResult(billingChartInfoForGrouping, grouping, pageNum, pageSize);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new SearchException(e.getMessage(), e);
        }
    }

    private List<BillingChartInfo> finalizeResult(final List<BillingChartInfo> fullResult,
                                                  final BillingGrouping grouping,
                                                  final Long pageNum,
                                                  final Long pageSize) {
        if (CollectionUtils.isNotEmpty(fullResult)) {
            if (pageSize == null) {
                return fullResult;
            } else {
                final Long requiredPageNum = pageNum == null
                                             ? 0
                                             : pageNum;
                final int from = (int) (requiredPageNum * pageSize);
                final int to = (int) (from + pageSize);
                final int resultSize = fullResult.size();
                if (from > resultSize) {
                    return getEmptyGroupingResponse(grouping);
                }
                return fullResult.subList(from, Math.min(to, resultSize));
            }
        } else {
            return getEmptyGroupingResponse(grouping);
        }
    }

    private List<BillingChartInfo> getEmptyGroupingResponse(final BillingGrouping grouping) {
        final Map<String, String> details = new HashMap<>();
        details.put(grouping.name(), emptyValue);
        if (grouping.usageDetailsRequired()) {
            details.put(USAGE_FIELD, emptyValue);
            details.put(UNIQUE_RUNS, emptyValue);
        }
        final BillingChartInfo emptyResponse = BillingChartInfo.builder()
            .groupingInfo(details)
            .build();
        return Collections.singletonList(emptyResponse);
    }

    private List<BillingChartInfo> getBillingChartInfoForGrouping(final LocalDate from, final LocalDate to,
                                                                  final BillingGrouping grouping,
                                                                  final SearchResponse searchResponse,
                                                                  final boolean isLoadDetails) {
        final Aggregations allAggregations = searchResponse.getAggregations();
        if (allAggregations == null) {
            return Collections.emptyList();
        }
        if (grouping != null) {
            final String groupingField = grouping.getCorrespondingField();
            final ParsedStringTerms terms = allAggregations.get(groupingField);
            return terms.getBuckets().stream().map(bucket -> {
                final Aggregations aggregations = bucket.getAggregations();
                return getCostAggregation(from, to,
                                          grouping,
                                          (String) bucket.getKey(),
                                          aggregations,
                                          isLoadDetails);
            })
                .collect(Collectors.toList());
        } else {
            return CollectionUtils.isEmpty(allAggregations.asList())
                   ? Collections.emptyList()
                   : Collections.singletonList(getCostAggregation(from, to, null, null, allAggregations, false));
        }
    }

    private BillingChartInfo getCostAggregation(final LocalDate from, final LocalDate to,
                                                final BillingGrouping grouping,
                                                final String groupValue,
                                                final Aggregations aggregations,
                                                final boolean loadDetails) {
        final ParsedSum sumAggResult = aggregations.get(COST_FIELD);
        final long costVal = new Double(sumAggResult.getValue()).longValue();
        final BillingChartInfo.BillingChartInfoBuilder builder = BillingChartInfo.builder()
            .periodStart(from.atStartOfDay())
            .periodEnd(to.atTime(LocalTime.MAX))
            .cost(costVal);
        final Map<String, String> groupingInfo = new HashMap<>();
        final EntityBillingDetailsLoader detailsLoader = billingDetailsLoaders.get(grouping);
        if (grouping != null) {
            if (detailsLoader == null) {
                groupingInfo.put(grouping.toString(), groupValue);
            } else {
                groupingInfo.put(grouping.name(), detailsLoader.loadName(groupValue));
            }
        }
        if (loadDetails) {
            if (grouping.usageDetailsRequired()) {
                final ParsedSum usageAggResult = aggregations.get(USAGE_FIELD);
                final long usageVal = new Double(usageAggResult.getValue()).longValue();
                groupingInfo.put(USAGE_FIELD, Long.toString(usageVal));
                final ParsedStringTerms ids = aggregations.get(UNIQUE_RUNS);
                groupingInfo.put(UNIQUE_RUNS, Integer.toString(ids.getBuckets().size()));
            }
            if (detailsLoader != null) {
                try {
                    groupingInfo.putAll(detailsLoader.loadDetails(groupValue));
                } catch (RuntimeException e) {
                    log.info(messageHelper.getMessage(MessageConstants.INFO_BILLING_ENTITY_FOR_DETAILS_NOT_FOUND,
                                                      groupValue, grouping));
                    groupingInfo.putAll(detailsLoader.getEmptyDetails());
                }
            }
        }
        builder.groupingInfo(groupingInfo);
        return builder.build();
    }

    private void setFiltersAndPeriodForSearchRequest(final LocalDate from, final LocalDate to,
                                                     final Map<String, List<String>> filters,
                                                     final SearchSourceBuilder searchSource,
                                                     final SearchRequest searchRequest) {
        searchRequest.indicesOptions(IndicesOptions.strictExpandOpen());
        final BoolQueryBuilder compoundQuery = QueryBuilders.boolQuery();
        if (MapUtils.isNotEmpty(filters)) {
            filters.forEach((k, v) -> compoundQuery.filter(QueryBuilders.termsQuery(k, v)));
        }
        compoundQuery.filter(QueryBuilders.rangeQuery(BILLING_DATE_FIELD).from(from, true).to(to, true));
        final String[] indices = Stream.iterate(from, d -> d.plus(1, ChronoUnit.MONTHS))
            .limit(ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1)
            .map(date -> String.format(billingIndicesMonthlyPattern, date.getYear(), date.getMonthValue()))
            .toArray(String[]::new);
        searchRequest.indices(indices);
        searchSource.query(compoundQuery);
        searchRequest.source(searchSource);
    }

    private List<BillingChartInfo> parseHistogram(final DateHistogramInterval interval,
                                                  final ParsedDateHistogram histogram) {
        return histogram.getBuckets().stream()
            .map(bucket -> getChartInfo(bucket, interval))
            .collect(Collectors.toList());
    }

    private BillingChartInfo getChartInfo(final Histogram.Bucket bucket, final DateHistogramInterval interval) {
        final BillingChartInfo.BillingChartInfoBuilder builder = BillingChartInfo.builder()
            .groupingInfo(null);
        final ParsedSum sumAggResult = bucket.getAggregations().get(COST_FIELD);
        final long costVal = new Double(sumAggResult.getValue()).longValue();
        builder.cost(costVal);
        final ParsedSimpleValue accumulatedSumAggResult = bucket.getAggregations().get(ACCUMULATED_COST);
        final long accumulatedCostVal = new Double(accumulatedSumAggResult.getValueAsString()).longValue();
        builder.accumulatedCost(accumulatedCostVal);
        final DateTime date = (DateTime) bucket.getKey();
        final LocalDate periodStart = LocalDate.of(date.getYear(), date.getMonthOfYear(), date.getDayOfMonth());
        builder.periodStart(periodStart.atStartOfDay());
        final TemporalAdjuster adjuster = periodAdjusters.get(interval);
        if (adjuster != null) {
            builder.periodEnd(periodStart.with(adjuster).atTime(LocalTime.MAX));
        } else {
            builder.periodEnd(periodStart.atTime(LocalTime.MAX));
        }
        return builder.build();
    }
}
