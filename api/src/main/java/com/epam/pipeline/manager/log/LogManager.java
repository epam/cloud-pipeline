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
import com.epam.pipeline.entity.log.LogEntry;
import com.epam.pipeline.entity.log.LogFilter;
import com.epam.pipeline.entity.log.LogPagination;
import com.epam.pipeline.entity.log.LogPaginationRequest;
import com.epam.pipeline.entity.log.PageMarker;
import com.epam.pipeline.exception.PipelineException;
import com.epam.pipeline.manager.utils.GlobalSearchElasticHelper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.BooleanUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.ShardSearchFailure;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
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
    public static final String ID = "event_id";
    public static final String SERVICE_ACCOUNT = "service_account";
    public static final String KEYWORD = ".keyword";

    private final GlobalSearchElasticHelper elasticHelper;
    private final MessageHelper messageHelper;

    @Value("${log.security.elastic.index.prefix}")
    private String indexTemplate;

    /**
     * Searches log according to specified log filter.
     * @param logFilter - filter for constructing elasticsearch query
     * @return {@link LogPagination} object with related search result and additional information
     * */
    public LogPagination filter(final LogFilter logFilter) {
        final LogPaginationRequest pagination = logFilter.getPagination();

        Assert.notNull(pagination, messageHelper.getMessage(MessageConstants.ERROR_PAGINATION_IS_NOT_PROVIDED));
        Assert.isTrue(pagination.getPageSize() > 0,
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_PAGE_INDEX_OR_SIZE));

        final SortOrder sortOrder = logFilter.getSortOrder() != null
                ? SortOrder.fromString(logFilter.getSortOrder())
                : SortOrder.DESC;
        final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                .query(constructQueryFilter(logFilter))
                .sort(MESSAGE_TIMESTAMP, sortOrder)
                .sort(ID, sortOrder)
                .size(pagination.getPageSize() + 1);

        final PageMarker pageMarker = logFilter.getPagination().getToken();
        if (pageMarker != null) {
            Assert.isTrue(pageMarker.getId() != null && pageMarker.getMessageTimestamp() != null,
                    "Token should contain id and messageTimestamp values");
            searchSourceBuilder.searchAfter(
                    new Object[]{
                            pageMarker.getMessageTimestamp()
                                    .toInstant(ZoneOffset.UTC).toEpochMilli(),
                            pageMarker.getId()
                    }
            );
        }

        final SearchHits hits = verifyResponse(
                                    executeRequest(new SearchRequest(indexTemplate)
                                            .source(searchSourceBuilder)
                                            .indicesOptions(INDICES_OPTIONS))
                                ).getHits();

        final List<LogEntry> entries = Arrays.stream(hits.getHits())
                .map(this::mapHitToLogEntry)
                .collect(Collectors.toList());

        return LogPagination.builder()
                .logEntries(entries.stream().limit(pagination.getPageSize()).collect(Collectors.toList()))
                .pageSize(pagination.getPageSize())
                .token(getToken(entries, pagination.getPageSize()))
                .totalHits(hits.totalHits)
                .build();
    }

    private PageMarker getToken(final List<LogEntry> items, final int pageSize) {
        if (items == null || items.size() <= pageSize) {
            return null;
        }
        final LogEntry entry = items.get(items.size() - 1);
        return new PageMarker(entry.getEventId(), entry.getMessageTimestamp());
    }

    private BoolQueryBuilder constructQueryFilter(final LogFilter logFilter) {
        final BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        if (BooleanUtils.isNotTrue(logFilter.getIncludeServiceAccountEvents())) {
            boolQuery.filter(QueryBuilders.termQuery(SERVICE_ACCOUNT, false));
        }

        if (!CollectionUtils.isEmpty(logFilter.getUsers())) {
            List<String> formattedUsers = logFilter.getUsers().stream()
                    .flatMap(user -> Stream.of(user.toLowerCase(), user.toUpperCase()))
                    .collect(Collectors.toList());
            boolQuery.filter(QueryBuilders.termsQuery(USER, formattedUsers));
        }
        if (!CollectionUtils.isEmpty(logFilter.getHostnames())) {
            boolQuery.filter(QueryBuilders.termsQuery(HOSTNAME + KEYWORD, logFilter.getHostnames()));
        }
        if (!CollectionUtils.isEmpty(logFilter.getServiceNames())) {
            boolQuery.filter(QueryBuilders.termsQuery(SERVICE_NAME + KEYWORD, logFilter.getServiceNames()));
        }
        if (!CollectionUtils.isEmpty(logFilter.getTypes())) {
            boolQuery.filter(QueryBuilders.termsQuery(TYPE + KEYWORD, logFilter.getTypes()));
        }
        if (!StringUtils.isEmpty(logFilter.getMessage())) {
            boolQuery.filter(QueryBuilders.matchQuery(MESSAGE, logFilter.getMessage()));
        }

        addRageFilter(boolQuery, logFilter.getMessageTimestampFrom(),
                logFilter.getMessageTimestampTo());
        return boolQuery;
    }

    private void addRageFilter(final BoolQueryBuilder boolQuery,
                               final LocalDateTime timestampFrom, final LocalDateTime timestampTo) {
        if (timestampFrom == null && timestampTo == null) {
            return;
        }

        final RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(MESSAGE_TIMESTAMP);

        if (timestampFrom != null) {
            rangeQueryBuilder.from(timestampFrom.toInstant(ZoneOffset.UTC));
        }
        if (timestampTo != null) {
            rangeQueryBuilder.to(timestampTo.toInstant(ZoneOffset.UTC));
        }

        boolQuery.filter(rangeQueryBuilder);
    }

    private SearchResponse verifyResponse(final SearchResponse logsResponse) {
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

    private LogEntry mapHitToLogEntry(SearchHit searchHit) {
        Map<String, Object> hit = searchHit.getSourceAsMap();
        return LogEntry.builder()
                .eventId((Long) hit.get(ID))
                .messageTimestamp(LocalDateTime.parse((String) hit.get(MESSAGE_TIMESTAMP), DATE_TIME_FORMATTER))
                .hostname((String) hit.get(HOSTNAME))
                .serviceName((String) hit.get(SERVICE_NAME))
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

    public LogFilter getFilters() {
        final LogFilter result = new LogFilter();
        verifyResponse(
                executeRequest(new SearchRequest(indexTemplate)
                        .source(
                                new SearchSourceBuilder()
                                        .query(QueryBuilders.boolQuery())
                                        .size(0)
                                        .aggregation(AggregationBuilders.terms(TYPE).field(TYPE + KEYWORD))
                                        .aggregation(AggregationBuilders.terms(SERVICE_NAME)
                                                .field(SERVICE_NAME + KEYWORD))
                                        .aggregation(AggregationBuilders.terms(HOSTNAME).field(HOSTNAME + KEYWORD))
                        ).indicesOptions(INDICES_OPTIONS)
                )
        ).getAggregations()
                .asList()
                .stream()
                .map(Terms.class::cast)
                .forEach(terms -> {
                    List<String> values = terms.getBuckets().stream()
                            .map(MultiBucketsAggregation.Bucket::getKey)
                            .map(Object::toString)
                            .collect(Collectors.toList());

                    if (HOSTNAME.equals(terms.getName())) {
                        result.setHostnames(values);
                    } else if (SERVICE_NAME.equals(terms.getName())) {
                        result.setServiceNames(values);
                    } else if (TYPE.equals(terms.getName())) {
                        result.setTypes(values);
                    }
                });


        return result;
    }
}
