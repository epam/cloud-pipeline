package com.epam.pipeline.manager.billing;

import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
import com.epam.pipeline.controller.vo.billing.BillingExportType;
import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.entity.billing.UserGeneralReportBilling;
import com.epam.pipeline.entity.billing.UserGeneralReportYearMonthBilling;
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
import org.elasticsearch.client.RestClient;
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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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
    private static final String GRAND_TOTAL_SYNTHETIC_USER = "Grand total";

    @Getter
    private final BillingExportType type = BillingExportType.USER_GENERAL_REPORT;
    private final BillingHelper billingHelper;
    private final GlobalSearchElasticHelper elasticHelper;
    private final PreferenceManager preferenceManager;
    private final UserBillingDetailsLoader userBillingDetailsLoader;

    @Override
    public void export(final BillingExportRequest request, final OutputStream out) {
        try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(out);
             BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter);
             UserGeneralReportWriter writer = new UserGeneralReportWriter(bufferedWriter, billingHelper,
                     preferenceManager, request.getFrom(), request.getTo());
             RestClient elasticSearchLowLevelClient = elasticHelper.buildLowLevelClient()) {
            final RestHighLevelClient elasticSearchClient = new RestHighLevelClient(elasticSearchLowLevelClient);
            writer.writeHeader();
            userGeneralReportBillings(request, elasticSearchClient).forEach(writer::write);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new SearchException(e.getMessage(), e);
        }
    }

    private Stream<UserGeneralReportBilling> userGeneralReportBillings(final BillingExportRequest request,
                                                                       final RestHighLevelClient elasticSearchClient) {
        final LocalDate from = request.getFrom();
        final LocalDate to = request.getTo();
        final Map<String, List<String>> filters = billingHelper.getFilters(request.getFilters());
        return userGeneralReportBillings(elasticSearchClient, from, to, filters);
    }

    private Stream<UserGeneralReportBilling> userGeneralReportBillings(final RestHighLevelClient elasticSearchClient,
                                                                       final LocalDate from,
                                                                       final LocalDate to,
                                                                       final Map<String, List<String>> filters) {
        return Optional.of(getUserGeneralReportDataRequest(from, to, filters))
                .map(billingHelper.searchWith(elasticSearchClient))
                .map(this::userGeneralReportBillings)
                .orElseGet(Stream::empty);
    }

    private Stream<UserGeneralReportBilling> userGeneralReportBillings(final SearchResponse response) {
        final Stream<UserGeneralReportBilling> userBillings = Optional.ofNullable(response.getAggregations())
                .map(it -> it.get(BillingGrouping.USER.getCorrespondingField()))
                .filter(ParsedStringTerms.class::isInstance)
                .map(ParsedStringTerms.class::cast)
                .map(ParsedTerms::getBuckets)
                .map(Collection::stream)
                .orElse(Stream.empty())
                .map(bucket -> getUserGeneralReportBilling(bucket.getKeyAsString(), bucket.getAggregations()))
                .map(this::withBillingCenter);
        final Stream<UserGeneralReportBilling> grandTotalBillings = Stream.of(getUserGeneralReportBilling(
                GRAND_TOTAL_SYNTHETIC_USER, response.getAggregations()));
        return Stream.concat(userBillings, grandTotalBillings);
    }

    private UserGeneralReportBilling withBillingCenter(final UserGeneralReportBilling billing) {
        return billing.toBuilder().billingCenter(getBillingCenter(billing.getUser())).build();
    }

    private String getBillingCenter(final String user) {
        return userBillingDetailsLoader.loadInformation(user, true)
                .get(BillingGrouping.BILLING_CENTER.getCorrespondingField());
    }

    private UserGeneralReportBilling getUserGeneralReportBilling(final String user, final Aggregations aggregations) {
        return UserGeneralReportBilling.builder()
                .user(user)
                .runsNumber(billingHelper.getRunCount(aggregations).orElse(NumberUtils.LONG_ZERO))
                .runsDuration(billingHelper.getRunUsageSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                .runsCost(billingHelper.getRunCostSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                .storagesCost(billingHelper.getStorageCostSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                .billings(getUserGeneralReportMonthBillings(aggregations))
                .build();
    }

    private Map<YearMonth, UserGeneralReportYearMonthBilling> getUserGeneralReportMonthBillings(
            final Aggregations aggregations) {
        return Optional.ofNullable(aggregations)
                .map(it -> it.get(BillingHelper.HISTOGRAM_AGGREGATION_NAME))
                .filter(ParsedDateHistogram.class::isInstance)
                .map(ParsedDateHistogram.class::cast)
                .map(ParsedDateHistogram::getBuckets)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .map(bucket -> getUserGeneralReportMonthBilling(bucket.getKeyAsString(), bucket.getAggregations()))
                .collect(Collectors.toMap(UserGeneralReportYearMonthBilling::getYearMonth, Function.identity()));
    }

    private UserGeneralReportYearMonthBilling getUserGeneralReportMonthBilling(final String ymString,
                                                                               final Aggregations aggregations) {
        final YearMonth ym = YearMonth.parse(ymString, DateTimeFormatter.ofPattern("yyyy-MM"));
        return UserGeneralReportYearMonthBilling.builder()
                .yearMonth(ym)
                .runsNumber(billingHelper.getRunCount(aggregations).orElse(NumberUtils.LONG_ZERO))
                .runsDuration(billingHelper.getRunUsageSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                .runsCost(billingHelper.getFilteredRunCostSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                .storagesCost(billingHelper.getFilteredStorageCostSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                .build();
    }

    private SearchRequest getUserGeneralReportDataRequest(final LocalDate from,
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
                                .subAggregation(aggregateUserGeneralReportBillingsByMonth())
                                .subAggregation(aggregateRunCountSumBucket())
                                .subAggregation(aggregateRunUsageSumBucket())
                                .subAggregation(aggregateRunCostSumBucket())
                                .subAggregation(aggregateStorageCostSumBucket()))
                        .aggregation(aggregateUserGeneralReportBillingsByMonth())
                        .aggregation(aggregateRunCountSumBucket())
                        .aggregation(aggregateRunUsageSumBucket())
                        .aggregation(aggregateRunCostSumBucket())
                        .aggregation(aggregateStorageCostSumBucket()));
    }

    private DateHistogramAggregationBuilder aggregateUserGeneralReportBillingsByMonth() {
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
