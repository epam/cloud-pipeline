/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage.providers.azure;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.PagedResponse;
import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.common.sas.SasIpRange;
import com.azure.storage.common.sas.SasProtocol;
import com.azure.storage.file.datalake.DataLakeDirectoryClient;
import com.azure.storage.file.datalake.DataLakeFileClient;
import com.azure.storage.file.datalake.DataLakeFileSystemClient;
import com.azure.storage.file.datalake.DataLakeServiceClient;
import com.azure.storage.file.datalake.DataLakeServiceClientBuilder;
import com.azure.storage.file.datalake.models.DataLakeStorageException;
import com.azure.storage.file.datalake.models.DownloadRetryOptions;
import com.azure.storage.file.datalake.models.FileRange;
import com.azure.storage.file.datalake.models.ListPathsOptions;
import com.azure.storage.file.datalake.models.PathItem;
import com.azure.storage.file.datalake.models.PathProperties;
import com.azure.storage.file.datalake.models.UserDelegationKey;
import com.azure.storage.file.datalake.sas.DataLakeServiceSasSignatureValues;
import com.azure.storage.file.datalake.sas.FileSystemSasPermission;
import com.azure.storage.file.datalake.sas.PathSasPermission;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.access.DataAccessEvent;
import com.epam.pipeline.entity.datastorage.access.DataAccessType;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.entity.region.AzurePolicy;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.exception.AuthenticationException;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.datastorage.providers.StorageEventCollector;
import com.epam.pipeline.utils.FileContentUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Util class providing methods to interact with Azure 'Hierarchical Namespace' Storage API.
 */
@Slf4j
public class AzureHNStorageHelper implements AzureStorageHelper {
    private final AzureRegion region;
    private final AzureRegionCredentials credentials;
    private final MessageHelper messageHelper;
    private final DateFormat dateFormat;
    private final StorageEventCollector events;

    public AzureHNStorageHelper(final AzureRegion region,
                                final AzureRegionCredentials credentials,
                                final StorageEventCollector events,
                                final MessageHelper messageHelper) {
        this.region = region;
        this.credentials = credentials;
        this.events = events;
        this.messageHelper = messageHelper;
        final TimeZone tz = TimeZone.getTimeZone("UTC");
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        this.dateFormat.setTimeZone(tz);
    }

