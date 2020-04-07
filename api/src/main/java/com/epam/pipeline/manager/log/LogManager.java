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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.log.*;
import com.epam.pipeline.exception.PipelineException;
import com.epam.pipeline.manager.utils.GlobalSearchElasticHelper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.ShardSearchFailure;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manager to work with ElasticSearch engine in order to search for system logs.
 * */
@Slf4j
@Service
@Getter
@Setter
@RequiredArgsConstructor
public class LogManager {

    private static final IndicesOptions INDICES_OPTIONS = IndicesOptions.fromOptions(true,
            SearchRequest.DEFAULT_INDICES_OPTIONS.allowNoIndices(),
            SearchRequest.DEFAULT_INDICES_OPTIONS.expandWildcardsOpen(),
            SearchRequest.DEFAULT_INDICES_OPTIONS.expandWildcardsClosed(),
            SearchRequest.DEFAULT_INDICES_OPTIONS);

    private static final String USER = "user";
    private static final String TIMESTAMP = "@timestamp";
    private static final String MESSAGE_TIMESTAMP = "message_timestamp";
    private static final String HOSTNAME = "hostname";
    private static final String SERVICE_NAME = "service_name";
    private static final String TYPE = "type";
    private static final String MESSAGE = "message";
    private static final String SEVERITY = "level";
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static final String DEFAULT_SEVERITY = "INFO";
    public static final int ELASTIC_MAX_SCROLL_SIZE = 9999;

    private final GlobalSearchElasticHelper elasticHelper;
    private final MessageHelper messageHelper;

    @Value("${log.security.elastic.index.prefix}")
    private String indexTemplate;

    /**
     * Searches log according to specified log filter.
     * Max depth of a search is 10000 total. If ElasticSearch result contains totalHits > that max number
     * flag overflow will be set to {@code true} and totalHits will be set to max value (10000)
     * @param logFilter - filter for constructing elasticsearch query
     * @return {@link LogPagination} object with related search result and additional information
     * */
    public LogPagination filter(final LogFilter logFilter) {
        final LogPaginationRequest pagination = logFilter.getPagination();

        Assert.notNull(pagination, messageHelper.getMessage(MessageConstants.ERROR_PAGINATION_IS_NOT_PROVIDED));
        Assert.isTrue(pagination.getToken() > 0 && pagination.getPageSize() > 0,
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_PAGE_INDEX_OR_SIZE));

        final int offset = (pagination.getToken() - 1) * pagination.getPageSize();
        final int size = pagination.getPageSize() + offset < ELASTIC_MAX_SCROLL_SIZE
                ? pagination.getPageSize()
                : ELASTIC_MAX_SCROLL_SIZE - offset;
        final SearchHits hits = verifyResponse(
                                    executeRequest(new SearchRequest(indexTemplate)
                                            .source(new SearchSourceBuilder()
                                                    .query(constructQueryFilter(logFilter))
                                                    .from(offset)
                                                    .size(size))
                                            .indicesOptions(INDICES_OPTIONS))
                                ).getHits();

        return LogPagination.builder().logEntries(
                Arrays.stream(hits.getHits())
                    .map(SearchHit::getSourceAsMap)
                    .map(this::mapHitToLogEntry)
                    .collect(Collectors.toList()))
                .pageSize(pagination.getPageSize())
                .token(pagination.getToken())
                .totalHits(hits.totalHits <= ELASTIC_MAX_SCROLL_SIZE
                        ? hits.totalHits
                        : ELASTIC_MAX_SCROLL_SIZE)
                .overflow(hits.totalHits > ELASTIC_MAX_SCROLL_SIZE)
                .build();
    }

    private BoolQueryBuilder constructQueryFilter(LogFilter logFilter) {
        final BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        if (!CollectionUtils.isEmpty(logFilter.getUsers())) {
            List<String> formattedUsers = logFilter.getUsers().stream()
                    .flatMap(user -> Stream.of(user.toLowerCase(), user.toUpperCase()))
                    .collect(Collectors.toList());
            boolQuery.filter(QueryBuilders.termsQuery(USER, formattedUsers));
        }
        if (!CollectionUtils.isEmpty(logFilter.getHostnames())) {
            boolQuery.filter(QueryBuilders.termsQuery(HOSTNAME, logFilter.getHostnames()));
        }
        if (!CollectionUtils.isEmpty(logFilter.getServiceNames())) {
            boolQuery.filter(QueryBuilders.termsQuery(SERVICE_NAME, logFilter.getServiceNames()
                    .stream().map(ServiceName::getService).collect(Collectors.toList())));
        }
        if (!CollectionUtils.isEmpty(logFilter.getTypes())) {
            boolQuery.filter(QueryBuilders.termsQuery(TYPE, logFilter.getTypes()));
        }
        if (!StringUtils.isEmpty(logFilter.getMessage())) {
            boolQuery.filter(QueryBuilders.matchQuery(MESSAGE, logFilter.getMessage()));
        }

        addRageFilter(boolQuery, TIMESTAMP, logFilter.getTimestampFrom(), logFilter.getTimestampTo());
        addRageFilter(boolQuery, MESSAGE_TIMESTAMP, logFilter.getMessageTimestampFrom(), logFilter.getMessageTimestampTo());
        return boolQuery;
    }

    private void addRageFilter(BoolQueryBuilder boolQuery, String timestamp,
                               LocalDateTime timestampFrom, LocalDateTime timestampTo) {
        if (timestampFrom == null && timestampTo == null) {
            return;
        }

        RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(timestamp);

        if (timestampFrom != null) {
            rangeQueryBuilder = rangeQueryBuilder.from(timestampFrom.toInstant(ZoneOffset.UTC));
        }
        if (timestampTo != null) {
            rangeQueryBuilder = rangeQueryBuilder.to(timestampTo.toInstant(ZoneOffset.UTC));
        }

        boolQuery.filter(rangeQueryBuilder);
    }

    private SearchResponse verifyResponse(SearchResponse logsResponse) {
        if (logsResponse.status().getStatus() != HttpStatus.OK.value()) {
            throw new IllegalStateException(
                    "Illegal Rest status: "  + logsResponse.status().name()
                            + " code: " + logsResponse.status().getStatus());
        }

        if (logsResponse.getTotalShards() > logsResponse.getSuccessfulShards()) {
            final String shardsFailCause = Arrays.stream(logsResponse.getShardFailures())
                    .map(ShardSearchFailure::getCause)
                    .map(Throwable::getMessage).collect(Collectors.joining("\n"));
            throw new IllegalStateException(shardsFailCause);
        }

        return logsResponse;
    }

    private LogEntry mapHitToLogEntry(Map<String, Object> hit) {
        return LogEntry.builder()
                .timestamp(LocalDateTime.parse((String) hit.get(TIMESTAMP), DATE_TIME_FORMATTER))
                .messageTimestamp(LocalDateTime.parse((String) hit.get(MESSAGE_TIMESTAMP), DATE_TIME_FORMATTER))
                .hostname((String) hit.get(HOSTNAME))
                .serviceName(ServiceName.getByService((String) hit.get(SERVICE_NAME)))
                .type((String) hit.get(TYPE))
                .user((String) hit.get(USER))
                .message((String) hit.get(MESSAGE))
                .severity((String) hit.getOrDefault(SEVERITY, DEFAULT_SEVERITY))
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
