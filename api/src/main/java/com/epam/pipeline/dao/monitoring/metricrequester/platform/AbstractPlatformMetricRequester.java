/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.dao.monitoring.metricrequester.platform;

import com.epam.pipeline.dao.monitoring.metricrequester.*;
import com.epam.pipeline.entity.cluster.monitoring.platform.PlatformResource;
import com.epam.pipeline.entity.cluster.monitoring.platform.histogram.HistogramBin;
import com.epam.pipeline.entity.cluster.monitoring.platform.histogram.HistogramType;
import com.epam.pipeline.exception.PipelineException;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.aggregations.*;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public abstract class AbstractPlatformMetricRequester {

    private static final DateTimeFormatter DATE_FORMATTER =DateTimeFormatter.ofPattern("yyyy.MM.dd");
    protected static final IndicesOptions INDICES_OPTIONS = IndicesOptions.STRICT_EXPAND_OPEN_CLOSED;
    private static final String ES_WILDCARD = "*";

    private static final String ORDER_FIELD = "order";

    private final HeapsterElasticRestHighLevelClient client;
    private final String indexNamePattern;

    public AbstractPlatformMetricRequester(final HeapsterElasticRestHighLevelClient client,
                                           final String indexNamePattern) {
        this.client = client;
        this.indexNamePattern = indexNamePattern;
    }

    public static AbstractPlatformMetricRequester getRequester(PlatformResource resource,
                                                               String indexNamePattern,
                                                               HeapsterElasticRestHighLevelClient client) {
        switch (resource) {
            case NETWORK_EVENT:
                return new NetworkEventRequester(client, indexNamePattern);
            default:
                throw new IllegalArgumentException("Platform metric type: " + resource.name() + " isn't supported!");
        }
    }

    public abstract Map<String, List<String>> performHistogramFilterRequest();

    public List<HistogramBin> performHistogramRequest(final HistogramType histogramType,
                                                      final LocalDateTime from, final LocalDateTime to,
                                                      final int nBins,
                                                      final Map<String, List<String>> additional) {
        return parseHistResponse(executeRequest(buildHistogramRequest(histogramType, from, to, nBins, additional)));
    }

    protected SearchResponse executeRequest(final SearchRequest searchRequest) {
        try {
            return client.searchHeapsterElastic(searchRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new PipelineException(e);
        }
    }

    public abstract SearchRequest buildHistogramRequest(HistogramType histogramType,
                                                        LocalDateTime from, LocalDateTime to, int nBins,
                                                        Map<String, List<String>> additional);

    public List<HistogramBin> parseHistResponse(final SearchResponse response) {
        return Optional.ofNullable(response.getAggregations())
                .map(Aggregations::asList)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .filter(it -> it instanceof MultiBucketsAggregation)
                .map(MultiBucketsAggregation.class::cast)
                .findFirst()
                .map(MultiBucketsAggregation::getBuckets)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .map(this::toHistogramBin)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    protected abstract HistogramBin toHistogramBin(MultiBucketsAggregation.Bucket bucket);


    protected List<Aggregation> aggregations(final MultiBucketsAggregation.Bucket bucket) {
        return Optional.ofNullable(bucket.getAggregations())
                .map(Aggregations::asList)
                .orElseGet(Collections::emptyList);
    }

    protected AggregationBuilder ordered(final AggregationBuilder aggregation) {
        try {
            final Field field = aggregation.getClass().getDeclaredField(ORDER_FIELD);
            field.setAccessible(true);
            field.set(aggregation, BucketOrder.count(false));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new PipelineException(e);
        }
        return aggregation;
    }

    protected String[] getAllIndices() {
        return new String[]{String.format(indexNamePattern, ES_WILDCARD)};
    }

    protected String[] getIndexNames(final LocalDateTime from, final LocalDateTime to) {
        final LocalDate fromDate = from.toLocalDate();
        final LocalDate toDate = to.toLocalDate();
        return Stream.iterate(fromDate, date -> date.plusDays(1))
                .limit(Period.between(fromDate, toDate).getDays() + 1)
                .map(date -> date.format(DATE_FORMATTER))
                .map(dateStr -> dateStr + "-*")
                .map(str -> String.format(indexNamePattern, str))
                .toArray(String[]::new);
    }

}

