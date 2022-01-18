package com.epam.pipeline.manager.billing;

import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
import com.epam.pipeline.entity.billing.BillingDiscount;
import com.epam.pipeline.entity.billing.RunBilling;
import com.epam.pipeline.manager.billing.index.BillingIndexHelper;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.StreamUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunBillingLoader implements BillingLoader<RunBilling> {

    private final BillingHelper billingHelper;
    private final BillingIndexHelper billingIndexHelper;
    private final PreferenceManager preferenceManager;

    @Override
    public Stream<RunBilling> billings(final RestHighLevelClient client,
                                       final BillingExportRequest request) {
        final LocalDate from = request.getFrom();
        final LocalDate to = request.getTo();
        final Map<String, List<String>> filters = billingHelper.getFilters(request.getFilters());
        final BillingDiscount discount = Optional.ofNullable(request.getDiscount()).orElseGet(BillingDiscount::empty);
        return billings(client, from, to, filters, discount, getPageSize());
    }

    private int getPageSize() {
        return Optional.of(SystemPreferences.BILLING_EXPORT_AGGREGATION_PAGE_SIZE)
                .map(preferenceManager::getPreference)
                .orElse(BillingUtils.FALLBACK_EXPORT_AGGREGATION_PAGE_SIZE);
    }

    private Stream<RunBilling> billings(final RestHighLevelClient client,
                                        final LocalDate from,
                                        final LocalDate to,
                                        final Map<String, List<String>> filters,
                                        final BillingDiscount discount,
                                        final int pageSize) {
        final String[] indices = billingIndexHelper.yearlyRunIndicesBetween(from, to);
        final LowLevelBillingLoader<RunBilling> loader = indices.length > 1
                ? new MultiIndexRunBillingLoader(billingHelper)
                : new SingleIndexRunBillingLoader(billingHelper);
        return loader.billings(client, indices, from, to, filters, discount, pageSize);
    }

    @RequiredArgsConstructor
    public static class SingleIndexRunBillingLoader implements LowLevelBillingLoader<RunBilling> {

        private final BillingHelper billingHelper;

        @Override
        public Stream<RunBilling> billings(final RestHighLevelClient client,
                                           final String[] indices,
                                           final LocalDate from,
                                           final LocalDate to,
                                           final Map<String, List<String>> filters,
                                           final BillingDiscount discount,
                                           final int pageSize) {
            return StreamUtils.from(iterator(client, indices, from, to, filters, discount, pageSize))
                    .flatMap(response -> billings(response, discount));
        }

        private Iterator<SearchResponse> iterator(final RestHighLevelClient client,
                                                  final String[] indices,
                                                  final LocalDate from,
                                                  final LocalDate to,
                                                  final Map<String, List<String>> filters,
                                                  final BillingDiscount discount,
                                                  final int pageSize) {
            return new ElasticDocumentsIterator(pageSize,
                    searchAfter -> getRequest(indices, from, to, filters, discount, searchAfter, pageSize),
                    billingHelper.searchWith(client));
        }

        private SearchRequest getRequest(final String[] indices,
                                         final LocalDate from,
                                         final LocalDate to,
                                         final Map<String, List<String>> filters,
                                         final BillingDiscount discount,
                                         final Object[] searchAfter,
                                         final int pageSize) {
            final SearchSourceBuilder source = new SearchSourceBuilder()
                    .size(pageSize)
                    .query(billingHelper.queryByDateAndFilters(from, to, filters))
                    .sort(SortBuilders.fieldSort(BillingUtils.COST_FIELD).order(SortOrder.DESC))
                    .sort(SortBuilders.fieldSort(BillingUtils.DOC_ID_FIELD).order(SortOrder.DESC));
            Optional.ofNullable(searchAfter).ifPresent(source::searchAfter);
            return new SearchRequest()
                    .indicesOptions(IndicesOptions.lenientExpandOpen())
                    .indices(indices)
                    .source(source);
        }

        private Stream<RunBilling> billings(final SearchResponse response, final BillingDiscount discount) {
            return Optional.ofNullable(response)
                    .map(SearchResponse::getHits)
                    .map(SearchHits::getHits)
                    .map(Arrays::stream)
                    .orElseGet(Stream::empty)
                    .map(hit -> getBilling(hit, discount));
        }

        private RunBilling getBilling(final SearchHit hit, final BillingDiscount discount) {
            final Map<String, Object> topHitFields = MapUtils.emptyIfNull(hit.getSourceAsMap());
            return RunBilling.builder()
                    .runId(NumberUtils.toLong(BillingUtils.asString(topHitFields.get(BillingUtils.RUN_ID_FIELD))))
                    .owner(BillingUtils.asString(topHitFields.get(BillingUtils.OWNER_FIELD)))
                    .billingCenter(BillingUtils.asString(topHitFields.get(BillingUtils.BILLING_CENTER_FIELD)))
                    .pipeline(BillingUtils.asString(topHitFields.get(BillingUtils.PIPELINE_NAME_FIELD)))
                    .tool(BillingUtils.asString(topHitFields.get(BillingUtils.TOOL_FIELD)))
                    .computeType(BillingUtils.asString(topHitFields.get(BillingUtils.COMPUTE_TYPE_FIELD)))
                    .instanceType(BillingUtils.asString(topHitFields.get(BillingUtils.INSTANCE_TYPE_FIELD)))
                    .started(BillingUtils.asDateTime(topHitFields.get(BillingUtils.STARTED_FIELD)))
                    .finished(BillingUtils.asDateTime(topHitFields.get(BillingUtils.FINISHED_FIELD)))
                    .duration(NumberUtils.toLong(BillingUtils.asString(topHitFields.get(BillingUtils.RUN_USAGE_FIELD))))
                    .cost(BillingUtils.withDiscount(discount.getComputes(), NumberUtils.toLong(BillingUtils.asString(
                            topHitFields.get(BillingUtils.COST_FIELD)))))
                    .build();
        }
    }

    @RequiredArgsConstructor
    public static class MultiIndexRunBillingLoader implements LowLevelBillingLoader<RunBilling> {

        private final BillingHelper billingHelper;

        @Override
        public Stream<RunBilling> billings(final RestHighLevelClient client,
                                           final String[] indices,
                                           final LocalDate from,
                                           final LocalDate to,
                                           final Map<String, List<String>> filters,
                                           final BillingDiscount discount,
                                           final int pageSize) {
            return StreamUtils.from(iterator(client, indices, from, to, filters, discount, pageSize))
                    .flatMap(documents -> billings(documents, discount));
        }

        private Iterator<SearchResponse> iterator(final RestHighLevelClient client,
                                                  final String[] indices,
                                                  final LocalDate from,
                                                  final LocalDate to,
                                                  final Map<String, List<String>> filters,
                                                  final BillingDiscount discount,
                                                  final int pageSize) {
            return new ElasticMultiBucketsIterator(BillingUtils.RUN_ID_FIELD, pageSize,
                    pageOffset -> getRequest(indices, from, to, filters, discount, pageOffset, pageSize),
                    billingHelper.searchWith(client),
                    billingHelper::getTerms);
        }

        private SearchRequest getRequest(final String[] indices,
                                         final LocalDate from,
                                         final LocalDate to,
                                         final Map<String, List<String>> filters,
                                         final BillingDiscount discount,
                                         final int pageOffset,
                                         final int pageSize) {
            return new SearchRequest()
                    .indicesOptions(IndicesOptions.lenientExpandOpen())
                    .indices(indices)
                    .source(new SearchSourceBuilder()
                            .size(NumberUtils.INTEGER_ZERO)
                            .query(billingHelper.queryByDateAndFilters(from, to, filters))
                            .aggregation(billingHelper.aggregateBy(BillingUtils.RUN_ID_FIELD)
                                    .size(Integer.MAX_VALUE)
                                    .subAggregation(billingHelper.aggregateCostSum(discount.getComputes()))
                                    .subAggregation(billingHelper.aggregateRunUsageSum())
                                    .subAggregation(billingHelper.aggregateLastByDateDoc())
                                    .subAggregation(billingHelper.aggregateCostSortBucket(pageOffset, pageSize))));
        }

        private Stream<RunBilling> billings(final SearchResponse response, final BillingDiscount discount) {
            return billingHelper.termBuckets(response.getAggregations(), BillingUtils.RUN_ID_FIELD)
                    .map(bucket -> getBilling(bucket.getKeyAsString(), bucket.getAggregations()));
        }

        private RunBilling getBilling(final String id, final Aggregations aggregations) {
            final Map<String, Object> topHitFields = billingHelper.getLastByDateDocFields(aggregations);
            return RunBilling.builder()
                    .runId(NumberUtils.toLong(id))
                    .owner(BillingUtils.asString(topHitFields.get(BillingUtils.OWNER_FIELD)))
                    .billingCenter(BillingUtils.asString(topHitFields.get(BillingUtils.BILLING_CENTER_FIELD)))
                    .pipeline(BillingUtils.asString(topHitFields.get(BillingUtils.PIPELINE_NAME_FIELD)))
                    .tool(BillingUtils.asString(topHitFields.get(BillingUtils.TOOL_FIELD)))
                    .computeType(BillingUtils.asString(topHitFields.get(BillingUtils.COMPUTE_TYPE_FIELD)))
                    .instanceType(BillingUtils.asString(topHitFields.get(BillingUtils.INSTANCE_TYPE_FIELD)))
                    .started(BillingUtils.asDateTime(topHitFields.get(BillingUtils.STARTED_FIELD)))
                    .finished(BillingUtils.asDateTime(topHitFields.get(BillingUtils.FINISHED_FIELD)))
                    .duration(billingHelper.getRunUsageSum(aggregations))
                    .cost(billingHelper.getCostSum(aggregations))
                    .build();
        }
    }

}
