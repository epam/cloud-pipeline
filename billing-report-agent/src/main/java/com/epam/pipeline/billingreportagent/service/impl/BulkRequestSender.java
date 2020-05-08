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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkResponse;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@AllArgsConstructor
public class BulkRequestSender {

    private static final int DEFAULT_BULK_SIZE = 1000;
    private final ElasticsearchServiceClient elasticsearchClient;

    private int currentBulkSize = DEFAULT_BULK_SIZE;
    private long insertTimeout = 0;

    public void indexDocuments(final List<DocWriteRequest> documentRequests) {
        indexDocuments(documentRequests, currentBulkSize);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void indexDocuments(final List<DocWriteRequest> documentRequests,
                               final int bulkSize) {
        if (documentRequests.isEmpty()) {
            return;
        }
        ListUtils.partition(documentRequests, bulkSize).forEach(chunk -> {
            try {
                indexChunk(chunk);
                Thread.sleep(insertTimeout);
            } catch (Exception e) {
                log.error("Partial error during index sync: {}.", e.getMessage());
            }
        });
    }

    private void indexChunk(final List<DocWriteRequest> documentRequests) {
        final BulkResponse response = elasticsearchClient.sendRequests(documentRequests);
        if (ObjectUtils.isEmpty(response)) {
            log.debug("No documents were created in Elasticsearch for {} request(s).", documentRequests.size());
            return;
        }
        final Map<Boolean, List<BulkItemResponse>> indexResults = Arrays.stream(response.getItems())
                .collect(Collectors.partitioningBy(BulkItemResponse::isFailed));
        final List<BulkItemResponse> failed = indexResults.get(true);
        if (CollectionUtils.isNotEmpty(failed)) {
            log.error("Failed to insert {} of {} document(s) into Elasticsearch.",
                    failed.size(), documentRequests.size());
            failed.forEach(item ->
                    log.error("Error for doc {} index {}: {}.", item.getId(), item.getIndex(), item.getFailureMessage()));
        }
        final List<BulkItemResponse> successful = indexResults.get(false);
        if (CollectionUtils.isNotEmpty(successful)) {
            log.debug("Successfully inserted {} of {} document(s) into Elasticsearch).",
                    successful.size(), documentRequests.size());
        }
    }
}
