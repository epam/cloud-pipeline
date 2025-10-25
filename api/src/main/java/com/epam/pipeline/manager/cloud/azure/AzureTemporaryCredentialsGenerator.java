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

package com.epam.pipeline.manager.cloud.azure;

import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.manager.cloud.TemporaryCredentialsGenerator;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.sas.BlobContainerSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.common.sas.SasProtocol;
import com.epam.pipeline.manager.datastorage.providers.azure.AzureBlobStorageProvider;
import com.epam.pipeline.manager.datastorage.providers.azure.AzureStorageHelper;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AzureTemporaryCredentialsGenerator implements TemporaryCredentialsGenerator<AzureBlobStorage> {

    private final CloudRegionManager cloudRegionManager;
    private final PreferenceManager preferenceManager;
    private final AzureBlobStorageProvider storageProvider;

    @Override
    public DataStorageType getStorageType() {
        return DataStorageType.AZ;
    }

    @Override
    public TemporaryCredentials generate(final List<DataStorageAction> actions, final List<AzureBlobStorage> storages) {
        Assert.isTrue(storages.size() == 1, "Multiple regions are not supported for AZURE provider");
        return generate(actions, storages.get(0));
    }

    private TemporaryCredentials generate(final List<DataStorageAction> actions, final AzureBlobStorage dataStorage) {
        final AzureStorageHelper helper = storageProvider.getAzureStorageHelper(dataStorage);
        final AzureRegion region = cloudRegionManager.getAzureRegion(dataStorage);
        final BlobContainerClient blobContainerClient = helper.getContainerClient(dataStorage);
        final Integer duration =
                preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_TEMP_CREDENTIALS_DURATION);
        final OffsetDateTime expiryTime = OffsetDateTime.now().plusSeconds(duration);
        final DataStorageAction dataStorageAction = actions.get(0);
        Assert.isTrue(actions.size() == 1, "Multiple actions is not supported for AZURE provider");

        final BlobServiceSasSignatureValues values = new BlobServiceSasSignatureValues(expiryTime,
                buildPermissions(dataStorageAction))
                .setProtocol(SasProtocol.HTTPS_ONLY)
                .setContentType("container");
        helper.addIPRangeToSASValue(values);

        final String sasToken = blobContainerClient.generateSas(values);
        return TemporaryCredentials.builder()
                .region(region.getRegionCode())
                .accessKey(region.getStorageAccount())
                .token(sasToken)
                .expirationTime(TemporaryCredentialsGenerator
                        .expirationTimeWithUTC(new Date(expiryTime.toInstant().toEpochMilli())))
                .build();
    }

    @Override
    public AzureRegion getRegion(final AzureBlobStorage dataStorage) {
        return cloudRegionManager.getAzureRegion(dataStorage);
    }

    private BlobContainerSasPermission buildPermissions(final DataStorageAction dataStorageAction) {
        final BlobContainerSasPermission permission = new BlobContainerSasPermission();
        permission.setListPermission(true);
        permission.setReadPermission(dataStorageAction.isRead());
        if (dataStorageAction.isWrite()) {
            permission.setWritePermission(true);
            permission.setDeletePermission(true);
        }
        return permission;
    }
}
