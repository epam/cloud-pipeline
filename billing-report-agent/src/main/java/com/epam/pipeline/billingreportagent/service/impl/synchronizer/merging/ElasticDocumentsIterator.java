package com.epam.pipeline.billingreportagent.service.impl.synchronizer.merging;

import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.Function;

@RequiredArgsConstructor
public class ElasticDocumentsIterator implements Iterator<SearchResponse> {

    private final int pageSize;
    private final Function<Object[], SearchRequest> getRequest;
    private final Function<SearchRequest, SearchResponse> getResponse;

    private Object[] searchAfter;
    private SearchResponse response;

    @Override
    public boolean hasNext() {
        return response == null || pageSizeOf(response) >= pageSize;
    }

    private Integer pageSizeOf(final SearchResponse response) {
        return Optional.ofNullable(response)
                .map(SearchResponse::getHits)
                .map(SearchHits::getHits)
                .map(documents -> documents.length)
                .orElse(0);
    }

    @Override
    public SearchResponse next() {
        response = getResponse.apply(getRequest.apply(searchAfter));
        searchAfter = Optional.ofNullable(response)
                .map(SearchResponse::getHits)
                .map(SearchHits::getHits)
                .map(documents -> documents[documents.length - 1])
                .map(SearchHit::getSortValues)
                .orElse(null);
        return response;
    }
}
