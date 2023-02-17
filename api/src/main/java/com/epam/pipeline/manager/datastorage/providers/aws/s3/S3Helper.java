/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage.providers.aws.s3;

import com.amazonaws.HttpMethod;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.Headers;
import com.amazonaws.services.s3.model.AbortIncompleteMultipartUpload;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.BucketCrossOriginConfiguration;
import com.amazonaws.services.s3.model.BucketLifecycleConfiguration;
import com.amazonaws.services.s3.model.BucketTaggingConfiguration;
import com.amazonaws.services.s3.model.BucketVersioningConfiguration;
import com.amazonaws.services.s3.model.CORSRule;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.GetObjectMetadataRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.GetObjectTaggingRequest;
import com.amazonaws.services.s3.model.HeadBucketRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ListVersionsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.ObjectTagging;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.ResponseHeaderOverrides;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.S3VersionSummary;
import com.amazonaws.services.s3.model.SSEAlgorithm;
import com.amazonaws.services.s3.model.ServerSideEncryptionByDefault;
import com.amazonaws.services.s3.model.ServerSideEncryptionConfiguration;
import com.amazonaws.services.s3.model.ServerSideEncryptionRule;
import com.amazonaws.services.s3.model.SetBucketEncryptionRequest;
import com.amazonaws.services.s3.model.SetBucketVersioningConfigurationRequest;
import com.amazonaws.services.s3.model.SetObjectTaggingRequest;
import com.amazonaws.services.s3.model.StorageClass;
import com.amazonaws.services.s3.model.Tag;
import com.amazonaws.services.s3.model.TagSet;
import com.amazonaws.services.s3.model.VersionListing;
import com.amazonaws.services.s3.model.lifecycle.LifecycleFilter;
import com.amazonaws.services.s3.model.lifecycle.LifecyclePrefixPredicate;
import com.amazonaws.util.IOUtils;
import com.amazonaws.util.StringUtils;
import com.amazonaws.waiters.Waiter;
import com.amazonaws.waiters.WaiterParameters;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.ActionStatus;
import com.epam.pipeline.entity.datastorage.ContentDisposition;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.datastorage.lifecycle.DataStorageLifecycleRestoredListingContainer;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.utils.FileContentUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

/**
 * Util class providing methods to interact with AWS S3 API.
 * Uses Default Credential Provider Chain for AWS authorization.
 */
@RequiredArgsConstructor
public class S3Helper {
    private static final Logger LOGGER = LoggerFactory.getLogger(S3Helper.class);

    private static final int NOT_FOUND = 404;
    private static final int INVALID_RANGE = 416;
    private static final long COPYING_FILE_SIZE_LIMIT = 5L * 1024L * 1024L * 1024L; // 5gb
    private static final String BACKUP_RULE_ID = "Backup rule";
    private static final String STS_RULE_ID = "Short term storage rule";
    private static final String LTS_RULE_ID = "Long term storage rule";
    private static final String INCOMPLETE_UPLOAD_CLEANUP_RULE_ID = "Clean Incomplete Multipart Uploads";
    private static final String PATH_SHOULD_NOT_BE_EMPTY_MESSAGE = "Path should not be empty";
    private static final Long URL_EXPIRATION = 24 * 60 * 60 * 1000L;
    private static final CannedAccessControlList DEFAULT_CANNED_ACL = CannedAccessControlList.BucketOwnerFullControl;
    private static final String FOLDER_GLOB_SUFFIX = "/**";
    private static final String EMPTY_STRING = "";
    public static final String STANDARD_STORAGE_CLASS = "STANDARD";
    public static final String STORAGE_CLASS = "StorageClass";

    private final MessageHelper messageHelper;

    public AmazonS3 getDefaultS3Client() {
        return AmazonS3ClientBuilder.defaultClient();
    }

    public String createS3Bucket(final String name) {
        AmazonS3 s3client = getDefaultS3Client();
        if (s3client.doesBucketExistV2(name)) {
            throw new IllegalArgumentException(String.format("Bucket with name '%s' already exist", name));
        }
        final Bucket bucket = s3client.createBucket(new CreateBucketRequest(name));
        final Waiter waiter = s3client.waiters().bucketExists();
        waiter.run(new WaiterParameters<>(new HeadBucketRequest(name)));

        return bucket.getName();
    }

    public ActionStatus postCreationProcessing(final String name,
                                               final String policy,
                                               final List<String> allowedCidrs,
                                               final List<CORSRule> corsRules,
                                               final AwsRegion region,
                                               final boolean shared,
                                               String kmsDataEncryptionKeyId,
                                               final Map<String, String> tags) {
        try {
            final AmazonS3 s3client = getDefaultS3Client();

            if (!CollectionUtils.isEmpty(tags)) {
                s3client.setBucketTaggingConfiguration(name,
                        new BucketTaggingConfiguration(Collections.singletonList(new TagSet(tags))));
            }

            if (!StringUtils.isNullOrEmpty(policy)) {
                String contents = populateBucketPolicy(name, policy, allowedCidrs, shared);
                s3client.setBucketPolicy(name, contents);
            }

            if (!StringUtils.isNullOrEmpty(kmsDataEncryptionKeyId)) {
                enableBucketEncryption(s3client, name, kmsDataEncryptionKeyId);
            }

            if (corsRules != null && !CollectionUtils.isEmpty(corsRules)) {
                s3client.setBucketCrossOriginConfiguration(name,
                        new BucketCrossOriginConfiguration().withRules(corsRules));
            }
        } catch (AmazonS3Exception | IOException e) {
            LOGGER.error(e.getMessage(), e);
            return ActionStatus.error(e.getMessage());
        }
        return ActionStatus.success();
    }

    String populateBucketPolicy(final String name,
                                final String policy,
                                final List<String> allowedCidrs,
                                final boolean shared) throws IOException {
        return S3PolicyBuilder.builder()
                .bucketName(name)
                .allowedCidrs(allowedCidrs)
                .shared(shared)
                .build()
                .buildPolicy(policy);
    }

