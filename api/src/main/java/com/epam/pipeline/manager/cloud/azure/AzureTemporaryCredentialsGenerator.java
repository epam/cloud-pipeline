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

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.manager.cloud.TemporaryCredentialsGenerator;
import com.epam.pipeline.manager.datastorage.providers.azure.AzureStorageHelper;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.microsoft.azure.storage.blob.ContainerSASPermission;
import com.microsoft.azure.storage.blob.SASProtocol;
import com.microsoft.azure.storage.blob.SASQueryParameters;
import com.microsoft.azure.storage.blob.ServiceSASSignatureValues;
import com.microsoft.azure.storage.blob.SharedKeyCredentials;
import com.vividsolutions.jts.util.Assert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AzureTemporaryCredentialsGenerator implements TemporaryCredentialsGenerator<AzureBlobStorage> {

    private final CloudRegionManager cloudRegionManager;
    private final PreferenceManager preferenceManager;
    private final MessageHelper messageHelper;

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
        final AzureRegion region = cloudRegionManager.getAzureRegion(dataStorage);
        final AzureRegionCredentials credentials = cloudRegionManager.loadCredentials(region);

        final Integer duration =
                preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_TEMP_CREDENTIALS_DURATION);

        final AzureStorageHelper helper = new AzureStorageHelper(region, credentials, messageHelper);

        Assert.isTrue(actions.size() == 1, "Multiple actions is not supported for AZURE provider");

        final DataStorageAction dataStorageAction = actions.get(0);
        final OffsetDateTime expiryTime = OffsetDateTime.now().plusSeconds(duration);
        final ServiceSASSignatureValues values = new ServiceSASSignatureValues()
                .withProtocol(SASProtocol.HTTPS_ONLY)
                .withExpiryTime(expiryTime)
                .withContainerName(dataStorage.getPath())
                .withContentType("container")
                .withPermissions(buildPermissions(dataStorageAction));
        helper.addIPRangeToSASValue(values);
        final SharedKeyCredentials credential = helper.getStorageCredential();
        final SASQueryParameters token = values.generateSASQueryParameters(credential);

        return TemporaryCredentials.builder()
                .region(region.getRegionCode())
                .accessKey(region.getStorageAccount())
                .token(token.encode())
                .expirationTime(TemporaryCredentialsGenerator
                        .expirationTimeWithUTC(new Date(expiryTime.toInstant().toEpochMilli())))
                .build();
    }

    @Override
    public AzureRegion getRegion(final AzureBlobStorage dataStorage) {
        return cloudRegionManager.getAzureRegion(dataStorage);
    }

    private String buildPermissions(final DataStorageAction dataStorageAction) {
        final ContainerSASPermission permission = new ContainerSASPermission();
        permission.withList(true);
        permission.withRead(dataStorageAction.isRead());
        if (dataStorageAction.isWrite()) {
            permission.withWrite(true);
            permission.withDelete(true);
        }
        return permission.toString();
    }
}
