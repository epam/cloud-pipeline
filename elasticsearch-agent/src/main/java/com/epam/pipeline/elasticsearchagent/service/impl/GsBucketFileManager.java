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

import com.epam.pipeline.elasticsearchagent.service.ObjectStorageFileManager;
import com.epam.pipeline.elasticsearchagent.utils.ESConstants;
import com.epam.pipeline.utils.StreamUtils;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.google.api.services.storage.StorageScopes;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageClass;
import com.google.cloud.storage.StorageOptions;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class GsBucketFileManager implements ObjectStorageFileManager {

    private static final String DELIMITER = "/";

    @Getter
    private final DataStorageType type = DataStorageType.GS;

    static {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        ESConstants.FILE_DATE_FORMAT.setTimeZone(tz);
    }

    @Override
    public Stream<DataStorageFile> files(final String storage,
                                         final String path,
                                         final Supplier<TemporaryCredentials> credentialsSupplier) {
        final Storage googleStorage = getGoogleStorage(credentialsSupplier);
        final Iterator<Blob> iterator = googleStorage.list(storage, 
                Storage.BlobListOption.prefix(path))
                .iterateAll()
                .iterator();
        return StreamUtils.from(iterator)
                .filter(blob -> !StringUtils.endsWithIgnoreCase(blob.getName(), ESConstants.HIDDEN_FILE_NAME))
                .filter(blob -> !StringUtils.endsWithIgnoreCase(blob.getName(), DELIMITER))
                .map(this::convertToStorageFile);
    }

    @Override
    public Stream<DataStorageFile> versions(final String storage,
                                            final String path,
                                            final Supplier<TemporaryCredentials> credentialsSupplier,
                                            final boolean showDeleted) {
        return versionsWithNativeTags(storage, path, credentialsSupplier);
    }

    @Override
    public Stream<DataStorageFile> versionsWithNativeTags(final String storage,
                                                          final String path,
                                                          final Supplier<TemporaryCredentials> credentialsSupplier) {
        final Storage googleStorage = getGoogleStorage(credentialsSupplier);
        final Iterator<Blob> iterator = googleStorage.list(storage,
                Storage.BlobListOption.prefix(path),
                Storage.BlobListOption.versions(true))
                .iterateAll()
                .iterator();
        return StreamUtils.from(iterator)
                .filter(blob -> !StringUtils.endsWithIgnoreCase(blob.getName(), ESConstants.HIDDEN_FILE_NAME))
                .filter(blob -> !StringUtils.endsWithIgnoreCase(blob.getName(), DELIMITER))
                .map(this::convertToStorageFileVersion);
    }

    @RequiredArgsConstructor
    public static class CloudPipelineRefreshableGoogleCredentials extends GoogleCredentials {

        private final Supplier<TemporaryCredentials> credentialsSupplier;
        private TemporaryCredentials credentials;

        @Override
        @SneakyThrows
        public AccessToken refreshAccessToken() {
            credentials = credentialsSupplier.get();
            final Date expirationDate = ESConstants.GS_DATE_FORMAT.parse(credentials.getExpirationTime());
            return new AccessToken(credentials.getToken(), expirationDate);
        }
        
        public String getProjectId() {
            return Optional.ofNullable(credentials).map(TemporaryCredentials::getAccessKey).orElse(null);
        }
    }

    @SneakyThrows
    private Storage getGoogleStorage(final Supplier<TemporaryCredentials> credentialsSupplier) {
        final CloudPipelineRefreshableGoogleCredentials credentials = 
                new CloudPipelineRefreshableGoogleCredentials(credentialsSupplier);
        credentials.createScoped(Collections.singletonList(StorageScopes.DEVSTORAGE_READ_ONLY));
        credentials.refresh();
        return StorageOptions
                .newBuilder()
                .setCredentials(credentials)
                .setProjectId(credentials.getProjectId())
                .build()
                .getService();
    }

    private DataStorageFile convertToStorageFile(final Blob blob) {
        final DataStorageFile file = new DataStorageFile();
        file.setName(FilenameUtils.getName(blob.getName()));
        file.setPath(blob.getName());
        file.setSize(blob.getSize());
        file.setChanged(ESConstants.FILE_DATE_FORMAT.format(Date.from(Instant.ofEpochMilli(blob.getUpdateTime()))));
        file.setVersion(null);
        file.setDeleteMarker(null);
        final Map<String, String> labels = new HashMap<>(MapUtils.emptyIfNull(blob.getMetadata()));
        final StorageClass storageClass = blob.getStorageClass();
        if (storageClass != null) {
            labels.put(ESConstants.STORAGE_CLASS_LABEL, storageClass.name());
        }
        file.setLabels(labels);
        return file;
    }

    private DataStorageFile convertToStorageFileVersion(final Blob blob) {
        final DataStorageFile file = new DataStorageFile();
        file.setName(FilenameUtils.getName(blob.getName()));
        file.setPath(blob.getName());
        file.setSize(blob.getSize());
        file.setChanged(ESConstants.FILE_DATE_FORMAT.format(Date.from(Instant.ofEpochMilli(blob.getUpdateTime()))));
        file.setVersion(blob.getGeneration().toString());
        file.setDeleteMarker(false);
        file.setTags(blob.getMetadata());
        final Map<String, String> labels = new HashMap<>();
        labels.put("LATEST", BooleanUtils.toStringTrueFalse(blob.getDeleteTime() == null));
        Optional.ofNullable(blob.getStorageClass())
                .ifPresent(it -> labels.put(ESConstants.STORAGE_CLASS_LABEL, it.name()));
        file.setLabels(labels);
        return file;
    }
}