    /**
     * Method deletes all content of a bucket and the S3 bucket itself. At first we try to delete
     * all versions present in bucket for buckets that have versioning enabled or suspended, then
     * we delete all left objects (mainly for buckets with disabled versioning)
     * @param name of the S3 bucket
     */
    public void deleteS3Bucket(String name) {
        AmazonS3 s3client = getDefaultS3Client();
        if (s3client.doesBucketExistV2(name)) {
            deleteAllVersions(s3client, name, null);
            deleteAllInBucketObjects(name, s3client);
            s3client.deleteBucket(name);
        } else {
            LOGGER.warn("The bucket does not exist: %s", name);
        }
    }

    public void applyStoragePolicy(String bucketName, StoragePolicy policy) {
        try {
            final AmazonS3 s3client = getDefaultS3Client();
            final List<BucketLifecycleConfiguration.Rule> rules = new ArrayList<>();
            final List<BucketLifecycleConfiguration.Rule> currentRules =
                    getCurrentRules(bucketName, s3client);

            final BucketVersioningConfiguration conf = s3client.getBucketVersioningConfiguration(bucketName);
            final String currentStatus = conf.getStatus();
            final String disableStatus =
                    currentStatus.equals(BucketVersioningConfiguration.ENABLED) ||
                            currentStatus.equals(BucketVersioningConfiguration.SUSPENDED) ?
                            BucketVersioningConfiguration.SUSPENDED :
                            BucketVersioningConfiguration.OFF;

            if (policy == null || !policy.isVersioningEnabled()) {
                conf.setStatus(disableStatus);
                disableRule(rules, currentRules, BACKUP_RULE_ID);
            } else {
                conf.setStatus(BucketVersioningConfiguration.ENABLED);
                rules.add(createVersionExpirationRule(BACKUP_RULE_ID, policy.getBackupDuration()));
            }
            if (conf.getStatus().equals(BucketVersioningConfiguration.ENABLED) ||
                    !currentStatus.equalsIgnoreCase(conf.getStatus())) {
                applyVersioningConfig(bucketName, s3client, conf);
            }

            if (policy != null) {
                if (policy.getShortTermStorageDuration() != null) {
                    rules.add(createStsRule(STS_RULE_ID, policy.getShortTermStorageDuration()));
                } else {
                    disableRule(rules, currentRules, STS_RULE_ID);
                }
                if (policy.getLongTermStorageDuration() != null) {
                    rules.add(createLtsRule(LTS_RULE_ID, policy.getLongTermStorageDuration()));
                } else {
                    disableRule(rules, currentRules, LTS_RULE_ID);
                }
                if (policy.getIncompleteUploadCleanupDays() != null) {
                    rules.add(createIncompleteUploadCleanupRule(INCOMPLETE_UPLOAD_CLEANUP_RULE_ID,
                            policy.getIncompleteUploadCleanupDays()));
                }
            }
            if (!rules.isEmpty()) {
                applyRules(bucketName, s3client, rules);
            }
        } catch (AmazonS3Exception e) {
            LOGGER.error("Bucket Lifecycle configuration is not available for this account");
        }
    }

    private void enableBucketEncryption(AmazonS3 s3client, String bucketName, String kmsDataEncryptionKeyId) {
        SetBucketEncryptionRequest encryptionRequest = new SetBucketEncryptionRequest()
                .withBucketName(bucketName).withServerSideEncryptionConfiguration(
                        new ServerSideEncryptionConfiguration().withRules(
                                new ServerSideEncryptionRule().withApplyServerSideEncryptionByDefault(
                                        new ServerSideEncryptionByDefault().withSSEAlgorithm(SSEAlgorithm.KMS)
                                                .withKMSMasterKeyID(kmsDataEncryptionKeyId)
                                )));
        s3client.setBucketEncryption(encryptionRequest);
    }

    private List<BucketLifecycleConfiguration.Rule> getCurrentRules(String bucketName,
            AmazonS3 s3client) {
        BucketLifecycleConfiguration configuration =
                s3client.getBucketLifecycleConfiguration(bucketName);
        if (configuration == null || configuration.getRules() == null) {
            return Collections.emptyList();
        }
        return configuration.getRules();
    }

    private void disableRule(List<BucketLifecycleConfiguration.Rule> rules,
            List<BucketLifecycleConfiguration.Rule> currentRules, String ltsRuleId) {
        BucketLifecycleConfiguration.Rule rule = getRuleByName(ltsRuleId, currentRules);
        if (rule != null) {
            rule.setStatus(BucketLifecycleConfiguration.DISABLED);
            rules.add(rule);
        }
    }

    private BucketLifecycleConfiguration.Rule getRuleByName(String ruleId,
            List<BucketLifecycleConfiguration.Rule> currentRules) {
        return currentRules.stream().filter(rule -> rule.getId().equals(ruleId)).findFirst().orElse(null);

    }

    public void restoreFileVersion(String bucket, String path, String version) {
        AmazonS3 client = getDefaultS3Client();
        if (fileSizeExceedsLimit(client, bucket, path, version)) {
            throw new DataStorageException(String.format("Restoring file '%s' version '%s' was aborted because " +
                    "file size exceeds the limit of %s bytes", path, version, COPYING_FILE_SIZE_LIMIT));
        }
        moveS3Object(client, bucket, new MoveObjectRequest(path, version, path));
    }

    private void moveS3Object(final AmazonS3 client, final String bucket, final MoveObjectRequest moveRequest) {
        moveS3Objects(client, bucket, Collections.singletonList(moveRequest));
    }

    private void moveS3Objects(final AmazonS3 client, final String bucket, final List<MoveObjectRequest> moveRequests) {
        try (S3ObjectDeleter deleter = new S3ObjectDeleter(client, bucket)) {
            moveRequests.forEach(moveRequest -> {
                client.copyObject(moveRequest.toCopyRequest(bucket));
                deleter.deleteKey(moveRequest.getSourcePath(), moveRequest.getVersion());
            });
        } catch (SdkClientException e) {
            throw new DataStorageException(e.getMessage(), e.getCause());
        }
    }

