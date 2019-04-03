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

package com.epam.pipeline.manager.datastorage.providers.gcp;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.region.GCPRegion;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageClass;
import com.google.cloud.storage.StorageOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class GSBucketStorageHelper {
    private static final String EMPTY_PREFIX = "";
    private static final int REGION_ZONE_LENGTH = -2;

    private final MessageHelper messageHelper;
    private final GCPRegion region;

    public String createGoogleStorage(final GSBucketStorage storage) {
        final Storage client = getClient();
        final Bucket bucket = client.create(BucketInfo.newBuilder(storage.getPath())
                .setStorageClass(StorageClass.REGIONAL)
                .setLocation(trimRegionZone(region.getRegionCode()))
                .build());
        return bucket.getName();
    }

    public void deleteGoogleStorage(final String bucketName) {
        final Storage client = getClient();
        final Iterable<Blob> blobs = client.list(bucketName, Storage.BlobListOption.prefix(EMPTY_PREFIX)).iterateAll();
        blobs.forEach(blob -> blob.delete());
        final boolean deleted = client.delete(bucketName);

        if (!deleted) {
            throw new IllegalArgumentException(String.format("Failed to delete google data storage %s", bucketName));
        }
    }

    public boolean checkStorageExists(final String bucketName) {
        final Storage client = getClient();
        return Objects.nonNull(client.get(bucketName));
    }

    private Storage getClient() {
        if (StringUtils.isBlank(region.getAuthFile())) {
            return StorageOptions.getDefaultInstance().getService();
        }
        try (InputStream stream = new FileInputStream(region.getAuthFile())) {
            final GoogleCredentials sourceCredentials = ServiceAccountCredentials
                    .fromStream(stream);
            return StorageOptions.newBuilder()
                    .setProjectId(region.getProject())
                    .setCredentials(sourceCredentials)
                    .build()
                    .getService();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to retrieve google storage client");
        }
    }

    private String trimRegionZone(final String region) {
        return StringUtils.substring(region, 0, REGION_ZONE_LENGTH);
    }
}
