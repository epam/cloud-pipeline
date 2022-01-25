package com.epam.pipeline.billingreportagent.service.impl.synchronizer.merging;

import com.epam.pipeline.billingreportagent.model.EntityDocument;
import com.epam.pipeline.billingreportagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.billingreportagent.utils.BillingHelper;
import com.epam.pipeline.billingreportagent.utils.BillingUtils;
import com.epam.pipeline.utils.StreamUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class RunBillingDocumentLoader implements EntityDocumentLoader {

    private final ElasticsearchServiceClient client;
    private final BillingHelper billingHelper;
    private final int pageSize;

    @Override
    public Stream<EntityDocument> documents(final LocalDate from, final LocalDate to,
                                            final String[] indices) {
        return StreamUtils.from(iterator(from, to, indices))
                .flatMap(this::billings);
    }

    private Iterator<SearchResponse> iterator(final LocalDate from, final LocalDate to, final String[] indices) {
        return new ElasticMultiBucketsIterator(BillingUtils.RUN_ID_FIELD, pageSize,
            pageOffset -> getRequest(from, to, indices, pageOffset, pageSize),
            client::search,
            billingHelper::getTerms);
    }

    private SearchRequest getRequest(final LocalDate from, final LocalDate to,
                                     final String[] indices,
                                     final int offset,
                                     final int size) {
        return new SearchRequest()
                .indicesOptions(IndicesOptions.lenientExpandOpen())
                .indices(indices)
                .source(new SearchSourceBuilder()
                        .query(billingHelper.queryByDate(from, to))
                        .size(0)
                        .aggregation(billingHelper.aggregateBy(BillingUtils.RUN_ID_FIELD)
                                .size(Integer.MAX_VALUE)
                                .subAggregation(billingHelper.aggregateCostSum())
                                .subAggregation(billingHelper.aggregateCostSortBucket(offset, size))
                                .subAggregation(billingHelper.aggregateBy(BillingUtils.BILLING_CENTER_FIELD)
                                        .size(Integer.MAX_VALUE)
                                        .missing(StringUtils.EMPTY)
                                        .subAggregation(billingHelper.aggregateCostSum())
                                        .subAggregation(billingHelper.aggregateRunUsageSum())
                                        .subAggregation(billingHelper.aggregateLastByDateDoc()))));
    }

    private Stream<EntityDocument> billings(final SearchResponse response) {
        return billingHelper.termBuckets(response.getAggregations(), BillingUtils.RUN_ID_FIELD)
                .flatMap(this::billings);
    }

    private Stream<EntityDocument> billings(final Terms.Bucket bucket) {
        return billingHelper.termBuckets(bucket.getAggregations(), BillingUtils.BILLING_CENTER_FIELD)
                .map(billingCenterBucket -> getBilling(bucket.getKeyAsString(), billingCenterBucket.getAggregations()));
    }

    private EntityDocument getBilling(final String id, final Aggregations aggregations) {
        final Map<String, Object> lastDoc = new HashMap<>(billingHelper.getLastByDateDocFields(aggregations));
        lastDoc.put(BillingUtils.RUN_USAGE_FIELD, billingHelper.getRunUsageSum(aggregations));
        lastDoc.put(BillingUtils.COST_FIELD, billingHelper.getCostSum(aggregations));
        return EntityDocument.builder()
                .fields(lastDoc)
                .build();
    }
}
