package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.exception.search.SearchException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.NumericMetricsAggregation;
import org.elasticsearch.search.aggregations.metrics.avg.AvgAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHits;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHitsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCountAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilders;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.avg.AvgBucketPipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.sum.SumBucketPipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.bucketsort.BucketSortPipelineAggregationBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

@Slf4j
@Service
public class BillingHelper {

    private final SumAggregationBuilder costAggregation;
    private final SumAggregationBuilder runUsageAggregation;
    private final TermsAggregationBuilder storageUsageGroupingAggregation;
    private final SumBucketPipelineAggregationBuilder storageUsageTotalAggregation;
    private final ValueCountAggregationBuilder uniqueRunsAggregation;
    private final TopHitsAggregationBuilder lastByDateStorageDocAggregation;
    private final TopHitsAggregationBuilder lastByDateDocAggregation;

    public BillingHelper() {
        this.costAggregation = AggregationBuilders.sum(BillingUtils.COST_FIELD)
                .field(BillingUtils.COST_FIELD);
        this.runUsageAggregation = AggregationBuilders.sum(BillingUtils.RUN_USAGE_AGG)
                .field(BillingUtils.RUN_USAGE_FIELD);
        this.uniqueRunsAggregation = AggregationBuilders.count(BillingUtils.RUN_COUNT_AGG)
                .field(BillingUtils.RUN_ID_FIELD);
        this.storageUsageGroupingAggregation = AggregationBuilders.terms(BillingUtils.STORAGE_GROUPING_AGG)
                .field(BillingUtils.STORAGE_ID_FIELD)
                .size(Integer.MAX_VALUE)
                .subAggregation(AggregationBuilders.avg(BillingUtils.SINGLE_STORAGE_USAGE_AGG)
                        .script(new Script(BillingUtils.STORAGE_USAGE_AVG_SCRIPT)));
        this.storageUsageTotalAggregation = PipelineAggregatorBuilders
                .sumBucket(BillingUtils.TOTAL_STORAGE_USAGE_AGG,
                        fieldsPath(BillingUtils.STORAGE_GROUPING_AGG, BillingUtils.SINGLE_STORAGE_USAGE_AGG));
        this.lastByDateStorageDocAggregation = AggregationBuilders.topHits(BillingUtils.BUCKET_DOCUMENTS)
                .size(1)
                .fetchSource(BillingUtils.STORAGE_USAGE_FIELD, null)
                .sort(BillingUtils.BILLING_DATE_FIELD, SortOrder.DESC);
        this.lastByDateDocAggregation = AggregationBuilders.topHits(BillingUtils.LAST_BY_DATE_DOC_AGG)
                .size(1)
                .sort(BillingUtils.BILLING_DATE_FIELD, SortOrder.DESC);
    }

    public BoolQueryBuilder queryByDateAndFilters(final LocalDate from,
                                                  final LocalDate to,
                                                  final Map<String, List<String>> filters) {
        return queryByFilters(filters)
                .filter(queryByDate(from, to));
    }

    private BoolQueryBuilder queryByFilters(final Map<String, List<String>> filters) {
        return MapUtils.emptyIfNull(filters).entrySet().stream()
                .reduce(QueryBuilders.boolQuery(),
                    (query, entry) -> query.filter(QueryBuilders.termsQuery(entry.getKey(), entry.getValue())),
                    BoolQueryBuilder::filter);
    }

    public RangeQueryBuilder queryByDate(final LocalDate from, final LocalDate to) {
        return QueryBuilders.rangeQuery(BillingUtils.BILLING_DATE_FIELD)
                .from(from, true)
                .to(to, true);
    }

    private TermsQueryBuilder queryRunTerms() {
        return queryDocTypeTerms(SearchDocumentType.PIPELINE_RUN.name());
    }

    private TermsQueryBuilder queryStorageTerms() {
        return queryDocTypeTerms(SearchDocumentType.S3_STORAGE.name(), SearchDocumentType.AZ_BLOB_STORAGE.name(),
                SearchDocumentType.NFS_STORAGE.name(), SearchDocumentType.GS_STORAGE.name());
    }

    private TermsQueryBuilder queryDocTypeTerms(final String... docTypes) {
        return QueryBuilders.termsQuery(BillingUtils.DOC_TYPE, docTypes);
    }

    public TermsAggregationBuilder aggregateBy(final String field) {
        return AggregationBuilders.terms(field)
                .field(field);
    }

    public DateHistogramAggregationBuilder aggregateByMonth() {
        return AggregationBuilders.dateHistogram(BillingUtils.HISTOGRAM_AGGREGATION_NAME)
                .field(BillingUtils.BILLING_DATE_FIELD)
                .dateHistogramInterval(DateHistogramInterval.MONTH)
                .format(BillingUtils.HISTOGRAM_AGGREGATION_FORMAT)
                .order(BucketOrder.key(true))
                .minDocCount(NumberUtils.LONG_ONE);
    }

