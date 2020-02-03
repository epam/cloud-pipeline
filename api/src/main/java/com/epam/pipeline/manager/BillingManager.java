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

package com.epam.pipeline.manager;

import com.epam.pipeline.controller.vo.billing.BillingChartRequest;
import com.epam.pipeline.entity.billing.BillingChartInfo;
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
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
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
public class BillingManager {

    private static final String COST_FIELD = "cost";
    private static final String BILLING_DATE_FIELD = "created_date";
    private static final String HISTOGRAM_AGGREGATION_NAME = "hist_agg";
    private static final String ES_MONTHLY_DATE_REGEXP = "%d-%02d-*";
    private static final String ES_BILLABLE_RESOURCE_WILDCARD = "*";
    private static final LocalDate FIRST_DAY_OF_BILLING = LocalDate.of(2019, 4, 19);

    private final AuthManager authManager;
    private final RestHighLevelClient elasticsearchClient;
    private final String billingIndicesMonthlyPattern;
    private final Map<DateHistogramInterval, TemporalAdjuster> periodAdjusters;
    private final Map<DateHistogramInterval, ChronoUnit> previousPeriodShifts;
    private final List<DateHistogramInterval> validIntervals;
    private final List<DateHistogramInterval> validPeriodShifts;
    private final SumAggregationBuilder costAggregation;