    public DataStorageListing getItems(final String bucket, final String path, final Boolean showVersion,
                                       final Integer pageSize, final String marker, final String prefix,
                                       final DataStorageLifecycleRestoredListingContainer restoredListing) {
        String requestPath = Optional.ofNullable(path).orElse(EMPTY_STRING);
        AmazonS3 client = getDefaultS3Client();
        if (!StringUtils.isNullOrEmpty(requestPath)) {
            DataStorageItemType type = checkItemType(client, bucket, requestPath, showVersion);
            if (type == DataStorageItemType.Folder && !requestPath.endsWith(ProviderUtils.DELIMITER)) {
                requestPath += ProviderUtils.DELIMITER;
            }
        }
        DataStorageListing result = showVersion
                ? listVersions(client, bucket, requestPath, pageSize, marker, prefix, restoredListing)
                : listFiles(client, bucket, requestPath, pageSize, marker, prefix, restoredListing);
        result.getResults().sort(AbstractDataStorageItem.getStorageItemComparator());
        return result;
    }

    public Optional<DataStorageFile> findFile(final String bucket, final String path, final String version) {
        final AmazonS3 client = getDefaultS3Client();
        return findFile(client, bucket, path, version);
    }

    private Optional<DataStorageFile> findFile(final AmazonS3 client,
                                               final String bucket,
                                               final String path,
                                               final String version) {
        try {
            final GetObjectMetadataRequest metadataRequest = new GetObjectMetadataRequest(bucket, path, version);
            final ObjectMetadata metadata = client.getObjectMetadata(metadataRequest);
            final TimeZone tz = TimeZone.getTimeZone("UTC");
            final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            df.setTimeZone(tz);
            final DataStorageFile file = new DataStorageFile();
            file.setName(path);
            file.setPath(path);
            file.setSize(metadata.getContentLength());
            file.setChanged(df.format(metadata.getLastModified()));
            file.setVersion(metadata.getVersionId());
            final Map<String, String> labels = new HashMap<>();
            if (metadata.getStorageClass() != null) {
                labels.put(STORAGE_CLASS, metadata.getStorageClass());
            }
            file.setLabels(labels);
            return Optional.of(file);
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public DataStorageDownloadFileUrl generateDownloadURL(String bucket, String path,
                                                          String version, ContentDisposition contentDisposition) {
        AmazonS3 client = getDefaultS3Client();
        Date expires = new Date((new Date()).getTime() + URL_EXPIRATION);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, path);
        request.setVersionId(version);
        request.setExpiration(expires);
        if (contentDisposition != null) {
            request.setResponseHeaders(new ResponseHeaderOverrides()
                    .withContentDisposition(contentDisposition.getHeader(FilenameUtils.getName(path))));
        }
        return generatePresignedUrl(client, expires, request);
    }

    public DataStorageDownloadFileUrl generateDataStorageItemUploadUrl(String bucket, String path, String owner) {
        AmazonS3 client = getDefaultS3Client();
        Date expires = new Date((new Date()).getTime() + URL_EXPIRATION);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, path)
                .withMethod(HttpMethod.PUT)
                .withExpiration(expires);
        String ownerTag = buildOwnerTag(owner);
        request.putCustomRequestHeader(Headers.S3_TAGGING, ownerTag);
        request.putCustomRequestHeader(Headers.S3_CANNED_ACL, DEFAULT_CANNED_ACL.toString());
        return generatePresignedUrl(client, expires, ownerTag, DEFAULT_CANNED_ACL.toString(), request);
    }

    public DataStorageFile createFile(String bucket, String path, byte[] contents, String owner)
            throws DataStorageException {
        if (StringUtils.isNullOrEmpty(path)) {
            throw new DataStorageException(PATH_SHOULD_NOT_BE_EMPTY_MESSAGE);
        }
        AmazonS3 client = getDefaultS3Client();
        try {
            ByteArrayInputStream byteInputStream = new ByteArrayInputStream(contents);
            return putFileToBucket(bucket, path, client, byteInputStream, owner);
        } catch (SdkClientException e) {
            throw new DataStorageException(e.getMessage(), e.getCause());
        }
    }

    public DataStorageFile createFile(String bucket, String path, InputStream dataStream, String owner)
        throws DataStorageException {
        if (StringUtils.isNullOrEmpty(path)) {
            throw new DataStorageException(PATH_SHOULD_NOT_BE_EMPTY_MESSAGE);
        }
        AmazonS3 client = getDefaultS3Client();
        try {
            return putFileToBucket(bucket, path, client, dataStream, owner);
        } catch (SdkClientException e) {
            throw new DataStorageException(e.getMessage(), e.getCause());
        }
    }