    public ValueCountAggregationBuilder aggregateUniqueRunsCount() {
        return uniqueRunsAggregation;
    }

    public SumAggregationBuilder aggregateCostSum() {
        return costAggregation;
    }

    public AggregationBuilder aggregateCostSum(final long discount) {
        if (discount == 0L) {
            return aggregateCostSum();
        }
        return AggregationBuilders.sum(BillingUtils.COST_FIELD)
                .field(BillingUtils.COST_FIELD)
                .script(new Script(String.format(BillingUtils.DISCOUNT_SCRIPT_TEMPLATE,
                        BillingUtils.asPercentToDecimalString(discount))));
    }

    public SumAggregationBuilder aggregateRunUsageSum() {
        return runUsageAggregation;
    }

    public TermsAggregationBuilder aggregateByStorageUsageGrouping() {
        return storageUsageGroupingAggregation;
    }

    public SumBucketPipelineAggregationBuilder aggregateStorageUsageTotalSumBucket() {
        return storageUsageTotalAggregation;
    }

    public TopHitsAggregationBuilder aggregateLastByDateStorageDoc() {
        return lastByDateStorageDocAggregation;
    }

    public TopHitsAggregationBuilder aggregateLastByDateDoc() {
        return lastByDateDocAggregation;
    }

    public FilterAggregationBuilder aggregateFilteredRuns() {
        return AggregationBuilders.filter(BillingUtils.RUN_COST_AGG, queryRunTerms());
    }

    public FilterAggregationBuilder aggregateFilteredStorages() {
        return AggregationBuilders.filter(BillingUtils.STORAGE_COST_AGG, queryStorageTerms());
    }

    public AvgAggregationBuilder aggregateStorageUsageAvg() {
        return AggregationBuilders.avg(BillingUtils.STORAGE_USAGE_AGG)
                .script(new Script(BillingUtils.STORAGE_USAGE_AVG_SCRIPT));
    }

    public BucketSortPipelineAggregationBuilder aggregateCostSortBucket() {
        return PipelineAggregatorBuilders.bucketSort(BillingUtils.SORT_AGG, Collections.singletonList(
                new FieldSortBuilder(BillingUtils.COST_FIELD).order(SortOrder.DESC)));
    }

    public BucketSortPipelineAggregationBuilder aggregateCostSortBucket(final int from, final int size) {
        return aggregateCostSortBucket().from(from).size(size);
    }

    public SumBucketPipelineAggregationBuilder aggregateRunUsageSumBucket() {
        return PipelineAggregatorBuilders.sumBucket(BillingUtils.RUN_USAGE_AGG,
                aggsPath(BillingUtils.HISTOGRAM_AGGREGATION_NAME, BillingUtils.RUN_USAGE_AGG));
    }

    public SumBucketPipelineAggregationBuilder aggregateRunCostSumBucket() {
        return PipelineAggregatorBuilders.sumBucket(BillingUtils.RUN_COST_AGG,
                aggsPath(BillingUtils.HISTOGRAM_AGGREGATION_NAME, BillingUtils.RUN_COST_AGG, BillingUtils.COST_FIELD));
    }

    public SumBucketPipelineAggregationBuilder aggregateStorageCostSumBucket() {
        return PipelineAggregatorBuilders.sumBucket(BillingUtils.STORAGE_COST_AGG,
                aggsPath(BillingUtils.HISTOGRAM_AGGREGATION_NAME, BillingUtils.STORAGE_COST_AGG,
                        BillingUtils.COST_FIELD));
    }

    public SumBucketPipelineAggregationBuilder aggregateCostSumBucket() {
        return PipelineAggregatorBuilders.sumBucket(BillingUtils.COST_FIELD,
                aggsPath(BillingUtils.HISTOGRAM_AGGREGATION_NAME, BillingUtils.COST_FIELD));
    }

    public AvgBucketPipelineAggregationBuilder aggregateStorageUsageAverageBucket() {
        return PipelineAggregatorBuilders.avgBucket(BillingUtils.STORAGE_USAGE_AGG,
                aggsPath(BillingUtils.HISTOGRAM_AGGREGATION_NAME, BillingUtils.STORAGE_USAGE_AGG));
    }

    private String aggsPath(final String... paths) {
        return bucketsPath(BillingUtils.ES_DOC_AGGS_SEPARATOR, paths);
    }

    private String fieldsPath(final String... paths) {
        return bucketsPath(BillingUtils.ES_DOC_FIELDS_SEPARATOR, paths);
    }