    public String createBlobStorage(final AzureBlobStorage storage) {
        if (getFileSystemClient(storage).createIfNotExists()) {
            return storage.getPath();
        } else {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_ALREADY_EXIST, storage.getPath()));
        }
    }

    public String deleteStorage(final AzureBlobStorage storage) {
        if (getFileSystemClient(storage).deleteIfExists()) {
            return storage.getPath();
        } else {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_NOT_FOUND_BY_NAME, storage.getPath()));
        }
    }

    public boolean checkStorage(final AzureBlobStorage storage) {
        return getFileSystemClient(storage).exists();
    }

    public Stream<DataStorageFile> listDataStorageFiles(final AzureBlobStorage storage, final String path) {
        return getItemsRecursively(storage, path)
                .filter(DataStorageFile.class::isInstance)
                .map(DataStorageFile.class::cast);
    }

    public DataStorageListing getItems(final AzureBlobStorage storage, final String path,
                                       final Integer pageSize, final String continuationToken) {
        final DataLakeFileSystemClient fileSystemClient = getFileSystemClient(storage);
        final Integer effectivePageSize = Optional.ofNullable(pageSize).orElse(MAX_PAGE_SIZE);
        final ListPathsOptions options = new ListPathsOptions()
                .setPath(path)
                .setRecursive(false)
                .setMaxResults(effectivePageSize);

        final PagedIterable<PathItem> pagedIterable = fileSystemClient.listPaths(options, AZURE_STORAGE_TIMEOUT);
        final Iterable<PagedResponse<PathItem>> pages = (continuationToken == null)
                ? pagedIterable.iterableByPage()
                : pagedIterable.iterableByPage(continuationToken);

        final Iterator<PagedResponse<PathItem>> iterator = pages.iterator();

        if (!iterator.hasNext()) {
            return new DataStorageListing(null, null, Collections.emptyList());
        }

        try (PagedResponse<PathItem> pageResponse = iterator.next()) {
            final List<AbstractDataStorageItem> items = pageResponse.getValue().stream()
                    .map(i -> toDataStorageItem(fileSystemClient, i))
                    .collect(Collectors.toList());
            return new DataStorageListing(pageResponse.getContinuationToken(), null, items);
        } catch (IOException e) {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_GET_CONTENT_FAILED, path), e);
        }
    }

    public DataStorageFolder createFolder(final AzureBlobStorage storage, final String path) {
        return createFolder(getFileSystemClient(storage), storage, path);
    }

    public void deleteFolder(final AzureBlobStorage storage,
                             final String path) {
        deleteFolder(getFileSystemClient(storage), storage, path);
    }

    public DataStorageFolder moveFolder(final AzureBlobStorage storage, final String oldPath, final String newPath) {
        final DataLakeFileSystemClient fileSystemClient = getFileSystemClient(storage);
        validateDirectory(fileSystemClient, storage, oldPath, true);
        validateDirectory(fileSystemClient, storage, newPath, false);
        final PagedIterable<PathItem> pagedIterable = getItemsRecursively(fileSystemClient, oldPath);
        createFolder(fileSystemClient, storage, newPath);
        copyItems(pagedIterable, fileSystemClient, storage, oldPath, newPath);
        deleteFolder(fileSystemClient, storage, oldPath);
        return toDataStorageFolder(newPath);
    }

    public Optional<DataStorageFile> findFile(final AzureBlobStorage storage, final String path) {
        return findFile(getFileSystemClient(storage), path);
    }

    public DataStorageFile createFile(final AzureBlobStorage storage, final String path,
                                      final byte[] contents, final String owner) {
        validatePath(path);
        events.put(new DataAccessEvent(path, DataAccessType.WRITE, storage));
        final DataLakeFileSystemClient fileSystemClient = getFileSystemClient(storage);
        final DataLakeFileClient fileClient = getFileClient(storage, path);
        final Map<String, String> metadata = StringUtils.isBlank(owner)
                ? Collections.emptyMap()
                : Collections.singletonMap(ProviderUtils.OWNER_TAG_KEY, owner);
        if (!metadata.isEmpty()) {
            fileClient.setMetadata(metadata);
        }
        try (InputStream dataStream = new ByteArrayInputStream(contents)) {
            fileClient.append(dataStream, 0, contents.length);
            fileClient.flush(contents.length, true);
        } catch (IOException e) {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_BLOB_UPLOAD_FAILED, path), e);
        }
        return getDataStorageFile(fileSystemClient, storage, path);
    }

    public DataStorageFile createFile(final AzureBlobStorage storage, final String path,
                                      final InputStream dataStream, final String owner) {
        return createFile(storage, path, toByteArray(dataStream), owner);
    }

    public void deleteFile(final AzureBlobStorage storage, final String path) {
        deleteFile(getFileSystemClient(storage), storage, path);
    }

    public DataStorageFile moveFile(final AzureBlobStorage storage, final String oldPath, final String newPath) {
        final DataLakeFileSystemClient fileSystemClient = getFileSystemClient(storage);
        validateFile(fileSystemClient, storage, oldPath, true);
        validateFile(fileSystemClient, storage, newPath, false);
        copyFile(fileSystemClient, storage, oldPath, newPath);
        deleteFile(fileSystemClient, storage, oldPath);
        return getDataStorageFile(fileSystemClient, storage, newPath);
    }

    public Map<String, String> updateObjectTags(final AzureBlobStorage storage,
                                                final String path, final Map<String, String> tags) {
        final DataLakeFileSystemClient fileSystemClient = getFileSystemClient(storage);
        validateFile(fileSystemClient, storage, path, true);
        final DataLakeFileClient fileClient = fileSystemClient.getFileClient(path);
        fileClient.setMetadata(tags);
        return tags;
    }

    public Map<String, String> listObjectTags(final AzureBlobStorage storage, final String path) {
        final DataLakeFileSystemClient fileSystemClient = getFileSystemClient(storage);
        validateFile(fileSystemClient, storage, path, true);
        final PathProperties properties = getPathProperties(fileSystemClient, path);
        return properties.getMetadata();
    }

    public Map<String, String> deleteObjectTags(final AzureBlobStorage storage,
                                                final String path, final Set<String> tagsToDelete) {
        final DataLakeFileSystemClient fileSystemClient = getFileSystemClient(storage);
        validateFile(fileSystemClient, storage, path, true);
        final DataLakeFileClient fileClient = fileSystemClient.getFileClient(path);
        final PathProperties properties = fileClient.getProperties();
        final Map<String, String> metadata = new HashMap<>(properties.getMetadata());
        for (String tag : tagsToDelete) {
            if (!metadata.containsKey(tag)) {
                throw new DataStorageException(messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_FILE_TAG_NOT_EXIST, tag)
                );
            }
        }
        metadata.keySet().removeAll(tagsToDelete);
        fileClient.setMetadata(metadata);
        return metadata;
    }

    public DataStorageItemContent getFile(final AzureBlobStorage storage, final String path,
                                          final Long maxDownloadSize) {
        final DataLakeFileSystemClient fileSystemClient = getFileSystemClient(storage);
        final DataLakeFileClient fileClient = fileSystemClient.getFileClient(path);
        validateFile(fileSystemClient, storage, path, true);
        events.put(new DataAccessEvent(path, DataAccessType.READ, storage));
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            final PathProperties properties = fileClient.getProperties();
            final long fileSize = properties.getFileSize();
            final long rangeEnd = Math.min(maxDownloadSize, fileSize);
            final FileRange fileRange = new FileRange(0, rangeEnd);
            final DownloadRetryOptions options = new DownloadRetryOptions().setMaxRetryRequests(5);

            fileClient.readWithResponse(outputStream, fileRange, options, null,
                    false, AZURE_STORAGE_TIMEOUT, Context.NONE);
            byte[] bytes = outputStream.toByteArray();
            final DataStorageItemContent content = new DataStorageItemContent();
            content.setContent(bytes);
            content.setContentType(properties.getContentType());
            content.setTruncated(fileSize > maxDownloadSize);
            content.setMayBeBinary(FileContentUtils.isBinaryContent(bytes));
            return content;
        } catch (DataLakeStorageException | IOException e) {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_BLOB_DOWNLOAD_FAILED, path), e);
        }
    }

    public DataStorageStreamingContent getStream(final AzureBlobStorage storage, final String path) {
        final DataLakeFileSystemClient fileSystemClient = getFileSystemClient(storage);
        final DataLakeFileClient fileClient = fileSystemClient.getFileClient(path);
        validateFile(fileSystemClient, storage, path, true);
        events.put(new DataAccessEvent(path, DataAccessType.READ, storage));
        try {
            return new DataStorageStreamingContent(fileClient.openInputStream().getInputStream(), path);
        } catch (DataLakeStorageException e) {
            if (e.getStatusCode() == RANGE_NOT_SATISFIABLE_STATUS_CODE) {
                return new DataStorageStreamingContent(new ByteArrayInputStream(EMPTY_CONTENT), path);
            }
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_BLOB_DOWNLOAD_FAILED, path), e);
        }
    }

    public DataStorageDownloadFileUrl generateDownloadUrl(final AzureBlobStorage storage, final String path) {
        final PathSasPermission permission = new PathSasPermission()
                .setReadPermission(true)
                .setAddPermission(false)
                .setWritePermission(false);
        validateFile(getFileSystemClient(storage), storage, path, true);
        return generateGenericPresignedUrl(storage, path, permission.toString(), Duration.ZERO);
    }

    public DataStorageDownloadFileUrl generateUploadUrl(final AzureBlobStorage storage, final String path) {
        final PathSasPermission permission = new PathSasPermission()
                .setReadPermission(true)
                .setAddPermission(true)
                .setWritePermission(true);
        return generateGenericPresignedUrl(storage, path, permission.toString(), Duration.ZERO);
    }

    public DataStorageDownloadFileUrl generateGenericPresignedUrl(final AzureBlobStorage storage, final String path,
                                                                  final String permission, final Duration duration) {
        final DataLakeServiceClient serviceClient = getServiceClient();
        final String sasToken = generateSASToken(serviceClient, storage, path, permission, expirationOf(duration));
        return generateResponseObject(storage, path, sasToken);
    }

    public PathDescription getDataSize(final AzureBlobStorage storage,
                                       final String path,
                                       final PathDescription pathDescription) {
        final DataLakeFileSystemClient fileSystemClient = getFileSystemClient(storage);
        validateFile(fileSystemClient, storage, path, true);
        final DataLakeFileClient fileClient = fileSystemClient.getFileClient(path);
        final PathProperties properties = fileClient.getProperties();
        pathDescription.setSize(properties.getFileSize());
        pathDescription.setCompleted(true);
        return pathDescription;
    }

    public String generateSASToken(final AzureBlobStorage storage,
                                   final List<DataStorageAction> actions,
                                   final OffsetDateTime expiryTime) {
        final DataLakeServiceClient serviceClient = getServiceClient();
        final DataLakeFileSystemClient fileSystemClient = getFileSystemClient(storage);
        final DataStorageAction dataStorageAction = actions.get(0);
        Assert.isTrue(actions.size() == 1, "Multiple actions is not supported for AZURE provider");
        final DataLakeServiceSasSignatureValues sasSignatureValues = new DataLakeServiceSasSignatureValues(expiryTime,
                buildPermissions(dataStorageAction))
                .setProtocol(SasProtocol.HTTPS_HTTP)
                .setContentType("container");
        addIPRangeToSASValue(sasSignatureValues);
        return getContainerSASToken(serviceClient, fileSystemClient, expiryTime, sasSignatureValues);
    }

    @Override
    public DataStorageItemType getItemType(final AzureBlobStorage storage, final String path) {
        final DataLakeFileSystemClient fileSystemClient = getFileSystemClient(storage);
        final PathProperties properties = fileSystemClient.getFileClient(path).getProperties();
        return properties.isDirectory() ? DataStorageItemType.Folder : DataStorageItemType.File;
    }

    public static DataLakeServiceClient getDataLakeServiceClient(final AzureRegion region,
                                                                 final AzureRegionCredentials credentials) {
        final String storageAccount = region.getStorageAccount();
        final String endpoint = String.format(DATALAKE_URL_FORMAT, storageAccount);
        if (StringUtils.isNotBlank(region.getManagedIdentity())) {
            log.debug("Authenticating with Managed Identity");
            return new DataLakeServiceClientBuilder()
                    .credential(new DefaultAzureCredentialBuilder()
                            .managedIdentityClientId(region.getManagedIdentity())
                            .build())
                    .endpoint(endpoint)
                    .buildClient();
        } else if (StringUtils.isNotBlank(credentials.getStorageAccountKey())) {
            log.debug("Authenticating with Storage Account Key");
            return new DataLakeServiceClientBuilder()
                    .credential(new StorageSharedKeyCredential(storageAccount, credentials.getStorageAccountKey()))
                    .endpoint(endpoint)
                    .buildClient();
        } else {
            throw new AuthenticationException(FAILED_TO_GET_STORAGE_CREDENTIALS);
        }
    }

    private DataLakeServiceClient getServiceClient() {
        return getDataLakeServiceClient(region, credentials);
    }

    private DataLakeFileSystemClient getFileSystemClient(final AzureBlobStorage storage) {
        return getServiceClient().getFileSystemClient(storage.getName());
    }

    private DataLakeFileClient getFileClient(final AzureBlobStorage storage, final String path) {
        return getFileSystemClient(storage).createFile(path);
    }

    private void addIPRangeToSASValue(final DataLakeServiceSasSignatureValues values) {
        final AzurePolicy policy = region.getAzurePolicy();
        if (policy != null &&
                (StringUtils.isNotBlank(policy.getIpMin()) || StringUtils.isNotBlank(policy.getIpMax()))) {
            if (StringUtils.isNotBlank(policy.getIpMin()) && StringUtils.isNotBlank(policy.getIpMax())) {
                values.setSasIpRange(new SasIpRange().setIpMin(policy.getIpMin()).setIpMax(policy.getIpMax()));
                return;
            }
            final String ipValue = Optional.ofNullable(policy.getIpMin()).orElse(policy.getIpMax());
            values.setSasIpRange(new SasIpRange().setIpMin(ipValue).setIpMax(ipValue));
        }
    }

    private String generateSASToken(final DataLakeServiceClient serviceClient,
                                    final AzureBlobStorage storage,
                                    final String path,
                                    final String permission,
                                    final OffsetDateTime expiryTime) {
        final DataLakeFileSystemClient fileSystemClient = getFileSystemClient(storage);
        return StringUtils.isBlank(path)
                ? generateContainerSASToken(serviceClient, fileSystemClient, permission, expiryTime)
                : generateSASToken(serviceClient, fileSystemClient, path, permission, expiryTime);
    }

    private String generateContainerSASToken(final DataLakeServiceClient serviceClient,
                                             final DataLakeFileSystemClient fileSystemClient,
                                             final String permission,
                                             final OffsetDateTime expiryTime) {
        final FileSystemSasPermission fileSystemSasPermission = FileSystemSasPermission.parse(permission);
        final DataLakeServiceSasSignatureValues sasSignatureValues = new DataLakeServiceSasSignatureValues(expiryTime,
                fileSystemSasPermission).setStartTime(OffsetDateTime.now());
        return getContainerSASToken(serviceClient, fileSystemClient, expiryTime, sasSignatureValues);
    }

    private String getContainerSASToken(final DataLakeServiceClient serviceClient,
                                        final DataLakeFileSystemClient fileSystemClient,
                                        final OffsetDateTime expiryTime,
                                        final DataLakeServiceSasSignatureValues sasSignatureValues) {
        if (StringUtils.isNotBlank(region.getManagedIdentity())) {
            final UserDelegationKey userDelegationKey = getUserDelegationKey(serviceClient, expiryTime);
            return fileSystemClient.generateUserDelegationSas(sasSignatureValues, userDelegationKey);
        } else if (StringUtils.isNotBlank(credentials.getStorageAccountKey())) {
            return fileSystemClient.generateSas(sasSignatureValues);
        } else {
            throw new AuthenticationException(FAILED_TO_GET_STORAGE_CREDENTIALS);
        }
    }

    private String generateSASToken(final DataLakeServiceClient serviceClient,
                                    final DataLakeFileSystemClient fileSystemClient,
                                    final String path,
                                    final String permission,
                                    final OffsetDateTime expiryTime) {
        final DataLakeFileClient fileClient = fileSystemClient.getFileClient(path);
        final PathSasPermission sasPermission = PathSasPermission.parse(permission);
        final DataLakeServiceSasSignatureValues sasSignatureValues = new DataLakeServiceSasSignatureValues(expiryTime,
                sasPermission).setStartTime(OffsetDateTime.now());
        if (StringUtils.isNotBlank(region.getManagedIdentity())) {
            final UserDelegationKey userDelegationKey = getUserDelegationKey(serviceClient, expiryTime);
            return fileClient.generateUserDelegationSas(sasSignatureValues, userDelegationKey);
        } else if (StringUtils.isNotBlank(credentials.getStorageAccountKey())) {
            return fileClient.generateSas(sasSignatureValues);
        } else {
            throw new AuthenticationException(FAILED_TO_GET_STORAGE_CREDENTIALS);
        }
    }

    private UserDelegationKey getUserDelegationKey(final DataLakeServiceClient serviceClient,
                                                   final OffsetDateTime expiryTime) {
        return serviceClient.getUserDelegationKey(OffsetDateTime.now(), expiryTime);
    }

    private Optional<DataStorageFile> findFile(final DataLakeFileSystemClient fileSystemClient, final String path) {
        final PathProperties properties = getPathProperties(fileSystemClient, path);
        return Optional.of(toDataStorageFile(properties, path));
    }

    @SneakyThrows
    private byte[] toByteArray(final InputStream dataStream) {
        return IOUtils.toByteArray(dataStream);
    }

    private OffsetDateTime expirationOf(final Duration duration) {
        final Duration adjustedDuration = Optional.ofNullable(duration)
                .filter(d -> d.getSeconds() > 0)
                .orElse(FALLBACK_EXPIRATION_DURATION);
        return OffsetDateTime.now().plus(adjustedDuration);
    }

    private DataStorageDownloadFileUrl generateResponseObject(final AzureBlobStorage storage,
                                                              final String path,
                                                              final String sasToken) {
        return responseWith(blobUrl(resourcePath(storage.getPath(), path), sasToken));
    }

    private String resourcePath(final String container, final String path) {
        return StringUtils.isBlank(path) || StringUtils.strip(path).equals(ProviderUtils.DELIMITER)
                ? container
                : container + ProviderUtils.DELIMITER + path;
    }

    private String blobUrl(final String resourcePath, final String sasToken) {
        return String.format(BLOB_URL_FORMAT + "/%s?%s", region.getStorageAccount(), resourcePath, sasToken);
    }

    private DataStorageDownloadFileUrl responseWith(final String blobUrl) {
        final DataStorageDownloadFileUrl dataStorageDownloadFileUrl = new DataStorageDownloadFileUrl();
        dataStorageDownloadFileUrl.setUrl(blobUrl);
        dataStorageDownloadFileUrl.setExpires(new Date((new Date()).getTime() + URL_EXPIRATION));
        return dataStorageDownloadFileUrl;
    }

    private DataStorageFolder createFolder(final DataLakeFileSystemClient fileSystemClient,
                                           final AzureBlobStorage storage, final String path) {
        validateDirectory(fileSystemClient, storage, path, false);
        events.put(new DataAccessEvent(path, DataAccessType.WRITE, storage));
        fileSystemClient.createDirectory(path);
        return toDataStorageFolder(path);
    }

    private void deleteFolder(final DataLakeFileSystemClient fileSystemClient,
                              final AzureBlobStorage storage, final String path) {
        validateDirectory(fileSystemClient, storage, path, true);
        events.put(new DataAccessEvent(path, DataAccessType.DELETE, storage));
        final DataLakeDirectoryClient directoryClient = fileSystemClient.getDirectoryClient(path);
        directoryClient.deleteWithResponse(true, null, null, null);
    }

    private void deleteFile(final DataLakeFileSystemClient fileSystemClient,
                            final AzureBlobStorage storage,
                            final String path) {
        validateFile(fileSystemClient, storage, path, true);
        events.put(new DataAccessEvent(path, DataAccessType.DELETE, storage));
        final DataLakeFileClient fileClient = fileSystemClient.getFileClient(path);
        if (!fileClient.deleteIfExists()) {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_BLOB_DELETE_FAILED, path));
        }
    }

    private Stream<AbstractDataStorageItem> getItemsRecursively(final AzureBlobStorage storage, final String path) {
        final DataLakeFileSystemClient fileSystemClient = getFileSystemClient(storage);
        final PagedIterable<PathItem> items = getItemsRecursively(fileSystemClient, path);
        return StreamSupport.stream(items.spliterator(), false).map(i -> toDataStorageItem(fileSystemClient, i));
    }

    private static PagedIterable<PathItem> getItemsRecursively(final DataLakeFileSystemClient fileSystemClient,
                                                               final String path) {
        return fileSystemClient.listPaths(new ListPathsOptions()
                .setPath(path)
                .setRecursive(true),
                AZURE_STORAGE_TIMEOUT);
    }

    private AbstractDataStorageItem toDataStorageItem(final DataLakeFileSystemClient fileSystemClient,
                                                      final PathItem item) {
        final String path = item.getName();
        final PathProperties properties = getPathProperties(fileSystemClient, path);
        return item.isDirectory() ? toDataStorageFolder(path) : toDataStorageFile(properties, path);
    }

    private void validateDirectory(final DataLakeFileSystemClient fileSystemClient,
                                   final AzureBlobStorage storage, final String path, final boolean exist) {
        validatePath(fileSystemClient, storage, path, exist, this::directoryExists);
    }

    private void validateFile(final DataLakeFileSystemClient fileSystemClient, final AzureBlobStorage storage,
                              final String path, final boolean exist) {
        validatePath(fileSystemClient, storage, path, exist, this::fileExists);
    }

    private void validatePath(final DataLakeFileSystemClient fileSystemClient, final AzureBlobStorage storage,
                              final String path, final boolean exist,
                              final BiPredicate<DataLakeFileSystemClient, String> existence) {
        validatePath(path);
        if (exist) {
            Assert.state(existence.test(fileSystemClient, path),
                    messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND,
                            path, storage.getPath()));
        } else {
            Assert.state(!existence.test(fileSystemClient, path),
                    messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_ALREADY_EXISTS,
                            path, storage.getPath()));
        }
    }

    private void validatePath(final String path) {
        Assert.state(!StringUtils.isBlank(path),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_IS_EMPTY));
    }

    private boolean directoryExists(final DataLakeFileSystemClient fileSystemClient, final String path) {
        return fileSystemClient.getDirectoryClient(path).exists();
    }

    private boolean fileExists(final DataLakeFileSystemClient fileSystemClient, final String path) {
        final DataLakeFileClient fileClient = fileSystemClient.getFileClient(path);
        return fileClient.exists();
    }

    private DataStorageFile toDataStorageFile(final PathProperties properties, final String path) {
        final String fileName = FilenameUtils.getName(path);
        final DataStorageFile dataStorageFile = new DataStorageFile();
        dataStorageFile.setName(fileName);
        dataStorageFile.setPath(path);
        final Map<String, String> labels = new HashMap<>();
        if (properties.getAccessTier() != null) {
            labels.put(STORAGE_CLASS, properties.getAccessTier().toString());
        }
        dataStorageFile.setLabels(labels);
        dataStorageFile.setTags(properties.getMetadata());
        dataStorageFile.setSize(properties.getFileSize());
        dataStorageFile.setChanged(dateFormat.format(Date.from(properties.getLastModified().toInstant())));
        return dataStorageFile;
    }

    private static PathProperties getPathProperties(final DataLakeFileSystemClient fileSystemClient,
                                                    final String path) {
        return fileSystemClient.getFileClient(path).getProperties();
    }

    private DataStorageFile getDataStorageFile(final DataLakeFileSystemClient fileSystemClient,
                                               final AzureBlobStorage storage, final String path) {
        return findFile(fileSystemClient, path)
                .orElseThrow(() -> new DataStorageException(messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND, storage.getPath())));
    }

    private DataStorageFolder toDataStorageFolder(final String folderFullPath) {
        final DataStorageFolder folder = new DataStorageFolder();
        final String[] parts = folderFullPath.split(ProviderUtils.DELIMITER);
        final String folderName = parts[parts.length - 1];
        folder.setName(folderName);
        folder.setPath(ProviderUtils.withoutTrailingDelimiter(folderFullPath));
        return folder;
    }

    private void copyItems(final PagedIterable<PathItem> pagedIterable,
                           final DataLakeFileSystemClient fileSystemClient,
                           final AzureBlobStorage storage,
                           final String sourceFolder,
                           final String destinationFolder) {
        for (PathItem item : pagedIterable) {
            final String sourcePath = item.getName();
            final String destPath = destinationFolder + item.getName().substring(sourceFolder.length());
            if (item.isDirectory()) {
                events.put(new DataAccessEvent(sourceFolder, DataAccessType.READ, storage),
                        new DataAccessEvent(destinationFolder, DataAccessType.WRITE, storage));
                createFolder(storage, destPath);
            } else {
                copyFile(fileSystemClient, storage, sourcePath, destPath);
            }
        }
    }

    private void copyFile(final DataLakeFileSystemClient fileSystemClient,
                          final AzureBlobStorage storage,
                          final String sourcePath,
                          final String destinationPath) {
        events.put(new DataAccessEvent(sourcePath, DataAccessType.READ, storage),
                new DataAccessEvent(destinationPath, DataAccessType.WRITE, storage));
        try {
            final DataLakeFileClient sourceFileClient = fileSystemClient.getFileClient(sourcePath);
            final DataLakeFileClient destFileClient = fileSystemClient.getFileClient(destinationPath);
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            sourceFileClient.read(outputStream);
            final byte[] fileContent = outputStream.toByteArray();
            destFileClient.create(true);
            destFileClient.append(BinaryData.fromBytes(fileContent), 0);
            destFileClient.flush(fileContent.length, true);
        } catch (DataLakeStorageException e) {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_BLOB_COPY_FAILED, sourcePath, destinationPath), e);
        }
    }

    private FileSystemSasPermission buildPermissions(final DataStorageAction dataStorageAction) {
        final FileSystemSasPermission permission = new FileSystemSasPermission();
        permission.setListPermission(true);
        permission.setReadPermission(dataStorageAction.isRead());
        if (dataStorageAction.isWrite()) {
            permission.setWritePermission(true);
            permission.setDeletePermission(true);
        }
        return permission;
    }
}
