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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.vo.data.storage.RestoreFolderVO;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.ActionStatus;
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
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.cloud.gcp.GCPClient;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.utils.FileContentUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.paging.Page;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.Identity;
import com.google.cloud.Policy;
import com.google.cloud.ReadChannel;
import com.google.cloud.Role;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.CopyWriter;
import com.google.cloud.storage.Cors;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageClass;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class GSBucketStorageHelper {
    private static final String EMPTY_PREFIX = "";
    private static final int REGION_ZONE_LENGTH = -2;
    private static final String LIFECYCLE_CONTENT_TYPE = "application/json";
    private static final String LATEST_VERSION_DELETION_MARKER = "_d";

    private static final byte[] EMPTY_FILE_CONTENT = new byte[0];
    private static final Long URL_EXPIRATION = 24 * 60 * 60 * 1000L;

    private final MessageHelper messageHelper;
    private final GCPRegion region;
    private final GCPClient gcpClient;

    public String createGoogleStorage(final GSBucketStorage storage) {
        final Storage client = gcpClient.buildStorageClient(region);
        final Bucket bucket = client.create(BucketInfo.newBuilder(storage.getPath())
                .setCors(buildCors())
                .setStorageClass(StorageClass.REGIONAL)
                .setLocation(trimRegionZone(region.getRegionCode()))
                .build());
        return bucket.getName();
    }

    public void deleteGoogleStorage(final String bucketName) {
        final Storage client = gcpClient.buildStorageClient(region);
        deleteAllVersions(bucketName, EMPTY_PREFIX, client);
        deleteBucket(bucketName, client);
    }

    public DataStorageListing listItems(final GSBucketStorage storage, final String path, final Boolean showVersion,
                                        final Integer pageSize, final String marker) {
        String requestPath = Optional.ofNullable(path).orElse(EMPTY_PREFIX);
        if (StringUtils.isNotBlank(requestPath)) {
            requestPath = normalizeFolderPath(requestPath);
        }
        final Storage client = gcpClient.buildStorageClient(region);
        final String bucketName = storage.getPath();

        final Page<Blob> blobs = client.list(bucketName,
                Storage.BlobListOption.versions(showVersion),
                Storage.BlobListOption.currentDirectory(),
                Storage.BlobListOption.prefix(requestPath),
                Storage.BlobListOption.pageToken(Optional.ofNullable(marker).orElse(EMPTY_PREFIX)),
                Storage.BlobListOption.pageSize(Optional.ofNullable(pageSize).orElse(Integer.MAX_VALUE)));
        final List<AbstractDataStorageItem> items = showVersion
                ? listItemsWithVersions(blobs)
                : listItemsWithoutVersions(blobs);
        return new DataStorageListing(blobs.getNextPageToken(), items);
    }

    public DataStorageFile createFile(final GSBucketStorage storage, final String path,
                                      final byte[] contents, final String owner) {
        final Storage client = gcpClient.buildStorageClient(region);

        final String bucketName = storage.getPath();

        final BlobId blobId = BlobId.of(bucketName, path);
        final BlobInfo blobInfo = BlobInfo
                .newBuilder(blobId)
                .setMetadata(StringUtils.isBlank(owner) ? null
                        : Collections.singletonMap(ProviderUtils.OWNER_TAG_KEY, owner))
                .build();
        final Blob blob = client.create(blobInfo, contents);
        return createDataStorageFile(blob);
    }

    @SneakyThrows
    public DataStorageFile createFile(final GSBucketStorage storage, final String path,
                                      final InputStream dataStream, final String owner) {
        return createFile(storage, path, IOUtils.toByteArray(dataStream), owner);
    }

    public DataStorageFolder createFolder(final GSBucketStorage storage, final String path) {
        String folderPath = path.trim();
        folderPath = normalizeFolderPath(folderPath);
        checkFolderDoesNotExist(storage.getPath(), folderPath, gcpClient.buildStorageClient(region));
        final String tokenFilePath = folderPath + ProviderUtils.FOLDER_TOKEN_FILE;

        createFile(storage, tokenFilePath, EMPTY_FILE_CONTENT, null);
        return createDataStorageFolder(folderPath);
    }

    public void deleteFile(final GSBucketStorage dataStorage, final String path, final String version,
                           final Boolean totally) {
        Assert.isTrue(StringUtils.isNotBlank(path), messageHelper
                .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_IS_EMPTY));

        final Storage client = gcpClient.buildStorageClient(region);
        final String bucketName = dataStorage.getPath();

        if (StringUtils.isBlank(version) && totally) {
            deleteAllFileVersions(bucketName, path, client);
            return;
        }

        if (latestVersionHasDeletedMarker(version)) {
            restoreFileVersion(dataStorage, path, cleanupVersion(version));
            return;
        }
        final Blob blob = checkBlobExistsAndGet(bucketName, path, client, version);
        deleteBlob(blob, client, StringUtils.isNotBlank(version));
    }

    public void deleteFolder(final GSBucketStorage dataStorage, final String path, final Boolean totally) {
        Assert.isTrue(StringUtils.isNotBlank(path), messageHelper
                .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_IS_EMPTY));

        final String folderPath = normalizeFolderPath(path);

        final Storage client = gcpClient.buildStorageClient(region);

        final String bucketName = dataStorage.getPath();
        if (totally) {
            deleteAllVersions(bucketName, path, client);
            return;
        }

        final Page<Blob> blobs = client.list(bucketName, Storage.BlobListOption.prefix(folderPath));
        blobs.iterateAll().forEach(blob -> deleteBlob(blob, client, false));
    }

    public DataStorageFile moveFile(final GSBucketStorage storage, final String oldPath, final String newPath) {
        Assert.isTrue(StringUtils.isNotBlank(oldPath), messageHelper
                .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_IS_EMPTY));
        Assert.isTrue(StringUtils.isNotBlank(newPath), messageHelper
                .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_IS_EMPTY));

        final Storage client = gcpClient.buildStorageClient(region);
        final String bucketName = storage.getPath();
        final Blob oldBlob = checkBlobExistsAndGet(bucketName, oldPath, client, null);
        checkBlobDoesNotExist(bucketName, newPath, client);

        final CopyWriter copyWriter = client.get(bucketName).get(oldPath).copyTo(bucketName, newPath);
        final Blob newBlob = copyWriter.getResult();
        deleteBlob(oldBlob, client, false);

        return createDataStorageFile(newBlob);
    }

    public DataStorageFolder moveFolder(final GSBucketStorage storage, final String oldPath, final String newPath) {
        Assert.isTrue(StringUtils.isNotBlank(oldPath), messageHelper
                .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_IS_EMPTY));
        Assert.isTrue(StringUtils.isNotBlank(newPath), messageHelper
                .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_IS_EMPTY));

        final Storage client = gcpClient.buildStorageClient(region);
        final String bucketName = storage.getPath();

        final String oldFolderPath = normalizeFolderPath(oldPath);
        final String newFolderPath = normalizeFolderPath(newPath);

        final Page<Blob> blobs = checkFolderExistsAndGet(bucketName, oldFolderPath, client);
        checkFolderDoesNotExist(bucketName, newFolderPath, client);

        blobs.iterateAll().forEach(oldBlob -> {
            final String oldBlobName = oldBlob.getName();
            final String newBlobName = newFolderPath + oldBlobName.substring(oldFolderPath.length());
            final CopyWriter copyWriter = oldBlob.copyTo(BlobId.of(bucketName, newBlobName));
            final Blob newBlob = copyWriter.getResult();
            Assert.notNull(newBlob, "Created blob should not be empty");
            deleteBlob(oldBlob, client, false);
        });

        return createDataStorageFolder(newFolderPath);
    }

    public boolean checkStorageExists(final String bucketName) {
        final Storage client = gcpClient.buildStorageClient(region);
        return Objects.nonNull(client.get(bucketName));
    }

    public DataStorageItemContent getFileContent(final GSBucketStorage storage, final String path, final String version,
                                                 final Long maxDownloadSize) {
        checkVersionHasNotDeletedMarker(version);
        final Storage client = gcpClient.buildStorageClient(region);
        final String bucketName = storage.getPath();

        final Blob blob = checkBlobExistsAndGet(bucketName, path, client, version);

        int bufferSize = Integer.MAX_VALUE;
        if (maxDownloadSize < Integer.MAX_VALUE) {
            bufferSize = Math.toIntExact(maxDownloadSize);
        }
        if (bufferSize > blob.getSize()) {
            bufferSize = Math.toIntExact(blob.getSize());
        }

        final DataStorageItemContent content = new DataStorageItemContent();
        content.setContentType(blob.getContentType());
        content.setTruncated(blob.getSize() > bufferSize);

        try (ReadChannel reader = blob.reader()) {
            final ByteBuffer bytes = ByteBuffer.allocate(bufferSize);
            reader.read(bytes);
            final byte[] byteContent = bytes.array();
            if (FileContentUtils.isBinaryContent(byteContent)) {
                content.setMayBeBinary(true);
            } else {
                content.setContent(byteContent);
            }
            return content;
        } catch (IOException e) {
            throw new DataStorageException(e);
        }
    }

    public DataStorageStreamingContent getFileStream(final GSBucketStorage storage, final String path,
                                                     final String version) {
        checkVersionHasNotDeletedMarker(version);
        final Storage client = gcpClient.buildStorageClient(region);
        final String bucketName = storage.getPath();

        final Blob blob = checkBlobExistsAndGet(bucketName, path, client, version);
        try (ReadChannel reader = blob.reader()) {
            return new DataStorageStreamingContent(Channels.newInputStream(reader), path);
        }
    }

    public DataStorageDownloadFileUrl generateDownloadUrl(final GSBucketStorage storage, final String path,
                                                          final String version) {
        checkVersionHasNotDeletedMarker(version);
        final Storage client = gcpClient.buildStorageClient(region);

        final String bucketName = storage.getPath();
        final Blob blob = checkBlobExistsAndGet(bucketName, path, client, version);

        final URL signedUrl = client.signUrl(BlobInfo.newBuilder(blob.getBlobId()).build(), 1, TimeUnit.DAYS);
        final DataStorageDownloadFileUrl dataStorageDownloadFileUrl = new DataStorageDownloadFileUrl();
        dataStorageDownloadFileUrl.setUrl(signedUrl.toString());
        dataStorageDownloadFileUrl.setExpires(new Date((new Date()).getTime() + URL_EXPIRATION));
        return dataStorageDownloadFileUrl;
    }

    public Map<String, String> listMetadata(final GSBucketStorage storage, final String path,
                                            final String version) {
        checkVersionHasNotDeletedMarker(version);
        final Storage client = gcpClient.buildStorageClient(region);

        final String bucketName = storage.getPath();
        final Blob blob = checkBlobExistsAndGet(bucketName, path, client, version);
        return new HashMap<>(MapUtils.emptyIfNull(blob.getMetadata()));
    }

    public Map<String, String> updateMetadata(final GSBucketStorage storage, final String path,
                                              final Map<String, String> tags, final String version) {
        checkVersionHasNotDeletedMarker(version);
        final Storage client = gcpClient.buildStorageClient(region);

        final String bucketName = storage.getPath();
        final Blob blob = checkBlobExistsAndGet(bucketName, path, client, version);
        client.update(blob.toBuilder()
                .setBlobId(blob.getBlobId())
                .setMetadata(null)
                .build());

        client.update(blob.toBuilder()
                .setBlobId(blob.getBlobId())
                .setMetadata(tags)
                .build());
        return client.get(bucketName, path).getMetadata();

    }

    public Map<String, String> deleteMetadata(final GSBucketStorage storage, final String path,
                                              final Set<String> tagsToDelete, final String version) {
        checkVersionHasNotDeletedMarker(version);
        final Map<String, String> existingTags = listMetadata(storage, path, version);
        tagsToDelete.forEach(tag -> Assert.isTrue(existingTags.containsKey(tag), messageHelper.getMessage(
                MessageConstants.ERROR_DATASTORAGE_FILE_TAG_NOT_EXIST, tag))
        );
        existingTags.keySet().removeAll(tagsToDelete);
        updateMetadata(storage, path, existingTags, version);
        return existingTags;
    }

    public void restoreFileVersion(final GSBucketStorage storage, final String path, final String version) {
        checkVersionHasNotDeletedMarker(version);
        final Storage client = gcpClient.buildStorageClient(region);
        final String bucketName = storage.getPath();
        final Blob blob = checkBlobExistsAndGet(bucketName, path, client, version);

        final Storage.CopyRequest request = Storage.CopyRequest.newBuilder()
                .setSource(blob.getBlobId())
                .setSourceOptions(Storage.BlobSourceOption.generationMatch())
                .setTarget(BlobId.of(bucketName, path))
                .build();
        client.copy(request).getResult();
        deleteBlob(blob, client, true);
    }

    public void restoreFolder(final GSBucketStorage storage, final String path,
                              final RestoreFolderVO restoreFolderVO) {
        final Storage client = gcpClient.buildStorageClient(region);
        final String bucketName = storage.getPath();
        cleanDeleteMarkers(client, bucketName, path, restoreFolderVO);
    }

    private void cleanDeleteMarkers(final Storage client,
                                    final String bucketName, final String requestPath,
                                    final RestoreFolderVO restoreFolderVO) {
        String folderPath = Optional.ofNullable(requestPath).orElse(EMPTY_PREFIX);
        if (StringUtils.isNotBlank(folderPath)) {
            folderPath = normalizeFolderPath(requestPath);
        }
        final Page<Blob> blobs = client.list(bucketName,
                Storage.BlobListOption.versions(true),
                Storage.BlobListOption.currentDirectory(),
                Storage.BlobListOption.prefix(folderPath),
                Storage.BlobListOption.pageToken(EMPTY_PREFIX),
                Storage.BlobListOption.pageSize(Integer.MAX_VALUE));
        Assert.isTrue(Objects.nonNull(blobs) && blobs.iterateAll().iterator().hasNext(), messageHelper
                .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND, folderPath, bucketName));
        listItemsWithVersions(blobs)
                .forEach(item -> {
                    recursiveRestoreFolderCall(item, client, bucketName, restoreFolderVO);
                    if (isFileWithDeleteMarkerAndShouldBeRestore(item, restoreFolderVO)) {
                        final Blob blob = checkBlobExistsAndGet(bucketName, item.getPath(), client,
                                removeDeletedMarkerFromVersion(((DataStorageFile) item).getVersion()));
                        final Storage.CopyRequest request = Storage.CopyRequest.newBuilder()
                                .setSource(blob.getBlobId())
                                .setSourceOptions(Storage.BlobSourceOption.generationMatch())
                                .setTarget(BlobId.of(bucketName, item.getPath()))
                                .build();
                        client.copy(request).getResult();
                    }
                });
    }

    private String removeDeletedMarkerFromVersion(final String versionWithDeletedMarker) {
        if (latestVersionHasDeletedMarker(versionWithDeletedMarker)) {
            return versionWithDeletedMarker.substring(0, versionWithDeletedMarker.length() - 2);
        }
        throw new DataStorageException(
                String.format("Corresponded version: '%s' should has deleted marker: '%s'", versionWithDeletedMarker,
                        LATEST_VERSION_DELETION_MARKER));
    }

    private void recursiveRestoreFolderCall(final AbstractDataStorageItem item, final Storage client,
                                            final String bucketName, final RestoreFolderVO restoreFolderVO) {
        if (item.getType() == DataStorageItemType.Folder && restoreFolderVO.isRecursively()) {
            cleanDeleteMarkers(client, bucketName, item.getPath(), restoreFolderVO);
        }
    }

    private boolean isFileWithDeleteMarkerAndShouldBeRestore(final AbstractDataStorageItem item,
                                                             final RestoreFolderVO restoreFolderVO) {
        final AntPathMatcher matcher = new AntPathMatcher();
        return item.getType() == DataStorageItemType.File &&
                ((DataStorageFile) item).getDeleteMarker() &&
                ((DataStorageFile) item).getVersion() != null &&
                Optional.ofNullable(restoreFolderVO.getIncludeList()).map(includeList -> includeList.stream()
                        .anyMatch(pattern -> matcher.match(pattern, item.getName()))).orElse(true) &&
                Optional.ofNullable(restoreFolderVO.getExcludeList()).map(excludeList -> excludeList.stream()
                        .noneMatch(pattern -> matcher.match(pattern, item.getName()))).orElse(true);
    }

    public void applyStoragePolicy(final GSBucketStorage storage, final StoragePolicy policy) {
        final Storage client = gcpClient.buildStorageClient(region);
        final String bucketName = storage.getPath();
        final Bucket bucket = client.get(bucketName);

        disableLifecycleRulesIfNeeded(client, bucketName, policy);

        final BucketInfo.Builder bucketInfoBuilder = BucketInfo.newBuilder(bucketName);
        if (policy.getVersioningEnabled() != bucket.versioningEnabled()) {
            bucketInfoBuilder.setVersioningEnabled(policy.getVersioningEnabled());
        }

        final List<BucketInfo.LifecycleRule> rules = new ArrayList<>();
        if (Objects.nonNull(policy.getBackupDuration())) {
            rules.add(buildDeleteRule(policy.getBackupDuration(), false));
        }
        if (Objects.nonNull(policy.getLongTermStorageDuration())) {
            rules.add(buildDeleteRule(policy.getLongTermStorageDuration(), true));
        }
        if (Objects.nonNull(policy.getShortTermStorageDuration())) {
            rules.add(buildStorageClassRelocationRule(policy));
        }

        if (CollectionUtils.isNotEmpty(rules)) {
            bucketInfoBuilder.setLifecycleRules(rules);
        }

        client.update(bucketInfoBuilder.build());
    }

    public PathDescription getDataSize(final GSBucketStorage dataStorage, final String path,
                                       final PathDescription pathDescription) {
        final String requestPath = Optional.ofNullable(path).orElse("");
        final Storage client = gcpClient.buildStorageClient(region);
        final Page<Blob> blobs = client.list(dataStorage.getPath(), Storage.BlobListOption.prefix(requestPath));

        ProviderUtils.getSizeByPath(blobs.iterateAll(), requestPath, BlobInfo::getSize, BlobInfo::getName,
                pathDescription);

        pathDescription.setCompleted(true);
        return pathDescription;
    }

    private List<Cors> buildCors() {
        if (StringUtils.isBlank(region.getCorsRules())) {
            return null;
        }

        final ObjectMapper corsRulesMapper = JsonMapper.newInstance()
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);

        final List<GCPCors> rawCors = ListUtils.emptyIfNull(JsonMapper.parseData(region.getCorsRules(),
                new TypeReference<List<GCPCors>>() {}, corsRulesMapper));

        return rawCors.stream()
                .map(rule ->
                        Cors.newBuilder()
                                .setMethods(rule.getMethod())
                                .setOrigins(rule.getOrigin())
                                .setMaxAgeSeconds(rule.getMaxAgeSeconds())
                                .setResponseHeaders(rule.getResponseHeader())
                                .build()
                ).collect(Collectors.toList());
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public ActionStatus applyIamPolicy(final GSBucketStorage storage) {
        final Storage client = gcpClient.buildStorageClient(region);

        if (StringUtils.isNotBlank(region.getPolicy())) {
            try {
                final Policy currentPolicy = client.getIamPolicy(storage.getPath());
                client.setIamPolicy(storage.getPath(),
                        buildIamPolicy(MapUtils.emptyIfNull(currentPolicy.getBindings())));
            } catch(Exception e) {
                log.error(e.getMessage(), e);
                return ActionStatus.error(e.getMessage());
            }
        }
        return ActionStatus.success();
    }

    private Policy buildIamPolicy(final Map<Role, Set<Identity>> currentPolicy) {
        final ObjectMapper policyMapper = JsonMapper.newInstance()
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
        final List<com.google.api.services.storage.model.Policy.Bindings> rawPolicy = ListUtils.emptyIfNull(
                JsonMapper.parseData(region.getPolicy(),
                new TypeReference<List<com.google.api.services.storage.model.Policy.Bindings>>() {}, policyMapper));

        final Map<Role, Set<Identity>> resultPolicy = new HashMap<>();
        rawPolicy.forEach(bindings -> {
            final Role role = Role.of(bindings.getRole());
            resultPolicy.putIfAbsent(role, new HashSet<>());
            resultPolicy.get(role).addAll(bindings.getMembers().stream()
                    .map(Identity::valueOf)
                    .collect(Collectors.toSet()));
        });
        currentPolicy.forEach((role, identities) -> {
            resultPolicy.putIfAbsent(role, new HashSet<>());
            resultPolicy.get(role).addAll(identities);
        });
        return Policy.newBuilder().setBindings(resultPolicy).build();
    }

    private BucketInfo.LifecycleRule buildStorageClassRelocationRule(final StoragePolicy policy) {
        final BucketInfo.LifecycleRule.SetStorageClassLifecycleAction coldlineLifecycleAction =
                BucketInfo.LifecycleRule.SetStorageClassLifecycleAction
                        .newSetStorageClassAction(StorageClass.COLDLINE);
        final BucketInfo.LifecycleRule.LifecycleCondition stsCondition =
                BucketInfo.LifecycleRule.LifecycleCondition
                .newBuilder()
                .setAge(policy.getShortTermStorageDuration())
                .setMatchesStorageClass(Collections.singletonList(StorageClass.REGIONAL))
                .build();
        return new BucketInfo.LifecycleRule(coldlineLifecycleAction, stsCondition);
    }

    private BucketInfo.LifecycleRule buildDeleteRule(final Integer duration, final boolean isLive) {
        final BucketInfo.LifecycleRule.DeleteLifecycleAction deleteLifecycleAction =
                BucketInfo.LifecycleRule.DeleteLifecycleAction.newDeleteAction();
        final BucketInfo.LifecycleRule.LifecycleCondition condition = BucketInfo.LifecycleRule.LifecycleCondition
                .newBuilder()
                .setAge(duration)
                .setIsLive(isLive)
                .build();
        return new BucketInfo.LifecycleRule(deleteLifecycleAction, condition);
    }

    private List<AbstractDataStorageItem> listItemsWithoutVersions(final Page<Blob> blobs) {
        final List<AbstractDataStorageItem> items = new ArrayList<>();

        for (Blob blob : blobs.getValues()) {
            if (blob.getName().endsWith(ProviderUtils.FOLDER_TOKEN_FILE)) {
                continue;
            }
            items.add(blob.isDirectory()
                    ? createDataStorageFolder(blob.getName())
                    : createDataStorageFile(blob));
        }
        return items;
    }

    private List<AbstractDataStorageItem> listItemsWithVersions(final Page<Blob> blobs) {
        final List<AbstractDataStorageItem> items = new ArrayList<>();
        final Map<String, List<Blob>> files = new HashMap<>();

        for (Blob blob : blobs.getValues()) {
            if (blob.getName().endsWith(ProviderUtils.FOLDER_TOKEN_FILE)) {
                continue;
            }
            if (blob.isDirectory()) {
                items.add(createDataStorageFolder(blob.getName()));
                continue;
            }
            files.putIfAbsent(blob.getName(), new ArrayList<>());
            final List<Blob> versionsByPath = files.get(blob.getName());
            versionsByPath.add(blob);
        }

        files.forEach((path, fileVersions) -> {
            fileVersions.sort(Comparator.comparingLong(BlobInfo::getUpdateTime).reversed());
            final Blob latestVersionBlob = fileVersions.remove(0);
            final DataStorageFile latestVersionFile = createDataStorageFileWithVersion(latestVersionBlob,
                    Objects.nonNull(latestVersionBlob.getDeleteTime()));
            final Map<String, AbstractDataStorageItem> filesByVersion = fileVersions
                    .stream()
                    .map(blob -> createDataStorageFileWithVersion(blob, false))
                    .collect(Collectors.toMap(DataStorageFile::getVersion, Function.identity()));
            putLatestVersion(latestVersionFile, filesByVersion);
            latestVersionFile.setVersions(filesByVersion);
            items.add(latestVersionFile);
        });
        return items;
    }

    private void putLatestVersion(final DataStorageFile latestVersionFile,
                                  final Map<String, AbstractDataStorageItem> filesByVersion) {
        if (latestVersionFile.getDeleteMarker()) {
            // The GCP returns the same version ID for current version and deleted
            // This way we should build a new fake version with deleted marker: <version ID>_d
            final DataStorageFile notDeletedLatestVersion = latestVersionFile.copy(latestVersionFile);
            notDeletedLatestVersion.setDeleteMarker(false);
            filesByVersion.put(latestVersionFile.getVersion(), notDeletedLatestVersion);

            latestVersionFile.setVersion(latestVersionFile.getVersion() + LATEST_VERSION_DELETION_MARKER);
            latestVersionFile.setSize(0L);
        }
        filesByVersion.put(latestVersionFile.getVersion(), latestVersionFile.copy(latestVersionFile));
    }

    private DataStorageFile createDataStorageFileWithVersion(final Blob blob, final boolean isDeleted) {
        final DataStorageFile dataStorageFile = createDataStorageFile(blob);
        dataStorageFile.setDeleteMarker(isDeleted);
        dataStorageFile.setVersion(String.valueOf(blob.getGeneration()));
        return dataStorageFile;
    }

    private void deleteAllFileVersions(final String bucketName, final String path, final Storage client) {
        final Page<Blob> blobs = client.list(bucketName,
                Storage.BlobListOption.versions(true),
                Storage.BlobListOption.currentDirectory(),
                Storage.BlobListOption.prefix(path));
        blobs.iterateAll().forEach(blob -> {
            if (blob.getName().equals(path)) {
                deleteBlob(blob, client, true);
            }
        });
    }

    private void deleteAllVersions(final String bucketName, final String path, final Storage client) {
        final Page<Blob> blobs = client.list(bucketName,
                Storage.BlobListOption.versions(true),
                Storage.BlobListOption.prefix(path));
        blobs.iterateAll().forEach(blob -> deleteBlob(blob, client, true));
    }

    private void deleteBlob(final Blob blob, final Storage client, final boolean withVersion) {
        final String bucketName = blob.getBucket();
        final String path = blob.getName();
        final BlobId blobId = BlobId.of(bucketName, path, withVersion ? blob.getGeneration() : null);
        final boolean deleted = client.delete(blobId);
        if (!deleted) {
            throw new DataStorageException(
                    String.format("Failed to delete google storage file '%s' from bucket '%s'", path, bucketName));
        }
    }

    private String normalizeFolderPath(final String path) {
        String normalizedFolderPath = ProviderUtils.withTrailingDelimiter(path);
        if (normalizedFolderPath.startsWith(ProviderUtils.DELIMITER)) {
            normalizedFolderPath = normalizedFolderPath.substring(1);
        }
        return normalizedFolderPath;
    }

    private DataStorageFolder createDataStorageFolder(final String path) {
        final DataStorageFolder folder = new DataStorageFolder();
        final String[] parts = path.split(ProviderUtils.DELIMITER);
        final String folderName = parts[parts.length - 1];
        final String folderFullPath = path.substring(0, path.length() - 1);
        folder.setName(folderName);
        folder.setPath(folderFullPath);
        return folder;
    }

    private DataStorageFile createDataStorageFile(final Blob blob) {
        final TimeZone tz = TimeZone.getTimeZone("UTC");
        final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(tz);

        final String[] parts = blob.getName().split(ProviderUtils.DELIMITER);
        final String fileName = parts[parts.length - 1];
        final DataStorageFile file = new DataStorageFile();
        file.setName(fileName);
        file.setPath(blob.getName());
        file.setSize(blob.getSize());
        file.setChanged(df.format(new Date(blob.getUpdateTime())));
        return file;
    }

    private Blob checkBlobExistsAndGet(final String bucketName, final String blobPath, final Storage client,
                                       final String version) {
        final BlobId blobId = BlobId.of(bucketName, blobPath,
                StringUtils.isNotBlank(version) ? Long.valueOf(version) : null);
        final Blob blob = client.get(blobId);
        Assert.notNull(blob, messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND, blobPath,
                bucketName));
        return blob;
    }

    private void checkBlobDoesNotExist(final String bucketName, final String blobPath, final Storage client) {
        final Blob blob = client.get(bucketName, blobPath);
        Assert.isNull(blob, messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_ALREADY_EXISTS, blobPath,
                bucketName));
    }

    private void deleteBucket(final String bucketName, final Storage client) {
        final boolean deleted = client.delete(bucketName);

        if (!deleted) {
            throw new DataStorageException(String.format("Failed to delete google storage %s", bucketName));
        }
    }

    private String trimRegionZone(final String region) {
        return StringUtils.substring(region, 0, REGION_ZONE_LENGTH);
    }

    private void disableLifecycleRulesIfNeeded(final Storage client, final String bucketName,
                                               final StoragePolicy policy) {
        if (Objects.nonNull(policy.getLongTermStorageDuration())
                || Objects.nonNull(policy.getShortTermStorageDuration())
                || Objects.nonNull(policy.getBackupDuration())) {
            return;
        }
        try {
            final GoogleStorageRestApiClient storageRestApiClient = GoogleStorageRestApiClient.buildClient();
            final GoogleCredentials credentials = (GoogleCredentials) client.getOptions().getScopedCredentials();
            final String token = credentials.refreshAccessToken().getTokenValue();
            GoogleStorageRestApiClient.executeRequest(() ->
                    storageRestApiClient.disableLifecycleRules(buildToken(token), LIFECYCLE_CONTENT_TYPE,
                            bucketName, new GCPDisablingLifecycleRules()));
        } catch (IOException e) {
            throw new DataStorageException(String
                    .format("Failed to disable lifecycle rules for bucket %s", bucketName), e);
        }
    }

    private String buildToken(final String refreshToken) {
        return "Bearer " + refreshToken;
    }

    private Page<Blob> checkFolderExistsAndGet(final String bucketName, final String folderPath, final Storage client) {
        final Page<Blob> blobs = client.list(bucketName, Storage.BlobListOption.prefix(folderPath));
        Assert.isTrue(Objects.nonNull(blobs) && blobs.iterateAll().iterator().hasNext(), messageHelper
                .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND, folderPath, bucketName));
        return blobs;
    }

    private void checkFolderDoesNotExist(final String bucketName, final String folderPath, final Storage client) {
        final Page<Blob> blobs = client.list(bucketName, Storage.BlobListOption.prefix(folderPath));
        Assert.isTrue(Objects.isNull(blobs) || !blobs.iterateAll().iterator().hasNext(), messageHelper
                .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_ALREADY_EXISTS, folderPath, bucketName));
    }

    private String cleanupVersion(final String version) {
        if (latestVersionHasDeletedMarker(version)) {
            return version.replace(LATEST_VERSION_DELETION_MARKER, StringUtils.EMPTY);
        }
        return version;
    }

    private void checkVersionHasNotDeletedMarker(final String version) {
        if (latestVersionHasDeletedMarker(version)) {
            throw new DataStorageException("Operation is not allowed for deleted version");
        }
    }

    private boolean latestVersionHasDeletedMarker(final String version) {
        return StringUtils.isNotBlank(version) && version.endsWith(LATEST_VERSION_DELETION_MARKER);
    }
}
