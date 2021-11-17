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
import com.epam.pipeline.controller.ResultWriter;
import com.epam.pipeline.controller.vo.billing.BillingChartRequest;
import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
import com.epam.pipeline.controller.vo.billing.BillingExportType;
import com.epam.pipeline.entity.billing.BillingChartInfo;
import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.exception.search.SearchException;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.utils.GlobalSearchElasticHelper;
import com.epam.pipeline.utils.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ContextParser;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.rest.action.search.RestSearchAction;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedDateHistogram;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedTerms;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.tophits.ParsedTopHits;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHitsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.valuecount.ParsedValueCount;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCountAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.InternalSimpleValue;
import org.elasticsearch.search.aggregations.pipeline.ParsedSimpleValue;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class BillingManager {

    private static final BillingExportType FALLBACK_BILLING_EXPORT_TYPE = BillingExportType.RUN;

    private final Map<DateHistogramInterval, TemporalAdjuster> periodAdjusters;
    private final List<DateHistogramInterval> validIntervals;
    private final Map<BillingGrouping, EntityBillingDetailsLoader> billingDetailsLoaders;

    private final BillingHelper billingHelper;
    private final Map<BillingExportType, BillingExporter> billingExporters;
    private final MessageHelper messageHelper;
    private final MetadataManager metadataManager;
    private final GlobalSearchElasticHelper elasticHelper;
    private final String emptyValue;
    private final String billingCenterKey;
    private final List<NamedXContentRegistry.Entry> requiredGroupingAggregationsEntries;

    @Autowired
    public BillingManager(final BillingHelper billingHelper,
                          final List<BillingExporter> billingExporters,
                          final MessageHelper messageHelper,
                          final MetadataManager metadataManager,
                          final GlobalSearchElasticHelper globalSearchElasticHelper,
                          final @Value("${billing.empty.report.value:unknown}") String emptyValue,
                          final @Value("${billing.center.key}") String billingCenterKey,
                          final List<EntityBillingDetailsLoader> billingDetailsLoaders) {
        this.billingHelper = billingHelper;
        this.billingExporters = CommonUtils.groupByKey(billingExporters, BillingExporter::getType);
        this.messageHelper = messageHelper;
        this.metadataManager = metadataManager;
        this.elasticHelper = globalSearchElasticHelper;
        this.emptyValue = emptyValue;
        this.billingCenterKey = billingCenterKey;
        this.periodAdjusters = new HashMap<DateHistogramInterval, TemporalAdjuster>() {{
                put(DateHistogramInterval.MONTH, TemporalAdjusters.lastDayOfMonth());
                put(DateHistogramInterval.YEAR, TemporalAdjusters.lastDayOfYear());
            }
        };
        this.validIntervals = Arrays.asList(DateHistogramInterval.DAY,
                                            DateHistogramInterval.MONTH,
                                            DateHistogramInterval.YEAR);
        this.billingDetailsLoaders = CommonUtils.groupByKey(billingDetailsLoaders,
                EntityBillingDetailsLoader::getGrouping);
        this.requiredGroupingAggregationsEntries = getRequiredGroupingResponseAggParsers().entrySet().stream()
            .map(entry -> new NamedXContentRegistry.Entry(Aggregation.class,
                                                          new ParseField(entry.getKey()), entry.getValue()))
            .collect(Collectors.toList());
    }

    public List<BillingChartInfo> getBillingChartInfo(final BillingChartRequest request) {
        verifyRequest(request);
        try (RestClient lowLevelEsClient = elasticHelper.buildLowLevelClient()) {
            final RestHighLevelClient elasticsearchClient = new RestHighLevelClient(lowLevelEsClient);
            final LocalDate from = request.getFrom();
            final LocalDate to = request.getTo();
            final BillingGrouping grouping = request.getGrouping();
            final DateHistogramInterval interval = request.getInterval();
            final Map<String, List<String>> filters = billingHelper.getFilters(request.getFilters());
            if (interval != null) {
                return getBillingStats(elasticsearchClient, from, to, filters, interval);
            } else {
                return getBillingStats(lowLevelEsClient, from, to, filters, grouping, request.isLoadDetails());
            }
        } catch (IOException e) {
            throw new SearchException(e.getMessage(), e);
        }
    }

    public List<BillingChartInfo> getBillingChartInfoPaginated(final BillingChartRequest request) {
        verifyPagingParameters(request);
        return paginateResult(getBillingChartInfo(request),
                              request.getGrouping(),
                              request.getPageNum(),
                              request.getPageSize());
    }

    public ResultWriter export(final BillingExportRequest request) {
        final BillingExportType exportType = getBillingExportType(request);
        final BillingExporter exporter = billingExporters.get(exportType);
        Assert.notNull(exporter, messageHelper.getMessage(MessageConstants.ERROR_BILLING_EXPORT_TYPE_NOT_SUPPORTED,
                exportType));
        return ResultWriter.unchecked("billing." + exportType.name().toLowerCase() + ".csv",
                out -> exporter.export(request, out));
    }

    private BillingExportType getBillingExportType(final BillingExportRequest request) {
        return Optional.ofNullable(request)
                .map(BillingExportRequest::getType)
                .orElse(FALLBACK_BILLING_EXPORT_TYPE);
    }

    public List<String> getAllBillingCenters() {
        return metadataManager.loadUniqueValuesFromEntityClassMetadata(AclClass.PIPELINE_USER, billingCenterKey);
    }

    private void verifyPagingParameters(final BillingChartRequest request) {
        final Long pageSize = request.getPageSize();
        final Long pageNum = request.getPageNum();
        if (pageNum != null && pageNum < 0
            || pageSize != null && pageSize <= 0) {
            throw new IllegalArgumentException(messageHelper
                                                   .getMessage(MessageConstants.ERROR_ILLEGAL_PAGING_PARAMETERS));
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
                BillingHelper.HISTOGRAM_AGGREGATION_NAME)
            .field(BillingHelper.BILLING_DATE_FIELD)
            .dateHistogramInterval(interval)
            .subAggregation(billingHelper.getCostAggregation())
            .subAggregation(PipelineAggregatorBuilders.cumulativeSum(BillingHelper.ACCUMULATED_COST,
                    BillingHelper.COST_FIELD));

        searchSource.aggregation(intervalAgg);

        billingHelper.setFiltersAndPeriodForSearchRequest(from, to, filters, searchSource, searchRequest);

        try {
            final SearchResponse searchResponse = elasticsearchClient.search(searchRequest);
            return Optional.ofNullable(searchResponse.getAggregations())
                .map(aggs -> aggs.get(BillingHelper.HISTOGRAM_AGGREGATION_NAME))
                .map(ParsedDateHistogram.class::cast)
                .map(histogram -> parseHistogram(interval, histogram))
                .orElse(Collections.emptyList());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new SearchException(e.getMessage(), e);
        }
    }

    private List<BillingChartInfo> getBillingStats(final RestClient elasticsearchLowLevelClient,
                                                   final LocalDate from, final LocalDate to,
                                                   final Map<String, List<String>> filters,
                                                   final BillingGrouping grouping,
                                                   final boolean isLoadDetails) {
        final SearchRequest searchRequest = new SearchRequest();
        final SearchSourceBuilder searchSource = new SearchSourceBuilder();
        if (grouping != null) {
            final AggregationBuilder fieldAgg = AggregationBuilders.terms(grouping.getCorrespondingField())
                .field(grouping.getCorrespondingField())
                .order(Terms.Order.aggregation(BillingHelper.COST_FIELD, false))
                .size(Integer.MAX_VALUE);
            fieldAgg.subAggregation(billingHelper.getCostAggregation());
            if (grouping.runUsageDetailsRequired()) {
                fieldAgg.subAggregation(billingHelper.getRunUsageAggregation());
                fieldAgg.subAggregation(billingHelper.getUniqueRunsAggregation());
            }
            if (grouping.storageUsageDetailsRequired()) {
                fieldAgg.subAggregation(billingHelper.getStorageUsageGroupingAggregation());
                fieldAgg.subAggregation(billingHelper.getStorageUsageTotalAggregation());
                if (BillingGrouping.STORAGE.equals(grouping)) {
                    fieldAgg.subAggregation(billingHelper.getLastByDateDocAggregation());
                }
            }
            searchSource.aggregation(fieldAgg);
        }
        searchSource.aggregation(billingHelper.getCostAggregation());

        billingHelper.setFiltersAndPeriodForSearchRequest(from, to, filters, searchSource, searchRequest);

        try {
            final SearchResponse searchResponse =
                searchForGrouping(elasticsearchLowLevelClient, searchRequest, grouping.getCorrespondingField());
            final List<BillingChartInfo> billingChartInfoForGrouping =
                getBillingChartInfoForGrouping(from, to, grouping, searchResponse, isLoadDetails);
            return CollectionUtils.isEmpty(billingChartInfoForGrouping)
                   ? getEmptyGroupingResponse(grouping)
                   : billingChartInfoForGrouping;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new SearchException(e.getMessage(), e);
        }
    }

    private SearchResponse searchForGrouping(final RestClient lowLevelClient, final SearchRequest request,
                                             final String groupingName) throws IOException {
        final String searchEndpoint = String.format(BillingHelper.ES_INDICES_SEARCH_PATTERN,
                String.join(BillingHelper.ES_ELEMENTS_SEPARATOR, request.indices()));
        final HttpEntity httpEntity = new NStringEntity(request.source().toString(), ContentType.APPLICATION_JSON);
        final Map<String, String> parameters = new HashMap<>();
        parameters.put(BillingHelper.ES_FILTER_PATH, buildResponseFilterForGrouping(groupingName));
        parameters.put(RestSearchAction.TYPED_KEYS_PARAM, Boolean.TRUE.toString());
        final Response response =
            lowLevelClient.performRequest(HttpPost.METHOD_NAME, searchEndpoint, parameters, httpEntity);
        final XContentParser parser = JsonXContent.jsonXContent
            .createParser(new NamedXContentRegistry(requiredGroupingAggregationsEntries),
                          EntityUtils.toString(response.getEntity()));
        return SearchResponse.fromXContent(parser);
    }

    private String buildResponseFilterForGrouping(final String groupingName) {
        final String groupingBuckets = String.format(BillingHelper.FIRST_LEVEL_TERMS_AGG_BUCKETS_PATTERN, groupingName);
        final List<String> responseFilters = Stream.of(billingHelper.getCostAggregation().getName(),
                        billingHelper.getRunUsageAggregation().getName(),
                        billingHelper.getUniqueRunsAggregation().getName(),
                        billingHelper.getStorageUsageTotalAggregation().getName())
            .map(aggName -> String.join(BillingHelper.ES_DOC_FIELDS_SEPARATOR, groupingBuckets,
                    BillingHelper.ES_WILDCARD + aggName))
            .collect(Collectors.toList());
        responseFilters.add(groupingBuckets + BillingHelper.ES_DOC_FIELDS_SEPARATOR + BillingHelper.ES_WILDCARD +
                billingHelper.getLastByDateDocAggregation().getName() + ".hits.hits._source");
        responseFilters.add(String.format(BillingHelper.FIRST_LEVEL_AGG_PATTERN,
                billingHelper.getCostAggregation().getName()));
        responseFilters.add(groupingBuckets + BillingHelper.ES_DOC_FIELDS_SEPARATOR +
                BillingHelper.ES_TERMS_AGG_BUCKET_KEY);
        return String.join(BillingHelper.ES_ELEMENTS_SEPARATOR, responseFilters);
    }

    private Map<String, ContextParser<Object, ? extends Aggregation>> getRequiredGroupingResponseAggParsers() {
        final Map<String, ContextParser<Object, ? extends Aggregation>> map = new HashMap<>();
        map.put(StringTerms.NAME, (p, c) -> ParsedStringTerms.fromXContent(p, (String) c));
        map.put(SumAggregationBuilder.NAME, (p, c) -> ParsedSum.fromXContent(p, (String) c));
        map.put(InternalSimpleValue.NAME, (p, c) -> ParsedSimpleValue.fromXContent(p, (String) c));
        map.put(ValueCountAggregationBuilder.NAME, (p, c) -> ParsedValueCount.fromXContent(p, (String) c));
        map.put(TopHitsAggregationBuilder.NAME, (p, c) -> ParsedTopHits.fromXContent(p, (String) c));
        return map;
    }

    private List<BillingChartInfo> paginateResult(final List<BillingChartInfo> fullResult,
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
                final List<BillingChartInfo> finalBilling = fullResult.subList(from, Math.min(to, resultSize));
                final String totalPagesVal = Long.toString((long) Math.ceil(1.0 * resultSize / pageSize));
                final String pageNumVal = Long.toString(requiredPageNum);
                finalBilling.forEach(record -> {
                    record.getGroupingInfo().put(BillingHelper.PAGE, pageNumVal);
                    record.getGroupingInfo().put(BillingHelper.TOTAL_PAGES, totalPagesVal);
                });
                return finalBilling;
            }
        } else {
            return getEmptyGroupingResponse(grouping);
        }
    }

    private List<BillingChartInfo> getEmptyGroupingResponse(final BillingGrouping grouping) {
        final Map<String, String> details = new HashMap<>();
        details.put(grouping.name(), emptyValue);
        if (grouping.runUsageDetailsRequired()) {
            details.put(BillingHelper.RUN_USAGE_FIELD, emptyValue);
            details.put(BillingHelper.UNIQUE_RUNS, emptyValue);
        }
        if (grouping.storageUsageDetailsRequired()) {
            details.put(BillingHelper.STORAGE_USAGE_FIELD, emptyValue);
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
            return Optional.ofNullable(terms)
                .map(ParsedTerms::getBuckets)
                .map(Collection::stream)
                .orElse(Stream.empty())
                .map(bucket -> {
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
        final ParsedSum sumAggResult = aggregations.get(BillingHelper.COST_FIELD);
        final long costVal = new Double(sumAggResult.getValue()).longValue();
        final BillingChartInfo.BillingChartInfoBuilder builder = BillingChartInfo.builder()
            .periodStart(from.atStartOfDay())
            .periodEnd(to.atTime(LocalTime.MAX))
            .cost(costVal);
        final Map<String, String> groupingInfo = new HashMap<>();
        final EntityBillingDetailsLoader detailsLoader = billingDetailsLoaders.get(grouping);
        final Map<String, String> entityDetails = new HashMap<>();
        if (grouping != null) {
            if (detailsLoader == null) {
                groupingInfo.put(grouping.toString(), groupValue);
            } else {
                entityDetails.putAll(detailsLoader.loadInformation(groupValue, loadDetails));
                groupingInfo.put(grouping.name(), entityDetails.remove(EntityBillingDetailsLoader.NAME));
            }
        }
        if (loadDetails) {
            groupingInfo.putAll(entityDetails);
            if (grouping.runUsageDetailsRequired()) {
                final ParsedSum usageAggResult = aggregations.get(BillingHelper.RUN_USAGE_AGG);
                final long usageVal = new Double(usageAggResult.getValue()).longValue();
                groupingInfo.put(BillingHelper.RUN_USAGE_AGG, Long.toString(usageVal));
                final ParsedValueCount uniqueRunIds = aggregations.get(BillingHelper.UNIQUE_RUNS);
                groupingInfo.put(BillingHelper.UNIQUE_RUNS, Long.toString(uniqueRunIds.getValue()));
            }
            if (grouping.storageUsageDetailsRequired()) {
                final ParsedSimpleValue totalStorageUsage = aggregations.get(BillingHelper.TOTAL_STORAGE_USAGE_AGG);
                final long storageUsageVal = new Double(totalStorageUsage.value()).longValue();
                groupingInfo.put(BillingHelper.TOTAL_STORAGE_USAGE_AGG, Long.toString(storageUsageVal));
                if (BillingGrouping.STORAGE.equals(grouping)) {
                    final ParsedTopHits hits = aggregations.get(BillingHelper.BUCKET_DOCUMENTS);
                    final String lastStorageUsageValue = Optional.of(hits.getHits())
                        .map(SearchHits::getHits)
                        .filter(storageDocs -> storageDocs.length > 0)
                        .map(storageDocs -> storageDocs[0])
                        .map(SearchHit::getSourceAsMap)
                        .map(source -> source.get(BillingHelper.STORAGE_USAGE_FIELD))
                        .map(Object::toString)
                        .orElse("0");
                    groupingInfo.put(BillingHelper.LAST_STORAGE_USAGE_VALUE, lastStorageUsageValue);
                }
            }
        }
        builder.groupingInfo(groupingInfo);
        return builder.build();
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
        final ParsedSum sumAggResult = bucket.getAggregations().get(BillingHelper.COST_FIELD);
        final long costVal = new Double(sumAggResult.getValue()).longValue();
        builder.cost(costVal);
        final ParsedSimpleValue accumulatedSumAggResult = bucket.getAggregations().get(BillingHelper.ACCUMULATED_COST);
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
