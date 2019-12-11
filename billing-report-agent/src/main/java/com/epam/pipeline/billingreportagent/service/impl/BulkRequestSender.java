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
package com.epam.pipeline.billingreportagent.service.impl;

import com.epam.pipeline.billingreportagent.service.ElasticsearchServiceClient;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.springframework.util.ObjectUtils;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@AllArgsConstructor
public class BulkRequestSender {

    private static final int DEFAULT_BULK_SIZE = 1000;
    private static final int MAX_PARTITION_SIZE = 200;
    private static final int MIN_PARTITION_SIZE = 10;
    private final ElasticsearchServiceClient elasticsearchClient;

    private int currentBulkSize = DEFAULT_BULK_SIZE;

    public void indexDocuments(final String indexName,
                               final String objectType,
                               final List<DocWriteRequest> documentRequests) {
        indexDocuments(indexName, objectType, documentRequests, currentBulkSize);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void indexDocuments(final String indexName,
                               final String objectType,
                               final List<DocWriteRequest> documentRequests,
                               final int bulkSize) {
        final int partitionSize = Integer.min(MAX_PARTITION_SIZE,
                                              Integer.max(MIN_PARTITION_SIZE, bulkSize / 10));
        ListUtils.partition(documentRequests, partitionSize).forEach(chunk -> {
            try {
                indexChunk(indexName, chunk, objectType);
            } catch (Exception e) {
                log.error("Partial error during {} index sync: {}.", indexName, e.getMessage());
            }
        });
    }

    private void indexChunk(final String indexName,
                            final List<DocWriteRequest> documentRequests,
                            final String objectType) {
        log.debug("Inserting {} documents for {}", documentRequests.size(), objectType);
        final BulkResponse response = elasticsearchClient.sendRequests(indexName, documentRequests);
        if (ObjectUtils.isEmpty(response)) {
            log.error("Elasticsearch documents for {} were not created.", objectType);
            return;
        }
    }
}
