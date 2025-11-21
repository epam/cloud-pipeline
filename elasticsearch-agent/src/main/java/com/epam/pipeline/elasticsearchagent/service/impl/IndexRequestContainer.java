/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.elasticsearch.client.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearch.model.BulkItemResponse;
import com.epam.pipeline.elasticsearch.model.BulkResponse;
import com.epam.pipeline.elasticsearch.model.DocWriteRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class IndexRequestContainer implements AutoCloseable {
    private final String indexName;
    private List<DocWriteRequest> requests;
    private ElasticsearchServiceClient elasticsearchServiceClient;
    private Integer bulkSize;

    public IndexRequestContainer(String indexName,
                                 ElasticsearchServiceClient elasticsearchServiceClient, Integer bulkSize) {
        this.indexName = indexName;
        this.elasticsearchServiceClient = elasticsearchServiceClient;
        this.bulkSize = bulkSize;
        this.requests = new ArrayList<>();
    }

    public void add(final DocWriteRequest request) {
        requests.add(request);
        if (requests.size() == bulkSize) {
            flush();
        }
    }

    @Override
    public void close() {
        if (CollectionUtils.isEmpty(requests)) {
            return;
        }
        flush();
    }

    private void flush() {
        BulkResponse documents = elasticsearchServiceClient.sendRequests(indexName, requests);
        long unsuccessfulRequestsCount = 0L;
        if (documents != null && documents.getItems() != null) {
            BulkItemResponse[] documentsItems = documents.getItems();
            for (final BulkItemResponse response : documentsItems) {
                if (response.isFailed()) {
                    unsuccessfulRequestsCount += 1;
                }
            }
            if (unsuccessfulRequestsCount == 0) {
                log.info("{} files have been uploaded", documentsItems.length);
            } else {
                log.info("{} files have been uploaded and {} files have not been uploaded",
                        documentsItems.length, unsuccessfulRequestsCount);
                Arrays.stream(documentsItems)
                        .filter(BulkItemResponse::isFailed)
                        .findFirst()
                        .ifPresent(response -> log.debug("One of the files has not been uploaded due to: {}",
                                response.getFailureMessage()));
            }
        } else {
            log.info("No documents where uploaded");
        }
        requests.clear();
    }
}
