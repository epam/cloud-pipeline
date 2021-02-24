/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.pipeline.elasticsearchagent.service.impl;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.elasticsearchagent.service.ObjectStorageFileManager;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.storage.StorageFileMapper;
import com.epam.pipeline.elasticsearchagent.utils.ChunkedIterator;
import com.epam.pipeline.elasticsearchagent.utils.ESConstants;
import com.epam.pipeline.elasticsearchagent.utils.ItemIterator;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.vo.data.storage.DataStorageTagLoadBatchRequest;
import com.epam.pipeline.vo.data.storage.DataStorageTagLoadRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static com.epam.pipeline.elasticsearchagent.utils.ESConstants.DOC_MAPPING_TYPE;

@RequiredArgsConstructor
public class S3FileManager implements ObjectStorageFileManager {

    private static final String DELIMITER = "/";

    private final CloudPipelineAPIClient cloudPipelineAPIClient;
    private final StorageFileMapper fileMapper = new StorageFileMapper();

    static {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        ESConstants.FILE_DATE_FORMAT.setTimeZone(tz);
    }

    @Override
    public void listAndIndexFiles(final String indexName,
                                  final AbstractDataStorage dataStorage,
                                  final TemporaryCredentials credentials,
                                  final PermissionsContainer permissions,
                                  final IndexRequestContainer requestContainer) {
        listFiles(getS3Client(credentials), indexName, dataStorage, credentials,
                permissions, requestContainer);
    }

    private AmazonS3 getS3Client(final TemporaryCredentials credentials) {
        BasicSessionCredentials sessionCredentials = new BasicSessionCredentials(credentials.getKeyId(),
                credentials.getAccessKey(), credentials.getToken());

        return AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(sessionCredentials))
                .withRegion(credentials.getRegion())
                .build();
    }

    private void listFiles(final AmazonS3 client,
                           final String indexName,
                           final AbstractDataStorage dataStorage,
                           final TemporaryCredentials credentials,
                           final PermissionsContainer permissions,
                           final IndexRequestContainer requestContainer) {
        iterateFilesInChunks(client, dataStorage).forEachRemaining(chunk -> {
            final Map<String, Map<String, String>> pathTags = cloudPipelineAPIClient.loadDataStorageTagsMap(
                    dataStorage.getId(),
                    new DataStorageTagLoadBatchRequest(
                            chunk.stream()
                                    .map(AbstractDataStorageItem::getPath)
                                    .map(DataStorageTagLoadRequest::new)
                                    .collect(Collectors.toList())));
            for (final DataStorageFile file : chunk) {
                file.setTags(pathTags.get(file.getPath()));
                requestContainer.add(createIndexRequest(file, indexName, dataStorage, credentials, permissions));
            }
        });
    }

    private Iterator<List<DataStorageFile>> iterateFilesInChunks(final AmazonS3 client,
                                                                 final AbstractDataStorage dataStorage) {
        final int chunkSize = 100;
        return new ChunkedIterator<>(iterateFiles(client, dataStorage), chunkSize);
    }

    private Iterator<DataStorageFile> iterateFiles(final AmazonS3 client, final AbstractDataStorage dataStorage) {
        return new ItemIterator<>(new S3PageIterator(client, dataStorage.getPath(), ""));
    }

    private IndexRequest createIndexRequest(final DataStorageFile item,
                                            final String indexName,
                                            final AbstractDataStorage dataStorage,
                                            final TemporaryCredentials credentials,
                                            final PermissionsContainer permissions) {
        return new IndexRequest(indexName, DOC_MAPPING_TYPE)
                .source(fileMapper.fileToDocument(item, dataStorage, credentials.getRegion(), permissions,
                        SearchDocumentType.S3_FILE));
    }

    @RequiredArgsConstructor
    public static class S3PageIterator implements Iterator<List<DataStorageFile>> {

        private final AmazonS3 client;
        private final String bucket;
        private final String path;

        private String continuationToken;
        private List<DataStorageFile> items;

        @Override
        public boolean hasNext() {
            return items == null || StringUtils.isNotBlank(continuationToken);
        }

        @Override
        public List<DataStorageFile> next() {
            final ListObjectsV2Result objectsListing = client.listObjectsV2(new ListObjectsV2Request()
                    .withBucketName(bucket)
                    .withPrefix(path)
                    .withContinuationToken(continuationToken));
            continuationToken = objectsListing.isTruncated() ? null : objectsListing.getNextContinuationToken();
            items = objectsListing.getObjectSummaries()
                    .stream()
                    .map(this::convertToStorageFile)
                    .filter(file -> !StringUtils.endsWithIgnoreCase(file.getPath(), ESConstants.HIDDEN_FILE_NAME.toLowerCase()))
                    .collect(Collectors.toList());
            return items;
        }

        private DataStorageFile convertToStorageFile(final S3ObjectSummary s3ObjectSummary) {
            final DataStorageFile file = new DataStorageFile();
            file.setName(StringUtils.removeEnd(s3ObjectSummary.getKey(), S3FileManager.DELIMITER));
            file.setPath(file.getName());
            file.setSize(s3ObjectSummary.getSize());
            file.setVersion(null);
            file.setChanged(ESConstants.FILE_DATE_FORMAT.format(s3ObjectSummary.getLastModified()));
            file.setDeleteMarker(null);
            file.setLabels(Optional.ofNullable(s3ObjectSummary.getStorageClass())
                    .map(it -> Collections.singletonMap(ESConstants.STORAGE_CLASS_LABEL, it))
                    .orElseGet(Collections::emptyMap));
            return file;
        }
    }
}
