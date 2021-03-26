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

import com.epam.pipeline.elasticsearchagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearchagent.service.ObjectStorageFileManager;
import com.epam.pipeline.elasticsearchagent.service.ObjectStorageIndex;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.GSBucketStorage;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.search.SearchDocumentType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@RunWith(MockitoJUnitRunner.class)
public class ObjectStorageIndexTest {
    
    private static final String TEST_BLOB_NAME_1 = "1";
    private static final String TEST_BLOB_NAME_2 = "2";

    private final AbstractDataStorage dataStorage = new GSBucketStorage();
    private final TemporaryCredentials temporaryCredentials = new TemporaryCredentials();
    
    @Mock
    private IndexRequestContainer requestContainer;
    @Mock
    private ObjectStorageFileManager fileManager;
    @Mock
    private CloudPipelineAPIClient cloudPipelineAPIClient;
    @Mock
    private ElasticsearchServiceClient elasticsearchServiceClient;
    @Mock
    private ElasticIndexService elasticIndexService;
    
    @Spy
    private final ObjectStorageIndex objectStorageIndex = new ObjectStorageIndexImpl(
            cloudPipelineAPIClient, 
            elasticsearchServiceClient,
            elasticIndexService,
            fileManager,
            TEST_NAME,
            TEST_NAME,
            1000,
            1000,
            DataStorageType.GS,
            SearchDocumentType.GS_FILE);

    @Test
    public void shouldAddZeroFilesToRequestContainer() {
        final List<DataStorageFile> files = Collections.emptyList();
        verifyRequestContainerState(files, 0);
    }

    @Test
    public void shouldAddTwoFilesToRequestContainer() {
        final List<DataStorageFile> files = Arrays.asList(createFile(TEST_BLOB_NAME_1), createFile(TEST_BLOB_NAME_2));
        verifyRequestContainerState(files, 2);
    }

    private void verifyRequestContainerState(final List<DataStorageFile> files, final int numberOfInvocation) {
        setUpReturnValues(files);
        objectStorageIndex.indexStorage(dataStorage);
        verifyNumberOfInsertions(numberOfInvocation);
    }

    private void setUpReturnValues(final List<DataStorageFile> files) {
        Mockito.doAnswer(i -> files.stream())
               .when(fileManager)
               .files(any(), any(), temporaryCredentials);
    }

    private void verifyNumberOfInsertions(final int numberOfInvocation) {
        verify(requestContainer, times(numberOfInvocation)).add(any());
    }

    private DataStorageFile createFile(final String name) {
        final DataStorageFile file = new DataStorageFile();
        file.setName(name);
        file.setPath(name);
        file.setSize(1L);
        return file;
    }
}