    @Autowired
    public BillingManager(final AuthManager authManager,
                          final PreferenceManager preferenceManager,
                          final @Value("${billing.index.common.prefix}") String commonPrefix) {
        final RestClient lowLevelClient =
            RestClient.builder(new HttpHost(preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_HOST),
                                            preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_PORT),
                                            preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_SCHEME)))
                .build();
        this.elasticsearchClient = new RestHighLevelClient(lowLevelClient);
        this.authManager = authManager;
        this.billingIndicesMonthlyPattern = String.join("-",
                                                        commonPrefix,
                                                        ES_BILLABLE_RESOURCE_WILDCARD,
                                                        ES_MONTHLY_DATE_REGEXP);
        this.periodAdjusters = new HashMap<DateHistogramInterval, TemporalAdjuster>() {{
                put(DateHistogramInterval.MONTH, TemporalAdjusters.lastDayOfMonth());
                put(DateHistogramInterval.YEAR, TemporalAdjusters.lastDayOfYear());
            }
        };
        this.previousPeriodShifts = new HashMap<DateHistogramInterval, ChronoUnit>() {{
                put(DateHistogramInterval.MONTH, ChronoUnit.MONTHS);
                put(DateHistogramInterval.QUARTER, ChronoUnit.YEARS);
                put(DateHistogramInterval.YEAR, ChronoUnit.YEARS);
            }
        };
        this.validIntervals = Arrays.asList(DateHistogramInterval.DAY,
                                            DateHistogramInterval.MONTH,
                                            DateHistogramInterval.YEAR);
        this.validPeriodShifts = Arrays.asList(DateHistogramInterval.MONTH,
                                               DateHistogramInterval.QUARTER,
                                               DateHistogramInterval.YEAR);
        this.costAggregation = AggregationBuilders.sum(COST_FIELD).field(COST_FIELD);
    }

    public List<BillingChartInfo> getBillingChartInfo(final BillingChartRequest request) {
        final BillingChartRequest adjustedRequest = verifyRequest(request);
        final LocalDate from = adjustedRequest.getFrom();
        final LocalDate to = adjustedRequest.getTo();
        final List<String> grouping = adjustedRequest.getGrouping();
        final DateHistogramInterval interval = adjustedRequest.getInterval();
        final DateHistogramInterval prevPeriodShift = adjustedRequest.getPrevPeriodShift();
        final Map<String, List<String>> filters = adjustedRequest.getFilters();
        setAuthorizationFilters(filters);
        final List<BillingChartInfo> currentPeriodStats = getPeriodStats(from, to, grouping, interval, filters);
        if (prevPeriodShift != null) {
            final ChronoUnit minusPeriod = previousPeriodShifts.get(prevPeriodShift);
            final LocalDate prevFrom = from.minus(1, minusPeriod);
            final LocalDate prevTo = to.minus(1, minusPeriod);
            final List<BillingChartInfo> prevPeriodStats =
                getPeriodStats(prevFrom, prevTo, grouping, interval, filters);
            currentPeriodStats.forEach(currentPeriodStat ->
                prevPeriodStats.stream()
                    .filter(prevPeriodStat -> prevPeriodStat.getPeriodStart().plus(1, minusPeriod)
                        .equals(currentPeriodStat.getPeriodStart()))
                    .findAny()
                    .ifPresent(matchingPrevStat -> setPreviousPeriodInfo(currentPeriodStat, matchingPrevStat))
            );
        }
        return currentPeriodStats;
    }

    private List<BillingChartInfo> getPeriodStats(final LocalDate from, final LocalDate to,
                                                  final List<String> grouping,
                                                  final DateHistogramInterval interval,
                                                  final Map<String, List<String>> filters) {
        return interval != null
               ? getBillingStats(from, to, filters, interval)
               : getBillingStats(from, to, filters, grouping);
    }

    private BillingChartRequest verifyRequest(final BillingChartRequest request) {
        final LocalDate to = (request.getTo() == null)
                             ? LocalDate.now()
                             : request.getTo();
        final DateHistogramInterval prevPeriodShift = request.getPrevPeriodShift();
        final LocalDate from = (request.getFrom() == null)
                               ? calculatePeriodStart(to, prevPeriodShift)
                               : request.getFrom();
        final List<String> grouping = request.getGrouping();
        final DateHistogramInterval interval = request.getInterval();
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Requested period is not correct!");
        }
        if (interval != null) {
            if (!validIntervals.contains(interval)) {
                throw new IllegalArgumentException("Given interval is not supported!");
            }
            if (CollectionUtils.isNotEmpty(grouping)) {
                throw new UnsupportedOperationException("Currently field and date grouping at"
                                                        + " the same time isn't supporting!");
            }
        }
        if (prevPeriodShift != null) {
            if (!validPeriodShifts.contains(prevPeriodShift)) {
                throw new IllegalArgumentException("Given period shift is not supported!");
            } else {
                final long requestedPeriodDays = ChronoUnit.DAYS.between(from, to);
                final long prevPeriodShiftDays = previousPeriodShifts.get(prevPeriodShift).getDuration().toDays();
                if (requestedPeriodDays > prevPeriodShiftDays) {
                    throw new IllegalArgumentException("Requested period clashes with the previous one!");
                }
                return new BillingChartRequest(from, to, request.getFilters(), interval, prevPeriodShift, grouping);
            }
        }
        return new BillingChartRequest(from, to, request.getFilters(), interval, prevPeriodShift, grouping);
    }

    private LocalDate calculatePeriodStart(final LocalDate periodEnd, final DateHistogramInterval prevPeriodShift) {
        final String period = (prevPeriodShift == null)
                              ? StringUtils.EMPTY
                              : prevPeriodShift.toString();
        switch (period) {
            case "1M":
                return periodEnd.with(periodEnd.withDayOfMonth(1));
            case "1q":
                return periodEnd.with(periodEnd.getMonth().firstMonthOfQuarter())
                    .with(TemporalAdjusters.firstDayOfMonth());
            case "1y":
                return periodEnd.with(periodEnd.withDayOfYear(1));
            default:
                return FIRST_DAY_OF_BILLING;
        }
    }

    private void setPreviousPeriodInfo(final BillingChartInfo current, final BillingChartInfo prev) {
        current.setPreviousCost(prev.getCost());
        current.setPreviousStart(prev.getPeriodStart());
        current.setPreviousEnd(prev.getPeriodEnd());
    }

    private void setAuthorizationFilters(final Map<String, List<String>> filters) {
        final PipelineUser authorizedUser = authManager.getCurrentUser();
        if (authorizedUser.isAdmin()) {
            return;
        }
        filters.put("owner", Collections.singletonList(authorizedUser.getUserName()));
        filters.put("groups", authorizedUser.getGroups());
    }

    private List<BillingChartInfo> getBillingStats(final LocalDate from, final LocalDate to,
                                                   final Map<String, List<String>> filters,
                                                   final DateHistogramInterval interval) {
        final SearchRequest searchRequest = new SearchRequest();
        final SearchSourceBuilder searchSource = new SearchSourceBuilder();

        final AggregationBuilder intervalAgg = AggregationBuilders.dateHistogram(
            HISTOGRAM_AGGREGATION_NAME)
            .field(BILLING_DATE_FIELD)
            .dateHistogramInterval(interval)
            .subAggregation(costAggregation);

        searchSource.aggregation(intervalAgg);

        setFiltersAndPeriodForSearchRequest(from, to, filters, searchSource, searchRequest);

        try {
            final SearchResponse searchResponse = elasticsearchClient.search(searchRequest);
            final Aggregations aggregations = searchResponse.getAggregations();
            if (aggregations == null) {
                return Collections.emptyList();
            }
            final ParsedDateHistogram histogram = aggregations.get(HISTOGRAM_AGGREGATION_NAME);
            return parseHistogram(interval, histogram);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new SearchException(e.getMessage(), e);
        }
    }

    private List<BillingChartInfo> getBillingStats(final LocalDate from, final LocalDate to,
                                                   final Map<String, List<String>> filters,
                                                   final List<String> grouping) {
        final SearchRequest searchRequest = new SearchRequest();
        final SearchSourceBuilder searchSource = new SearchSourceBuilder();
        if (CollectionUtils.isNotEmpty(grouping)) {
            grouping.forEach(field -> {
                final AggregationBuilder fieldAgg = AggregationBuilders.terms(field)
                    .field(field).subAggregation(costAggregation);
                searchSource.aggregation(fieldAgg);
            });
        }
        searchSource.aggregation(costAggregation);
        setFiltersAndPeriodForSearchRequest(from, to, filters, searchSource, searchRequest);

        try {
            final SearchResponse searchResponse = elasticsearchClient.search(searchRequest);
            return getBillingChartInfoForGrouping(from, to, grouping, searchResponse);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new SearchException(e.getMessage(), e);
        }
    }

    private List<BillingChartInfo> getBillingChartInfoForGrouping(final LocalDate from, final LocalDate to,
                                                                  final List<String> grouping,
                                                                  final SearchResponse searchResponse) {
        final Aggregations allAggregations = searchResponse.getAggregations();
        if (CollectionUtils.isNotEmpty(grouping)) {
            return grouping.stream()
                .map(field -> {
                    final ParsedStringTerms terms = allAggregations.get(field);
                    return terms.getBuckets().stream().map(bucket -> {
                        final Aggregations aggregations = bucket.getAggregations();
                        return getCostAggregation(from, to, field, (String) bucket.getKey(), aggregations);
                    });
                })
                .flatMap(Function.identity())
                .collect(Collectors.toList());
        } else {
            return CollectionUtils.isEmpty(allAggregations.asList())
                   ? Collections.emptyList()
                   : Collections.singletonList(getCostAggregation(from, to, null, null, allAggregations));
        }
    }

    private BillingChartInfo getCostAggregation(final LocalDate from, final LocalDate to,
                                                final String groupField,
                                                final String groupValue,
                                                final Aggregations aggregations) {
        final ParsedSum sumAggResult = aggregations.get(COST_FIELD);
        final long costVal = new Double(sumAggResult.getValue()).longValue();
        final BillingChartInfo.BillingChartInfoBuilder builder = BillingChartInfo.builder()
            .periodStart(from.atStartOfDay())
            .periodEnd(to.atTime(LocalTime.MAX))
            .cost(costVal);
        if (groupField != null) {
            builder.groupingInfo(Collections.singletonMap(groupField, groupValue));
        }
        return builder.build();
    }

    private void setFiltersAndPeriodForSearchRequest(final LocalDate from, final LocalDate to,
                                                     final Map<String, List<String>> filters,
                                                     final SearchSourceBuilder searchSource,
                                                     final SearchRequest searchRequest) {
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
