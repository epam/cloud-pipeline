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
package com.epam.pipeline.elasticsearchagent.utils;

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
import com.epam.pipeline.elasticsearchagent.service.impl.IndexRequestContainer;
import com.epam.pipeline.elasticsearchagent.service.BulkRequestCreator;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractTemporaryCredentials;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.search.SearchDocumentType;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;

public class S3Helper {
    private static final String DELIMITER = "/";
    private static final DateFormat AWS_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final String FOLDER_TOKEN_FILE = ".DS_Store";
    private static final int NOT_FOUND = 404;
    private static final String DOC_MAPPING_TYPE = "_doc";
    private static final String DOC_TYPE_FIELD = "doc_type";

    private Boolean enableTags;
    private AbstractTemporaryCredentials credentials;
    private BulkRequestCreator bulkRequestCreator;
    private AbstractDataStorage dataStorage;
    private String indexName;
    private Integer bulkInsertSize;
    private PermissionsContainer permissionsContainer;

    public S3Helper(Boolean enableTags, AbstractTemporaryCredentials credentials,
                    BulkRequestCreator bulkRequestCreator, AbstractDataStorage dataStorage,
                    String indexName, Integer bulkInsertSize, PermissionsContainer permissionsContainer) {
        this.enableTags = enableTags;
        this.credentials = credentials;
        this.bulkRequestCreator = bulkRequestCreator;
        this.dataStorage = dataStorage;
        this.indexName = indexName;
        this.bulkInsertSize = bulkInsertSize;
        this.permissionsContainer = permissionsContainer;
    }

    static {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        AWS_DATE_FORMAT.setTimeZone(tz);
    }

    public void addItems() {
        AmazonS3 client = getS3Client();
        listFiles(client, dataStorage.getPath());
    }

    private AmazonS3 getS3Client() {
        BasicSessionCredentials sessionCredentials = new BasicSessionCredentials(credentials.getKeyId(),
                credentials.getAccessKey(), credentials.getToken());

        return AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(sessionCredentials))
                .withRegion(credentials.getRegion())
                .build();
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

    private void listFiles(AmazonS3 client, String bucket) {
        ListObjectsV2Request req = new ListObjectsV2Request();
        req.setBucketName(bucket);
        req.setPrefix("");
        ListObjectsV2Result listing;
        try (IndexRequestContainer walker = new IndexRequestContainer(bulkRequestCreator, bulkInsertSize)) {
            do {
                listing = client.listObjectsV2(req);

                for (S3ObjectSummary s3ObjectSummary : listing.getObjectSummaries()) {
                    DataStorageFile file = convertToStorageFile(s3ObjectSummary);
                    if (file != null) {
                        if (enableTags) {
                            file.setTags(listObjectTags(client, bucket, s3ObjectSummary.getKey()));
                        }
                        walker.add(createIndexRequest(file));
                    }
                }
                req.setContinuationToken(listing.getNextContinuationToken());
            } while(listing.isTruncated());
        }
    }

    private DataStorageFile convertToStorageFile(S3ObjectSummary s3ObjectSummary) {
        String relativePath = s3ObjectSummary.getKey();
        if (StringUtils.endsWithIgnoreCase(relativePath, S3Helper.FOLDER_TOKEN_FILE.toLowerCase())) {
            return null;
        }
        if (relativePath.endsWith(S3Helper.DELIMITER)) {
            relativePath = relativePath.substring(0, relativePath.length() - 1);
        }
        DataStorageFile file = new DataStorageFile();
        file.setName(relativePath);
        file.setPath(relativePath);
        file.setSize(s3ObjectSummary.getSize());
        file.setVersion(null);
        file.setChanged(S3Helper.AWS_DATE_FORMAT.format(s3ObjectSummary.getLastModified()));
        file.setDeleteMarker(null);
        Map<String, String> labels = new HashMap<>();
        if (s3ObjectSummary.getStorageClass() != null) {
            labels.put("StorageClass", s3ObjectSummary.getStorageClass());
        }
        file.setLabels(labels);
        return file;
    }

    private IndexRequest createIndexRequest(final DataStorageFile item) {
        return new IndexRequest(indexName, DOC_MAPPING_TYPE)
                .source(dataStorageToDocument(item, dataStorage, credentials.getRegion(), permissionsContainer));
    }

    private static XContentBuilder dataStorageToDocument(final DataStorageFile dataStorageFile,
                                                         final AbstractDataStorage dataStorage,
                                                         final String awsRegion,
                                                         final PermissionsContainer permissions) {
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            jsonBuilder
                    .startObject()
                    .field("lastModified", dataStorageFile.getChanged())
                    .field("size", dataStorageFile.getSize())
                    .field("path", dataStorageFile.getPath())
                    .field("tags", dataStorageFile.getTags())
                    .field("storage_id", dataStorage.getId())
                    .field("storage_name", dataStorage.getName())
                    .field("storage_region", awsRegion)
                    .field(DOC_TYPE_FIELD, SearchDocumentType.S3_FILE.name());

            jsonBuilder.array("allowed_users", permissions.getAllowedUsers().toArray());
            jsonBuilder.array("denied_users", permissions.getDeniedUsers().toArray());
            jsonBuilder.array("allowed_groups", permissions.getAllowedGroups().toArray());
            jsonBuilder.array("denied_groups", permissions.getDeniedGroups().toArray());

            jsonBuilder.endObject();
            return jsonBuilder;
        } catch (IOException e) {
            throw new AmazonS3Exception("An error occurred while creating document: ", e);
        }
    }
}
