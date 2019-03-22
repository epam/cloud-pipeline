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

import com.epam.pipeline.elasticsearchagent.service.BulkRequestCreator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class IndexRequestContainer implements AutoCloseable {
    private List<IndexRequest> requests;
    private BulkRequestCreator bulkRequestCreator;
    private Integer bulkSize;

    public IndexRequestContainer(BulkRequestCreator bulkRequestCreator, Integer bulkSize) {
        this.requests = new ArrayList<>();
        this.bulkRequestCreator = bulkRequestCreator;
        this.bulkSize = bulkSize;
    }

    public void add(final IndexRequest request) {
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
        BulkResponse documents = bulkRequestCreator.sendRequest(requests);
        long successfulRequestsCount = 0L;
        if (documents != null && documents.getItems() != null) {
            successfulRequestsCount = Arrays
                    .stream(documents.getItems())
                    .filter(response -> !response.isFailed())
                    .count();
        }
        log.info("{} files has been uploaded", successfulRequestsCount);
        requests.clear();
    }
}
