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
import com.epam.pipeline.exception.PipelineException;
import com.epam.pipeline.manager.utils.GlobalSearchElasticHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collection;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogManager {

    private static final IndicesOptions INDICES_OPTIONS = IndicesOptions.fromOptions(true,
            SearchRequest.DEFAULT_INDICES_OPTIONS.allowNoIndices(),
            SearchRequest.DEFAULT_INDICES_OPTIONS.expandWildcardsOpen(),
            SearchRequest.DEFAULT_INDICES_OPTIONS.expandWildcardsClosed(),
            SearchRequest.DEFAULT_INDICES_OPTIONS);


    private final GlobalSearchElasticHelper elasticHelper;

    public Collection<LogEntry> filter(LogFilter logFilter) {
        final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                        .filter(QueryBuilders.termsQuery(path("user"), "pavel_silin")));
//                        .filter(QueryBuilders.rangeQuery("@timestamp")
//                                .from(logFilter.getTimestampFrom().toInstant(ZoneOffset.UTC).toEpochMilli())
//                                .to(logFilter.getTimestampTo().toInstant(ZoneOffset.UTC).toEpochMilli())))
//                        .filter(QueryBuilders.rangeQuery("messageTimestamp")
//                                .from(logFilter.getMessageTimestampFrom().toInstant(ZoneOffset.UTC).toEpochMilli())
//                                .to(logFilter.getMessageTimestampTo().toInstant(ZoneOffset.UTC).toEpochMilli()));

        SearchResponse security_log = executeRequest(new SearchRequest("security_log")
                                                            .source(searchSourceBuilder)
                                                            .indicesOptions(INDICES_OPTIONS));
        return null;
    }

    protected static String path(final String ...parts) {
        return String.join(".", parts);
    }

    protected SearchResponse executeRequest(final SearchRequest searchRequest) {
        try {
            return elasticHelper.buildClient().search(searchRequest);
        } catch (IOException e) {
            throw new PipelineException(e);
        }
    }
}
