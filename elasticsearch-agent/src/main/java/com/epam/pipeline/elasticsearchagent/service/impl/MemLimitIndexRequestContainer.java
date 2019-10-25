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
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.bytes.BytesReference;

import java.util.Optional;

@Slf4j
public class MemLimitIndexRequestContainer extends IndexRequestContainer {

    public static final long DEFAULT_MAX_REQUEST_SIZE_MB = 100;
    private static int MB_TO_BYTES = 2 << 20;
    private long byteSizeLimit = DEFAULT_MAX_REQUEST_SIZE_MB * MB_TO_BYTES;
    private long currentSizeBytes = 0L;

    public MemLimitIndexRequestContainer(final BulkRequestCreator bulkRequestCreator,
                                         final int bulkSize) {
        super(bulkRequestCreator, bulkSize);
    }

    public MemLimitIndexRequestContainer(final BulkRequestCreator bulkRequestCreator, final int bulkSize,
                                         final long byteSizeLimit) {
        this(bulkRequestCreator, bulkSize);
        if (byteSizeLimit <= 0) {
            throw new IllegalArgumentException("Byte limit should be a positive value!");
        }
        this.byteSizeLimit = byteSizeLimit * MB_TO_BYTES;
    }

    @Override
    public void add(final IndexRequest request) {
        if ((currentSizeBytes + getRequestSize(request)) > byteSizeLimit) {
            flush();
        }
        super.add(request);
    }

    @Override
    protected void flush() {
        log.debug("Inserting {} documents for {}", requests.size(), objectTypes);
        super.flush();
        currentSizeBytes = 0L;
    }

    private long getRequestSize(final IndexRequest request) {
        return Optional.ofNullable(request.source())
            .map(BytesReference::length)
            .orElse(0);
    }
}
