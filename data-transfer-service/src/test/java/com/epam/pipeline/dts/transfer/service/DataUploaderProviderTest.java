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

package com.epam.pipeline.dts.transfer.service;

import com.epam.pipeline.dts.transfer.model.StorageType;
import com.epam.pipeline.dts.transfer.model.TransferTask;
import com.epam.pipeline.dts.transfer.service.impl.DataUploaderProviderImpl;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.junit.jupiter.api.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

public class DataUploaderProviderTest extends AbstractTransferTest {

    private DataUploader s3DataUploader = mock(DataUploader.class);
    private DataUploader gsDataUploader = mock(DataUploader.class);
    private final Map<StorageType, DataUploader> dataUploaders = MapUtils.putAll(new HashMap<>(), new Object[][] {
        {StorageType.S3, s3DataUploader},
        {StorageType.GS, gsDataUploader}
    });

    private DataUploaderProvider getProvider() {
        return new DataUploaderProviderImpl(dataUploaders);
    }

    @Test
    void getProviderShouldFailIfBothSourceAndDestinationAreLocalPaths() {
        final TransferTask transferTask = taskOf(nonExistingLocalItem(), nonExistingLocalItem());

        assertThrows(RuntimeException.class, () -> getProvider().getStorageUploader(transferTask));
    }

    @Test
    void getProviderShouldFailIfBothSourceAndDestinationAreRemotePathsFromTheSameProvider() {
        final TransferTask transferTask = taskOf(s3Item(), s3Item());

        assertThrows(RuntimeException.class, () -> getProvider().getStorageUploader(transferTask));
    }

    @Test
    void getProviderShouldFailIfBothSourceAndDestinationAreRemotePathsFromDifferentProviders() {
        final TransferTask transferTask = taskOf(gsItem(), s3Item());

        assertThrows(RuntimeException.class, () -> getProvider().getStorageUploader(transferTask));
    }

    @Test
    void getProviderShouldReturnDataUploaderWithTheSameStorageTypeAsSourcePathIfDestinationPathIsLocal() {
        final TransferTask transferTask = taskOf(s3Item(), nonExistingLocalItem());

        final DataUploader uploader = getProvider().getStorageUploader(transferTask);

        assertThat(uploader, is(s3DataUploader));
    }

    @Test
    void getProviderShouldReturnDataUploaderWithTheSameStorageTypeAsDestinationPathIfSourcePathIsLocal() {
        final TransferTask transferTask = taskOf(nonExistingLocalItem(), s3Item());

        final DataUploader uploader = getProvider().getStorageUploader(transferTask);

        assertThat(uploader, is(s3DataUploader));
    }
}