    private String bucketsPath(final String separator, final String[] paths) {
        return String.join(separator, paths);
    }

    public Long getRunUsageSum(final Aggregations aggregations) {
        return getLongValue(aggregations, BillingUtils.RUN_USAGE_AGG);
    }

    public Long getRunCostSum(final Aggregations aggregations) {
        return getLongValue(aggregations, BillingUtils.RUN_COST_AGG);
    }

    public Long getStorageUsageAvg(final Aggregations aggregations) {
        return getLongValue(aggregations, BillingUtils.STORAGE_USAGE_AGG);
    }

    public Long getStorageCostSum(final Aggregations aggregations) {
        return getLongValue(aggregations, BillingUtils.STORAGE_COST_AGG);
    }

    public Long getCostSum(final Aggregations aggregations) {
        return getLongValue(aggregations, BillingUtils.COST_FIELD);
    }

    public Long getLongValue(final Aggregations aggregations, final String aggregation) {
        return getSingleValue(aggregations, aggregation)
                .map(NumericMetricsAggregation.SingleValue::value)
                .filter(it -> !it.isInfinite())
                .map(Double::longValue)
                .orElse(NumberUtils.LONG_ZERO);
    }

    public Long getFilteredRunCostSum(final Aggregations aggregations) {
        return getFilteredCostSum(aggregations, BillingUtils.RUN_COST_AGG);
    }

    public Long getFilteredStorageCostSum(final Aggregations aggregations) {
        return getFilteredCostSum(aggregations, BillingUtils.STORAGE_COST_AGG);
    }

    private Long getFilteredCostSum(final Aggregations aggregations, final String aggregation) {
        return getFilter(aggregations, aggregation)
                .map(ParsedFilter::getAggregations)
                .map(this::getCostSum)
                .orElse(NumberUtils.LONG_ZERO);
    }

    public Long getRunCount(final Aggregations aggregations) {
        return getSingleValue(aggregations, BillingUtils.RUN_COUNT_AGG)
                .map(NumericMetricsAggregation.SingleValue::value)
                .map(Double::longValue)
                .orElse(NumberUtils.LONG_ZERO);
    }

    public Map<String, Object> getLastByDateDocFields(final Aggregations aggregations) {
        return getTopHits(aggregations, BillingUtils.LAST_BY_DATE_DOC_AGG)
                .map(TopHits::getHits)
                .map(SearchHits::getHits)
                .map(Arrays::asList)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .findFirst()
                .map(SearchHit::getSourceAsMap)
                .orElseGet(Collections::emptyMap);
    }

    public Stream<? extends Terms.Bucket> termBuckets(final Aggregations aggregations,
                                                      final String aggregation) {
        return getTerms(aggregations, aggregation)
                .map(Terms::getBuckets)
                .map(Collection::stream)
                .orElseGet(Stream::empty);
    }

    public Stream<? extends Histogram.Bucket> histogramBuckets(final Aggregations aggregations,
                                                               final String aggregation) {
        return getHistogram(aggregations, aggregation)
                .map(Histogram::getBuckets)
                .map(Collection::stream)
                .orElseGet(Stream::empty);
    }

    public Optional<ParsedFilter> getFilter(final Aggregations aggregations, final String aggregation) {
        return getAggregation(aggregations, aggregation, ParsedFilter.class);
    }

    public Optional<NumericMetricsAggregation.SingleValue> getSingleValue(final Aggregations aggregations,
                                                                          final String aggregation) {
        return getAggregation(aggregations, aggregation, NumericMetricsAggregation.SingleValue.class);
    }

    public Optional<TopHits> getTopHits(final Aggregations aggregations, final String aggregation) {
        return getAggregation(aggregations, aggregation, TopHits.class);
    }

    public Optional<Terms> getTerms(final Aggregations aggregations, final String aggregation) {
        return getAggregation(aggregations, aggregation, Terms.class);
    }

    public Optional<Histogram> getHistogram(final Aggregations aggregations, final String aggregation) {
        return getAggregation(aggregations, aggregation, Histogram.class);
    }

    public <A> Optional<A> getAggregation(final Aggregations aggregations,
                                          final String aggregation,
                                          final Class<A> aggregationClass) {
        return Optional.ofNullable(aggregations)
                .map(it -> it.get(aggregation))
                .filter(aggregationClass::isInstance)
                .map(aggregationClass::cast);
    }

    public Function<SearchRequest, SearchResponse> searchWith(final RestHighLevelClient elasticSearchClient) {
        return request -> {
            try {
                log.debug("Billing request: {}", request);
                return elasticSearchClient.search(request, RequestOptions.DEFAULT);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                throw new SearchException(e.getMessage(), e);
            }
        };
    }
}
