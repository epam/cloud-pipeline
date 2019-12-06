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
import com.epam.pipeline.elasticsearchagent.service.ObjectStorageFileManager;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.storage.StorageFileMapper;
import com.epam.pipeline.elasticsearchagent.utils.ESConstants;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.google.api.services.storage.StorageScopes;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageClass;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;

import java.text.ParseException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;
import java.util.Map;
import java.util.HashMap;

import static com.epam.pipeline.elasticsearchagent.utils.ESConstants.DOC_MAPPING_TYPE;

@Slf4j
public class GsBucketFileManager implements ObjectStorageFileManager {
    private final StorageFileMapper fileMapper = new StorageFileMapper();

    static {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        ESConstants.FILE_DATE_FORMAT.setTimeZone(tz);
    }

    @Override
    public void listAndIndexFiles(final String indexName,
                                  final AbstractDataStorage dataStorage,
                                  final TemporaryCredentials credentials,
                                  final PermissionsContainer permissionsContainer,
                                  final IndexRequestContainer requestContainer) {
        final Iterable<Blob> blobs = getAllBlobsFromStorage(dataStorage, credentials);
        blobs.forEach(file -> indexFile(requestContainer,
                                        file,
                                        dataStorage,
                                        credentials,
                                        permissionsContainer,
                                        indexName));
    }

    Iterable<Blob> getAllBlobsFromStorage(final AbstractDataStorage dataStorage,
                                          final TemporaryCredentials credentials) {
        final Storage googleStorage = getGoogleStorage(credentials);
        final String bucketName = dataStorage.getPath();
        return googleStorage.list(bucketName).iterateAll();
    }

    private Storage getGoogleStorage(final TemporaryCredentials credentials) {
        final GoogleCredentials googleCredentials = createGoogleCredentials(credentials);
        return StorageOptions
                .newBuilder()
                .setCredentials(googleCredentials)
                .setProjectId(credentials.getAccessKey())
                .build()
                .getService();
    }

    private GoogleCredentials createGoogleCredentials(final TemporaryCredentials credentials) {
        try {
            final Date expirationDate = ESConstants.FILE_DATE_FORMAT.parse(credentials.getExpirationTime());
            final AccessToken token = new AccessToken(credentials.getToken(), expirationDate);
            return GoogleCredentials
                    .create(token)
                    .createScoped(Collections.singletonList(StorageScopes.DEVSTORAGE_READ_ONLY));
        } catch (ParseException e) {
            log.error(e.getMessage());
            throw new DateTimeParseException(e.getMessage(), credentials.getExpirationTime(), e.getErrorOffset());
        }
    }

    private void indexFile(final IndexRequestContainer indexContainer,
                           final Blob file,
                           final AbstractDataStorage storage,
                           final TemporaryCredentials credentials,
                           final PermissionsContainer permissions,
                           final String indexName) {
        convertToStorageFile(file)
                .ifPresent(
                    item -> indexContainer
                            .add(createIndexRequest(item, indexName, storage, credentials.getRegion(), permissions)));
    }

    private Optional<DataStorageFile> convertToStorageFile(final Blob blob) {
        final String relativePath = blob.getName();
        if (StringUtils.endsWithIgnoreCase(relativePath, ESConstants.HIDDEN_FILE_NAME)) {
            return Optional.empty();
        }
        final DataStorageFile file = new DataStorageFile();
        file.setName(relativePath);
        file.setPath(relativePath);
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
        return Optional.of(file);
    }

    IndexRequest createIndexRequest(final DataStorageFile item,
                                    final String indexName,
                                    final AbstractDataStorage storage,
                                    final String region,
                                    final PermissionsContainer permissions) {
        return new IndexRequest(indexName, DOC_MAPPING_TYPE)
                .source(fileMapper.fileToDocument(item, storage, region, permissions,
                                                  SearchDocumentType.GS_FILE));
    }
}
