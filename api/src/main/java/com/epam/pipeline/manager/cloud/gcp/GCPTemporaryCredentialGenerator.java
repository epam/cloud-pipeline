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

package com.epam.pipeline.manager.cloud.gcp;

import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.cloud.TemporaryCredentialsGenerator;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.google.api.services.iamcredentials.v1.IAMCredentials;
import com.google.api.services.iamcredentials.v1.model.GenerateAccessTokenRequest;
import com.google.api.services.iamcredentials.v1.model.GenerateAccessTokenResponse;
import com.google.api.services.storage.StorageScopes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Provides short-lived service account credentials for operations on data storages.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GCPTemporaryCredentialGenerator implements TemporaryCredentialsGenerator<GSBucketStorage> {
    private static final String ACCOUNT_NAME_REQUEST_FORMAT = "projects/-/serviceAccounts/%s";

    private final CloudRegionManager cloudRegionManager;
    private final PreferenceManager preferenceManager;
    private final GCPClient gcpClient;

    @Override
    public DataStorageType getStorageType() {
        return DataStorageType.GS;
    }

    @Override
    public TemporaryCredentials generate(final List<DataStorageAction> actions, final List<GSBucketStorage> storages) {
        return generate(actions, storages.get(0));
    }

    /**
     * Generates temporary access credentials. Returns full read access to all data storages if all actions require
     * only read access. If at least one action requires write access returns full write access to all data storages.
     * @param actions to be performed on storage(s)
     * @param dataStorage to get additional info
     * @return access token, project ID and expiration time
     */
    private TemporaryCredentials generate(final List<DataStorageAction> actions, final GSBucketStorage dataStorage) {
        try {
            final GCPRegion region = getRegion(dataStorage);
            final IAMCredentials credentials = gcpClient.buildIAMCredentialsClient(region);

            final GenerateAccessTokenRequest generateAccessTokenRequest = new GenerateAccessTokenRequest()
                    .setLifetime(buildDuration())
                    .setScope(buildScope(actions));

            final GenerateAccessTokenResponse tokenResponse = credentials
                    .projects()
                    .serviceAccounts()
                    .generateAccessToken(String.format(ACCOUNT_NAME_REQUEST_FORMAT, region.getImpersonatedAccount()),
                            generateAccessTokenRequest)
                    .execute();

            return TemporaryCredentials.builder()
                    .token(tokenResponse.getAccessToken())
                    .expirationTime(tokenResponse.getExpireTime())
                    .accessKey(region.getProject())
                    .build();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new IllegalArgumentException(String.format("An error occurred during generating temporary " +
                    "credentials for %s storage %s", dataStorage.getType(), dataStorage.getPath()));
        }
    }

    @Override
    public GCPRegion getRegion(final GSBucketStorage dataStorage) {
        return cloudRegionManager.getGCPRegion(dataStorage);
    }

    private String buildDuration() {
        final Integer duration =
                preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_TEMP_CREDENTIALS_DURATION);
        Assert.notNull(duration, "Data storage temporary credential duration must be specified");
        return String.format("%ds", duration);
    }

    private List<String> buildScope(final List<DataStorageAction> actions) {
        return actions.stream()
                .filter(action -> action.isWrite() || action.isWriteVersion()).findAny()
                .map(action -> Collections.singletonList(StorageScopes.DEVSTORAGE_FULL_CONTROL))
                .orElse(Collections.singletonList(StorageScopes.DEVSTORAGE_READ_ONLY));
    }
}
