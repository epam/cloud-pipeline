/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.log;

import com.epam.pipeline.entity.log.LogEntry;
import com.epam.pipeline.entity.log.LogFilter;
import com.epam.pipeline.entity.log.LogPagination;
import com.epam.pipeline.exception.PipelineException;
import com.epam.pipeline.manager.utils.GlobalSearchElasticHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.ShardSearchFailure;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogManager {

    private static final IndicesOptions INDICES_OPTIONS = IndicesOptions.fromOptions(true,
            SearchRequest.DEFAULT_INDICES_OPTIONS.allowNoIndices(),
            SearchRequest.DEFAULT_INDICES_OPTIONS.expandWildcardsOpen(),
            SearchRequest.DEFAULT_INDICES_OPTIONS.expandWildcardsClosed(),
            SearchRequest.DEFAULT_INDICES_OPTIONS);

    public static final String USER = "user";
    public static final String TIMESTAMP = "@timestamp";
    public static final String MESSAGE_TIMESTAMP = "message_timestamp";
    public static final String HOSTNAME = "hostname";
    public static final String SERVICE_NAME = "service_name";
    public static final String TYPE = "type";
    public static final String MESSAGE = "message";
    public static final String SEVERITY = "severity";

    private final GlobalSearchElasticHelper elasticHelper;
    private String indexTemplate = "security_log*";

    public LogPagination filter(LogFilter logFilter) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        if (!CollectionUtils.isEmpty(logFilter.getUsers())) {
            boolQuery = boolQuery.filter(QueryBuilders.termsQuery(USER, logFilter.getUsers()));
        }
        if (!CollectionUtils.isEmpty(logFilter.getHostnames())) {
            boolQuery = boolQuery.filter(QueryBuilders.termsQuery(HOSTNAME, logFilter.getHostnames()));
        }
        if (!CollectionUtils.isEmpty(logFilter.getServiceNames())) {
            boolQuery = boolQuery.filter(QueryBuilders.termsQuery(SERVICE_NAME, logFilter.getServiceNames()));
        }
        if (!CollectionUtils.isEmpty(logFilter.getTypes())) {
            boolQuery = boolQuery.filter(QueryBuilders.termsQuery(TYPE, logFilter.getTypes()));
        }

        boolQuery = addRageFilter(boolQuery, TIMESTAMP, logFilter.getTimestampFrom(), logFilter.getTimestampTo());
        boolQuery = addRageFilter(boolQuery, MESSAGE_TIMESTAMP, logFilter.getMessageTimestampFrom(), logFilter.getMessageTimestampTo());

        final SearchResponse securityLog =
                executeRequest(new SearchRequest(indexTemplate)
                        .source(new SearchSourceBuilder()
                                .query(boolQuery)
                                .from(logFilter.getPagination().getToken() * logFilter.getPagination().getPageSize())
                                .size(logFilter.getPagination().getPageSize()))
                        .indicesOptions(INDICES_OPTIONS));

        if (securityLog.status().getStatus() != HttpStatus.OK.value()) {
            throw new IllegalStateException(
                    "Illegal Rest status: "  + securityLog.status().name()
                            + " code: " + securityLog.status().getStatus());
        }

        if (securityLog.getTotalShards() > securityLog.getSuccessfulShards()) {
            final String shardsFailCause = Arrays.stream(securityLog.getShardFailures())
                    .map(ShardSearchFailure::getCause)
                    .map(Throwable::getMessage).collect(Collectors.joining("\n"));
            throw new IllegalStateException(shardsFailCause);
        }

        return LogPagination.builder().logEntries(
                Arrays.stream(securityLog.getHits().getHits())
                    .map(SearchHit::getSourceAsMap)
                    .map(this::mapHitToLogEntry)
                    .collect(Collectors.toList()))
                .pageSize(logFilter.getPagination().getPageSize())
                .token(logFilter.getPagination().getToken())
                .totalHits(securityLog.getHits().totalHits).build();
    }

    private BoolQueryBuilder addRageFilter(BoolQueryBuilder boolQuery, String timestamp,
                                           LocalDateTime timestampFrom, LocalDateTime timestampTo) {
        if (timestampFrom == null && timestampTo == null) {
            return boolQuery;
        }

        RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(timestamp);

        if (timestampFrom != null) {
            rangeQueryBuilder = rangeQueryBuilder.from(timestampFrom.toInstant(ZoneOffset.UTC));
        }
        if (timestampTo != null) {
            rangeQueryBuilder = rangeQueryBuilder.to(timestampTo.toInstant(ZoneOffset.UTC));
        }

        return boolQuery.filter(rangeQueryBuilder);
    }

    private LogEntry mapHitToLogEntry(Map<String, Object> hit) {
        return LogEntry.builder()
                .timestamp(LocalDateTime.parse((String) hit.get(TIMESTAMP),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")))
                .messageTimestamp(LocalDateTime.parse((String) hit.get(MESSAGE_TIMESTAMP),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")))
                .hostname((String) hit.get(HOSTNAME))
                .serviceName((String) hit.get(SERVICE_NAME))
                .type((String) hit.get(TYPE))
                .user((String) hit.get(USER))
                .message((String) hit.get(MESSAGE))
                .severity((String) hit.getOrDefault(SEVERITY, "INFO"))
                .build();
    }

    private SearchResponse executeRequest(final SearchRequest searchRequest) {
        try {
            return elasticHelper.buildClient().search(searchRequest);
        } catch (IOException e) {
            throw new PipelineException(e);
        }
    }
}
