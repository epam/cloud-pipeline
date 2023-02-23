/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ListVersionsRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.S3VersionSummary;
import com.amazonaws.services.s3.model.VersionListing;
import com.epam.pipeline.elasticsearchagent.service.ObjectStorageFileManager;
import com.epam.pipeline.elasticsearchagent.utils.ESConstants;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.utils.StreamUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class S3FileManager implements ObjectStorageFileManager {

    private static final String DELIMITER = "/";
    @Getter
    private final DataStorageType type = DataStorageType.S3;

    public S3FileManager() {
        //
    }

    static {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        ESConstants.FILE_DATE_FORMAT.setTimeZone(tz);
    }

    @Override
    public Stream<DataStorageFile> files(final String storage, final String path,
                                         final Supplier<TemporaryCredentials> credentialsSupplier) {
        final AmazonS3 client = getS3Client(credentialsSupplier.get());
        return StreamUtils.from(new S3PageIterator(client, storage, path))
                .flatMap(List::stream);
    }

    public InputStream readFileContent(final String storage, final String path,
                                       final Supplier<TemporaryCredentials> credentialsSupplier) {
        final AmazonS3 client = getS3Client(credentialsSupplier.get());
        final S3Object object = client.getObject(new GetObjectRequest(storage, path));
        return object.getObjectContent();
    }

    public void deleteFile(final String storage, final String path,
                           final Supplier<TemporaryCredentials> credentialsSupplier) {
        final AmazonS3 client = getS3Client(credentialsSupplier.get());
        client.deleteObject(storage, path);
    }

    @Override
    public Stream<DataStorageFile> versions(final String storage,
                                            final String path,
                                            final Supplier<TemporaryCredentials> credentialsSupplier,
                                            final boolean showDeleted) {
        final AmazonS3 client = getS3Client(credentialsSupplier);
        return StreamUtils.from(new S3VersionPageIterator(client, storage, path))
                .flatMap(List::stream)
                .filter(f -> showDeleted || !f.getDeleteMarker());
    }

    private AmazonS3 getS3Client(final TemporaryCredentials credentials) {
        BasicSessionCredentials sessionCredentials = new BasicSessionCredentials(credentials.getKeyId(),
                credentials.getAccessKey(), credentials.getToken());

        return AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(sessionCredentials))
                .withRegion(credentials.getRegion())
                .build();
    }

    private AmazonS3 getS3Client(final Supplier<TemporaryCredentials> credentialsSupplier) {
        final CloudPipelineRefreshableAWSCredentialsProvider credentialsProvider =
                new CloudPipelineRefreshableAWSCredentialsProvider(credentialsSupplier);
        return AmazonS3ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion(credentialsProvider.getRegion())
                .build();
    }

    @RequiredArgsConstructor
    public static class CloudPipelineRefreshableAWSCredentialsProvider implements AWSCredentialsProvider {

        private static final Duration DEFAULT_EXPIRE_DURATION = Duration.ofMinutes(15);
        private static final DateTimeFormatter TEMPORARY_CREDENTIALS_DATE_TIME_FORMATTER =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

        private final Supplier<TemporaryCredentials> credentialsSupplier;

        private AWSCredentials credentials;
        private ZonedDateTime expiration;
        private String region;

        @Override
        public AWSCredentials getCredentials() {
            if (isCredentialsExpired()) {
                refresh();
            }
            return credentials;
        }

        private boolean isCredentialsExpired() {
            return credentials == null
                    || Duration.between(ZonedDateTime.now(), expiration).compareTo(DEFAULT_EXPIRE_DURATION) < 0;
        }

        @Override
        public void refresh() {
            final TemporaryCredentials temporaryCredentials = credentialsSupplier.get();
            credentials = new BasicSessionCredentials(temporaryCredentials.getKeyId(),
                    temporaryCredentials.getAccessKey(),
                    temporaryCredentials.getToken());
            expiration = ZonedDateTime.parse(temporaryCredentials.getExpirationTime(),
                    TEMPORARY_CREDENTIALS_DATE_TIME_FORMATTER);
            region = temporaryCredentials.getRegion();
        }

        public String getRegion() {
            getCredentials();
            return region;
        }
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
            final ListObjectsV2Result objectsListing = client.listObjectsV2(
                new ListObjectsV2Request()
                    .withBucketName(bucket)
                    .withPrefix(path)
                    .withContinuationToken(continuationToken));
            continuationToken = objectsListing.isTruncated() ? objectsListing.getNextContinuationToken() : null;
            items = objectsListing.getObjectSummaries()
                .stream()
                .filter(file -> !StringUtils.endsWithIgnoreCase(file.getKey(),
                                                                ESConstants.HIDDEN_FILE_NAME.toLowerCase()))
                .filter(file -> !StringUtils.endsWithIgnoreCase(file.getKey(), S3FileManager.DELIMITER))
                .map(this::convertToStorageFile)
                .collect(Collectors.toList());
            return items;
        }

        private DataStorageFile convertToStorageFile(final S3ObjectSummary s3ObjectSummary) {
            final DataStorageFile file = new DataStorageFile();
            file.setName(s3ObjectSummary.getKey());
            file.setPath(s3ObjectSummary.getKey());
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

    @RequiredArgsConstructor
    private static class S3VersionPageIterator implements Iterator<List<DataStorageFile>> {

        private final AmazonS3 client;
        private final String bucket;
        private final String path;

        private String nextKeyMarker;
        private String nextVersionIdMarker;
        private List<DataStorageFile> items;

        @Override
        public boolean hasNext() {
            return items == null
                    || StringUtils.isNotBlank(nextKeyMarker)
                    || StringUtils.isNotBlank(nextVersionIdMarker);
        }

        @Override
        public List<DataStorageFile> next() {
            final VersionListing versionListing = client.listVersions(new ListVersionsRequest()
                    .withBucketName(bucket)
                    .withPrefix(path)
                    .withKeyMarker(nextKeyMarker)
                    .withVersionIdMarker(nextVersionIdMarker));
            if (versionListing.isTruncated()) {
                nextKeyMarker = versionListing.getNextKeyMarker();
                nextVersionIdMarker = versionListing.getNextVersionIdMarker();
            } else {
                nextKeyMarker = null;
                nextVersionIdMarker = null;
            }
            items = versionListing.getVersionSummaries()
                    .stream()
                    .filter(file -> !StringUtils.endsWithIgnoreCase(file.getKey(),
                            ESConstants.HIDDEN_FILE_NAME.toLowerCase()))
                    .filter(file -> !StringUtils.endsWithIgnoreCase(file.getKey(), S3FileManager.DELIMITER))
                    .map(this::convertToStorageFile)
                    .collect(Collectors.toList());
            return items;
        }

        private DataStorageFile convertToStorageFile(final S3VersionSummary summary) {
            final DataStorageFile file = new DataStorageFile();
            file.setName(FilenameUtils.getName(summary.getKey()));
            file.setPath(summary.getKey());
            file.setSize(summary.getSize());
            if (summary.getVersionId() != null && !summary.getVersionId().equals("null")) {
                file.setVersion(summary.getVersionId());
            }
            file.setChanged(ESConstants.FILE_DATE_FORMAT.format(summary.getLastModified()));
            file.setDeleteMarker(summary.isDeleteMarker());
            final Map<String, String> labels = new HashMap<>();
            labels.put("LATEST", BooleanUtils.toStringTrueFalse(summary.isLatest()));
            Optional.ofNullable(summary.getStorageClass())
                    .ifPresent(it -> labels.put(ESConstants.STORAGE_CLASS_LABEL, it));
            file.setLabels(labels);
            file.setLatest(summary.isLatest());
            return file;
        }
    }
}
