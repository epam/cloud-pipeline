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

import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.elasticsearchagent.utils.ESConstants;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.GSBucketStorage;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.StorageClass;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;

@RunWith(MockitoJUnitRunner.class)
public class GsBucketFileManagerTest {
    private static final String INDEX_NAME = "testIndex";
    private static final String TEST_BLOB_NAME_1 = "1";
    private static final String TEST_BLOB_NAME_2 = "2";

    @Mock
    private CloudPipelineAPIClient apiClient;
    @Mock
    private IndexRequestContainer requestContainer;
    @Spy
    private final GsBucketFileManager manager = new GsBucketFileManager(apiClient);

    private final AbstractDataStorage dataStorage = new GSBucketStorage();
    private final TemporaryCredentials temporaryCredentials = new TemporaryCredentials();
    private final PermissionsContainer permissionsContainer = new PermissionsContainer();

    @Test
    public void shouldAddZeroFilesToRequestContainer() {
        final List<Blob> files = Collections.emptyList();
        verifyRequestContainerState(files, 0);
    }

    @Test
    public void shouldAddTwoFilesToRequestContainer() {
        final List<Blob> files = Arrays.asList(createBlob(TEST_BLOB_NAME_1), createBlob(TEST_BLOB_NAME_2));
        verifyRequestContainerState(files, 2);
    }

    @Test
    public void shouldNotAddHiddenFilesToRequestContainer() {
        final List<Blob> files = Arrays.asList(createBlob(TEST_BLOB_NAME_1), createHiddenBlob(TEST_BLOB_NAME_2));
        verifyRequestContainerState(files, 1);
    }

    private void verifyRequestContainerState(final List<Blob> files, final int numberOfInvocation) {
        setUpReturnValues(files);
        manager.listAndIndexFiles(INDEX_NAME,
                                  dataStorage,
                                  temporaryCredentials,
                                  permissionsContainer,
                                  requestContainer);
        verifyNumberOfInsertions(numberOfInvocation);
        verifyBlobMapping(files, numberOfInvocation);
    }

    private void setUpReturnValues(final List<Blob> files) {
        Mockito.doAnswer(i -> files.stream())
               .when(manager)
               .files(dataStorage, temporaryCredentials);
    }

    private void verifyBlobMapping(final List<Blob> files, final int numberOfInvocation) {
        final List<DataStorageFile> capturedValues = captureDataStorageFilesIndexing(numberOfInvocation);
        final Map<String, DataStorageFile> dsFiles =
                capturedValues.stream()
                              .collect(Collectors.toMap(DataStorageFile::getName,
                                                        Function.identity()));
        files.stream().filter(this::isNotHiddenBlob)
             .forEach(blob -> assertBlobToFile(blob, dsFiles.get(blob.getName())));
    }

    private boolean isNotHiddenBlob(final Blob blob) {
        return !StringUtils.endsWithIgnoreCase(blob.getName(), ESConstants.HIDDEN_FILE_NAME);
    }

    private List<DataStorageFile> captureDataStorageFilesIndexing(final int numberOfInvocation) {
        final ArgumentCaptor<DataStorageFile> captor = ArgumentCaptor.forClass(DataStorageFile.class);
        verify(manager, times(numberOfInvocation))
                .createIndexRequest(captor.capture(), any(), any(), any(), any());
        return captor.getAllValues();
    }

    private void assertBlobToFile(final Blob blob, final DataStorageFile file) {
        assertEquals(blob.getName(), file.getName());
        assertEquals(blob.getName(), file.getPath());
        assertEquals(blob.getSize(), file.getSize());
        assertThat(file.getLabels(),
                   hasEntry(ESConstants.STORAGE_CLASS_LABEL, blob.getStorageClass().name()));
    }

    private void verifyNumberOfInsertions(final int numberOfInvocation) {
        verify(requestContainer, times(numberOfInvocation)).add(any());
    }

    private Blob createBlob(final String name) {
        final Blob result = Mockito.mock(Blob.class);
        final Long fileSize = 100L;
        final StorageClass storageClass = StorageClass.REGIONAL;
        Mockito.doReturn(name).when(result).getName();
        Mockito.doReturn(fileSize).when(result).getSize();
        Mockito.doReturn(storageClass).when(result).getStorageClass();
        return result;
    }

    private Blob createHiddenBlob(final String name) {
        return createBlob(name + ESConstants.HIDDEN_FILE_NAME.toLowerCase());
    }
}