    private DataStorageFile putFileToBucket(String bucket, String path, AmazonS3 client,
                                            InputStream dataStream, String owner) {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setLastModified(new Date());
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, path, dataStream, objectMetadata);
        List<Tag> tags = Collections.singletonList(new Tag(ProviderUtils.OWNER_TAG_KEY, owner));
        putObjectRequest.withTagging(new ObjectTagging(tags));
        putObjectRequest.withCannedAcl(DEFAULT_CANNED_ACL);
        client.putObject(putObjectRequest);
        return this.getFile(client, bucket, path);
    }

    private boolean itemExists(AmazonS3 client, String bucket, String path, boolean isFolder) {
        if (path == null) {
            path = "";
        } else if (!StringUtils.isNullOrEmpty(path) && isFolder && !path.endsWith(ProviderUtils.DELIMITER)) {
            path += ProviderUtils.DELIMITER;
        }
        ListObjectsRequest req = new ListObjectsRequest();
        req.setBucketName(bucket);
        req.setPrefix(path);
        req.setDelimiter(ProviderUtils.DELIMITER);
        ObjectListing listing = client.listObjects(req);
        return isFolder && (!listing.getCommonPrefixes().isEmpty() || !listing.getObjectSummaries().isEmpty())
                || !isFolder && hasExactMatch(listing.getObjectSummaries(), listing.getCommonPrefixes(), path);
    }

    private void deleteAllInBucketObjects(String bucket, AmazonS3 s3client) {
        try(S3ObjectDeleter deleter = new S3ObjectDeleter(s3client, bucket)) {
            ObjectListing listing;
            ListObjectsRequest request = new ListObjectsRequest();
            request.setBucketName(bucket);
            do {
                listing = s3client.listObjects(request);
                for (S3ObjectSummary s3ObjectSummary : listing.getObjectSummaries()) {
                    deleter.deleteKey(s3ObjectSummary.getKey());
                }
                request.setMarker(listing.getNextMarker());
            } while (listing.isTruncated());
        }
    }

    private DataStorageFile getFile(AmazonS3 client, String bucket, String path) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(tz);
        ListObjectsRequest req = new ListObjectsRequest();
        req.setBucketName(bucket);
        req.setPrefix(path);
        req.setDelimiter(ProviderUtils.DELIMITER);
        ObjectListing listing = client.listObjects(req);
        for (S3ObjectSummary s3ObjectSummary : listing.getObjectSummaries()) {
            String relativePath = s3ObjectSummary.getKey();
            if (relativePath.equalsIgnoreCase(path)) {
                String fileName = relativePath.substring(path.length());
                DataStorageFile file = new DataStorageFile();
                file.setName(fileName);
                file.setPath(relativePath);
                file.setSize(s3ObjectSummary.getSize());
                file.setChanged(df.format(s3ObjectSummary.getLastModified()));
                Map<String, String> labels = new HashMap<>();
                if (s3ObjectSummary.getStorageClass() != null) {
                    labels.put("StorageClass", s3ObjectSummary.getStorageClass());
                }
                file.setLabels(labels);
                return file;
            }
        }
        return null;
    }

    public DataStorageFolder createFolder(String bucket, String path) throws DataStorageException {
        if (StringUtils.isNullOrEmpty(path) || StringUtils.isNullOrEmpty(path.trim())) {
            throw new DataStorageException(PATH_SHOULD_NOT_BE_EMPTY_MESSAGE);
        }
        String folderPath = ProviderUtils.withoutLeadingDelimiter(ProviderUtils.withTrailingDelimiter(path.trim()));
        final String folderFullPath = folderPath.substring(0, folderPath.length() - 1);
        AmazonS3 client = getDefaultS3Client();
        checkItemDoesNotExist(client, bucket, folderPath, true);
        folderPath += ProviderUtils.FOLDER_TOKEN_FILE;
        String[] parts = folderPath.split(ProviderUtils.DELIMITER);
        final String folderName = parts[parts.length - 2];
        try {
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setLastModified(new Date());
            byte[] contents = "".getBytes();
            ByteArrayInputStream byteInputStream = new ByteArrayInputStream(contents);
            final PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, folderPath, byteInputStream, 
                    objectMetadata);
            putObjectRequest.withCannedAcl(DEFAULT_CANNED_ACL);
            client.putObject(putObjectRequest);
            DataStorageFolder folder = new DataStorageFolder();
            folder.setName(folderName);
            folder.setPath(folderFullPath);
            return folder;
        } catch (SdkClientException e) {
            throw new DataStorageException(e.getMessage(), e.getCause());
        }
    }

    public void deleteFile(String bucket, String path, String version, Boolean totally) {
        if (StringUtils.isNullOrEmpty(path)) {
            throw new DataStorageException(PATH_SHOULD_NOT_BE_EMPTY_MESSAGE);
        }
        AmazonS3 client = getDefaultS3Client();
        if (!StringUtils.hasValue(version) && !totally && !itemExists(client, bucket, path, false)) {
            throw new DataStorageException(messageHelper
                    .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND, path, bucket));
        }
        try {
            if (!StringUtils.hasValue(version) && totally) {
                deleteAllVersions(client, bucket, path);
            } else {
                try (S3ObjectDeleter deleter = new S3ObjectDeleter(client, bucket)) {
                    deleter.deleteKey(path, version);
                }
            }
        } catch (SdkClientException e) {
            throw new DataStorageException(e.getMessage(), e.getCause());
        }
    }

    public void deleteFolder(String bucket, String path, Boolean totally) {
        if (StringUtils.isNullOrEmpty(path)) {
            throw new DataStorageException(PATH_SHOULD_NOT_BE_EMPTY_MESSAGE);
        }
        if (!path.endsWith(ProviderUtils.DELIMITER)) {
            path += ProviderUtils.DELIMITER;
        }
        AmazonS3 client = getDefaultS3Client();
        if (!totally && !itemExists(client, bucket, path, true)) {
            throw new DataStorageException("Folder does not exist");
        }
        if (totally) {
            deleteAllVersions(client, bucket, path);
        } else {
            //indicates that only DUMMY file is present in a folder and thus it should be deleted completely
            boolean noFiles = true;
            try(S3ObjectDeleter deleter = new S3ObjectDeleter(client, bucket)) {
                ListObjectsRequest request = new ListObjectsRequest();
                request.setBucketName(bucket);
                request.setPrefix(path);
                ObjectListing listing;
                do {
                    listing = client.listObjects(request);
                    for (S3ObjectSummary s3ObjectSummary : listing.getObjectSummaries()) {
                        String relativePath = s3ObjectSummary.getKey();
                        if (relativePath.startsWith(path)) {
                            if (!relativePath.endsWith(ProviderUtils.FOLDER_TOKEN_FILE)) {
                                noFiles = false;
                            }
                            deleter.deleteKey(relativePath);
                        }
                    }
                    request.setMarker(listing.getNextMarker());
                } while (listing.isTruncated());
            }
            if (noFiles) {
                deleteAllVersions(client, bucket, path);
            }
        }
    }

    public DataStorageFile moveFile(String bucket, String oldPath, String newPath) throws DataStorageException {
        if (StringUtils.isNullOrEmpty(oldPath) || StringUtils.isNullOrEmpty(newPath)) {
            throw new DataStorageException(PATH_SHOULD_NOT_BE_EMPTY_MESSAGE);
        }
        AmazonS3 client = getDefaultS3Client();
        checkItemExists(client, bucket, oldPath, false);
        checkItemDoesNotExist(client, bucket, newPath, false);
        if (fileSizeExceedsLimit(client, bucket, oldPath)) {
            throw new DataStorageException(String.format("File '%s' moving was aborted because " +
                    "file size exceeds the limit of %s bytes", newPath, COPYING_FILE_SIZE_LIMIT));
        }
        moveS3Object(client, bucket, new MoveObjectRequest(oldPath, newPath));
        return getFile(client, bucket, newPath);
    }

    private boolean fileSizeExceedsLimit(final AmazonS3 client, final String bucket, final String path) {
        return fileSizeExceedsLimit(client, bucket, path, null);
    }

    private boolean fileSizeExceedsLimit(final AmazonS3 client, final String bucket, final String path,
                                         final String version) {
        final GetObjectMetadataRequest request = new GetObjectMetadataRequest(bucket, path, version);
        return client.getObjectMetadata(request).getContentLength() > COPYING_FILE_SIZE_LIMIT;
    }

    public DataStorageFolder moveFolder(String bucket, String rawOldPath, String rawNewPath)
            throws DataStorageException {
        if (StringUtils.isNullOrEmpty(rawOldPath) || StringUtils.isNullOrEmpty(rawNewPath)) {
            throw new DataStorageException(PATH_SHOULD_NOT_BE_EMPTY_MESSAGE);
        }
        final String oldPath = ProviderUtils.withTrailingDelimiter(rawOldPath);
        final String newPath = ProviderUtils.withTrailingDelimiter(rawNewPath);
        final String folderFullPath = newPath.substring(0, newPath.length() - 1);
        String[] parts = newPath.split(ProviderUtils.DELIMITER);
        final String folderName = parts[parts.length - 1];
        AmazonS3 client = getDefaultS3Client();
        checkItemExists(client, bucket, oldPath, true);
        checkItemDoesNotExist(client, bucket, newPath, true);
        ListObjectsRequest req = new ListObjectsRequest();
        req.setBucketName(bucket);
        ObjectListing listing = client.listObjects(req);
        boolean listingFinished = false;
        List<String> oldKeys = new ArrayList<>();
        while (!listingFinished) {
            for (S3ObjectSummary s3ObjectSummary : listing.getObjectSummaries()) {
                if (s3ObjectSummary.getSize() > COPYING_FILE_SIZE_LIMIT) {
                    throw new DataStorageException(String.format("Moving folder '%s' was aborted because " +
                                    "some of its files '%s' size exceeds the limit of %s bytes",
                            oldPath, s3ObjectSummary.getKey(), COPYING_FILE_SIZE_LIMIT));
                }
                String relativePath = s3ObjectSummary.getKey();
                if (relativePath.startsWith(oldPath)) {
                    oldKeys.add(relativePath);
                }
            }
            if (listing.isTruncated()) {
                listing = client.listNextBatchOfObjects(listing);
            } else {
                listingFinished = true;
            }
        }
        final List<MoveObjectRequest> moveRequests = oldKeys.stream()
                .map(oldKey -> new MoveObjectRequest(oldKey, newPath + oldKey.substring(oldPath.length())))
                .collect(Collectors.toList());
        moveS3Objects(client, bucket, moveRequests);
        DataStorageFolder folder = new DataStorageFolder();
        folder.setName(folderName);
        folder.setPath(folderFullPath);
        return folder;
    }

    public boolean checkBucket(String bucket) {
        AmazonS3 client = getDefaultS3Client();
        return client.doesBucketExistV2(bucket);
    }

    public PathDescription getDataSize(final S3bucketDataStorage dataStorage, final String path,
                                       final PathDescription pathDescription) {
        final String requestPath = Optional.ofNullable(path).orElse("");
        final AmazonS3 client = getDefaultS3Client();

        ObjectListing listing = client.listObjects(dataStorage.getRoot(), requestPath);
        boolean hasNextPageMarker = true;
        while (hasNextPageMarker && !pathDescription.getCompleted()) {
            ProviderUtils.getSizeByPath(listing.getObjectSummaries(), requestPath,
                    S3ObjectSummary::getSize, S3ObjectSummary::getKey, pathDescription);
            hasNextPageMarker = listing.isTruncated();
            listing = client.listNextBatchOfObjects(listing);
        }

        pathDescription.setCompleted(true);
        return pathDescription;
    }

    private BucketLifecycleConfiguration.Rule createLtsRule(String ltsRuleId, Integer longTermStorageDuration) {
        return new BucketLifecycleConfiguration.Rule()
                .withId(ltsRuleId)
                .withFilter(new LifecycleFilter(new LifecyclePrefixPredicate("")))
                .withExpirationInDays(longTermStorageDuration)
                .withStatus(BucketLifecycleConfiguration.ENABLED);
    }

    private BucketLifecycleConfiguration.Rule createStsRule(String stsRuleId, Integer shortTermStorageDuration) {
        return new BucketLifecycleConfiguration.Rule()
                .withId(stsRuleId)
                .withFilter(new LifecycleFilter(new LifecyclePrefixPredicate("")))
                .addTransition(
                        new BucketLifecycleConfiguration.Transition()
                                .withDays(shortTermStorageDuration)
                                .withStorageClass(StorageClass.Glacier))
                .withStatus(BucketLifecycleConfiguration.ENABLED);
    }

    private BucketLifecycleConfiguration.Rule createIncompleteUploadCleanupRule(String ruleId,
                                                                                Integer incompleteUploadCleanupDays) {
        return new BucketLifecycleConfiguration.Rule()
                .withId(ruleId)
                .withFilter(new LifecycleFilter(new LifecyclePrefixPredicate("")))
                .withAbortIncompleteMultipartUpload(
                        new AbortIncompleteMultipartUpload().withDaysAfterInitiation(incompleteUploadCleanupDays)
                )
                .withStatus(BucketLifecycleConfiguration.ENABLED);
    }

    private BucketLifecycleConfiguration.Rule createVersionExpirationRule(String name, Integer duration) {
        return new BucketLifecycleConfiguration.Rule()
                .withId(name)
                .withFilter(new LifecycleFilter(new LifecyclePrefixPredicate("")))
                .withNoncurrentVersionExpirationInDays(duration)
                .withStatus(BucketLifecycleConfiguration.ENABLED);
    }

    private void applyRules(String bucketName, AmazonS3 client,
            List<BucketLifecycleConfiguration.Rule> rules) {
        BucketLifecycleConfiguration configuration =
                new BucketLifecycleConfiguration();
        configuration.setRules(rules);
        client.setBucketLifecycleConfiguration(bucketName, configuration);
    }

    private void applyVersioningConfig(String bucketName, AmazonS3 s3client,
            BucketVersioningConfiguration configuration) {
        SetBucketVersioningConfigurationRequest setBucketVersioningConfigurationRequest =
                new SetBucketVersioningConfigurationRequest(bucketName, configuration);

        s3client.setBucketVersioningConfiguration(setBucketVersioningConfigurationRequest);
    }

    private DataStorageListing listFiles(final AmazonS3 client, final String bucket, final String requestPath,
                                         final Integer pageSize, final String marker, final String prefix,
                                         final DataStorageLifecycleRestoredListingContainer restoredListing) {
        ListObjectsV2Request req = new ListObjectsV2Request();
        req.setBucketName(bucket);
        req.setPrefix(requestPath);
        req.setDelimiter(ProviderUtils.DELIMITER);
        if (pageSize != null) {
            req.setMaxKeys(pageSize);
        }
        if (StringUtils.hasValue(marker)) {
            req.setStartAfter(marker);
        }
        ListObjectsV2Result listing;
        List<AbstractDataStorageItem> items = new ArrayList<>();
        String previous = null;
        do {
            listing = client.listObjectsV2(req);

            for (String name : listing.getCommonPrefixes()) {
                previous = getPreviousKey(previous, name);
                items.add(parseFolder(requestPath, name, prefix));
            }
            for (S3ObjectSummary s3ObjectSummary : listing.getObjectSummaries()) {
                DataStorageFile file =
                        AbstractS3ObjectWrapper.getWrapper(s3ObjectSummary)
                                .convertToStorageFile(requestPath, prefix);
                if (file != null) {
                    final String fileName = requestPath + file.getName();
                    if (filterNotRestored(file, fileName, restoredListing)) {
                        continue;
                    }
                    previous = getPreviousKey(previous, s3ObjectSummary.getKey());
                    items.add(file);
                }
            }
            req.setContinuationToken(listing.getNextContinuationToken());
            if (pageSize != null) {
                req.setMaxKeys(pageSize - items.size());
            }
        } while(listing.isTruncated() && (pageSize == null || items.size() < pageSize));
        String returnToken = listing.isTruncated() ? previous : null;
        return new DataStorageListing(returnToken, items);
    }

    private String getPreviousKey(String previous, String key) {
        if (previous == null) {
            return key;
        }
        return key.compareTo(previous) > 0 ? key : previous;
    }

    private DataStorageFolder parseFolder(String requestPath, String name, String prefix) {
        String relativePath = name;
        if (relativePath.endsWith(ProviderUtils.DELIMITER)) {
            relativePath = relativePath.substring(0, relativePath.length() - 1);
        }
        String folderName = relativePath.substring(requestPath.length());
        DataStorageFolder folder = new DataStorageFolder();
        folder.setName(folderName);
        folder.setPath(ProviderUtils.removePrefix(relativePath, prefix));
        return folder;
    }

    private DataStorageListing listVersions(final AmazonS3 client, final String bucket, final String requestPath,
                                            final Integer pageSize, final String marker, final String prefix,
                                            final DataStorageLifecycleRestoredListingContainer restoredListing) {
        ListVersionsRequest request = new ListVersionsRequest()
                .withBucketName(bucket).withPrefix(requestPath).withDelimiter(ProviderUtils.DELIMITER);
        if (StringUtils.hasValue(marker)) {
            request.setKeyMarker(marker);
        }
        if (pageSize != null) {
            request.setMaxResults(pageSize);
        }
        VersionListing versionListing;
        List<AbstractDataStorageItem> items = new ArrayList<>();
        Map<String, DataStorageFile> itemKeys = new HashMap<>();
        String previous = null;
        do {
            versionListing = client.listVersions(request);
            for (String commonPrefix : versionListing.getCommonPrefixes()) {
                if (checkListingSize(pageSize, items, itemKeys)) {
                    items.addAll(itemKeys.values());
                    return new DataStorageListing(previous, items);
                }
                previous = getPreviousKey(previous, commonPrefix);
                AbstractDataStorageItem folder = parseFolder(requestPath, commonPrefix, prefix);
                items.add(folder);
            }
            for (S3VersionSummary versionSummary : versionListing.getVersionSummaries()) {
                if (!pathMatch(requestPath, versionSummary.getKey())) {
                    continue;
                }
                DataStorageFile file =
                        AbstractS3ObjectWrapper.getWrapper(versionSummary).convertToStorageFile(requestPath, prefix);
                if (file == null) {
                    continue;
                }
                if (filterNotRestored(file, file.getPath(), restoredListing)) {
                    continue;
                }
                final String fileName = file.getName();
                if (!itemKeys.containsKey(fileName)) {
                    if (checkListingSize(pageSize, items, itemKeys)) {
                        items.addAll(itemKeys.values());
                        return new DataStorageListing(previous, items);
                    }
                    previous = getPreviousKey(previous, versionSummary.getKey());
                    Map<String, AbstractDataStorageItem> versions = new LinkedHashMap<>();
                    versions.put(file.getVersion(), file.copy(file));
                    file.setVersions(versions);
                    itemKeys.put(file.getName(), file);
                } else {
                    DataStorageFile item = itemKeys.get(file.getName());
                    Map<String, AbstractDataStorageItem> versions = item.getVersions();
                    versions.put(file.getVersion(), file.copy(file));
                    if (isLaterVersion(file.getChanged(), item.getChanged())) {
                        file.setVersions(versions);
                        itemKeys.put(file.getName(), file);
                    }
                }
            }
            request.setKeyMarker(versionListing.getNextKeyMarker());
            request.setVersionIdMarker(versionListing.getNextVersionIdMarker());
        } while (versionListing.isTruncated());
        items.addAll(itemKeys.values());
        String returnToken = versionListing.isTruncated() ? previous : null;
        return new DataStorageListing(returnToken, items);
    }

    private boolean checkListingSize(Integer pageSize, List<AbstractDataStorageItem> items,
            Map<String, DataStorageFile> itemKeys) {
        return pageSize != null && items.size() + itemKeys.size() >= pageSize;
    }

    private boolean isLaterVersion(String newVersion, String oldVersion) {
        try {
            return S3Constants.getAwsDateFormat().parse(newVersion)
                    .after(S3Constants.getAwsDateFormat().parse(oldVersion));
        } catch (ParseException e) {
            LOGGER.error(e.getMessage(), e);
            return false;
        }
    }

    private String buildOwnerTag(String owner) {
        return ProviderUtils.OWNER_TAG_KEY + "=" + owner;
    }

    private void deleteAllVersions(AmazonS3 client, String bucket, String path) {
        try(S3ObjectDeleter s3ObjectDeleter = new S3ObjectDeleter(client, bucket)) {
            ListVersionsRequest request = new ListVersionsRequest().withBucketName(bucket);
            if (path != null) {
                request = request.withPrefix(path);
            }
            VersionListing versionListing;
            do {
                versionListing = client.listVersions(request);
                for (S3VersionSummary versionSummary : versionListing.getVersionSummaries()) {
                    if (!pathMatch(path, versionSummary.getKey())) {
                        continue;
                    }
                    s3ObjectDeleter.deleteKey(versionSummary.getKey(), versionSummary.getVersionId());
                }
                request.setKeyMarker(versionListing.getNextKeyMarker());
                request.setVersionIdMarker(versionListing.getNextVersionIdMarker());
            } while (versionListing.isTruncated());
        }
    }

    private boolean pathMatch(String path, String key) {
        if (StringUtils.isNullOrEmpty(path)) {
            return true;
        }
        if (path.endsWith(ProviderUtils.DELIMITER) && key.startsWith(path)) {
            return true;
        }
        return key.equals(path);
    }

    public Map<String, String> updateObjectTags(AbstractDataStorage dataStorage, String path, Map<String, String> tags,
                                 String version) {
        AmazonS3 client = getDefaultS3Client();
        SetObjectTaggingRequest setTaggingRequest = new SetObjectTaggingRequest(dataStorage.getRoot(), path,
                new ObjectTagging(convertMapToAwsTags(tags)));
        if (!StringUtils.isNullOrEmpty(version)) {
            setTaggingRequest.withVersionId(version);
        }
        client.setObjectTagging(setTaggingRequest);
        return listObjectTags(dataStorage, path, version);
    }

    public Map<String, String> listObjectTags(AbstractDataStorage dataStorage, String path, String version) {
        try {
            AmazonS3 client = getDefaultS3Client();
            GetObjectTaggingRequest getTaggingRequest =
                    new GetObjectTaggingRequest(dataStorage.getRoot(), path);
            if (!StringUtils.isNullOrEmpty(version)) {
                getTaggingRequest.withVersionId(version);
            }
            return convertAwsTagsToMap(client.getObjectTagging(getTaggingRequest).getTagSet());
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == NOT_FOUND) {
                throw new DataStorageException(messageHelper
                        .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND, path, dataStorage.getRoot()));
            } else {
                throw new DataStorageException(e.getMessage(), e);
            }
        }
    }

    public Map<String, String> deleteObjectTags(AbstractDataStorage dataStorage, String path, Set<String> tagKeys,
                                                String version) {
        Map<String, String> existingTags = listObjectTags(dataStorage, path, version);
        tagKeys.forEach(tag -> Assert.isTrue(existingTags.containsKey(tag), messageHelper.getMessage(
                MessageConstants.ERROR_DATASTORAGE_FILE_TAG_NOT_EXIST, tag)));
        existingTags.keySet().removeAll(tagKeys);
        updateObjectTags(dataStorage, path, existingTags, version);
        return existingTags;
    }

    public DataStorageItemContent getFileContent(AbstractDataStorage dataStorage, String path, String version,
            Long maxDownloadSize) {
        try {
            AmazonS3 client = getDefaultS3Client();
            GetObjectRequest rangeObjectRequest =
                    new GetObjectRequest(dataStorage.getRoot(), path, version).withRange(0, maxDownloadSize - 1);
            S3Object objectPortion = client.getObject(rangeObjectRequest);
            return downloadContent(maxDownloadSize, objectPortion);
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == NOT_FOUND) {
                throw new DataStorageException(messageHelper
                        .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND, path, dataStorage.getRoot()));
            } else if (e.getStatusCode() == INVALID_RANGE) {
                // is thrown in case of en empty file
                LOGGER.debug(e.getMessage(), e);
                DataStorageItemContent content = new DataStorageItemContent();
                content.setTruncated(false);
                return content;
            } else {
                throw new DataStorageException(e.getMessage(), e);
            }
        }
    }

    public DataStorageStreamingContent getFileStream(AbstractDataStorage dataStorage, String path, String version) {
        try {
            AmazonS3 client = getDefaultS3Client();
            GetObjectRequest rangeObjectRequest =
                new GetObjectRequest(dataStorage.getRoot(), path, version);
            S3Object object = client.getObject(rangeObjectRequest);
            return new DataStorageStreamingContent(object.getObjectContent(), object.getKey());
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == NOT_FOUND) {
                throw new DataStorageException(messageHelper
                        .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND, path, dataStorage.getRoot()));
            } else {
                throw new DataStorageException(e.getMessage(), e);
            }
        }
    }

    private DataStorageItemContent downloadContent(Long maxDownloadSize, S3Object objectPortion) {
        try(InputStream objectData = objectPortion.getObjectContent()) {
            DataStorageItemContent content = new DataStorageItemContent();
            content.setContentType(objectPortion.getObjectMetadata().getContentType());
            content.setTruncated(objectPortion.getObjectMetadata().getInstanceLength() > maxDownloadSize);
            byte[] byteContent = IOUtils.toByteArray(objectData);
            if (FileContentUtils.isBinaryContent(byteContent)) {
                content.setMayBeBinary(true);
            } else {
                content.setContent(byteContent);
            }
            return content;
        } catch (IOException e) {
            throw new DataStorageException(e.getMessage(), e);
        }
    }

    private List<Tag> convertMapToAwsTags(Map<String, String> tags) {
        return MapUtils.emptyIfNull(tags).entrySet()
                .stream()
                .map(entry -> new Tag(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private Map<String, String> convertAwsTagsToMap(List<Tag> awsTags) {
        return ListUtils.emptyIfNull(awsTags).stream()
                .collect(Collectors.toMap(Tag::getKey, Tag::getValue, (v1, v2) -> v2));
    }

    private DataStorageItemType checkItemType(AmazonS3 client, String bucket, String path,
            boolean showVersion) {
        String requestPath = path;
        if (requestPath.endsWith(ProviderUtils.DELIMITER)) {
            requestPath = requestPath.substring(0, requestPath.length() - 1);
        }
        if (showVersion) {
            return getVersionType(client, bucket, path, requestPath);
        } else {
            return getNonVersionType(client, bucket, path, requestPath);
        }
    }

    private DataStorageItemType getNonVersionType(AmazonS3 client, String bucket, String path,
            String requestPath) {
        ListObjectsRequest req = new ListObjectsRequest();
        req.setBucketName(bucket);
        req.setPrefix(requestPath);
        req.setDelimiter(ProviderUtils.DELIMITER);
        ObjectListing listing = client.listObjects(req);
        if (!listing.getCommonPrefixes().isEmpty()) {
            return DataStorageItemType.Folder;
        } else if (!listing.getObjectSummaries().isEmpty()) {
            return DataStorageItemType.File;
        } else {
            throw new IllegalArgumentException(messageHelper
                    .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND, path, bucket));
        }
    }

    private DataStorageItemType getVersionType(AmazonS3 client, String bucket, String path,
            String requestPath) {
        ListVersionsRequest req = new ListVersionsRequest();
        req.setBucketName(bucket);
        req.setPrefix(requestPath);
        req.setDelimiter(ProviderUtils.DELIMITER);
        VersionListing listing = client.listVersions(req);
        if (!listing.getCommonPrefixes().isEmpty()) {
            return DataStorageItemType.Folder;
        } else if (!listing.getVersionSummaries().isEmpty()) {
            return DataStorageItemType.File;
        } else {
            throw new IllegalArgumentException(messageHelper
                    .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND, path, bucket));
        }
    }

    private boolean hasExactMatch(List<S3ObjectSummary> s3ObjectSummaries, List<String> commonPrefixes, String prefix) {
        return !CollectionUtils.isEmpty(s3ObjectSummaries)
                    && s3ObjectSummaries.stream().anyMatch(s3ObjectSummary -> s3ObjectSummary.getKey().equals(prefix))
                || !CollectionUtils.isEmpty(commonPrefixes)
                    && commonPrefixes.stream().anyMatch(commonPrefix -> commonPrefix.equals(prefix));
    }

    private DataStorageDownloadFileUrl generatePresignedUrl(AmazonS3 client, Date expires,
                                                            GeneratePresignedUrlRequest request) {
        return generatePresignedUrl(client, expires, null, null, request);
    }

    private DataStorageDownloadFileUrl generatePresignedUrl(AmazonS3 client, Date expires, String tagValue,
                                                            String cannedACLValue,
                                                            GeneratePresignedUrlRequest request) {
        URL url = client.generatePresignedUrl(request);
        DataStorageDownloadFileUrl dataStorageDownloadFileUrl = new DataStorageDownloadFileUrl();
        dataStorageDownloadFileUrl.setUrl(url.toExternalForm());
        dataStorageDownloadFileUrl.setExpires(expires);
        dataStorageDownloadFileUrl.setTagValue(tagValue);
        dataStorageDownloadFileUrl.setCannedACLValue(cannedACLValue);
        return dataStorageDownloadFileUrl;
    }

    private void checkItemDoesNotExist(final AmazonS3 client, final String bucketName, final String itemPath,
                                       final boolean isFolder) {
        Assert.isTrue(!itemExists(client, bucketName, itemPath, isFolder), messageHelper
                .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_ALREADY_EXISTS, itemPath, bucketName));
    }

    private void checkItemExists(final AmazonS3 client, final String bucketName, final String itemPath,
                                 final boolean isFolder) {
        Assert.isTrue(itemExists(client, bucketName, itemPath, isFolder), messageHelper
                .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND, itemPath, bucketName));
    }

    private boolean filterNotRestored(final DataStorageFile file, final String fileName,
                                      final DataStorageLifecycleRestoredListingContainer restoredListing) {
        return Objects.nonNull(restoredListing) && isArchived(file) && !restoredListing.containsPath(fileName);
    }

    private boolean isArchived(final DataStorageFile item) {
        final String storageClass = MapUtils.emptyIfNull(item.getLabels()).get(STORAGE_CLASS);
        return !StringUtils.isNullOrEmpty(storageClass) && !STANDARD_STORAGE_CLASS.equals(storageClass);
    }
}
