/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.elasticsearchagent.service.index;

import com.epam.pipeline.elasticsearch.client.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearch.model.DocWriteRequest;
import com.epam.pipeline.elasticsearch.model.IndexRequest;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.elasticsearchagent.service.lock.LockService;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.GSBucketStorage;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.vo.EntityPermissionVO;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_NAME;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class StorageDocumentServiceTest {

    private static final Long STORAGE_ID = 1L;
    private static final String INDEX = "index";
    private static final int BULK_SIZE = 1000;
    private static final int MAX_DOCUMENT_ID_BYTES = 512;

    @Mock
    private CloudPipelineAPIClient cloudPipelineAPIClient;
    @Mock
    private ElasticsearchServiceClient elasticsearchServiceClient;
    @Mock
    private LockService lockService;

    private StorageDocumentService storageDocumentService;

    @BeforeEach
    void init() {
        storageDocumentService = new StorageDocumentService(
                cloudPipelineAPIClient, elasticsearchServiceClient, lockService);
        ReflectionTestUtils.setField(storageDocumentService, "bulkInsertSize", BULK_SIZE);
        ReflectionTestUtils.setField(storageDocumentService, "tagDelimiter", ";");
        ReflectionTestUtils.setField(storageDocumentService, "indexPrefix", TEST_NAME);
        ReflectionTestUtils.setField(storageDocumentService, "indexName", TEST_NAME);
    }

    @Test
    void shouldIndexFileWithPathLongerThanDocumentIdLimit() {
        final String longPath = StringUtils.repeat('a', MAX_DOCUMENT_ID_BYTES + 1);
        final List<DocWriteRequest> sentRequests = new ArrayList<>();
        doReturn(new GSBucketStorage(STORAGE_ID, "storage", "storage", new StoragePolicy(), null))
                .when(cloudPipelineAPIClient).loadDataStorage(STORAGE_ID);
        doReturn(new EntityPermissionVO()).when(cloudPipelineAPIClient).loadPermissionsForEntity(any(), any());
        doReturn(Collections.singletonList(INDEX)).when(elasticsearchServiceClient).findIndices(any());
        // the requests list is cleared once it is sent, so it is copied here
        doAnswer(invocation -> {
            final List<DocWriteRequest> requests = invocation.getArgument(1);
            sentRequests.addAll(requests);
            return null;
        }).when(elasticsearchServiceClient).sendRequests(eq(INDEX), any());

        storageDocumentService.indexFile(STORAGE_ID, Collections.singletonList(createFile(longPath)));

        assertEquals(1, sentRequests.size());
        final IndexRequest request = (IndexRequest) sentRequests.get(0);
        assertAll(
            () -> assertTrue(request.id().getBytes(StandardCharsets.UTF_8).length <= MAX_DOCUMENT_ID_BYTES),
            () -> assertEquals(longPath, request.sourceAsMap().get("id")),
            () -> assertEquals(longPath, request.sourceAsMap().get("path"))
        );
    }

    private DataStorageFile createFile(final String path) {
        final DataStorageFile file = new DataStorageFile();
        file.setName(path);
        file.setPath(path);
        file.setSize(1L);
        return file;
    }
}
