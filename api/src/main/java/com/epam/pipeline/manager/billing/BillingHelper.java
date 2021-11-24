package com.epam.pipeline.manager.billing;

import com.epam.pipeline.config.Constants;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.exception.search.SearchException;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.terms.IncludeExclude;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.NumericMetricsAggregation;
import org.elasticsearch.search.aggregations.metrics.cardinality.Cardinality;
import org.elasticsearch.search.aggregations.metrics.cardinality.CardinalityAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHits;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHitsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCountAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilders;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.sum.SumBucketPipelineAggregationBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

@Slf4j
@Service
public class BillingHelper {

    public static final String COST_FIELD = "cost";
    public static final String ACCUMULATED_COST = "accumulatedCost";
    public static final String RUN_USAGE_AGG = "usage_runs";
    public static final String STORAGE_USAGE_AGG = "usage_storages";
    public static final String RUN_USAGE_FIELD = "usage_minutes";
    public static final String STORAGE_GROUPING_AGG = "storage_buckets";
    public static final String SINGLE_STORAGE_USAGE_AGG = "usage_storage";
    public static final String TOTAL_STORAGE_USAGE_AGG = "usage_storages";
    public static final String LAST_STORAGE_USAGE_VALUE = "usage_storages_last";
    public static final String STORAGE_USAGE_FIELD = "usage_bytes";
    public static final String LAST_BY_DATE_DOC_AGG = "last_by_date";
    public static final String RUN_ID_FIELD = "run_id";
    public static final String STORAGE_ID_FIELD = "storage_id";
    public static final String PAGE = "page";
    public static final String TOTAL_PAGES = "totalPages";
    public static final String BILLING_DATE_FIELD = "created_date";
    public static final String HISTOGRAM_AGGREGATION_NAME = "hist_agg";
    public static final String ES_MONTHLY_DATE_REGEXP = "%d-%02d-*";
    public static final String ES_WILDCARD = "*";
    public static final String ES_DOC_FIELDS_SEPARATOR = ".";
    public static final String ES_DOC_AGGS_SEPARATOR = ">";
    public static final String ES_ELEMENTS_SEPARATOR = ",";
    public static final String ES_FILTER_PATH = "filter_path";
    public static final String ES_INDICES_SEARCH_PATTERN = "/%s/_search";
    public static final String FIRST_LEVEL_AGG_PATTERN = "aggregations.*#%s";
    public static final String FIRST_LEVEL_TERMS_AGG_BUCKETS_PATTERN = FIRST_LEVEL_AGG_PATTERN + ".buckets";
    public static final String ES_TERMS_AGG_BUCKET_KEY = "key";
    public static final String BUCKET_DOCUMENTS = "bucketDocs";
    public static final String OWNER_FIELD = "owner";
    public static final String BILLING_CENTER_FIELD = "billing_center";
    public static final String CARDINALITY_AGG = "cardinality";
    public static final String PIPELINE_FIELD = "pipeline_name";
    public static final String TOOL_FIELD = "tool";
    public static final String COMPUTE_TYPE_FIELD = "compute_type";
    public static final String INSTANCE_TYPE_FIELD = "instance_type";
    public static final String STARTED_FIELD = "started_date";
    public static final String FINISHED_FIELD = "finished_date";
    public static final String RUN = "run";
    public static final String STORAGE = "storage";
    public static final String RUNS = "runs";
    public static final int FALLBACK_EXPORT_AGGREGATION_PARTITION_SIZE = 5000;
    public static final String RUN_COST_AGG = "cost_runs";
    public static final String STORAGE_COST_AGG = "cost_storages";
    public static final String HISTOGRAM_AGGREGATION_FORMAT = "yyyy-MM";
    public static final String RUN_COUNT_AGG = "count_runs";
    public static final String PROVIDER_FIELD = "provider";
    public static final String SORT_AGG = "sort";

    private final AuthManager authManager;
    private final String billingIndicesMonthlyPattern;
    private final String billingRunIndicesMonthlyPattern;
    private final String billingStorageIndicesMonthlyPattern;
    private final SumAggregationBuilder costAggregation;
    private final SumAggregationBuilder runUsageAggregation;
    private final TermsAggregationBuilder storageUsageGroupingAggregation;
    private final SumBucketPipelineAggregationBuilder storageUsageTotalAggregation;
    private final ValueCountAggregationBuilder uniqueRunsAggregation;
    private final TopHitsAggregationBuilder lastByDateStorageDocAggregation;
    private final TopHitsAggregationBuilder lastByDateDocAggregation;
    private final CardinalityAggregationBuilder runIdCardinalityAggregation;

