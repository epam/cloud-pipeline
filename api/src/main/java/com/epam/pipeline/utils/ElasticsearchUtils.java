/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.utils;

import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.ShardSearchFailure;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The utility class for common Elasticsearch connected methods
 */
@Slf4j
public final class ElasticsearchUtils {

    private ElasticsearchUtils() {
        // no-op
    }

    public static SearchResponse verifyResponse(final SearchResponse searchResponse) {
        if (searchResponse.status().getStatus() != HttpStatus.OK.value()) {
            throw new IllegalStateException(String.format("Search request has failed with HTTP status %s (%s).",
                    searchResponse.status().name(), searchResponse.status().getStatus()));
        }

        log.debug("Search request has finished with {} successful, {} skipped and {} failed shards of {} total.",
                searchResponse.getSuccessfulShards(), searchResponse.getSkippedShards(),
                searchResponse.getFailedShards(), searchResponse.getTotalShards());
        final ShardSearchFailure[] failures = searchResponse.getShardFailures();
        if (failures.length > 0) {
            final List<Throwable> errors = Arrays.stream(failures)
                    .map(ShardSearchFailure::getCause)
                    .collect(Collectors.toList());
            final String errorMessages = errors.stream()
                    .map(Throwable::getMessage)
                    .collect(Collectors.joining("\n"));
            log.error("Search request has finished with the following shard failures: {}", errorMessages);
            throw new IllegalStateException("Search request has failed because some shards failed. ",
                    failures[0].getCause());
        }

        return searchResponse;
    }

    public static void addRangeFilter(final BoolQueryBuilder boolQuery,
                                final LocalDateTime from,
                                final LocalDateTime to,
                                final String field) {
        if (from == null && to == null) {
            return;
        }

        final RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(field);

        if (from != null) {
            rangeQueryBuilder.from(from.toInstant(ZoneOffset.UTC));
        }
        if (to != null) {
            rangeQueryBuilder.to(to.toInstant(ZoneOffset.UTC));
        }

        boolQuery.filter(rangeQueryBuilder);
    }
}
