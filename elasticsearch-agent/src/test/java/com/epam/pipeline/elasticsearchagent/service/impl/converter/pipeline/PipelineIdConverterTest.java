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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.pipeline;

import com.epam.pipeline.elasticsearchagent.TestConstants;
import com.epam.pipeline.elasticsearchagent.service.ResponseIdConverter;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.shard.ShardId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PipelineIdConverterTest {

    private static final String PIPELINE_INDEX_NAME = "cp-pipeline";
    private static final String PIPELINE_CODE_INDEX_NAME = "cp-pipeline-code";
    private static final String DOCUMENT_ID = "123";

    private ResponseIdConverter idConverter;

    @BeforeEach
    void setup() {
        idConverter = new PipelineIdConverter(PIPELINE_INDEX_NAME, PIPELINE_CODE_INDEX_NAME) {};
    }

    @Test
    void shouldGetPipelineIdFromBulkItemResponse() {
        final BulkItemResponse response = getResponseForIndex(PIPELINE_INDEX_NAME);

        final Long id = idConverter.getId(response);
        assertNotNull(id);
        assertEquals(Long.parseLong(DOCUMENT_ID), (long) id);
    }

    @Test
    void shouldGetPipelineCodeIdFromBulkItemResponse() {
        String indexName = PIPELINE_CODE_INDEX_NAME + "-" + DOCUMENT_ID;
        final BulkItemResponse response = getResponseForIndex(indexName);

        final Long id = idConverter.getId(response);
        assertNotNull(id);
        assertEquals(Long.parseLong(DOCUMENT_ID), (long) id);
    }

    @Test
    void shouldFailGetPipelineIdFromBulkItemResponse() {
        final BulkItemResponse response = getResponseForIndex(PIPELINE_INDEX_NAME + "-" + DOCUMENT_ID);

        assertThrows(IllegalArgumentException.class, () -> idConverter.getId(response));
    }

    private BulkItemResponse getResponseForIndex(final String indexName) {
        final Index index = new Index(indexName, DOCUMENT_ID);
        final ShardId shardId = new ShardId(index, 1);
        final DocWriteResponse docWriteResponse = new IndexResponse(shardId, TestConstants.TEST_NAME, DOCUMENT_ID,
                1, 1, 1, true);
        return new BulkItemResponse(1, DocWriteRequest.OpType.CREATE, docWriteResponse);
    }
}