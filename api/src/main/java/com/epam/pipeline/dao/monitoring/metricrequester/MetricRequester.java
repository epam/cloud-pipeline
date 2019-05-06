package com.epam.pipeline.dao.monitoring.metricrequester;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

public interface MetricRequester {

    SearchRequest buildRequest(Collection<String> resourceIds, LocalDateTime from,
                               LocalDateTime to, Map <String, String> additional);

    Map<String, Double> parseResponse(SearchResponse response);

    Map<String, Double> performRequest(Collection<String> resourceIds,
                                       LocalDateTime from, LocalDateTime to);

}
