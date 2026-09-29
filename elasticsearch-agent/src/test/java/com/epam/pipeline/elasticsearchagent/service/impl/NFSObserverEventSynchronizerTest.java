/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.elasticsearch.ElasticStackVersion;
import com.epam.pipeline.elasticsearch.client.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearch.model.MultiSearchRequest;
import com.epam.pipeline.elasticsearch.model.MultiSearchResponse;
import com.epam.pipeline.elasticsearch.model.MultiSearchResponseInner;
import com.epam.pipeline.elasticsearchagent.service.ObjectStorageFileManager;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.S3bucketDataStorage;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.vo.EntityVO;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;

import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NFSObserverEventSynchronizerTest {

    private static final String EXCLUDE_KEY = "key";
    private static final String EXCLUDE_VALUE = "value";
    private static final String EVENTS_BUCKET = "events-bucket";
    private static final String EVENTS_FOLDER = "events";
    private static final String EVENTS_FILE = EVENTS_FOLDER + "/producer/events.txt";
    private static final String INCLUDED_STORAGE_PATH = "nfs-host:/included";
    private static final String EXCLUDED_STORAGE_PATH = "nfs-host:/excluded";
    private static final Long INCLUDED_STORAGE_ID = 1L;
    private static final Long EXCLUDED_STORAGE_ID = 2L;
    private static final Long EVENTS_STORAGE_ID = 3L;
    private static final Long INCLUDED_SHARE_MOUNT_ID = 11L;
    private static final Long EXCLUDED_SHARE_MOUNT_ID = 12L;

    @Mock
    private CloudPipelineAPIClient cloudPipelineAPIClient;
    @Mock
    private ElasticsearchServiceClient elasticsearchServiceClient;
    @Mock
    private ElasticIndexService elasticIndexService;
    @Mock
    private NFSStorageMounter nfsMounter;
    @Mock
    private ObjectStorageFileManager eventsFileManager;

    private Path rootMountPoint;
    private NFSObserverEventSynchronizer synchronizer;

    @BeforeEach
    public void setUp() throws IOException {
        rootMountPoint = Files.createTempDirectory(TEST_NAME);
        when(eventsFileManager.getType()).thenReturn(DataStorageType.S3);
        synchronizer = new NFSObserverEventSynchronizer(TEST_NAME, rootMountPoint.toString(), TEST_NAME, TEST_NAME,
                1, 1, ";", "s3://" + EVENTS_BUCKET + "/" + EVENTS_FOLDER, 10, 1, EXCLUDE_KEY, EXCLUDE_VALUE,
                cloudPipelineAPIClient, elasticsearchServiceClient, elasticIndexService,
                Collections.singletonList(eventsFileManager), nfsMounter);
    }

    @AfterEach
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(rootMountPoint.toFile());
    }

    @Test
    public void shouldSkipEventsOfStoragesExcludedByMetadata() {
        when(cloudPipelineAPIClient.loadAllDataStorages()).thenReturn(Arrays.asList(
                new S3bucketDataStorage(EVENTS_STORAGE_ID, EVENTS_BUCKET, EVENTS_BUCKET),
                nfsStorage(INCLUDED_STORAGE_ID, INCLUDED_STORAGE_PATH, INCLUDED_SHARE_MOUNT_ID),
                nfsStorage(EXCLUDED_STORAGE_ID, EXCLUDED_STORAGE_PATH, EXCLUDED_SHARE_MOUNT_ID)));
        when(cloudPipelineAPIClient.searchEntriesByMetadata(AclClass.DATA_STORAGE, EXCLUDE_KEY, EXCLUDE_VALUE))
                .thenReturn(Collections.singletonList(new EntityVO(EXCLUDED_STORAGE_ID, AclClass.DATA_STORAGE)));
        final DataStorageFile eventsFile = new DataStorageFile();
        eventsFile.setPath(EVENTS_FILE);
        when(eventsFileManager.files(eq(EVENTS_BUCKET), eq(EVENTS_FOLDER), any()))
                .thenReturn(Stream.of(eventsFile));
        final String events = String.format("1,c,%s,a.txt%n2,c,%s,b.txt%n",
                INCLUDED_STORAGE_PATH, EXCLUDED_STORAGE_PATH);
        when(eventsFileManager.readFileContent(eq(EVENTS_BUCKET), eq(EVENTS_FILE), any()))
                .thenReturn(new ByteArrayInputStream(events.getBytes(StandardCharsets.UTF_8)));
        when(elasticsearchServiceClient.getVersion()).thenReturn(ElasticStackVersion.V6);
        final MultiSearchResponse searchResponse = mock(MultiSearchResponse.class);
        when(searchResponse.getResponses()).thenReturn(new MultiSearchResponseInner.Item[0]);
        when(elasticsearchServiceClient.search(any(MultiSearchRequest.class))).thenReturn(searchResponse);

        synchronizer.synchronize(null, null);

        verify(cloudPipelineAPIClient, times(1)).loadFileShareMount(INCLUDED_SHARE_MOUNT_ID);
        verify(cloudPipelineAPIClient, never()).loadFileShareMount(EXCLUDED_SHARE_MOUNT_ID);
        verify(eventsFileManager, times(1)).deleteFile(eq(EVENTS_BUCKET), eq(EVENTS_FILE), any());
    }

    private AbstractDataStorage nfsStorage(final Long id, final String path, final Long shareMountId) {
        final NFSDataStorage storage = new NFSDataStorage(id, path, path);
        storage.setFileShareMountId(shareMountId);
        return storage;
    }
}
