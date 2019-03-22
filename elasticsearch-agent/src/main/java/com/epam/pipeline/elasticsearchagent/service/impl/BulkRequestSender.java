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
package com.epam.pipeline.elasticsearchagent.service.impl;

import com.epam.pipeline.elasticsearchagent.model.PipelineEvent;
import com.epam.pipeline.elasticsearchagent.service.BulkResponsePostProcessor;
import com.epam.pipeline.elasticsearchagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearchagent.service.ResponseIdConverter;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@AllArgsConstructor
public class BulkRequestSender {

    private static final int DEFAULT_BULK_SIZE = 1000;
    private final ElasticsearchServiceClient elasticsearchClient;
    private final BulkResponsePostProcessor responsePostProcessor;

    private ResponseIdConverter idConverter = new ResponseIdConverter() {};
    private int bulkSize = DEFAULT_BULK_SIZE;

    public BulkRequestSender(final ElasticsearchServiceClient elasticsearchClient,
                             final BulkResponsePostProcessor responsePostProcessor,
                             final ResponseIdConverter idConverter) {
        this(elasticsearchClient, responsePostProcessor);
        this.idConverter = idConverter;
    }

    public void indexDocuments(final String indexName,
                               final PipelineEvent.ObjectType objectType,
                               final List<DocWriteRequest> documentRequests,
                               final LocalDateTime syncStart) {
        indexDocuments(indexName, Collections.singletonList(objectType), documentRequests, syncStart);

    }

    public void indexDocuments(final String indexName,
                               final List<PipelineEvent.ObjectType> objectTypes,
                               final List<DocWriteRequest> documentRequests,
                               final LocalDateTime syncStart) {
        ListUtils.partition(documentRequests, bulkSize)
                .forEach(chunk -> indexChunk(indexName, chunk, objectTypes, syncStart));

    }

    private void indexChunk(final String indexName,
                            final List<DocWriteRequest> documentRequests,
                            final List<PipelineEvent.ObjectType> objectTypes,
                            final LocalDateTime syncStart) {
        log.debug("Inserting {} documents for {}", documentRequests.size(), objectTypes);
        final BulkResponse response = elasticsearchClient
                .sendRequests(indexName, documentRequests);

        if (ObjectUtils.isEmpty(response)) {
            log.error("Elasticsearch documents for {} were not created.", objectTypes);
            return;
        }
        Arrays.stream(response.getItems())
                .collect(Collectors.groupingBy(idConverter::getId))
                .forEach((id, items) -> responsePostProcessor.postProcessResponse(items, objectTypes, id, syncStart));
    }
}
