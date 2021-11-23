package com.epam.pipeline.manager.billing;

import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
import com.epam.pipeline.controller.vo.billing.BillingExportType;
import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.entity.billing.UserGeneralReportBilling;
import com.epam.pipeline.entity.billing.GeneralReportYearMonthBilling;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.exception.search.SearchException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.utils.GlobalSearchElasticHelper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedDateHistogram;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedTerms;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilders;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.sum.SumBucketPipelineAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserGeneralReportExporter implements BillingExporter {

    private static final String DOC_TYPE = "doc_type";
    private static final String SYNTHETIC_TOTAL_USER = "Grand total";
    private static final String MISSING_USER = "unknown";

    @Getter
    private final BillingExportType type = BillingExportType.USER;
    private final BillingHelper billingHelper;
    private final GlobalSearchElasticHelper elasticHelper;
    private final PreferenceManager preferenceManager;
    private final UserBillingDetailsLoader userBillingDetailsLoader;

    @Override
    public void export(final BillingExportRequest request, final Writer writer) {
        final UserGeneralReportWriter billingWriter = new UserGeneralReportWriter(writer,
                billingHelper, preferenceManager, request.getFrom(), request.getTo());
        try (RestHighLevelClient elasticSearchClient = elasticHelper.buildClient()) {
            billingWriter.writeHeader();
            billings(elasticSearchClient, request).forEach(billingWriter::write);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new SearchException(e.getMessage(), e);
        } finally {
            try {
                billingWriter.flush();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    private Stream<UserGeneralReportBilling> billings(final RestHighLevelClient elasticSearchClient,
                                                      final BillingExportRequest request) {
        final LocalDate from = request.getFrom();
        final LocalDate to = request.getTo();
        final Map<String, List<String>> filters = billingHelper.getFilters(request.getFilters());
        return billings(elasticSearchClient, from, to, filters);
    }

    private Stream<UserGeneralReportBilling> billings(final RestHighLevelClient elasticSearchClient,
                                                      final LocalDate from,
                                                      final LocalDate to,
                                                      final Map<String, List<String>> filters) {
        return Optional.of(getRequest(from, to, filters))
                .map(billingHelper.searchWith(elasticSearchClient))
                .map(this::billings)
                .orElseGet(Stream::empty);
    }

    private Stream<UserGeneralReportBilling> billings(final SearchResponse response) {
        final Stream<UserGeneralReportBilling> billings = Optional.ofNullable(response.getAggregations())
                .map(it -> it.get(BillingGrouping.USER.getCorrespondingField()))
                .filter(ParsedStringTerms.class::isInstance)
                .map(ParsedStringTerms.class::cast)
                .map(ParsedTerms::getBuckets)
                .map(Collection::stream)
                .orElse(Stream.empty())
                .map(bucket -> getBilling(bucket.getKeyAsString(), bucket.getAggregations()))
                .map(this::withDetails);
        final Stream<UserGeneralReportBilling> totalBillings = Stream.of(getBilling(SYNTHETIC_TOTAL_USER,
                response.getAggregations()));
        return Stream.concat(billings, totalBillings);
    }

    private UserGeneralReportBilling withDetails(final UserGeneralReportBilling billing) {
        final Map<String, String> details = userBillingDetailsLoader.loadInformation(billing.getUser(), true);
        return billing.toBuilder()
                .billingCenter(details.get(BillingGrouping.BILLING_CENTER.getCorrespondingField()))
                .build();
    }

    private UserGeneralReportBilling getBilling(final String name, final Aggregations aggregations) {
        return UserGeneralReportBilling.builder()
                .user(name)
                .runsNumber(billingHelper.getRunCount(aggregations).orElse(NumberUtils.LONG_ZERO))
                .runsDuration(billingHelper.getRunUsageSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                .runsCost(billingHelper.getRunCostSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                .storagesCost(billingHelper.getStorageCostSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                .billings(getYearMonthBillings(aggregations))
                .build();
    }

    private Map<YearMonth, GeneralReportYearMonthBilling> getYearMonthBillings(final Aggregations aggregations) {
        return Optional.ofNullable(aggregations)
                .map(it -> it.get(BillingHelper.HISTOGRAM_AGGREGATION_NAME))
                .filter(ParsedDateHistogram.class::isInstance)
                .map(ParsedDateHistogram.class::cast)
                .map(ParsedDateHistogram::getBuckets)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .map(bucket -> getYearMonthBilling(bucket.getKeyAsString(), bucket.getAggregations()))
                .collect(Collectors.toMap(GeneralReportYearMonthBilling::getYearMonth, Function.identity()));
    }

    private GeneralReportYearMonthBilling getYearMonthBilling(final String ym, final Aggregations aggregations) {
        return GeneralReportYearMonthBilling.builder()
                .yearMonth(YearMonth.parse(ym, DateTimeFormatter.ofPattern(BillingHelper.HISTOGRAM_AGGREGATION_FORMAT)))
                .runsNumber(billingHelper.getRunCount(aggregations).orElse(NumberUtils.LONG_ZERO))
                .runsDuration(billingHelper.getRunUsageSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                .runsCost(billingHelper.getFilteredRunCostSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                .storagesCost(billingHelper.getFilteredStorageCostSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                .build();
    }

    private SearchRequest getRequest(final LocalDate from,
                                     final LocalDate to,
                                     final Map<String, List<String>> filters) {
        return new SearchRequest()
                .indicesOptions(IndicesOptions.strictExpandOpen())
                .indices(billingHelper.indicesByDate(from, to))
                .source(new SearchSourceBuilder()
                        .size(0)
                        .query(billingHelper.queryByDateAndFilters(from, to, filters))
                        .aggregation(billingHelper.aggregateBy(BillingGrouping.USER.getCorrespondingField())
                                .size(Integer.MAX_VALUE)
                                .missing(MISSING_USER)
                                .subAggregation(aggregateBillingsByMonth())
                                .subAggregation(aggregateRunCountSumBucket())
                                .subAggregation(aggregateRunUsageSumBucket())
                                .subAggregation(aggregateRunCostSumBucket())
                                .subAggregation(aggregateStorageCostSumBucket()))
                        .aggregation(aggregateBillingsByMonth())
                        .aggregation(aggregateRunCountSumBucket())
                        .aggregation(aggregateRunUsageSumBucket())
                        .aggregation(aggregateRunCostSumBucket())
                        .aggregation(aggregateStorageCostSumBucket()));
    }

    private DateHistogramAggregationBuilder aggregateBillingsByMonth() {
        return billingHelper.aggregateByMonth()
                .subAggregation(billingHelper.aggregateUniqueRunsCount())
                .subAggregation(billingHelper.aggregateRunUsageSum())
                .subAggregation(AggregationBuilders.filter(BillingHelper.RUN_COST_AGG,
                                QueryBuilders.termsQuery(DOC_TYPE,
                                        SearchDocumentType.PIPELINE_RUN.name()))
                        .subAggregation(billingHelper.aggregateCostSum()))
                .subAggregation(AggregationBuilders.filter(BillingHelper.STORAGE_COST_AGG,
                                QueryBuilders.termsQuery(DOC_TYPE,
                                        SearchDocumentType.S3_STORAGE.name(),
                                        SearchDocumentType.AZ_BLOB_STORAGE.name(),
                                        SearchDocumentType.NFS_STORAGE.name(),
                                        SearchDocumentType.GS_STORAGE.name()))
                        .subAggregation(billingHelper.aggregateCostSum()));
    }

    private SumBucketPipelineAggregationBuilder aggregateRunCountSumBucket() {
        return PipelineAggregatorBuilders
                .sumBucket(BillingHelper.RUN_COUNT_AGG,
                        String.join(BillingHelper.ES_DOC_AGGS_SEPARATOR, BillingHelper.HISTOGRAM_AGGREGATION_NAME, BillingHelper.RUN_COUNT_AGG));
    }

    private SumBucketPipelineAggregationBuilder aggregateRunUsageSumBucket() {
        return PipelineAggregatorBuilders
                .sumBucket(BillingHelper.RUN_USAGE_AGG,
                        String.join(BillingHelper.ES_DOC_AGGS_SEPARATOR, BillingHelper.HISTOGRAM_AGGREGATION_NAME, BillingHelper.RUN_USAGE_AGG));
    }

    private SumBucketPipelineAggregationBuilder aggregateRunCostSumBucket() {
        return PipelineAggregatorBuilders
                .sumBucket(BillingHelper.RUN_COST_AGG,
                        String.join(BillingHelper.ES_DOC_AGGS_SEPARATOR, BillingHelper.HISTOGRAM_AGGREGATION_NAME, BillingHelper.RUN_COST_AGG, BillingHelper.COST_FIELD));
    }

    private SumBucketPipelineAggregationBuilder aggregateStorageCostSumBucket() {
        return PipelineAggregatorBuilders
                .sumBucket(BillingHelper.STORAGE_COST_AGG,
                        String.join(BillingHelper.ES_DOC_AGGS_SEPARATOR, BillingHelper.HISTOGRAM_AGGREGATION_NAME, BillingHelper.STORAGE_COST_AGG, BillingHelper.COST_FIELD));
    }
}