    public BillingHelper(final AuthManager authManager,
                         final @Value("${billing.index.common.prefix}") String commonPrefix) {
        this.authManager = authManager;
        this.billingIndicesMonthlyPattern = String.join("-",
                commonPrefix,
                ES_WILDCARD,
                ES_MONTHLY_DATE_REGEXP);
        this.billingRunIndicesMonthlyPattern = String.join("-",
                commonPrefix,
                ES_WILDCARD + RUN + ES_WILDCARD,
                ES_MONTHLY_DATE_REGEXP);
        this.billingStorageIndicesMonthlyPattern = String.join("-",
                commonPrefix,
                ES_WILDCARD + STORAGE + ES_WILDCARD,
                ES_MONTHLY_DATE_REGEXP);
        this.costAggregation = AggregationBuilders.sum(COST_FIELD).field(COST_FIELD);
        this.runUsageAggregation = AggregationBuilders.sum(RUN_USAGE_AGG).field(RUN_USAGE_FIELD);
        this.uniqueRunsAggregation = AggregationBuilders.count(RUN_COUNT_AGG).field(RUN_ID_FIELD);
        this.storageUsageGroupingAggregation = AggregationBuilders
                .terms(STORAGE_GROUPING_AGG).field(STORAGE_ID_FIELD).size(Integer.MAX_VALUE)
                .subAggregation(AggregationBuilders.avg(SINGLE_STORAGE_USAGE_AGG).field(STORAGE_USAGE_FIELD));
        this.storageUsageTotalAggregation = PipelineAggregatorBuilders
                .sumBucket(TOTAL_STORAGE_USAGE_AGG,
                        String.format("%s.%s", STORAGE_GROUPING_AGG, SINGLE_STORAGE_USAGE_AGG));
        this.lastByDateStorageDocAggregation = AggregationBuilders.topHits(BUCKET_DOCUMENTS)
                .size(1)
                .fetchSource(STORAGE_USAGE_FIELD, null)
                .sort(BILLING_DATE_FIELD, SortOrder.DESC);
        this.lastByDateDocAggregation = AggregationBuilders.topHits(LAST_BY_DATE_DOC_AGG)
                .size(1)
                .sort(BILLING_DATE_FIELD, SortOrder.DESC);
        this.runIdCardinalityAggregation = AggregationBuilders.cardinality(CARDINALITY_AGG)
                .field(RUN_ID_FIELD);
    }

    public Map<String, List<String>> getFilters(final Map<String, List<String>> requestedFilters) {
        final Map<String, List<String>> filters = new HashMap<>(MapUtils.emptyIfNull(requestedFilters));
        final PipelineUser authorizedUser = authManager.getCurrentUser();
        if (!hasFullBillingAccess(authorizedUser)) {
            filters.put(OWNER_FIELD, Collections.singletonList(authorizedUser.getUserName()));
        }
        return filters;
    }

    private boolean hasFullBillingAccess(final PipelineUser authorizedUser) {
        return authorizedUser.isAdmin()
                || authorizedUser.getRoles().stream()
                .map(Role::getName)
                .anyMatch(DefaultRoles.ROLE_BILLING_MANAGER.getName()::equals);
    }

    public String[] indicesByDate(final LocalDate from, final LocalDate to) {
        return indicesByDate(from, to, billingIndicesMonthlyPattern);
    }

    public String[] runIndicesByDate(final LocalDate from, final LocalDate to) {
        return indicesByDate(from, to, billingRunIndicesMonthlyPattern);
    }

    public String[] storageIndicesByDate(final LocalDate from, final LocalDate to) {
        return indicesByDate(from, to, billingStorageIndicesMonthlyPattern);
    }

