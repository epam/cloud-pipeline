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
import com.epam.pipeline.controller.vo.billing.BillingCostDetailsRequest;
import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
import com.epam.pipeline.dto.quota.Quota;
import com.epam.pipeline.dto.quota.QuotaType;
import com.epam.pipeline.entity.billing.BillingChartInfo;
import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.entity.billing.BillingGroupingSortOrder;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.exception.search.SearchException;
import com.epam.pipeline.manager.billing.billingdetails.BillingChartCostDetailsLoader;
import com.epam.pipeline.manager.billing.billingdetails.ComputeBillingCostDetailsLoader;
import com.epam.pipeline.manager.billing.billingdetails.StorageBillingCostDetailsLoader;
import com.epam.pipeline.manager.billing.detail.EntityBillingDetailsLoader;
import com.epam.pipeline.manager.billing.order.BillingOrderApplier;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.utils.GlobalSearchElasticHelper;
import com.epam.pipeline.utils.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ContextParser;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.BoolQueryBuilder;
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
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.avg.AvgAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.avg.ParsedAvg;
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

    private final Map<DateHistogramInterval, TemporalAdjuster> periodAdjusters;
    private final List<DateHistogramInterval> validIntervals;
    private final Map<BillingGrouping, EntityBillingDetailsLoader> billingDetailsLoaders;
    private final BillingHelper billingHelper;
    private final BillingExportManager billingExportManager;
    private final MessageHelper messageHelper;
    private final MetadataManager metadataManager;
    private final GlobalSearchElasticHelper elasticHelper;
    private final String emptyValue;
    private final String billingCenterKey;
    private final List<NamedXContentRegistry.Entry> requiredGroupingAggregationsEntries;

    @Autowired
    public BillingManager(final BillingHelper billingHelper,
                          final BillingExportManager billingExportManager,
                          final MessageHelper messageHelper,
                          final MetadataManager metadataManager,
                          final GlobalSearchElasticHelper globalSearchElasticHelper,
                          final @Value("${billing.empty.report.value:unknown}") String emptyValue,
                          final @Value("${billing.center.key}") String billingCenterKey,
                          final List<EntityBillingDetailsLoader> billingDetailsLoaders) {
        this.billingHelper = billingHelper;
        this.billingExportManager = billingExportManager;
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
        try (RestHighLevelClient elasticsearchClient = elasticHelper.buildClient()) {
            final LocalDate from = request.getFrom();
            final LocalDate to = request.getTo();
            final BillingGrouping grouping = request.getGrouping();
            final DateHistogramInterval interval = request.getInterval();
            final Map<String, List<String>> filters = billingHelper.getFilters(request.getFilters());
            final BillingCostDetailsRequest costDetailsRequest = BillingCostDetailsRequest.builder()
                    .enabled(request.isLoadCostDetails()).filters(filters)
                    .isHistogram(interval != null).grouping(grouping).build();
            if (interval != null) {
                return getBillingStats(elasticsearchClient, from, to, filters, interval, costDetailsRequest);
            }
            if (grouping != null) {
                final BillingGroupingSortOrder order = Optional.ofNullable(request.getOrder())
                        .orElse(BillingGroupingSortOrder.DEFAULT_SORT_ORDER);
                return getBillingStats(elasticsearchClient.getLowLevelClient(), from, to, filters, grouping,
                        order, request.isLoadDetails(), costDetailsRequest);
            }
            throw new IllegalArgumentException("Either interval or grouping parameter has to be specified.");
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
        return billingExportManager.export(request);
    }

    public List<String> getAllBillingCenters() {
        return metadataManager.loadUniqueValuesFromEntityClassMetadata(AclClass.PIPELINE_USER, billingCenterKey);
    }

    public Double getQuotaExpense(final Quota quota, final LocalDate from, final LocalDate to) {
        try (RestHighLevelClient elasticsearchClient = elasticHelper.buildClient()) {
            final HashMap<String, List<String>> filters = buildQuotaFilters(quota);
            final SearchRequest searchRequest = new SearchRequest()
                    .indicesOptions(IndicesOptions.strictExpandOpen())
                    .indices(billingHelper.indicesByDate(from, to))
                    .source(new SearchSourceBuilder()
                            .size(0)
                            .aggregation(billingHelper.aggregateCostSum())
                            .query(billingHelper.queryByDateAndFilters(from, to, filters)));
            final SearchResponse searchResponse = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
            return Optional.ofNullable(searchResponse.getAggregations())
                    .map(aggregations -> aggregations.<ParsedSum>get(BillingUtils.COST_FIELD))
                    .map(ParsedSum::getValue)
                    .orElse(0.0);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new SearchException(e.getMessage(), e);
        }
    }

    private HashMap<String, List<String>> buildQuotaFilters(final Quota quota) {
        final HashMap<String, List<String>> filters = new HashMap<>();
        Optional.ofNullable(quota.getQuotaGroup()
                .getResourceType())
                .ifPresent(resource -> filters.put(BillingGrouping.RESOURCE_TYPE.getCorrespondingField(),
                        Collections.singletonList(resource)));
        Optional.ofNullable(quota.getType())
                .map(QuotaType::getFilterField)
                .ifPresent(filter -> filters.put(filter, Collections.singletonList(quota.getSubject())));
        return filters;
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
                                                   final DateHistogramInterval interval,
                                                   final BillingCostDetailsRequest costDetailsRequest) {
        if (!validIntervals.contains(interval)) {
            throw new IllegalArgumentException(messageHelper
                                                   .getMessage(MessageConstants.ERROR_BILLING_INTERVAL_NOT_SUPPORTED));
        }

        final AggregationBuilder intervalAgg = AggregationBuilders.dateHistogram(
                        BillingUtils.HISTOGRAM_AGGREGATION_NAME)
            .field(BillingUtils.BILLING_DATE_FIELD)
            .dateHistogramInterval(interval)
            .subAggregation(billingHelper.aggregateCostSum())
            .subAggregation(PipelineAggregatorBuilders.cumulativeSum(BillingUtils.ACCUMULATED_COST,
                    BillingUtils.COST_FIELD));

        BillingChartCostDetailsLoader.buildQuery(costDetailsRequest, intervalAgg);

        final SearchRequest searchRequest = new SearchRequest()
                .indicesOptions(IndicesOptions.strictExpandOpen())
                .indices(billingHelper.indicesByDate(from, to))
                .source(new SearchSourceBuilder()
                        .size(0)
                        .aggregation(intervalAgg)
                        .query(billingHelper.queryByDateAndFilters(from, to, filters)));

        try {
            final SearchResponse searchResponse = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
            return Optional.ofNullable(searchResponse.getAggregations())
                .map(aggs -> aggs.get(BillingUtils.HISTOGRAM_AGGREGATION_NAME))
                .map(ParsedDateHistogram.class::cast)
                .map(histogram -> parseHistogram(interval, histogram, costDetailsRequest))
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
                                                   final BillingGroupingSortOrder order,
                                                   final boolean isLoadDetails,
                                                   final BillingCostDetailsRequest costDetailsRequest) {
        final SearchSourceBuilder searchSource = new SearchSourceBuilder();
        final TermsAggregationBuilder fieldAgg = AggregationBuilders.terms(grouping.getCorrespondingField())
            .field(grouping.getCorrespondingField()).size(Integer.MAX_VALUE);

        final BoolQueryBuilder query = BillingOrderApplier.applyOrder(
                grouping, order, billingHelper.queryByDateAndFilters(from, to, filters), fieldAgg
        );

        fieldAgg.subAggregation(billingHelper.aggregateCostSum());
        fieldAgg.subAggregation(billingHelper.aggregateLastByDateDoc());
        if (grouping.isRunUsageDetailsRequired()) {
            fieldAgg.subAggregation(billingHelper.aggregateRunUsageSum());
            fieldAgg.subAggregation(billingHelper.aggregateUniqueRunsCount());
        }
        if (grouping.isStorageUsageDetailsRequired()) {
            fieldAgg.subAggregation(billingHelper.aggregateByStorageUsageGrouping());
            fieldAgg.subAggregation(billingHelper.aggregateStorageUsageTotalSumBucket());
            if (BillingGrouping.STORAGE.equals(grouping)) {
                fieldAgg.subAggregation(billingHelper.aggregateLastByDateStorageDoc());
            }
        }

        BillingChartCostDetailsLoader.buildQuery(costDetailsRequest, fieldAgg);

        searchSource.aggregation(fieldAgg);
        searchSource.aggregation(billingHelper.aggregateCostSum());
        final SearchRequest searchRequest = new SearchRequest()
                .indicesOptions(IndicesOptions.strictExpandOpen())
                .indices(billingHelper.indicesByDate(from, to))
                .source(searchSource
                        .size(0)
                        .query(query));

        try {
            return searchForGrouping(elasticsearchLowLevelClient, searchRequest, grouping.getCorrespondingField())
                    .map(response -> getBillingChartInfoForGrouping(
                            from, to, grouping, response, isLoadDetails, costDetailsRequest))
                    .filter(CollectionUtils::isNotEmpty)
                    .orElseGet(() -> getEmptyGroupingResponse(grouping));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new SearchException(e.getMessage(), e);
        }
    }

    private Optional<SearchResponse> searchForGrouping(final RestClient lowLevelClient, final SearchRequest request,
                                                       final String groupingName) throws IOException {
        final String searchEndpoint = String.format(BillingUtils.ES_INDICES_SEARCH_PATTERN,
                String.join(BillingUtils.ES_ELEMENTS_SEPARATOR, request.indices()));
        final HttpEntity httpEntity = new NStringEntity(request.source().toString(), ContentType.APPLICATION_JSON);
        final Map<String, String> parameters = new HashMap<>();
        parameters.put(BillingUtils.ES_FILTER_PATH, buildResponseFilterForGrouping(groupingName));
        parameters.put(RestSearchAction.TYPED_KEYS_PARAM, Boolean.TRUE.toString());
        final Request lowLevelRequest = new Request(HttpPost.METHOD_NAME, searchEndpoint);
        MapUtils.emptyIfNull(parameters).forEach(lowLevelRequest::addParameter);
        lowLevelRequest.setEntity(httpEntity);
        final Response response = lowLevelClient.performRequest(lowLevelRequest);
        final String body = EntityUtils.toString(response.getEntity());
        if (StringUtils.equals(body, BillingUtils.ES_EMPTY_JSON)) {
            return Optional.empty();
        }
        final XContentParser parser = JsonXContent.jsonXContent
            .createParser(new NamedXContentRegistry(requiredGroupingAggregationsEntries),
                    DeprecationHandler.THROW_UNSUPPORTED_OPERATION, body);
        return Optional.of(SearchResponse.fromXContent(parser));
    }

    private String buildResponseFilterForGrouping(final String groupingName) {
        final String groupingBuckets = String.format(BillingUtils.FIRST_LEVEL_TERMS_AGG_BUCKETS_PATTERN, groupingName);
        final List<String> costDetailsAggregations = ListUtils.union(
                StorageBillingCostDetailsLoader.STORAGE_COST_DETAILS_AGGREGATION_MASKS,
                ComputeBillingCostDetailsLoader.getCostDetailsAggregations());
        final List<String> responseFilters =
            Stream.concat(
                Stream.of(
                    billingHelper.aggregateCostSum().getName(),
                    billingHelper.aggregateRunUsageSum().getName(),
                    billingHelper.aggregateUniqueRunsCount().getName(),
                    billingHelper.aggregateStorageUsageTotalSumBucket().getName(),
                    billingHelper.aggregateLastByDateDoc().getName()
                ),
                costDetailsAggregations.stream()
            ).map(aggName -> String.join(BillingUtils.ES_DOC_FIELDS_SEPARATOR, groupingBuckets,
                BillingUtils.ES_WILDCARD + aggName)).collect(Collectors.toList());
        responseFilters.add(groupingBuckets + BillingUtils.ES_DOC_FIELDS_SEPARATOR + BillingUtils.ES_WILDCARD +
                billingHelper.aggregateLastByDateStorageDoc().getName() + ".hits.hits._source");
        responseFilters.add(String.format(BillingUtils.FIRST_LEVEL_AGG_PATTERN,
                billingHelper.aggregateCostSum().getName()));
        responseFilters.add(groupingBuckets + BillingUtils.ES_DOC_FIELDS_SEPARATOR +
                BillingUtils.ES_TERMS_AGG_BUCKET_KEY);
        return String.join(BillingUtils.ES_ELEMENTS_SEPARATOR, responseFilters);
    }

    private Map<String, ContextParser<Object, ? extends Aggregation>> getRequiredGroupingResponseAggParsers() {
        final Map<String, ContextParser<Object, ? extends Aggregation>> map = new HashMap<>();
        map.put(StringTerms.NAME, (p, c) -> ParsedStringTerms.fromXContent(p, (String) c));
        map.put(SumAggregationBuilder.NAME, (p, c) -> ParsedSum.fromXContent(p, (String) c));
        map.put(AvgAggregationBuilder.NAME, (p, c) -> ParsedAvg.fromXContent(p, (String) c));
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
                    record.getGroupingInfo().put(BillingUtils.PAGE, pageNumVal);
                    record.getGroupingInfo().put(BillingUtils.TOTAL_PAGES, totalPagesVal);
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
        if (grouping.isRunUsageDetailsRequired()) {
            details.put(BillingUtils.RUN_USAGE_FIELD, emptyValue);
            details.put(BillingUtils.RUN_COUNT_AGG, emptyValue);
        }
        if (grouping.isStorageUsageDetailsRequired()) {
            details.put(BillingUtils.STORAGE_USAGE_FIELD, emptyValue);
        }
        final BillingChartInfo emptyResponse = BillingChartInfo.builder()
            .groupingInfo(details)
            .build();
        return Collections.singletonList(emptyResponse);
    }

    private List<BillingChartInfo> getBillingChartInfoForGrouping(final LocalDate from, final LocalDate to,
                                                                  final BillingGrouping grouping,
                                                                  final SearchResponse searchResponse,
                                                                  final boolean isLoadDetails,
                                                                  final BillingCostDetailsRequest costDetailsRequest) {
        final Aggregations allAggregations = searchResponse.getAggregations();
        if (allAggregations == null) {
            return Collections.emptyList();
        }
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
                                          isLoadDetails,
                        costDetailsRequest);
            })
            .collect(Collectors.toList());
    }

    private BillingChartInfo getCostAggregation(final LocalDate from, final LocalDate to,
                                                final BillingGrouping grouping,
                                                final String groupValue,
                                                final Aggregations aggregations,
                                                final boolean loadDetails,
                                                final BillingCostDetailsRequest costDetailsRequest) {
        final BillingChartInfo.BillingChartInfoBuilder builder = BillingChartInfo.builder()
            .periodStart(from.atStartOfDay())
            .periodEnd(to.atTime(LocalTime.MAX))
            .cost(BillingUtils.parseSum(aggregations, BillingUtils.COST_FIELD));

        final Map<String, String> groupingInfo = new HashMap<>();
        final EntityBillingDetailsLoader detailsLoader = billingDetailsLoaders.get(grouping);
        final Map<String, String> defaultDetails = billingHelper.getLastByDateDocFields(aggregations)
                .entrySet()
                .stream()
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
        final Map<String, String> details = new HashMap<>(Optional.ofNullable(detailsLoader)
                .map(loader -> loader.loadInformation(groupValue, loadDetails, defaultDetails))
                .orElseGet(() -> Collections.singletonMap(EntityBillingDetailsLoader.NAME, groupValue)));
        groupingInfo.put(grouping.name(), details.get(EntityBillingDetailsLoader.NAME));
        if (loadDetails) {
            groupingInfo.putAll(details);
            if (grouping.isRunUsageDetailsRequired()) {
                final ParsedSum usageAggResult = aggregations.get(BillingUtils.RUN_USAGE_AGG);
                final long usageVal = new Double(usageAggResult.getValue()).longValue();
                groupingInfo.put(BillingUtils.RUN_USAGE_AGG, Long.toString(usageVal));
                final ParsedValueCount uniqueRunIds = aggregations.get(BillingUtils.RUN_COUNT_AGG);
                groupingInfo.put(BillingUtils.RUNS, Long.toString(uniqueRunIds.getValue()));
            }
            if (grouping.isStorageUsageDetailsRequired()) {
                final ParsedSimpleValue totalStorageUsage = aggregations.get(BillingUtils.TOTAL_STORAGE_USAGE_AGG);
                final long storageUsageVal = new Double(totalStorageUsage.value()).longValue();
                groupingInfo.put(BillingUtils.TOTAL_STORAGE_USAGE_AGG, Long.toString(storageUsageVal));
                if (BillingGrouping.STORAGE.equals(grouping)) {
                    final ParsedTopHits hits = aggregations.get(BillingUtils.BUCKET_DOCUMENTS);
                    final String lastStorageUsageValue = Optional.of(hits.getHits())
                        .map(SearchHits::getHits)
                        .filter(storageDocs -> storageDocs.length > 0)
                        .map(storageDocs -> storageDocs[0])
                        .map(SearchHit::getSourceAsMap)
                        .map(source -> source.get(BillingUtils.STORAGE_USAGE_FIELD))
                        .map(Object::toString)
                        .orElse("0");
                    groupingInfo.put(BillingUtils.LAST_STORAGE_USAGE_VALUE, lastStorageUsageValue);
                }
            }
        }
        return builder
                .groupingInfo(groupingInfo)
                .costDetails(BillingChartCostDetailsLoader.parseResponse(costDetailsRequest, aggregations))
                .build();
    }

    private List<BillingChartInfo> parseHistogram(final DateHistogramInterval interval,
                                                  final ParsedDateHistogram histogram,
                                                  final BillingCostDetailsRequest costDetailsRequest) {
        return histogram.getBuckets().stream()
            .map(bucket -> getChartInfo(bucket, interval, costDetailsRequest))
            .collect(Collectors.toList());
    }

    private BillingChartInfo getChartInfo(final Histogram.Bucket bucket, final DateHistogramInterval interval,
                                          final BillingCostDetailsRequest costDetailsRequest) {
        final Aggregations aggregations = bucket.getAggregations();
        final BillingChartInfo.BillingChartInfoBuilder builder = BillingChartInfo.builder()
                .groupingInfo(null)
                .cost(BillingUtils.parseSum(aggregations, BillingUtils.COST_FIELD))
                .accumulatedCost(BillingUtils.parseAccumulatedSum(aggregations, BillingUtils.ACCUMULATED_COST));

        final DateTime date = (DateTime) bucket.getKey();
        final LocalDate periodStart = LocalDate.of(date.getYear(), date.getMonthOfYear(), date.getDayOfMonth());
        builder.periodStart(periodStart.atStartOfDay());
        final TemporalAdjuster adjuster = periodAdjusters.get(interval);
        if (adjuster != null) {
            builder.periodEnd(periodStart.with(adjuster).atTime(LocalTime.MAX));
        } else {
            builder.periodEnd(periodStart.atTime(LocalTime.MAX));
        }
        return builder
                .costDetails(BillingChartCostDetailsLoader.parseResponse(costDetailsRequest, aggregations))
                .build();
    }
}
