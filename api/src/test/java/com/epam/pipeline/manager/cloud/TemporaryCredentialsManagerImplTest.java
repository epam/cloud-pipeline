/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cloud;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.manager.ObjectCreatorUtils.createS3Bucket;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TemporaryCredentialsManagerImplTest {

    private static final Long ID_1 = 1L;
    private static final Long ID_2 = 2L;
    private static final String TEST_PATH_1 = "path1";
    private static final String TEST_PATH_2 = "path2";

    private final TemporaryCredentialsGenerator generator = buildAWSCredentialsGeneratorMock();
    private final List<TemporaryCredentialsGenerator> credentialsGenerators = Collections.singletonList(generator);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final DataStorageManager dataStorageManager = mock(DataStorageManager.class);

    private final TemporaryCredentialsManagerImpl manager = new TemporaryCredentialsManagerImpl(credentialsGenerators,
            messageHelper,  dataStorageManager);

    @Before
    public void setUp() {
        when(generator.generate(any(), any())).thenReturn(TemporaryCredentials.builder().build());
    }

    @Test
    public void shouldGenerateCredentialsForMultipleRegions() {
        final List<DataStorageAction> actions = Arrays.asList(
                dataStorageAction(ID_1, true),
                dataStorageAction(ID_1, false),
                dataStorageAction(ID_2, true));
        final S3bucketDataStorage storage1 = s3DataStorage(ID_1, TEST_PATH_1);
        final S3bucketDataStorage storage2 = s3DataStorage(ID_2, TEST_PATH_2);
        when(dataStorageManager.load(ID_1)).thenReturn(storage1);
        when(dataStorageManager.load(ID_2)).thenReturn(storage2);

        manager.generate(actions);

        final Pair<List<DataStorageAction>, List<S3bucketDataStorage>> captured = capture();

        assertThat(captured.getRight())
                .hasSize(2)
                .contains(storage1, storage2);
        assertThat(captured.getLeft()).hasSize(3);
        captured.getLeft().forEach(actualAction -> {
            if (actualAction.getId().equals(ID_1)) {
                assertThat(actualAction.getBucketName()).isEqualTo(TEST_PATH_1);
                assertThat(actualAction.getPath()).isEqualTo(TEST_PATH_1);
            } else if (actualAction.getId().equals(ID_2)) {
                assertThat(actualAction.getBucketName()).isEqualTo(TEST_PATH_2);
                assertThat(actualAction.getPath()).isEqualTo(TEST_PATH_2);
            }
        });
    }

    @Test
    public void shouldGenerateCredentialsForOneRegion() {
        final List<DataStorageAction> actions = Arrays.asList(
                dataStorageAction(ID_1, true),
                dataStorageAction(ID_1, false));
        final S3bucketDataStorage storage1 = s3DataStorage(ID_1, TEST_PATH_1);
        when(dataStorageManager.load(ID_1)).thenReturn(storage1);

        manager.generate(actions);

        final Pair<List<DataStorageAction>, List<S3bucketDataStorage>> captured = capture();

        assertThat(captured.getRight())
                .hasSize(1)
                .contains(storage1);
        assertThat(captured.getLeft()).hasSize(2);
        captured.getLeft().forEach(actualAction -> {
            assertThat(actualAction.getId()).isEqualTo(ID_1);
            assertThat(actualAction.getBucketName()).isEqualTo(TEST_PATH_1);
            assertThat(actualAction.getPath()).isEqualTo(TEST_PATH_1);
        });
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailIfStorageIdWasNotSpecified() {
        manager.generate(Collections.singletonList(dataStorageAction(null, true)));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailIfStorageTypesNotSame() {
        final List<DataStorageAction> actions = Arrays.asList(
                dataStorageAction(ID_2, true),
                dataStorageAction(ID_1, false));
        when(dataStorageManager.load(ID_1)).thenReturn(s3DataStorage(ID_1, TEST_PATH_1));
        when(dataStorageManager.load(ID_2)).thenReturn(azureDataStorage(ID_2, TEST_PATH_2));

        manager.generate(actions);
    }

    private DataStorageAction dataStorageAction(final Long storageId, final boolean read) {
        final DataStorageAction action = new DataStorageAction();
        action.setId(storageId);
        action.setRead(read);
        return action;
    }

    private TemporaryCredentialsGenerator buildAWSCredentialsGeneratorMock() {
        final TemporaryCredentialsGenerator generator = mock(TemporaryCredentialsGenerator.class);
        when(generator.getStorageType()).thenReturn(DataStorageType.S3);
        return generator;
    }

    private S3bucketDataStorage s3DataStorage(final Long id, final String name) {
        return createS3Bucket(id, name, name, "OWNER");
    }

    private AzureBlobStorage azureDataStorage(final Long id, final String name) {
        return new AzureBlobStorage(id, name, name, null, null);
    }

    private ArgumentCaptor argumentCaptor() {
        return ArgumentCaptor.forClass((Class) List.class);
    }

    private Pair<List<DataStorageAction>, List<S3bucketDataStorage>> capture() {
        final ArgumentCaptor<List<DataStorageAction>> actionsCaptor = argumentCaptor();
        final ArgumentCaptor<List<S3bucketDataStorage>> storagesCaptor = argumentCaptor();
        verify(generator).generate(actionsCaptor.capture(), storagesCaptor.capture());
        final List<DataStorageAction> actualActions = actionsCaptor.getValue();
        final List<S3bucketDataStorage> actualStorages = storagesCaptor.getValue();
        return new ImmutablePair<>(actualActions, actualStorages);
    }
}
