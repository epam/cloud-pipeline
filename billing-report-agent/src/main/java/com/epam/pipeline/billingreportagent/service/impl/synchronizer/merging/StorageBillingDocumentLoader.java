package com.epam.pipeline.billingreportagent.service.impl.synchronizer.merging;

import com.epam.pipeline.billingreportagent.model.EntityDocument;
import com.epam.pipeline.billingreportagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.billingreportagent.utils.BillingHelper;
import com.epam.pipeline.billingreportagent.utils.BillingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageBillingDocumentLoader implements EntityDocumentLoader {

    private final ElasticsearchServiceClient elasticsearchServiceClient;
    private final BillingHelper billingHelper;

    @Override
    public Stream<EntityDocument> documents(final LocalDate from, final LocalDate to,
                                            final String[] indices) {
        final SearchRequest request = getRequest(from, to, indices);
        log.debug("Billing request: {}", request);
        final SearchResponse response = elasticsearchServiceClient.search(request);
        return billings(response);
    }

    private SearchRequest getRequest(final LocalDate from, final LocalDate to,
                                     final String[] indices) {
        return new SearchRequest()
                .indicesOptions(IndicesOptions.lenientExpandOpen())
                .indices(indices)
                .source(new SearchSourceBuilder()
                        .query(billingHelper.queryByDate(from, to))
                        .size(0)
                        .aggregation(billingHelper.aggregateBy(BillingUtils.STORAGE_ID_FIELD)
                                .size(Integer.MAX_VALUE)
                                .subAggregation(billingHelper.aggregateCostSum())
                                .subAggregation(billingHelper.aggregateLastByDateDoc())));
    }

    private Stream<EntityDocument> billings(final SearchResponse response) {
        return billingHelper.termBuckets(response.getAggregations(), BillingUtils.STORAGE_ID_FIELD)
                .map(bucket -> getBilling(bucket.getKeyAsString(), bucket.getAggregations()));
    }

    private EntityDocument getBilling(final String id, final Aggregations aggregations) {
        final Map<String, Object> lastDoc = billingHelper.getLastByDateDocFields(aggregations);
        lastDoc.put(BillingUtils.COST_FIELD, billingHelper.getCostSum(aggregations));
        return EntityDocument.builder()
                .id(id)
                .fields(lastDoc)
                .build();
    }
}
