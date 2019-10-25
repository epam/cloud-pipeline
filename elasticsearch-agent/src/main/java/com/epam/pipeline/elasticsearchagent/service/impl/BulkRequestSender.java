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
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.index.IndexRequest;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@AllArgsConstructor
public class BulkRequestSender {

    private static final int DEFAULT_BULK_SIZE = 1000;
    private static final int MAX_PARTITION_SIZE = 200;
    private static final int MIN_PARTITION_SIZE = 10;
    private final ElasticsearchServiceClient elasticsearchClient;
    private final BulkResponsePostProcessor responsePostProcessor;
    private ResponseIdConverter idConverter = new ResponseIdConverter() {};
    private int currentBulkSize = DEFAULT_BULK_SIZE;
    private long requestLimitMb = MemLimitIndexRequestContainer.DEFAULT_MAX_REQUEST_SIZE_MB;

    public BulkRequestSender(final ElasticsearchServiceClient elasticsearchClient,
                             final BulkResponsePostProcessor responsePostProcessor,
                             final ResponseIdConverter idConverter) {
        this(elasticsearchClient, responsePostProcessor);
        this.idConverter = idConverter;
    }

    public BulkRequestSender(final ElasticsearchServiceClient elasticsearchClient,
                             final BulkResponsePostProcessor responsePostProcessor,
                             final ResponseIdConverter idConverter,
                             final Integer bulkSize,
                             final Integer requestLimitMb) {
        this(elasticsearchClient, responsePostProcessor, idConverter);
        this.currentBulkSize = bulkSize;
        this.requestLimitMb = requestLimitMb;
    }

    public void indexDocuments(final String indexName,
                               final PipelineEvent.ObjectType objectType,
                               final List<DocWriteRequest> documentRequests,
                               final LocalDateTime syncStart) {
        indexDocuments(indexName, objectType, documentRequests, syncStart, currentBulkSize);

    }

    public void indexDocuments(final String indexName,
                               final PipelineEvent.ObjectType objectType,
                               final List<DocWriteRequest> documentRequests,
                               final LocalDateTime syncStart,
                               final int bulkSize) {
        indexDocuments(indexName, Collections.singletonList(objectType), documentRequests, syncStart, bulkSize);

    }

    public void indexDocuments(final String indexName,
                               final List<PipelineEvent.ObjectType> objectTypes,
                               final List<DocWriteRequest> documentRequests,
                               final LocalDateTime syncStart) {
        indexDocuments(indexName, objectTypes, documentRequests, syncStart, currentBulkSize);

    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void indexDocuments(final String indexName,
                               final List<PipelineEvent.ObjectType> objectTypes,
                               final List<DocWriteRequest> documentRequests,
                               final LocalDateTime syncStart,
                               final int bulkSize) {
        final int partitionSize = Integer.min(MAX_PARTITION_SIZE,
                                              Integer.max(MIN_PARTITION_SIZE, bulkSize / 10));
        try (IndexRequestContainer requestContainer =
                 new MemLimitIndexRequestContainer(requests -> elasticsearchClient.sendRequests(indexName, requests),
                                                   partitionSize, requestLimitMb)) {
            requestContainer.enablePostProcessing(responsePostProcessor, idConverter, objectTypes, syncStart);
            documentRequests.stream()
                .map(this::tryToCastToIndexRequest)
                .filter(Objects::nonNull)
                .forEach(requestContainer::add);
        }
    }

    private IndexRequest tryToCastToIndexRequest(final DocWriteRequest request) {
        try {
            return (IndexRequest) request;
        } catch (ClassCastException e) {
            return null;
        }
    }
}
