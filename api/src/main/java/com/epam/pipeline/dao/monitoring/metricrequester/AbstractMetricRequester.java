package com.epam.pipeline.dao.monitoring.metricrequester;

import com.epam.pipeline.exception.PipelineException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

public abstract class AbstractMetricRequester implements MetricRequester {

    static final String FIELD_METRICS_TAGS = "MetricsTags";
    static final String FIELD_NAMESPACE_NAME = "namespace_name";
    static final String FIELD_TYPE = "type";

    static final String USAGE = "usage";
    static final String USAGE_RATE = "usage_rate";
    static final String NODE_UTILIZATION = "node_utilization";
    static final String LIMIT = "limit";

    static final String AVG_AGGREGATION = "avg_";
    static final String AGGREGATION_POD_NAME = "pod_name";
    static final String FIELD_POD_NAME_RAW = "pod_name.raw";
    static final String NODENAME_FIELD_VALUE = "nodename";
    static final String NODENAME_RAW_FIELD = "nodename.raw";

    AbstractMetricRequester(RestHighLevelClient client) {
        this.client = client;
    }

    private RestHighLevelClient client;


    public Map<String, Double> performRequest(final Collection<String> resourceIds, final String[] indexName,
                                              final LocalDateTime from, final LocalDateTime to) {
        SearchRequest searchRequest = buildRequest(resourceIds, indexName, from, to);
        SearchResponse response;
        try {
            response = client.search(searchRequest);
        } catch (IOException e) {
            throw new PipelineException(e);
        }

        return parseResponse(response);
    }

    protected String path(final String ...parts) {
        return String.join(".", parts);
    }
}