    private String[] indicesByDate(final LocalDate from, final LocalDate to, final String indexPattern) {
        return Stream.iterate(from, d -> d.plus(1, ChronoUnit.MONTHS))
                .limit(ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1)
                .map(date -> String.format(indexPattern, date.getYear(), date.getMonthValue()))
                .toArray(String[]::new);
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

    private RangeQueryBuilder queryByDate(final LocalDate from, final LocalDate to) {
        return QueryBuilders.rangeQuery(BILLING_DATE_FIELD)
                .from(from, true)
                .to(to, true);
    }

    public TermsAggregationBuilder aggregateBy(final String field) {
        return AggregationBuilders.terms(field)
                .field(field);
    }

    public TermsAggregationBuilder aggregatePartitionByRunId(final int partition, final int numberOfPartitions) {
        return AggregationBuilders.terms(RUN_ID_FIELD)
                .field(RUN_ID_FIELD)
                .includeExclude(new IncludeExclude(partition, numberOfPartitions))
                .order(BucketOrder.aggregation(COST_FIELD, false))
                .size(Integer.MAX_VALUE)
                .minDocCount(1);
    }

    public DateHistogramAggregationBuilder aggregateByMonth() {
        return AggregationBuilders.dateHistogram(HISTOGRAM_AGGREGATION_NAME)
                .field(BILLING_DATE_FIELD)
                .dateHistogramInterval(DateHistogramInterval.MONTH)
                .format(HISTOGRAM_AGGREGATION_FORMAT)
                .order(BucketOrder.key(true))
                .minDocCount(1L);
    }

    public CardinalityAggregationBuilder aggregateCardinalityByRunId() {
        return runIdCardinalityAggregation;
    }

    public ValueCountAggregationBuilder aggregateUniqueRunsCount() {
        return uniqueRunsAggregation;
    }

    public SumAggregationBuilder aggregateCostSum() {
        return costAggregation;
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

    public Optional<Long> getRunUsageSum(final Aggregations aggregations) {
        return getLongValue(aggregations, RUN_USAGE_AGG);
    }

    public Optional<Long> getRunCostSum(final Aggregations aggregations) {
        return getLongValue(aggregations, RUN_COST_AGG);
    }

    public Optional<Long> getStorageUsageAvg(final Aggregations aggregations) {
        return getLongValue(aggregations, STORAGE_USAGE_AGG);
    }

    public Optional<Long> getStorageCostSum(final Aggregations aggregations) {
        return getLongValue(aggregations, STORAGE_COST_AGG);
    }

    public Optional<Long> getCostSum(final Aggregations aggregations) {
        return getLongValue(aggregations, COST_FIELD);
    }

    public Optional<Long> getLongValue(final Aggregations aggregations, final String aggregation) {
        return Optional.ofNullable(aggregations)
                .map(it -> it.get(aggregation))
                .filter(NumericMetricsAggregation.SingleValue.class::isInstance)
                .map(NumericMetricsAggregation.SingleValue.class::cast)
                .map(NumericMetricsAggregation.SingleValue::value)
                .filter(it -> !it.isInfinite())
                .map(Double::longValue);
    }

    public Optional<Long> getFilteredRunCostSum(final Aggregations aggregations) {
        return getFilteredCostSum(aggregations, BillingHelper.RUN_COST_AGG);
    }

    public Optional<Long> getFilteredStorageCostSum(final Aggregations aggregations) {
        return getFilteredCostSum(aggregations, BillingHelper.STORAGE_COST_AGG);
    }

    private Optional<Long> getFilteredCostSum(final Aggregations aggregations, final String aggregation) {
        return Optional.ofNullable(aggregations)
                .map(it -> it.get(aggregation))
                .filter(ParsedFilter.class::isInstance)
                .map(ParsedFilter.class::cast)
                .map(ParsedFilter::getAggregations)
                .flatMap(this::getCostSum);
    }

    public Optional<Long> getRunCount(final Aggregations aggregations) {
        return Optional.ofNullable(aggregations)
                .map(it -> it.get(RUN_COUNT_AGG))
                .filter(NumericMetricsAggregation.SingleValue.class::isInstance)
                .map(NumericMetricsAggregation.SingleValue.class::cast)
                .map(NumericMetricsAggregation.SingleValue::value)
                .map(Double::longValue);
    }

    public Optional<Integer> getCardinalityByRunId(final Aggregations aggregations) {
        return Optional.ofNullable(aggregations)
                .map(it -> it.get(CARDINALITY_AGG))
                .filter(Cardinality.class::isInstance)
                .map(Cardinality.class::cast)
                .map(Cardinality::getValue)
                .map(Long::intValue);
    }

    public Map<String, Object> getLastByDateDocFields(final Aggregations aggregations) {
        return Optional.ofNullable(aggregations)
                .map(it -> it.get(LAST_BY_DATE_DOC_AGG))
                .filter(TopHits.class::isInstance)
                .map(TopHits.class::cast)
                .map(TopHits::getHits)
                .map(SearchHits::getHits)
                .map(Arrays::asList)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .findFirst()
                .map(SearchHit::getSourceAsMap)
                .orElseGet(Collections::emptyMap);
    }

    public Function<SearchRequest, SearchResponse> searchWith(final RestHighLevelClient elasticSearchClient) {
        return request -> {
            try {
                log.debug("Billing request: {}", request);
                return elasticSearchClient.search(request);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                throw new SearchException(e.getMessage(), e);
            }
        };
    }
}
