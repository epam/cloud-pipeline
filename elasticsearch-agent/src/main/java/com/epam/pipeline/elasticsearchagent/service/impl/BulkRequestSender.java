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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@AllArgsConstructor
public class BulkRequestSender {

    private static final int DEFAULT_BULK_SIZE = 1000;
    private static final int MAX_PARTITION_SIZE = 200;
    private static final int MIN_PARTITION_SIZE = 10;
    private static final int DEFAULT_MAX_REQUEST_SIZE_MB = 100;
    private final ElasticsearchServiceClient elasticsearchClient;
    private final BulkResponsePostProcessor responsePostProcessor;
    private ResponseIdConverter idConverter = new ResponseIdConverter() {};
    private int currentBulkSize = DEFAULT_BULK_SIZE;
    private int requestLimitMb = DEFAULT_MAX_REQUEST_SIZE_MB;

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
        final RequestChunk requestChunk = new RequestChunk(partitionSize);
        documentRequests.stream()
            .filter(this::fitsDocSizeLimit)
            .forEach(request -> tryToProceedRequestsInChunk(indexName, objectTypes, syncStart, requestChunk, request));
        if (!requestChunk.isEmpty()) {
            tryToIndexChunk(indexName, objectTypes, syncStart, requestChunk);
        }
    }

    private void tryToProceedRequestsInChunk(final String indexName, final List<PipelineEvent.ObjectType> objectTypes,
                                             final LocalDateTime syncStart, final RequestChunk chunk,
                                             final DocWriteRequest request) {
        if (chunk.isFull()
                || (chunk.getSizeMB() + RequestChunk.getRequestSizeMb(request)) > requestLimitMb) {
            tryToIndexChunk(indexName, objectTypes, syncStart, chunk);
            chunk.clear();
        }
        chunk.add(request);
    }

    private void tryToIndexChunk(final String indexName, final List<PipelineEvent.ObjectType> objectTypes,
                                 final LocalDateTime syncStart, final RequestChunk chunk) {
        try {
            indexChunk(indexName, chunk.getRequests(), objectTypes, syncStart);
        } catch (Exception e) {
            log.error("Partial error during {} index sync: {}.", indexName, e.getMessage());
        }
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

    private boolean fitsDocSizeLimit(final DocWriteRequest request) {
        if (RequestChunk.getRequestSizeMb(request) < requestLimitMb) {
            return true;
        } else {
            log.warn("Can't index {} due to the doc oversize!", request.id());
            return false;
        }
    }

    @Getter
    private static class RequestChunk {

        private double sizeMB;
        private final int maxElementsInChunk;
        private final List<DocWriteRequest> requests;

        public RequestChunk(final int maxElementsInChunk) {
            this.requests = new ArrayList<>();
            this.maxElementsInChunk = maxElementsInChunk;
        }

        public void add(final DocWriteRequest request) {
            requests.add(request);
            sizeMB += getRequestSizeMb(request);
        }

        public void clear() {
            requests.clear();
            sizeMB = 0;
        }

        public boolean isFull() {
            return requests.size() == maxElementsInChunk;
        }

        public boolean isEmpty() {
            return requests.isEmpty();
        }

        /**
         * Tries to cast request to {@link IndexRequest} and calculate its content size.
         *
         * @param request request to be evaluated
         * @return estimated size of a request in MB or 0 if unable to evaluate
         */
        public static double getRequestSizeMb(final DocWriteRequest request) {
            try {
                return ((IndexRequest) request).source().length() / (2 << 20);
            } catch (ClassCastException e) {
                return 0;
            }
        }
    }
}
