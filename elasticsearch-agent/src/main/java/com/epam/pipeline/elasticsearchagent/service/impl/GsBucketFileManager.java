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
import com.epam.pipeline.elasticsearchagent.utils.IteratorUtils;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.vo.data.storage.DataStorageTagLoadBatchRequest;
import com.epam.pipeline.vo.data.storage.DataStorageTagLoadRequest;
import com.google.api.services.storage.StorageScopes;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageClass;
import com.google.cloud.storage.StorageOptions;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;

import java.text.ParseException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.elasticsearchagent.utils.ESConstants.DOC_MAPPING_TYPE;

@Slf4j
@RequiredArgsConstructor
public class GsBucketFileManager implements ObjectStorageFileManager {

    private final CloudPipelineAPIClient cloudPipelineAPIClient;
    private final StorageFileMapper fileMapper = new StorageFileMapper();

    @Getter
    private final DataStorageType type = DataStorageType.GS;

    static {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        ESConstants.FILE_DATE_FORMAT.setTimeZone(tz);
    }

    @Override
    public Stream<DataStorageFile> listVersionsWithTags(final AbstractDataStorage dataStorage,
                                                        final TemporaryCredentials credentials) {
        return versions(dataStorage, credentials);
    }

    @Override
    public void listAndIndexFiles(final String indexName,
                                  final AbstractDataStorage dataStorage,
                                  final TemporaryCredentials credentials,
                                  final PermissionsContainer permissionsContainer,
                                  final IndexRequestContainer requestContainer) {
        fileChunks(dataStorage, credentials).forEach(chunk -> {
            final Map<String, Map<String, String>> pathTags = cloudPipelineAPIClient.loadDataStorageTagsMap(
                    dataStorage.getId(),
                    new DataStorageTagLoadBatchRequest(
                            chunk.stream()
                                    .map(DataStorageFile::getPath)
                                    .map(DataStorageTagLoadRequest::new)
                                    .collect(Collectors.toList())));
            for (final DataStorageFile file : chunk) {
                file.setTags(pathTags.get(file.getPath()));
                requestContainer.add(createIndexRequest(file, indexName, dataStorage, credentials.getRegion(),
                        permissionsContainer));
            }
        });
    }

    private Stream<DataStorageFile> versions(final AbstractDataStorage dataStorage,
                                             final TemporaryCredentials credentials) {
        final Storage googleStorage = getGoogleStorage(credentials);
        final String bucketName = dataStorage.getPath();
        final Iterator<Blob> iterator = googleStorage.list(bucketName, Storage.BlobListOption.versions(true))
                .iterateAll()
                .iterator();
        return IteratorUtils.streamFrom(iterator)
                .filter(blob -> !StringUtils.endsWithIgnoreCase(blob.getName(), ESConstants.HIDDEN_FILE_NAME))
                .map(blob -> {
                    final DataStorageFile file = new DataStorageFile();
                    file.setName(blob.getName());
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
                });
    }

    private Stream<List<DataStorageFile>> fileChunks(final AbstractDataStorage dataStorage,
                                                     final TemporaryCredentials credentials) {
        return IteratorUtils.streamFrom(IteratorUtils.chunked(files(dataStorage, credentials).iterator()));
    }

    Stream<DataStorageFile> files(final AbstractDataStorage dataStorage,
                                  final TemporaryCredentials credentials) {
        final Storage googleStorage = getGoogleStorage(credentials);
        final String bucketName = dataStorage.getPath();
        final Iterator<Blob> iterator = googleStorage.list(bucketName).iterateAll().iterator();
        return IteratorUtils.streamFrom(iterator)
                .filter(blob -> !StringUtils.endsWithIgnoreCase(blob.getName(), ESConstants.HIDDEN_FILE_NAME))
                .map(this::convertToStorageFile);
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

    private DataStorageFile convertToStorageFile(final Blob blob) {
        final DataStorageFile file = new DataStorageFile();
        file.setName(blob.getName());
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
