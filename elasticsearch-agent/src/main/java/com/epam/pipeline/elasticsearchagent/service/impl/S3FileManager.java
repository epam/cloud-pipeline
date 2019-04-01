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
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetObjectTaggingRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.Tag;
import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.elasticsearchagent.service.ObjectStorageFileManager;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.storage.StorageFileMapper;
import com.epam.pipeline.elasticsearchagent.utils.ESConstants;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.search.SearchDocumentType;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static com.epam.pipeline.elasticsearchagent.utils.ESConstants.DOC_MAPPING_TYPE;

public class S3FileManager implements ObjectStorageFileManager {

    private static final String DELIMITER = "/";
    private static final int NOT_FOUND = 404;

    private boolean enableTags;
    private StorageFileMapper fileMapper;

    public S3FileManager(boolean enableTags) {
        this.enableTags = enableTags;
        this.fileMapper = new StorageFileMapper();
    }

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
        final ListObjectsV2Request req = new ListObjectsV2Request();
        req.setBucketName(dataStorage.getPath());
        req.setPrefix("");
        ListObjectsV2Result listing;
        do {
            listing = client.listObjectsV2(req);

            for (S3ObjectSummary s3ObjectSummary : listing.getObjectSummaries()) {
                final DataStorageFile file = convertToStorageFile(s3ObjectSummary);
                if (file != null) {
                    if (enableTags) {
                        file.setTags(listObjectTags(client, dataStorage.getPath(), s3ObjectSummary.getKey()));
                    }
                    requestContainer.add(createIndexRequest(file, indexName, dataStorage, credentials, permissions));
                }
            }
            req.setContinuationToken(listing.getNextContinuationToken());
        } while (listing.isTruncated());
    }

    private Map<String, String> listObjectTags(AmazonS3 client, String bucket, String path) {
        try {
            GetObjectTaggingRequest getTaggingRequest =
                    new GetObjectTaggingRequest(bucket, path);
            return convertAwsTagsToMap(client.getObjectTagging(getTaggingRequest).getTagSet());
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == NOT_FOUND) {
                throw new DataStorageException(String.format("Path '%s' doesn't exist", path));
            } else {
                throw new DataStorageException(e.getMessage(), e);
            }
        }
    }

    private Map<String, String> convertAwsTagsToMap(List<Tag> awsTags) {
        return ListUtils.emptyIfNull(awsTags).stream()
                .collect(Collectors.toMap(Tag::getKey, Tag::getValue, (v1, v2) -> v2));
    }


    private DataStorageFile convertToStorageFile(S3ObjectSummary s3ObjectSummary) {
        String relativePath = s3ObjectSummary.getKey();
        if (StringUtils.endsWithIgnoreCase(relativePath, ESConstants.HIDDEN_FILE_NAME.toLowerCase())) {
            return null;
        }
        if (relativePath.endsWith(S3FileManager.DELIMITER)) {
            relativePath = relativePath.substring(0, relativePath.length() - 1);
        }
        DataStorageFile file = new DataStorageFile();
        file.setName(relativePath);
        file.setPath(relativePath);
        file.setSize(s3ObjectSummary.getSize());
        file.setVersion(null);
        file.setChanged(ESConstants.FILE_DATE_FORMAT.format(s3ObjectSummary.getLastModified()));
        file.setDeleteMarker(null);
        Map<String, String> labels = new HashMap<>();
        if (s3ObjectSummary.getStorageClass() != null) {
            labels.put("StorageClass", s3ObjectSummary.getStorageClass());
        }
        file.setLabels(labels);
        return file;
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
}
