package com.epam.pipeline.dao.monitoring.metricrequester;

import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.exception.PipelineException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

public abstract class AbstractMetricRequester implements MetricRequester {

    private static final DateTimeFormatter DATE_FORMATTER =DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private static final String INDEX_NAME_PATTERN = "heapster-%s";

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


    public static MetricRequester getRequester(final ELKUsageMetric metric, final RestHighLevelClient client) {
        switch (metric) {
            case CPU:
                return new CPURequester(client);
            case MEM:
                return new MemoryRequester(client);
            case FS:
                return new FSRequester(client);
            default:
                throw new IllegalArgumentException("Metric type: " + metric.getName() + " isn't supported!");

        }
    }

    public Map<String, Double> performRequest(final Collection<String> resourceIds,
                                              final LocalDateTime from, final LocalDateTime to) {
        SearchRequest searchRequest = buildRequest(resourceIds, from, to);
        SearchResponse response;
        try {
            response = client.search(searchRequest);
        } catch (IOException e) {
            throw new PipelineException(e);
        }

        return parseResponse(response);
    }

    protected static String[] getIndexNames(final LocalDateTime from, final LocalDateTime to) {
        return Stream.of(from, to)
                .map(d -> d.format(DATE_FORMATTER))
                .distinct()
                .map(dateStr -> String.format(INDEX_NAME_PATTERN, dateStr))
                .toArray(String[]::new);
    }

    protected String path(final String ...parts) {
        return String.join(".", parts);
    }
}
