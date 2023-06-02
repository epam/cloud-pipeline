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
import com.epam.pipeline.exception.ObjectNotFoundException;
import com.epam.pipeline.entity.datastorage.access.DataAccessType;
import com.epam.pipeline.entity.datastorage.access.DataAccessEvent;
import com.epam.pipeline.manager.datastorage.lifecycle.DataStorageLifecycleRestoredListingContainer;
import com.epam.pipeline.manager.datastorage.providers.StorageEventCollector;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.utils.FileContentUtils;
import com.google.common.primitives.SignedBytes;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.apache.commons.collections4.CollectionUtils;

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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    public static final String INTELLIGENT_TIERING_STORAGE_CLASS = "INTELLIGENT_TIERING";
    public static final String GLACIER_STORAGE_CLASS = "GLACIER";
    public static final String DEEP_ARCHIVE_STORAGE_CLASS = "DEEP_ARCHIVE";
    public static final String INVALID_OBJECT_STATE = "InvalidObjectState";

    public static final String STORAGE_CLASS = "StorageClass";

    private final StorageEventCollector events;
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

            if (!MapUtils.isEmpty(tags)) {
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
     * Method deletes all content of a bucket and the S3 bucket itself.
     * <p>
     * At first, we try to delete all versions present in bucket for buckets that have versioning enabled or suspended,
     * then we delete all left objects (mainly for buckets with disabled versioning).
     */
    public void deleteS3Bucket(final S3bucketDataStorage storage) {
        AmazonS3 s3client = getDefaultS3Client();
        if (s3client.doesBucketExistV2(storage.getRoot())) {
            deleteAllVersions(s3client, storage, null);
            deleteAllInBucketObjects(s3client, storage);
            s3client.deleteBucket(storage.getRoot());
        } else {
            LOGGER.warn(String.format("The bucket does not exist: %s", storage.getRoot()));
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

    public void restoreFileVersion(final S3bucketDataStorage bucket, final String path, final String version) {
        final AmazonS3 client = getDefaultS3Client();
        final ObjectMetadata objectHead = getObjectHead(client, bucket.getRoot(), path, version);
        verifyArchiveState(objectHead);
        if (fileSizeExceedsLimit(objectHead)) {
            throw new DataStorageException(String.format("Restoring file '%s' version '%s' was aborted because " +
                    "file size exceeds the limit of %s bytes", path, version, COPYING_FILE_SIZE_LIMIT));
        }
        moveS3Object(client, bucket, new MoveObjectRequest(path, version, path));
    }

    private void moveS3Object(final AmazonS3 client, final S3bucketDataStorage bucket,
                              final MoveObjectRequest moveRequest) {
        moveS3Objects(client, bucket, Collections.singletonList(moveRequest));
    }

    private void moveS3Objects(final AmazonS3 client, final S3bucketDataStorage bucket,
                               final List<MoveObjectRequest> moveRequests) {
        try (S3ObjectDeleter deleter = new S3ObjectDeleter(client, events, bucket)) {
            moveRequests.forEach(moveRequest -> {
                events.put(new DataAccessEvent(moveRequest.getSourcePath(), DataAccessType.READ, bucket),
                        new DataAccessEvent(moveRequest.getDestinationPath(), DataAccessType.WRITE, bucket));
                client.copyObject(moveRequest.toCopyRequest(bucket.getRoot()));
                deleter.deleteKey(moveRequest.getSourcePath(), moveRequest.getVersion());
            });
        } catch (AmazonS3Exception e) {
            handleInvalidObjectState(e);
        } catch (SdkClientException e) {
            throw new DataStorageException(e.getMessage(), e.getCause());
        }
    }

    public Stream<DataStorageFile> listDataStorageFiles(final String bucket, final String path) {
        final AmazonS3 client = getDefaultS3Client();
        return S3ListingHelper.files(client, bucket, path);
    }

    public DataStorageListing getItems(final String bucket, final String path, final Boolean showVersion,
                                       final Integer pageSize, final String marker, final String prefix,
                                       final Set<String> masks,
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
                ? listVersions(client, bucket, requestPath, pageSize, marker, prefix, masks, restoredListing)
                : listFiles(client, bucket, requestPath, pageSize, marker, prefix, masks, restoredListing);
        result.getResults().sort(AbstractDataStorageItem.getStorageItemComparator());
        return result;
    }

    private DataStorageFile getFile(final AmazonS3 client, final String bucket, final String path) {
        return findFile(client, bucket, path, null)
                .orElseThrow(() -> new DataStorageException(messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND, path, bucket)));
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
            final ObjectMetadata metadata = getObjectHead(client, bucket, path, version);
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
        final AmazonS3 client = getDefaultS3Client();
        verifyArchiveState(getObjectHead(client, bucket, path, version));
        final Date expires = new Date((new Date()).getTime() + URL_EXPIRATION);
        final GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, path);
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

    public DataStorageFile createFile(final S3bucketDataStorage bucket, final String path,
                                      final byte[] contents, final String owner)
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

    public DataStorageFile createFile(final S3bucketDataStorage bucket, final String path,
                                      final InputStream dataStream, final String owner)
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

    private DataStorageFile putFileToBucket(final S3bucketDataStorage bucket, final String path, final AmazonS3 client,
                                            final InputStream dataStream, final String owner) {
        events.put(new DataAccessEvent(path, DataAccessType.WRITE, bucket));
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setLastModified(new Date());
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucket.getRoot(), path, dataStream, objectMetadata);
        List<Tag> tags = Collections.singletonList(new Tag(ProviderUtils.OWNER_TAG_KEY, owner));
        putObjectRequest.withTagging(new ObjectTagging(tags));
        putObjectRequest.withCannedAcl(DEFAULT_CANNED_ACL);
        client.putObject(putObjectRequest);
        return getFile(client, bucket.getRoot(), path);
    }

    private boolean itemExists(AmazonS3 client, String bucket, String path, boolean isFolder) {
        if (path == null) {
            path = EMPTY_STRING;
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

    private void deleteAllInBucketObjects(final AmazonS3 client, final S3bucketDataStorage bucket) {
        try(S3ObjectDeleter deleter = new S3ObjectDeleter(client, events, bucket)) {
            ObjectListing listing;
            ListObjectsRequest request = new ListObjectsRequest();
            request.setBucketName(bucket.getRoot());
            do {
                listing = client.listObjects(request);
                for (S3ObjectSummary s3ObjectSummary : listing.getObjectSummaries()) {
                    deleter.deleteKey(s3ObjectSummary.getKey());
                }
                request.setMarker(listing.getNextMarker());
            } while (listing.isTruncated());
        }
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
            byte[] contents = EMPTY_STRING.getBytes();
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

    public void deleteFile(final S3bucketDataStorage bucket, final String path, final String version,
                           final Boolean totally) {
        if (StringUtils.isNullOrEmpty(path)) {
            throw new DataStorageException(PATH_SHOULD_NOT_BE_EMPTY_MESSAGE);
        }
        AmazonS3 client = getDefaultS3Client();
        if (!StringUtils.hasValue(version) && !totally && !itemExists(client, bucket.getRoot(), path, false)) {
            throw new DataStorageException(messageHelper
                    .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND, path, bucket));
        }
        try {
            if (!StringUtils.hasValue(version) && totally) {
                deleteAllVersions(client, bucket, path);
            } else {
                try (S3ObjectDeleter deleter = new S3ObjectDeleter(client, events, bucket)) {
                    deleter.deleteKey(path, version);
                }
            }
        } catch (SdkClientException e) {
            throw new DataStorageException(e.getMessage(), e.getCause());
        }
    }

    public void deleteFolder(final S3bucketDataStorage bucket, final String path, final Boolean totally) {
        String folderPath = path;
        if (StringUtils.isNullOrEmpty(folderPath)) {
            throw new DataStorageException(PATH_SHOULD_NOT_BE_EMPTY_MESSAGE);
        }
        if (!folderPath.endsWith(ProviderUtils.DELIMITER)) {
            folderPath += ProviderUtils.DELIMITER;
        }
        AmazonS3 client = getDefaultS3Client();
        if (!totally && !itemExists(client, bucket.getRoot(), folderPath, true)) {
            throw new DataStorageException("Folder does not exist");
        }
        if (totally) {
            deleteAllVersions(client, bucket, folderPath);
        } else {
            //indicates that only DUMMY file is present in a folder and thus it should be deleted completely
            boolean noFiles = true;
            try(S3ObjectDeleter deleter = new S3ObjectDeleter(client, events, bucket)) {
                ListObjectsRequest request = new ListObjectsRequest();
                request.setBucketName(bucket.getRoot());
                request.setPrefix(folderPath);
                ObjectListing listing;
                do {
                    listing = client.listObjects(request);
                    for (S3ObjectSummary s3ObjectSummary : listing.getObjectSummaries()) {
                        String relativePath = s3ObjectSummary.getKey();
                        if (relativePath.startsWith(folderPath)) {
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
                deleteAllVersions(client, bucket, folderPath);
            }
        }
    }

    private void deleteAllVersions(final AmazonS3 client, final S3bucketDataStorage bucket, final String path) {
        try(S3ObjectDeleter s3ObjectDeleter = new S3ObjectDeleter(client, events, bucket)) {
            ListVersionsRequest request = new ListVersionsRequest().withBucketName(bucket.getRoot());
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

    public DataStorageFile moveFile(final S3bucketDataStorage bucket, final String oldPath, final String newPath)
            throws DataStorageException {
        if (StringUtils.isNullOrEmpty(oldPath) || StringUtils.isNullOrEmpty(newPath)) {
            throw new DataStorageException(PATH_SHOULD_NOT_BE_EMPTY_MESSAGE);
        }
        final AmazonS3 client = getDefaultS3Client();
        checkItemExists(client, bucket.getRoot(), oldPath, false);
        checkItemDoesNotExist(client, bucket.getRoot(), newPath, false);
        final ObjectMetadata objectHead = getObjectHead(client, bucket.getRoot(), oldPath);
        verifyArchiveState(objectHead);
        if (fileSizeExceedsLimit(objectHead)) {
            throw new DataStorageException(String.format("File '%s' moving was aborted because " +
                    "file size exceeds the limit of %s bytes", newPath, COPYING_FILE_SIZE_LIMIT));
        }
        moveS3Object(client, bucket, new MoveObjectRequest(oldPath, newPath));
        return getFile(client, bucket.getRoot(), newPath);
    }

    public DataStorageFolder moveFolder(final S3bucketDataStorage bucket,
                                        final String rawOldPath, final String rawNewPath)
            throws DataStorageException {
        if (StringUtils.isNullOrEmpty(rawOldPath) || StringUtils.isNullOrEmpty(rawNewPath)) {
            throw new DataStorageException(PATH_SHOULD_NOT_BE_EMPTY_MESSAGE);
        }
        final String oldPath = ProviderUtils.withTrailingDelimiter(rawOldPath);
        final String newPath = ProviderUtils.withTrailingDelimiter(rawNewPath);
        final String folderFullPath = newPath.substring(0, newPath.length() - 1);
        final String[] parts = newPath.split(ProviderUtils.DELIMITER);
        final String folderName = parts[parts.length - 1];
        final AmazonS3 client = getDefaultS3Client();
        checkItemExists(client, bucket.getRoot(), oldPath, true);
        checkItemDoesNotExist(client, bucket.getRoot(), newPath, true);
        final ListObjectsRequest req = new ListObjectsRequest()
                .withBucketName(bucket.getRoot())
                .withPrefix(oldPath);
        ObjectListing listing = client.listObjects(req);
        boolean listingFinished = false;
        final List<String> oldKeys = new ArrayList<>();
        while (!listingFinished) {
            for (final S3ObjectSummary s3ObjectSummary : listing.getObjectSummaries()) {
                final String objectPath = s3ObjectSummary.getKey();
                if (s3ObjectSummary.getSize() > COPYING_FILE_SIZE_LIMIT) {
                    throw new DataStorageException(String.format("Moving folder '%s' was aborted because " +
                                    "some of its files '%s' size exceeds the limit of %s bytes",
                            oldPath, objectPath, COPYING_FILE_SIZE_LIMIT));
                }
                final String itemStorageClass = s3ObjectSummary.getStorageClass();
                if (!STANDARD_STORAGE_CLASS.equals(itemStorageClass)
                        && !INTELLIGENT_TIERING_STORAGE_CLASS.equals(itemStorageClass)) {
                    throw new DataStorageException(String.format("Moving folder '%s' was aborted because " +
                                    "some of its files '%s' located in %s storage class",
                            oldPath, objectPath, itemStorageClass));
                }
                oldKeys.add(objectPath);
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
        final DataStorageFolder folder = new DataStorageFolder();
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
        final String requestPath = Optional.ofNullable(path).orElse(EMPTY_STRING);
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
                .withFilter(new LifecycleFilter(new LifecyclePrefixPredicate(EMPTY_STRING)))
                .withExpirationInDays(longTermStorageDuration)
                .withStatus(BucketLifecycleConfiguration.ENABLED);
    }

    private BucketLifecycleConfiguration.Rule createStsRule(String stsRuleId, Integer shortTermStorageDuration) {
        return new BucketLifecycleConfiguration.Rule()
                .withId(stsRuleId)
                .withFilter(new LifecycleFilter(new LifecyclePrefixPredicate(EMPTY_STRING)))
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
                .withFilter(new LifecycleFilter(new LifecyclePrefixPredicate(EMPTY_STRING)))
                .withAbortIncompleteMultipartUpload(
                        new AbortIncompleteMultipartUpload().withDaysAfterInitiation(incompleteUploadCleanupDays)
                )
                .withStatus(BucketLifecycleConfiguration.ENABLED);
    }

    private BucketLifecycleConfiguration.Rule createVersionExpirationRule(String name, Integer duration) {
        return new BucketLifecycleConfiguration.Rule()
                .withId(name)
                .withFilter(new LifecycleFilter(new LifecyclePrefixPredicate(EMPTY_STRING)))
                .withNoncurrentVersionExpirationInDays(duration)
                .withExpiredObjectDeleteMarker(true)
                .withAbortIncompleteMultipartUpload(
                        new AbortIncompleteMultipartUpload().withDaysAfterInitiation(duration))
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
                                         final Set<String> masks,
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

        final String latestMarker;
        final boolean maskingEnabled;
        final Set<String> resolvedMasks = addPrefixToMasks(masks, requestPath);
        if (CollectionUtils.isNotEmpty(resolvedMasks)) {
            maskingEnabled = true;
            latestMarker = resolveStartAndLastTokens(req.getStartAfter(), resolvedMasks, req::setStartAfter);
            if (latestMarker == null) {
                return new DataStorageListing(null, Collections.emptyList());
            }
        } else {
            maskingEnabled = false;
            latestMarker = EMPTY_STRING;
        }

        ListObjectsV2Result listing;
        List<AbstractDataStorageItem> items = new ArrayList<>();
        String previous = null;
        do {
            listing = client.listObjectsV2(req);

            for (String name : listing.getCommonPrefixes()) {
                if (maskingEnabled) {
                    if (compareStrings(name, latestMarker) > 0) {
                        listing.setTruncated(false);
                        break;
                    }
                    if (!ProviderUtils.matchingMasks(name, resolvedMasks)) {
                        continue;
                    }
                }
                previous = getPreviousKey(previous, name);
                items.add(parseFolder(requestPath, name, prefix));
            }
            for (S3ObjectSummary s3ObjectSummary : listing.getObjectSummaries()) {
                DataStorageFile file =
                        AbstractS3ObjectWrapper.getWrapper(s3ObjectSummary)
                                .convertToStorageFile(requestPath, prefix);
                if (file != null) {
                    final String fileName = requestPath + file.getName();
                    if (maskingEnabled) {
                        if (compareStrings(fileName, latestMarker) > 0) {
                            listing.setTruncated(false);
                            break;
                        }
                        if (!ProviderUtils.matchingMasks(fileName, resolvedMasks)) {
                            continue;
                        }
                    }
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
                                            final Set<String> masks,
                                            final DataStorageLifecycleRestoredListingContainer restoredListing) {
        ListVersionsRequest request = new ListVersionsRequest()
                .withBucketName(bucket).withPrefix(requestPath).withDelimiter(ProviderUtils.DELIMITER);
        if (StringUtils.hasValue(marker)) {
            request.setKeyMarker(marker);
        }
        if (pageSize != null) {
            request.setMaxResults(pageSize);
        }

        final String latestMarker;
        final boolean maskingEnabled;
        final Set<String> resolvedMasks = addPrefixToMasks(masks, requestPath);
        if (CollectionUtils.isNotEmpty(resolvedMasks)) {
            maskingEnabled = true;
            latestMarker = resolveStartAndLastTokens(request.getKeyMarker(), resolvedMasks, request::setKeyMarker);
            if (latestMarker == null) {
                return new DataStorageListing(null, Collections.emptyList());
            }
        } else {
            maskingEnabled = false;
            latestMarker = EMPTY_STRING;
        }

        VersionListing versionListing;
        List<AbstractDataStorageItem> items = new ArrayList<>();
        Map<String, DataStorageFile> itemKeys = new HashMap<>();
        String previous = null;
        do {
            versionListing = client.listVersions(request);
            for (String commonPrefix : versionListing.getCommonPrefixes()) {
                if (maskingEnabled) {
                    if (compareStrings(commonPrefix, latestMarker) > 0) {
                        versionListing.setTruncated(false);
                        break;
                    }
                    if (!ProviderUtils.matchingMasks(commonPrefix, resolvedMasks)) {
                        continue;
                    }
                }
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
                if (maskingEnabled) {
                    final String fileNameWithFolderPrefix = requestPath + fileName;
                    if (compareStrings(fileNameWithFolderPrefix, latestMarker) > 0) {
                        versionListing.setTruncated(false);
                        break;
                    }
                    if (!ProviderUtils.matchingMasks(fileNameWithFolderPrefix, resolvedMasks)) {
                        continue;
                    }
                }
                if (!itemKeys.containsKey(fileName)) {
                    if (checkListingSize(pageSize, items, itemKeys)) {
                        items.addAll(itemKeys.values());
                        return new DataStorageListing(previous, items);
                    }
                    previous = getPreviousKey(previous, versionSummary.getKey());
                    Map<String, AbstractDataStorageItem> versions = new LinkedHashMap<>();
                    versions.put(file.getVersion(), file.copy(file));
                    file.setVersions(versions);
                    itemKeys.put(fileName, file);
                } else {
                    DataStorageFile item = itemKeys.get(fileName);
                    Map<String, AbstractDataStorageItem> versions = item.getVersions();
                    versions.put(file.getVersion(), file.copy(file));
                    if (isLaterVersion(file.getChanged(), item.getChanged())) {
                        file.setVersions(versions);
                        itemKeys.put(fileName, file);
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

    private boolean pathMatch(String path, String key) {
        if (StringUtils.isNullOrEmpty(path)) {
            return true;
        }
        if (path.endsWith(ProviderUtils.DELIMITER) && key.startsWith(path)) {
            return true;
        }
        return key.equals(path);
    }

    public Map<String, String> updateObjectTags(final S3bucketDataStorage dataStorage, final String path,
                                                final Map<String, String> tags, final String version) {
        AmazonS3 client = getDefaultS3Client();
        SetObjectTaggingRequest setTaggingRequest = new SetObjectTaggingRequest(dataStorage.getRoot(), path,
                new ObjectTagging(convertMapToAwsTags(tags)));
        if (!StringUtils.isNullOrEmpty(version)) {
            setTaggingRequest.withVersionId(version);
        }
        client.setObjectTagging(setTaggingRequest);
        return listObjectTags(dataStorage, path, version);
    }

    public Map<String, String> listObjectTags(final S3bucketDataStorage dataStorage, final String path,
                                              final String version) {
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

    public Map<String, String> deleteObjectTags(final S3bucketDataStorage dataStorage, final String path,
                                                final Set<String> tagKeys, final String version) {
        Map<String, String> existingTags = listObjectTags(dataStorage, path, version);
        tagKeys.forEach(tag -> Assert.isTrue(existingTags.containsKey(tag), messageHelper.getMessage(
                MessageConstants.ERROR_DATASTORAGE_FILE_TAG_NOT_EXIST, tag)));
        existingTags.keySet().removeAll(tagKeys);
        updateObjectTags(dataStorage, path, existingTags, version);
        return existingTags;
    }

    public DataStorageItemContent getFileContent(final S3bucketDataStorage dataStorage, final String path,
                                                 final String version, final Long maxDownloadSize) {
        try {
            AmazonS3 client = getDefaultS3Client();
            GetObjectRequest rangeObjectRequest =
                    new GetObjectRequest(dataStorage.getRoot(), path, version).withRange(0, maxDownloadSize - 1);
            events.put(new DataAccessEvent(path, DataAccessType.READ, dataStorage));
            S3Object objectPortion = client.getObject(rangeObjectRequest);
            return downloadContent(maxDownloadSize, objectPortion);
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == NOT_FOUND) {
                throw new DataStorageException(messageHelper
                        .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND, path, dataStorage.getRoot()));
            }
            if (e.getStatusCode() == INVALID_RANGE) {
                // is thrown in case of en empty file
                LOGGER.debug(e.getMessage(), e);
                DataStorageItemContent content = new DataStorageItemContent();
                content.setTruncated(false);
                return content;
            }
            handleInvalidObjectState(e);
            throw new DataStorageException(e.getMessage(), e);
        }
    }

    public DataStorageStreamingContent getFileStream(final S3bucketDataStorage dataStorage, final String path,
                                                     final String version) {
        try {
            AmazonS3 client = getDefaultS3Client();
            GetObjectRequest rangeObjectRequest =
                new GetObjectRequest(dataStorage.getRoot(), path, version);
            events.put(new DataAccessEvent(path, DataAccessType.READ, dataStorage));
            S3Object object = client.getObject(rangeObjectRequest);
            return new DataStorageStreamingContent(object.getObjectContent(), object.getKey());
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == NOT_FOUND) {
                throw new DataStorageException(messageHelper
                        .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND, path, dataStorage.getRoot()));
            }
            handleInvalidObjectState(e);
            throw new DataStorageException(e.getMessage(), e);
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
            throw new ObjectNotFoundException(messageHelper
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
            throw new ObjectNotFoundException(messageHelper
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

    public static Set<String> extractFileMasks(final Set<String> linkingMasks) {
        return CollectionUtils.emptyIfNull(linkingMasks).stream()
            .filter(mask -> !mask.endsWith(FOLDER_GLOB_SUFFIX))
            .collect(Collectors.toSet());
    }

    public static Set<String> extractFolderMasks(final Set<String> linkingMasks) {
        return CollectionUtils.emptyIfNull(linkingMasks).stream()
            .filter(mask -> mask.endsWith(FOLDER_GLOB_SUFFIX))
            .map(mask -> mask.substring(0, mask.length() - FOLDER_GLOB_SUFFIX.length()))
            .collect(Collectors.toSet());
    }

    public static void validateFilePathMatchingMasks(final S3bucketDataStorage dataStorage, final String path) {
        if (CollectionUtils.isNotEmpty(dataStorage.getLinkingMasks())) {
            validatePathMatchingMasks(dataStorage, path);
        }
    }

    public static Set<String> resolveFolderPathListingMasks(final S3bucketDataStorage dataStorage, final String path) {
        final Set<String> linkingMasks = dataStorage.getLinkingMasks();
        if (CollectionUtils.isEmpty(linkingMasks)) {
            return Collections.emptySet();
        }
        final String normalizedPath = StringUtils.isNullOrEmpty(path)
                                      ? EMPTY_STRING
                                      : ProviderUtils.withTrailingDelimiter(path);
        if (!normalizedPath.equals(EMPTY_STRING)
            && ProviderUtils.matchingMasks(normalizedPath, linkingMasks)) {
            return Collections.emptySet();
        }
        final Set<String> resolvedMasks = resolveMasksRelatedToPath(normalizedPath, linkingMasks);
        if (CollectionUtils.isNotEmpty(resolvedMasks)) {
            return resolvedMasks;
        }
        throw new IllegalArgumentException("Requested operation violates masking rules!");
    }

    public static void validateFolderPathMatchingMasks(final S3bucketDataStorage dataStorage, final String path) {
        Assert.state(StringUtils.hasValue(path), "Path for normalization shall be specified");
        final String folderPath = path.endsWith(ProviderUtils.DELIMITER) ? path : path + ProviderUtils.DELIMITER;
        if (CollectionUtils.isNotEmpty(dataStorage.getLinkingMasks())) {
            validatePathMatchingMasks(dataStorage, folderPath);
        }
    }

    public DataStorageItemType getItemType(final String bucket,
                                           final String path,
                                           final String version) {
        return checkItemType(getDefaultS3Client(), bucket, path, StringUtils.hasValue(version));
    }

    private static void validatePathMatchingMasks(final S3bucketDataStorage dataStorage, final String path) {
        final Set<String> linkingMasks = dataStorage.getLinkingMasks();
        if (CollectionUtils.isNotEmpty(linkingMasks)) {
            Assert.isTrue(ProviderUtils.matchingMasks(path, linkingMasks),
                          "Requested operation violates masking rules!");
        }
    }

    private String resolveStartAndLastTokens(final String currentFirstToken, final Set<String> resolvedMasks,
                                             final Consumer<String> firstTokenConsumer) {
        if (currentFirstToken != null) {
            resolvedMasks.removeIf(mask -> compareStrings(getMaskWithoutGlob(mask), currentFirstToken) <= 0);
            if (CollectionUtils.isEmpty(resolvedMasks)) {
                return null;
            }
        }
        final List<String> resolvedMaskList = new ArrayList<>(resolvedMasks);
        resolvedMaskList.sort(this::compareStrings);
        firstTokenConsumer.accept(getFirstTokenFromMasks(resolvedMaskList));
        return getLastTokenFromMasks(resolvedMaskList);
    }

    private static Set<String> resolveMasksRelatedToPath(final String path, final Set<String> masks) {
        return masks.stream()
            .filter(mask -> mask.startsWith(path))
            .map(mask ->  mask.substring(path.length()))
            .filter(StringUtils::hasValue)
            .map(relativeMask -> {
                final String[] relativeMaskParts = relativeMask.split(ProviderUtils.DELIMITER);
                final boolean isFullFileMask = !relativeMask.endsWith(FOLDER_GLOB_SUFFIX)
                                               && relativeMaskParts.length == 1;
                return isFullFileMask ? relativeMaskParts[0] : relativeMaskParts[0] + FOLDER_GLOB_SUFFIX;
            })
            .collect(Collectors.toSet());
    }

    private String getMaskWithoutGlob(final String mask) {
        return mask.endsWith(FOLDER_GLOB_SUFFIX)
               ? mask.substring(0, mask.length() - 2)
               : mask;
    }

    private Set<String> addPrefixToMasks(final Set<String> masks, final String prefix) {
        return CollectionUtils.emptyIfNull(masks).stream()
            .map(mask -> prefix + mask)
            .collect(Collectors.toSet());
    }

    private String getLastTokenFromMasks(final List<String> resolvedMaskList) {
        return getMaskWithoutGlob(resolvedMaskList.get(resolvedMaskList.size() - 1));
    }

    private String getFirstTokenFromMasks(final List<String> resolvedMaskList) {
        final String firstMask = getMaskWithoutGlob(resolvedMaskList.get(0));
        final boolean isFolder = firstMask.endsWith(ProviderUtils.DELIMITER);
        final int maskContentSubstringLength = firstMask.length() - (isFolder ? 2 : 1);
        final String suffix = isFolder ? ProviderUtils.DELIMITER : EMPTY_STRING;
        final char lastContentChar = firstMask.charAt(maskContentSubstringLength);
        final String tokenWoLastChar = firstMask.substring(0, maskContentSubstringLength);

        return lastContentChar == ' '
               ? tokenWoLastChar + suffix
               : tokenWoLastChar + (char) (lastContentChar - 1) + suffix;
    }

    private int compareStrings(final String s1, final String s2) {
        return SignedBytes.lexicographicalComparator()
                .compare(s1.getBytes(Charsets.UTF_8), s2.getBytes(Charsets.UTF_8));
    }

    private boolean filterNotRestored(final DataStorageFile file, final String fileName,
                                      final DataStorageLifecycleRestoredListingContainer restoredListing) {
        return Objects.nonNull(restoredListing) && isArchived(file) && !restoredListing.containsPath(fileName);
    }

    private boolean isArchived(final DataStorageFile item) {
        final String storageClass = MapUtils.emptyIfNull(item.getLabels()).get(STORAGE_CLASS);
        return !StringUtils.isNullOrEmpty(storageClass) && !STANDARD_STORAGE_CLASS.equals(storageClass)
                && !INTELLIGENT_TIERING_STORAGE_CLASS.equals(storageClass);
    }

    private ObjectMetadata getObjectHead(final AmazonS3 client, final String bucket, final String path) {
        return getObjectHead(client, bucket, path, null);
    }

    private ObjectMetadata getObjectHead(final AmazonS3 client, final String bucket, final String path,
                                         final String version) {
        return client.getObjectMetadata(new GetObjectMetadataRequest(bucket, path, version));
    }

    private void verifyArchiveState(final ObjectMetadata objectHead) {
        final String storageClass = objectHead.getStorageClass();
        if (StringUtils.isNullOrEmpty(storageClass)) {
            return;
        }

        if (INTELLIGENT_TIERING_STORAGE_CLASS.equals(storageClass)
                && !StringUtils.isNullOrEmpty(objectHead.getArchiveStatus())) {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_INTELLIGENT_TIERING_ARCHIVE_ACCESS));
        }

        if (GLACIER_STORAGE_CLASS.equals(storageClass) || DEEP_ARCHIVE_STORAGE_CLASS.equals(storageClass)) {
            throw new DataStorageException(messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_ARCHIVE_ACCESS));
        }
    }

    private boolean fileSizeExceedsLimit(final ObjectMetadata objectHead) {
        return objectHead.getContentLength() > COPYING_FILE_SIZE_LIMIT;
    }

    private void handleInvalidObjectState(final AmazonS3Exception error) {
        if (!INVALID_OBJECT_STATE.equals(error.getErrorCode())) {
            return;
        }

        LOGGER.error(error.getErrorMessage());
        final String storageClass = MapUtils.emptyIfNull(error.getAdditionalDetails()).get(STORAGE_CLASS);

        if (INTELLIGENT_TIERING_STORAGE_CLASS.equals(storageClass)) {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_INTELLIGENT_TIERING_ARCHIVE_ACCESS), error);
        }

        if (GLACIER_STORAGE_CLASS.equals(storageClass) || DEEP_ARCHIVE_STORAGE_CLASS.equals(storageClass)) {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_ARCHIVE_ACCESS), error);
        }
    }
}
