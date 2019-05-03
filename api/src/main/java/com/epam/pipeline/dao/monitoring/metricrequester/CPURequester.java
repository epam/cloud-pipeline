package com.epam.pipeline.dao.monitoring.metricrequester;

import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.avg.Avg;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public class CPURequester extends AbstractMetricRequester {

    CPURequester(RestHighLevelClient client) {
        super(client);
    }

    @Override
    public SearchRequest buildRequest(final Collection<String> resourceIds,
                                      final LocalDateTime from, final LocalDateTime to) {
        final SearchSourceBuilder builder = new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                        .filter(QueryBuilders.termsQuery(path(FIELD_METRICS_TAGS, FIELD_POD_NAME_RAW), resourceIds))
                        .filter(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, FIELD_NAMESPACE_NAME), "default"))
                        .filter(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, FIELD_TYPE), "pod_container"))
                        .filter(QueryBuilders.rangeQuery(ELKUsageMetric.CPU.getTimestamp())
                                .from(from.toInstant(ZoneOffset.UTC).toEpochMilli())
                                .to(to.toInstant(ZoneOffset.UTC).toEpochMilli())))
                .size(0)
                .aggregation(AggregationBuilders.terms(AGGREGATION_POD_NAME)
                        .field(path(FIELD_METRICS_TAGS, FIELD_POD_NAME_RAW))
                        .size(resourceIds.size())
                        .subAggregation(AggregationBuilders.avg(AVG_AGGREGATION + USAGE_RATE)
                                .field("Metrics." + ELKUsageMetric.CPU.getName() + "/"
                                        + USAGE_RATE + ".value")));

        return new SearchRequest(getIndexNames(from, to)).types(ELKUsageMetric.CPU.getName()).source(builder);
    }

    @Override
    public Map<String, Double> parseResponse(SearchResponse response) {
        return ((Terms)response.getAggregations().get(AGGREGATION_POD_NAME)).getBuckets().stream()
                .collect(Collectors.toMap(
                    b -> b.getKey().toString(),
                    b -> ((Avg) b.getAggregations().get(AVG_AGGREGATION + USAGE_RATE)).getValue()));
    }
}
