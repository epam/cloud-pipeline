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

package com.epam.pipeline.dts.transfer.service.impl;

import com.epam.pipeline.dts.transfer.model.StorageType;
import com.epam.pipeline.dts.transfer.model.TransferTask;
import com.epam.pipeline.dts.transfer.service.DataUploader;
import com.epam.pipeline.dts.transfer.service.DataUploaderProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.util.Assert;

import java.util.Map;

@RequiredArgsConstructor
public class DataUploaderProviderImpl implements DataUploaderProvider {

    private final Map<StorageType, DataUploader> dataUploaders;

    @Override
    public DataUploader getStorageUploader(TransferTask transferTask) {
        StorageType storageType = getTaskStorageType(transferTask);
        DataUploader uploader = dataUploaders.get(storageType);
        Assert.notNull(uploader, String.format("Storage type %s is not supported", storageType));
        return uploader;
    }

    private StorageType getTaskStorageType(TransferTask transferTask) {
        StorageType sourceType = transferTask.getSource().getType();
        StorageType destinationType = transferTask.getDestination().getType();
        Assert.isTrue(sourceType != destinationType,
                String.format("Cannot perform operation between files with the same type: %s", sourceType));
        Assert.isTrue(isLocal(sourceType) || isLocal(destinationType),
                "Transfer data between cloud types is not supported");
        return isLocal(sourceType) ? destinationType : sourceType;
    }

    private boolean isLocal(StorageType storageType) {
        return storageType == StorageType.LOCAL;
    }
}
