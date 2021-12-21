/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.monitoring;

import com.epam.pipeline.dao.monitoring.metricrequester.AbstractMetricRequester;
import com.epam.pipeline.dao.monitoring.metricrequester.HeapsterElasticRestHighLevelClient;
import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.exception.PipelineException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Repository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
@ConditionalOnProperty("monitoring.elasticsearch.url")
public class MonitoringESDao {
    protected static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private static final String INDEX_NAME_TOKEN = "heapster-";

    private HeapsterElasticRestHighLevelClient heapsterClient;
    private RestClient lowLevelClient;

    @Autowired
    public MonitoringESDao(HeapsterElasticRestHighLevelClient heapsterClient,
                           RestClient lowLevelClient) {
        this.heapsterClient = heapsterClient;
        this.lowLevelClient = lowLevelClient;
    }

    public Map<String, Double> loadMetrics(final ELKUsageMetric metric, final Collection<String> resourceIds,
                                           final LocalDateTime from, final LocalDateTime to) {
        if (CollectionUtils.isEmpty(resourceIds)) {
            return Collections.emptyMap();
        }
        return AbstractMetricRequester.getRequester(metric, heapsterClient).performRequest(resourceIds, from, to);
    }

    /**
     * Delete indices, that are older than retention period (in days)
     * @param retentionPeriodDays retention period (in days)
     */
    public void deleteIndices(final int retentionPeriodDays) {
        try {
            final Response response = requestIndices();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                final String indicesToDelete = indices(reader)
                        .map(this::withParsedDate)
                        .filter(pair -> pair.right != null && olderThanRetentionPeriod(retentionPeriodDays, pair.right))
                        .map(pair -> pair.left)
                        .collect(Collectors.joining(","));

                lowLevelClient.performRequest(HttpMethod.DELETE.name(), "/" + indicesToDelete);
            }
        } catch (IOException e) {
            throw new PipelineException(e);
        }
    }

    private Response requestIndices() throws IOException {
        return lowLevelClient.performRequest(HttpMethod.GET.name(), "/_cat/indices");
    }

    private Stream<String> indices(final BufferedReader reader) {
        return reader.lines()
                .flatMap(l -> Arrays.stream(l.split(" ")))
                .filter(str -> str.startsWith(INDEX_NAME_TOKEN));
    }

    private ImmutablePair<String, LocalDateTime> withParsedDate(final String name) {
        return toLocalDateTime(name)
                .map(it -> ImmutablePair.of(name, it))
                .orElseGet(() -> ImmutablePair.of(name, null));
    }

    private Optional<LocalDateTime> toLocalDateTime(final String name) {
        final String datetimeString = name.substring(INDEX_NAME_TOKEN.length());
        try {
            return Optional.of(LocalDate.parse(datetimeString, DATE_FORMATTER)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toLocalDateTime());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private boolean olderThanRetentionPeriod(final int retentionPeriod, final LocalDateTime date) {
        return date.isBefore(DateUtils.nowUTC().minusDays(retentionPeriod + 1L));
    }

    public Optional<LocalDateTime> oldestIndexDate() {
        try {
            final Response response = requestIndices();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                return indices(reader)
                        .map(this::toLocalDateTime)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .sorted()
                        .findFirst();
            }
        } catch (IOException e) {
            throw new PipelineException(e);
        }
    }
}
