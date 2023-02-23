package com.epam.pipeline.manager.billing;

import com.epam.pipeline.controller.vo.billing.BillingCostDetailsRequest;
import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
import com.epam.pipeline.entity.billing.BillingDiscount;
import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.entity.billing.StorageBilling;
import com.epam.pipeline.entity.billing.StorageBillingMetrics;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.manager.billing.billingdetails.StorageBillingCostDetailsLoader;
import com.epam.pipeline.manager.billing.detail.EntityBillingDetailsLoader;
import com.epam.pipeline.manager.billing.detail.StorageBillingDetailsLoader;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.StreamUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageBillingLoader implements BillingLoader<StorageBilling> {

    private final BillingHelper billingHelper;
    private final PreferenceManager preferenceManager;
    private final EntityBillingDetailsLoader storageBillingDetailsLoader;

    @Override
    public Stream<StorageBilling> billings(final RestHighLevelClient elasticSearchClient,
                                           final BillingExportRequest request) {
        final LocalDate from = request.getFrom();
        final LocalDate to = request.getTo();
        final Map<String, List<String>> filters = billingHelper.getFilters(request.getFilters());
        final BillingDiscount discount = Optional.ofNullable(request.getDiscount()).orElseGet(BillingDiscount::empty);
        return billings(elasticSearchClient, from, to, filters, discount, getPageSize());
    }

    private int getPageSize() {
        return Optional.of(SystemPreferences.BILLING_EXPORT_PERIOD_AGGREGATION_PAGE_SIZE)
                .map(preferenceManager::getPreference)
                .orElse(BillingUtils.FALLBACK_EXPORT_PERIOD_AGGREGATION_PAGE_SIZE);
    }

    private Stream<StorageBilling> billings(final RestHighLevelClient elasticSearchClient,
                                            final LocalDate from,
                                            final LocalDate to,
                                            final Map<String, List<String>> filters,
                                            final BillingDiscount discount,
                                            final int pageSize) {
        return StreamUtils.from(billingsIterator(elasticSearchClient, from, to, filters, discount, pageSize))
                .flatMap(this::billings);
    }

    private Iterator<SearchResponse> billingsIterator(final RestHighLevelClient elasticSearchClient,
                                                      final LocalDate from,
                                                      final LocalDate to,
                                                      final Map<String, List<String>> filters,
                                                      final BillingDiscount discount,
                                                      final int pageSize) {
        return new ElasticMultiBucketsIterator(BillingUtils.STORAGE_ID_FIELD, pageSize,
            pageOffset -> getBillingsRequest(from, to, filters, discount, pageOffset, pageSize),
            billingHelper.searchWith(elasticSearchClient),
            billingHelper::getTerms);
    }

    private SearchRequest getBillingsRequest(final LocalDate from,
                                             final LocalDate to,
                                             final Map<String, List<String>> filters,
                                             final BillingDiscount discount,
                                             final int pageOffset,
                                             final int pageSize) {
        final TermsAggregationBuilder byStorageIdAggregate = billingHelper.aggregateBy(BillingUtils.STORAGE_ID_FIELD)
                .size(Integer.MAX_VALUE)
                .subAggregation(billingHelper.aggregateCostSumBucket())
                .subAggregation(billingHelper.aggregateStorageUsageAverageBucket())
                .subAggregation(billingHelper.aggregateLastByDateDoc())
                .subAggregation(billingHelper.aggregateCostSortBucket(pageOffset, pageSize));

        final BillingCostDetailsRequest costDetailsRequest = BillingCostDetailsRequest.builder()
                .enabled(true).discount(discount).grouping(BillingGrouping.STORAGE).build();
        StorageBillingCostDetailsLoader.buildQuery(costDetailsRequest, byStorageIdAggregate);

        final DateHistogramAggregationBuilder byMonth = aggregateBillingsByMonth(discount);
        StorageBillingCostDetailsLoader.buildQuery(costDetailsRequest, byMonth);
        byStorageIdAggregate.subAggregation(byMonth);

        return new SearchRequest()
                .indicesOptions(IndicesOptions.strictExpandOpen())
                .indices(billingHelper.storageIndicesByDate(from, to))
                .source(new SearchSourceBuilder()
                        .size(NumberUtils.INTEGER_ZERO)
                        .query(billingHelper.queryByDateAndFilters(from, to, filters))
                        .aggregation(byStorageIdAggregate));
    }

    private DateHistogramAggregationBuilder aggregateBillingsByMonth(final BillingDiscount discount) {
        return billingHelper.aggregateByMonth()
                .subAggregation(billingHelper.aggregateCostSum(discount.getStorages()))
                .subAggregation(billingHelper.aggregateStorageUsageAvg())
                .subAggregation(billingHelper.aggregateLastByDateDoc());
    }

    private Stream<StorageBilling> billings(final SearchResponse response) {
        return billingHelper.termBuckets(response.getAggregations(), BillingUtils.STORAGE_ID_FIELD)
                .map(bucket -> getBilling(bucket.getKeyAsString(), bucket.getAggregations()))
                .map(this::withDetails);
    }

    private StorageBilling getBilling(final String id, final Aggregations aggregations) {
        final Map<String, Object> topHitFields = billingHelper.getLastByDateDocFields(aggregations);
        final BillingCostDetailsRequest costDetailsRequest = BillingCostDetailsRequest.builder()
                .enabled(true).grouping(BillingGrouping.STORAGE).build();
        return StorageBilling.builder()
                .id(NumberUtils.toLong(id))
                .name(BillingUtils.asString(topHitFields.get(BillingUtils.STORAGE_NAME_FIELD)))
                .owner(BillingUtils.asString(topHitFields.get(BillingUtils.OWNER_FIELD)))
                .billingCenter(BillingUtils.asString(topHitFields.get(BillingUtils.BILLING_CENTER_FIELD)))
                .type(DataStorageType.getByName(BillingUtils.asString(topHitFields.get(BillingUtils.PROVIDER_FIELD))))
                .region(BillingUtils.asString(topHitFields.get(BillingUtils.CLOUD_REGION_ID_FIELD)))
                .provider(BillingUtils.asString(topHitFields.get(BillingUtils.CLOUD_REGION_PROVIDER_FIELD)))
                .created(asDateTime(BillingUtils.asString(topHitFields.get(BillingUtils.STORAGE_CREATED_FIELD))))
                .totalMetrics(StorageBillingMetrics.builder()
                        .cost(billingHelper.getCostSum(aggregations))
                        .averageVolume(billingHelper.getStorageUsageAvg(aggregations))
                        .currentVolume(Long.valueOf(BillingUtils.asString(
                                topHitFields.get(BillingUtils.STORAGE_USAGE_FIELD))))
                        .details(StorageBillingCostDetailsLoader.parseResponse(costDetailsRequest, aggregations))
                        .build())
                .periodMetrics(getMetrics(aggregations, costDetailsRequest))
                .build();
    }

    private Map<Temporal, StorageBillingMetrics> getMetrics(final Aggregations aggregations,
                                                            final BillingCostDetailsRequest costDetailsRequest) {
        return billingHelper.histogramBuckets(aggregations, BillingUtils.HISTOGRAM_AGGREGATION_NAME)
                .map(bucket -> getMetrics(bucket.getKeyAsString(), costDetailsRequest, bucket.getAggregations()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }

    private Pair<Temporal, StorageBillingMetrics> getMetrics(final String period,
                                                             final BillingCostDetailsRequest costDetailsRequest,
                                                             final Aggregations aggregations) {
        final Map<String, Object> topHitFields = billingHelper.getLastByDateDocFields(aggregations);
        return Pair.of(
                YearMonth.parse(period, DateTimeFormatter.ofPattern(BillingUtils.HISTOGRAM_AGGREGATION_FORMAT)),
                StorageBillingMetrics.builder()
                        .cost(billingHelper.getCostSum(aggregations))
                        .averageVolume(billingHelper.getStorageUsageAvg(aggregations))
                        .currentVolume(Long.valueOf(BillingUtils.asString(
                                topHitFields.get(BillingUtils.STORAGE_USAGE_FIELD))))
                        .details(StorageBillingCostDetailsLoader.parseResponse(costDetailsRequest, aggregations))
                        .build());
    }

    private StorageBilling withDetails(final StorageBilling billing) {
        if (StringUtils.isNotBlank(billing.getName())
                && StringUtils.isNotBlank(billing.getRegion())
                && StringUtils.isNotBlank(billing.getProvider())
                && Objects.nonNull(billing.getCreated())) {
            return billing;
        }
        final Map<String, String> details = storageBillingDetailsLoader.loadDetails(billing.getId().toString());
        return billing.toBuilder()
                .name(StringUtils.isNotBlank(billing.getName())
                        ? billing.getName()
                        : details.get(StorageBillingDetailsLoader.STORAGE_NAME))
                .region(StringUtils.isNotBlank(billing.getRegion())
                        ? billing.getRegion()
                        : details.get(EntityBillingDetailsLoader.REGION))
                .provider(StringUtils.isNotBlank(billing.getProvider())
                        ? billing.getProvider()
                        : details.get(EntityBillingDetailsLoader.PROVIDER))
                .created(Objects.nonNull(billing.getCreated())
                        ? billing.getCreated()
                        : asDateTime(details.get(EntityBillingDetailsLoader.CREATED)))
                .build();
    }

    private LocalDateTime asDateTime(final String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
