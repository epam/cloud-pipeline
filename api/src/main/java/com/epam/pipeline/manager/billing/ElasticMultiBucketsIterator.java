package com.epam.pipeline.manager.billing;

import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

@RequiredArgsConstructor
public class ElasticMultiBucketsIterator implements Iterator<SearchResponse> {

    private final String aggregation;
    private final int pageSize;
    private final Function<Integer, SearchRequest> getRequest;
    private final Function<SearchRequest, SearchResponse> getResponse;
    private final BiFunction<Aggregations, String, Optional<? extends MultiBucketsAggregation>> getAggregation;

    private int offset;
    private SearchResponse response;

    @Override
    public boolean hasNext() {
        return response == null || pageSizeOf(response) >= pageSize;
    }

    private Integer pageSizeOf(final SearchResponse response) {
        return getAggregation.apply(response.getAggregations(), aggregation)
                .map(MultiBucketsAggregation::getBuckets)
                .map(List::size)
                .orElse(0);
    }

    @Override
    public SearchResponse next() {
        response = getResponse.apply(getRequest.apply(offset));
        offset += pageSize;
        return response;
    }
}
