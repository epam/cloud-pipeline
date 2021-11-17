package com.epam.pipeline.manager.billing;

import com.epam.pipeline.config.Constants;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.Getter;
import org.apache.commons.collections4.MapUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.cardinality.Cardinality;
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHits;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHitsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCountAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilders;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.sum.SumBucketPipelineAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Value;

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
import java.util.stream.Stream;

public class BillingHelper {

    public static final String COST_FIELD = "cost";
    public static final String ACCUMULATED_COST = "accumulatedCost";
    public static final String RUN_USAGE_AGG = "usage_runs";
    public static final String RUN_USAGE_FIELD = "usage_minutes";
    public static final String STORAGE_GROUPING_AGG = "storage_buckets";
    public static final String SINGLE_STORAGE_USAGE_AGG = "usage_storage";
    public static final String TOTAL_STORAGE_USAGE_AGG = "usage_storages";
    public static final String LAST_STORAGE_USAGE_VALUE = "usage_storages_last";
    public static final String STORAGE_USAGE_FIELD = "usage_bytes";
    public static final String TOP_HITS_AGG = "run_id_top_hits";
    public static final String RUN_ID_FIELD = "run_id";
    public static final String STORAGE_ID_FIELD = "storage_id";
    public static final String UNIQUE_RUNS = "runs";
    public static final String PAGE = "page";
    public static final String TOTAL_PAGES = "totalPages";
    public static final String BILLING_DATE_FIELD = "created_date";
    public static final String HISTOGRAM_AGGREGATION_NAME = "hist_agg";
    public static final String ES_MONTHLY_DATE_REGEXP = "%d-%02d-*";
    public static final String ES_WILDCARD = "*";
    public static final String ES_DOC_FIELDS_SEPARATOR = ".";
    public static final String ES_ELEMENTS_SEPARATOR = ",";
    public static final String ES_FILTER_PATH = "filter_path";
    public static final String ES_INDICES_SEARCH_PATTERN = "/%s/_search";
    public static final String FIRST_LEVEL_AGG_PATTERN = "aggregations.*#%s";
    public static final String FIRST_LEVEL_TERMS_AGG_BUCKETS_PATTERN = FIRST_LEVEL_AGG_PATTERN + ".buckets";
    public static final String ES_TERMS_AGG_BUCKET_KEY = "key";
    public static final String BUCKET_DOCUMENTS = "bucketDocs";
    public static final String OWNER_FIELD = "owner";
    public static final String CARDINALITY_AGG = "cardinality";
    public static final String PIPELINE_FIELD = "pipeline_name";
    public static final String TOOL_FIELD = "tool";
    public static final String INSTANCE_TYPE_FIELD = "instance_type";
    public static final String STARTED_FIELD = "started_date";
    public static final String FINISHED_FIELD = "finished_date";
    public static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(Constants.FMT_ISO_LOCAL_DATE);
    public static final int DEFAULT_BILLINGS_PAGE_SIZE = 1000;

    private final AuthManager authManager;
    private final String billingIndicesMonthlyPattern;
    @Getter
    private final SumAggregationBuilder costAggregation;
    @Getter
    private final SumAggregationBuilder runUsageAggregation;
    @Getter
    private final TermsAggregationBuilder storageUsageGroupingAggregation;
    @Getter
    private final TopHitsAggregationBuilder lastByDateDocAggregation;
    @Getter
    private final SumBucketPipelineAggregationBuilder storageUsageTotalAggregation;
    @Getter
    private final ValueCountAggregationBuilder uniqueRunsAggregation;
    @Getter
    private final TopHitsAggregationBuilder topHitsAggregation;

    public BillingHelper(final AuthManager authManager,
                         final @Value("${billing.index.common.prefix}") String commonPrefix) {
        this.authManager = authManager;
        this.billingIndicesMonthlyPattern = String.join("-",
                commonPrefix,
                ES_WILDCARD,
                ES_MONTHLY_DATE_REGEXP);
        this.costAggregation = AggregationBuilders.sum(COST_FIELD).field(COST_FIELD);
        this.runUsageAggregation = AggregationBuilders.sum(RUN_USAGE_AGG).field(RUN_USAGE_FIELD);
        this.storageUsageGroupingAggregation = AggregationBuilders
                .terms(STORAGE_GROUPING_AGG).field(STORAGE_ID_FIELD).size(Integer.MAX_VALUE)
                .subAggregation(AggregationBuilders.avg(SINGLE_STORAGE_USAGE_AGG).field(STORAGE_USAGE_FIELD));
        this.lastByDateDocAggregation = AggregationBuilders.topHits(BUCKET_DOCUMENTS)
                .size(1)
                .fetchSource(STORAGE_USAGE_FIELD, null)
                .sort(BILLING_DATE_FIELD, SortOrder.DESC);
        this.storageUsageTotalAggregation = PipelineAggregatorBuilders
                .sumBucket(TOTAL_STORAGE_USAGE_AGG,
                        String.format("%s.%s", STORAGE_GROUPING_AGG, SINGLE_STORAGE_USAGE_AGG));
        this.uniqueRunsAggregation = AggregationBuilders.count(UNIQUE_RUNS).field(RUN_ID_FIELD);
        this.topHitsAggregation = AggregationBuilders.topHits(TOP_HITS_AGG)
                .size(1)
                .sort(BILLING_DATE_FIELD, SortOrder.DESC);
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

    public void setFiltersAndPeriodForSearchRequest(final LocalDate from, final LocalDate to,
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
        searchSource.size(0);
        searchRequest.source(searchSource);
    }

    public String asString(final Object value) {
        return value != null ? value.toString() : null;
    }

    public String asString(final LocalDateTime value) {
        return value != null ? DATE_TIME_FORMATTER.format(value) : null;
    }

    public LocalDateTime asDateTime(final Object value) {
        return value != null ? DATE_TIME_FORMATTER.parse(value.toString(), LocalDateTime::from) : null;
    }

    public Optional<Long> getSumLongValue(final Aggregations aggregations, final String aggregation) {
        return sumDoubleValue(aggregations, aggregation).map(Double::longValue);
    }

    public Optional<Double> sumDoubleValue(final Aggregations aggregations, final String aggregation) {
        return Optional.ofNullable(aggregations.get(aggregation))
                .filter(ParsedSum.class::isInstance)
                .map(ParsedSum.class::cast)
                .map(ParsedSum::getValue)
                .filter(it -> !it.isInfinite());
    }

    public Optional<Integer> getCardinalityIntValue(final Aggregations aggregations, final String aggregation) {
        return Optional.ofNullable(aggregations.get(aggregation))
                .filter(Cardinality.class::isInstance)
                .map(Cardinality.class::cast)
                .map(Cardinality::getValue)
                .map(Long::intValue);
    }

    public Map<String, Object> getTopHitSourceMap(final Aggregations aggregations, final String aggregation) {
        return Optional.ofNullable(aggregations.get(aggregation))
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

}
