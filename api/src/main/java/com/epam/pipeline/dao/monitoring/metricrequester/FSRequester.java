package com.epam.pipeline.dao.monitoring.metricrequester;

import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import org.apache.commons.lang3.tuple.ImmutablePair;
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
import java.util.*;
import java.util.stream.Collectors;

public class FSRequester extends AbstractMetricRequester {

    FSRequester(RestHighLevelClient client) {
        super(client);
    }

    @Override
    public SearchRequest buildRequest(Collection<String> resourceIds, LocalDateTime from, LocalDateTime to) {
        final SearchSourceBuilder builder = new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                        .filter(QueryBuilders.termsQuery(path(FIELD_METRICS_TAGS, NODENAME_RAW_FIELD),
                                resourceIds))
                        .filter(QueryBuilders.termQuery(path(FIELD_METRICS_TAGS, FIELD_TYPE), "node"))
                        .filter(QueryBuilders.rangeQuery(ELKUsageMetric.FS.getTimestamp())
                                .from(from.toInstant(ZoneOffset.UTC).toEpochMilli())
                                .to(to.toInstant(ZoneOffset.UTC).toEpochMilli())))
                .size(0)
                .aggregation(AggregationBuilders.terms(NODENAME_FIELD_VALUE)
                        .field(path(FIELD_METRICS_TAGS, NODENAME_RAW_FIELD))
                        .subAggregation(AggregationBuilders.terms("resource_id")
                                .field(path(FIELD_METRICS_TAGS, "resource_id"))
                                .subAggregation(AggregationBuilders.avg(AVG_AGGREGATION + LIMIT)
                                        .field("Metrics." + ELKUsageMetric.FS.getName()
                                                + "/limit.value"))
                                .subAggregation(AggregationBuilders.avg(AVG_AGGREGATION + USAGE)
                                        .field("Metrics." + ELKUsageMetric.FS.getName()
                                                + "/usage.value"))));

        return new SearchRequest(getIndexNames(from, to)).types(ELKUsageMetric.FS.getName()).source(builder);
    }

    @Override
    public Map<String, Double> parseResponse(SearchResponse response) {
        Map<String, Map<String, Double>> rateByDisk = ((Terms) response.getAggregations().get(NODENAME_FIELD_VALUE))
                .getBuckets().stream().collect(
                    Collectors.toMap(b -> b.getKey().toString(),
                        b -> {
                            Terms aggsByDisks = b.getAggregations().get("resource_id");
                            return aggsByDisks.getBuckets().stream().collect(HashMap::new,
                                (m, e) -> {
                                    double limit = ((Avg) e.getAggregations().get(AVG_AGGREGATION + LIMIT)).getValue();
                                    double usage = ((Avg) e.getAggregations().get(AVG_AGGREGATION + USAGE)).getValue();
                                    m.put(e.getKey().toString(), getRate(usage, limit));
                                }, Map::putAll);
                        }));

        return rateByDisk.entrySet().stream().map(metricsByDisk ->
                new ImmutablePair<>(
                        metricsByDisk.getKey(),
                        metricsByDisk.getValue().entrySet().stream()
                .max(Comparator.comparing(Map.Entry::getValue))
                .map(Map.Entry::getValue)
                .orElse(null))
        ).collect(Collectors.toMap(ImmutablePair::getLeft, ImmutablePair::getRight));
    }

    private Double getRate(final Double usage, final Double limit) {
        return usage == null || limit == null ? null : usage / limit;
    }
}
