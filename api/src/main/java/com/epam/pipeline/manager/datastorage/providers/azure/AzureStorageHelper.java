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

package com.epam.pipeline.manager.datastorage.providers.azure;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.entity.region.AzurePolicy;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.datastorage.access.DataAccessType;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.exception.AuthenticationException;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.utils.FileContentUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import com.epam.pipeline.manager.datastorage.providers.StorageEventCollector;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.PagedResponse;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.models.*;
import com.azure.storage.blob.sas.BlobContainerSasPermission;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.common.sas.SasIpRange;
import com.epam.pipeline.entity.datastorage.*;
import com.epam.pipeline.entity.datastorage.access.DataAccessEvent;
import org.springframework.util.Assert;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.StreamSupport;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Util class providing methods to interact with Azure Storage API.
 */
@Slf4j
public class AzureStorageHelper {

    private static final Long URL_EXPIRATION = 24 * 60 * 60 * 1000L;
    private static final String BLOB_URL_FORMAT = "https://%s.blob.core.windows.net";
    private static final int RANGE_NOT_SATISFIABLE_STATUS_CODE = 416;
    private static final Duration FALLBACK_EXPIRATION_DURATION = Duration.ofDays(1);
    static final int MAX_PAGE_SIZE = 5000;
    private static final Duration AZURE_STORAGE_TIMEOUT = Duration.ofSeconds(30);
    private final AzureRegion region;
    private final AzureRegionCredentials credentials;
    private final MessageHelper messageHelper;
    private final DateFormat dateFormat;
    private final StorageEventCollector events;

    public AzureStorageHelper(final AzureRegion region,
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
        final BlobContainerClient containerClient = getBlobContainerClient(storage);
        try {
            containerClient.create();
        } catch (BlobStorageException error) {
            final String message = messageHelper.getMessage(
                    error.getErrorCode().equals(BlobErrorCode.CONTAINER_ALREADY_EXISTS)
                            ? MessageConstants.ERROR_DATASTORAGE_ALREADY_EXIST
                            : MessageConstants.ERROR_DATASTORAGE_CREATE_FAILED, storage.getPath());
            throw new DataStorageException(message, error);
        }
        return storage.getPath();
    }

    public String deleteStorage(final AzureBlobStorage storage) {
        final BlobContainerClient containerClient = getBlobContainerClient(storage);
        try {
            containerClient.delete();
        } catch (BlobStorageException error) {
            final String message = messageHelper.getMessage(
                    error.getErrorCode().equals(BlobErrorCode.CONTAINER_NOT_FOUND)
                            ? MessageConstants.ERROR_DATASTORAGE_NOT_FOUND_BY_NAME
                            : MessageConstants.ERROR_DATASTORAGE_DELETE_FAILED, storage.getPath());
            throw new DataStorageException(message, error);
        }
        return storage.getPath();
    }

    public Stream<DataStorageFile> listDataStorageFiles(final AzureBlobStorage dataStorage, final String path) {
        return listFilesRecursively(dataStorage, path)
                .filter(DataStorageFile.class::isInstance)
                .map(DataStorageFile.class::cast);
    }

    public DataStorageListing getItems(final AzureBlobStorage storage,
                                       final String path,
                                       final Integer pageSize,
                                       final String continuationToken) {
        final BlobContainerClient containerClient = getBlobContainerClient(storage);
        final String prefix = Optional.ofNullable(path).map(ProviderUtils::withTrailingDelimiter).orElse("");
        int effectivePageSize = Optional.ofNullable(pageSize).orElse(MAX_PAGE_SIZE);

        final ListBlobsOptions options = new ListBlobsOptions()
                .setPrefix(prefix)
                .setMaxResultsPerPage(effectivePageSize);
        final PagedIterable<BlobItem> pagedIterable = containerClient.listBlobsByHierarchy("/", options, null);

        final PagedResponse<BlobItem> pageResponse = (continuationToken == null)
                ? pagedIterable.iterableByPage().iterator().next()
                : pagedIterable.iterableByPage(continuationToken).iterator().next();

        final List<AbstractDataStorageItem> items = pageResponse.getValue().stream()
                .filter(this::isNotTokenFile)
                .map(this::toDataStorageItem)
                .collect(Collectors.toList());
        return new DataStorageListing(pageResponse.getContinuationToken(), null, items);
    }

    public Optional<DataStorageFile> findFile(final AzureBlobStorage storage, final String path) {
        final BlobContainerClient containerClient = getBlobContainerClient(storage);
        final String fullPath = ProviderUtils.withoutLeadingDelimiter(path);
        final PagedIterable<BlobItem> blobItems = getBlobItemsRecursively(containerClient, fullPath);
        return blobItems.stream()
                .filter(item -> item.getName().equals(fullPath))
                .findFirst()
                .map(this::createDataStorageFile);
    }

    public DataStorageFile createFile(final AzureBlobStorage dataStorage,
                                      final String path,
                                      final byte[] contents,
                                      final String owner) {
        validatePath(path);
        events.put(new DataAccessEvent(path, DataAccessType.WRITE, dataStorage));

        final BlockBlobClient blobClient = getBlobClient(dataStorage, path);

        final Map<String, String> metadata = StringUtils.isBlank(owner)
                ? Collections.emptyMap()
                : Collections.singletonMap(ProviderUtils.OWNER_TAG_KEY, owner);

        try (ByteArrayInputStream dataStream = new ByteArrayInputStream(contents)) {
            blobClient.upload(dataStream, contents.length, true);
            if (!metadata.isEmpty()) {
                blobClient.setMetadata(metadata);
            }
        } catch (BlobStorageException | IOException e) {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_BLOB_UPLOAD_FAILED, path), e);
        }
        return getDataStorageFile(dataStorage, path);
    }

    public DataStorageFile createFile(final AzureBlobStorage dataStorage,
                                      final String path,
                                      final InputStream dataStream,
                                      final String owner) {
        return createFile(dataStorage, path, toByteArray(dataStream), owner);
    }

    public DataStorageFolder createFolder(final AzureBlobStorage dataStorage, final String path) {
        validateDirectory(dataStorage, path, false);
        String fullPath = getFullPath(path);
        fullPath += ProviderUtils.FOLDER_TOKEN_FILE;
        final BlockBlobClient blobClient = getBlobClient(dataStorage, fullPath);
        try (ByteArrayInputStream emptyStream = new ByteArrayInputStream(new byte[0])) {
            blobClient.upload(emptyStream, 0, false);
        } catch (IOException e) {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_FOLDER_CREATE_FAILED, fullPath), e);
        }
        return getDataStorageFolder(path);
    }

    public DataStorageFile moveFile(final AzureBlobStorage dataStorage, final String oldPath, final String newPath) {
        validateBlob(dataStorage, oldPath, true);
        validateBlob(dataStorage, newPath, false);
        copyBlob(dataStorage, oldPath, newPath);
        deleteItem(dataStorage, oldPath);
        return getDataStorageFile(dataStorage, newPath);
    }

    public DataStorageFolder moveFolder(final AzureBlobStorage dataStorage, final String oldRawPath,
                                        final String newRawPath) {
        final String oldPath = getFullPath(oldRawPath);
        final String newPath = getFullPath(newRawPath);
        validateDirectory(dataStorage, oldPath, true);
        validateDirectory(dataStorage, newPath, false);
        final String folderFullPath = newPath.substring(0, newPath.length() - 1);
        copyBlobs(dataStorage, oldPath, newPath);
        deleteItem(dataStorage, oldPath);
        return getDataStorageFolder(folderFullPath);
    }

    public boolean checkStorage(final AzureBlobStorage storage) {
        return getBlobContainerClient(storage).exists();
    }

    public Map<String, String> updateObjectTags(final AzureBlobStorage dataStorage,
                                                final String path,
                                                final Map<String, String> tags) {
        validateBlob(dataStorage, path, true);
        try {
            getBlobClient(dataStorage, path).setMetadata(tags);
        } catch (BlobStorageException e) {
            throw new DataStorageException("Failed to update blob metadata: " + path, e);
        }
        return tags;
    }

    public Map<String, String> listObjectTags(final AzureBlobStorage dataStorage, final String path) {
        validateBlob(dataStorage, path, true);
        try {
            final BlobProperties properties = getBlobClient(dataStorage, path).getProperties();
            return properties.getMetadata();
        } catch (BlobStorageException e) {
            return Collections.emptyMap();
        }
    }

    public Map<String, String> deleteObjectTags(final AzureBlobStorage dataStorage,
                                                final String path,
                                                final Set<String> tagsToDelete) {
        validateBlob(dataStorage, path, true);
        final BlobContainerClient containerClient = getBlobContainerClient(dataStorage);
        final BlobClient blobClient = containerClient.getBlobClient(path);
        try {
            final BlobProperties properties = blobClient.getProperties();
            final Map<String, String> metadata = new HashMap<>(properties.getMetadata());
            for (String tag : tagsToDelete) {
                if (!metadata.containsKey(tag)) {
                    throw new IllegalStateException(messageHelper.getMessage(
                            MessageConstants.ERROR_DATASTORAGE_FILE_TAG_NOT_EXIST, tag)
                    );
                }
            }
            metadata.keySet().removeAll(tagsToDelete);
            blobClient.setMetadata(metadata);
            return metadata;
        } catch (BlobStorageException e) {
            return Collections.emptyMap();
        }
    }

    public DataStorageItemContent getFile(final AzureBlobStorage dataStorage,
                                          final String path,
                                          final Long maxDownloadSize) {
        validateBlob(dataStorage, path, true);
        final BlockBlobClient blobClient = getBlobClient(dataStorage, path);
        events.put(new DataAccessEvent(path, DataAccessType.READ, dataStorage));
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            final BlobProperties properties = blobClient.getProperties();
            final long fileSize = properties.getBlobSize();
            final long rangeEnd = Math.min(maxDownloadSize, fileSize);
            final BlobRange blobRange = new BlobRange(0, rangeEnd);
            final DownloadRetryOptions options = new DownloadRetryOptions().setMaxRetryRequests(5);
            blobClient.downloadStreamWithResponse(outputStream, blobRange, options, null, false,
                    null, null);
            byte[] bytes = outputStream.toByteArray();
            final DataStorageItemContent content = new DataStorageItemContent();
            content.setContent(bytes);
            content.setContentType(properties.getContentType());
            content.setTruncated(fileSize > maxDownloadSize);
            content.setMayBeBinary(FileContentUtils.isBinaryContent(bytes));
            return content;
        } catch (BlobStorageException e) {
            if (e.getStatusCode() == RANGE_NOT_SATISFIABLE_STATUS_CODE) {
                return new DataStorageItemContent();
            }
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_BLOB_DOWNLOAD_FAILED, path), e);
        } catch (IOException e) {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_BLOB_DOWNLOAD_FAILED, path), e);
        }
    }

    public DataStorageStreamingContent getStream(final AzureBlobStorage dataStorage, final String path) {
        validateBlob(dataStorage, path, true);
        events.put(new DataAccessEvent(path, DataAccessType.READ, dataStorage));
        final BlockBlobClient blobClient = getBlobClient(dataStorage, path);
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            blobClient.downloadStream(outputStream);
            InputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
            return new DataStorageStreamingContent(inputStream, path);
        } catch (BlobStorageException e) {
            if (e.getStatusCode() == RANGE_NOT_SATISFIABLE_STATUS_CODE) {
                return new DataStorageStreamingContent(new ByteArrayInputStream(new byte[0]), path);
            }
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_BLOB_DOWNLOAD_FAILED, path), e);
        }
    }

    public DataStorageDownloadFileUrl generatePresignedUrl(final AzureBlobStorage dataStorage,
                                                           final String path,
                                                           final String permission,
                                                           final boolean exist) {
        validateBlob(dataStorage, path, exist);
        return generateGenericPresignedUrl(dataStorage, path, permission, Duration.ZERO);
    }

    public DataStorageDownloadFileUrl generateGenericPresignedUrl(final AzureBlobStorage dataStorage,
                                                                  final String path,
                                                                  final String permission,
                                                                  final Duration duration) {
        final String sasToken = generateSASToken(dataStorage, path, permission, expirationOf(duration));
        return generateResponseObject(dataStorage, path, sasToken);
    }

    public String generateSASToken(final AzureBlobStorage dataStorage,
                                   final String path,
                                   final String permission,
                                   final OffsetDateTime expiryTime) {
        final BlobContainerClient blobContainerClient = getBlobContainerClient(dataStorage);
        return StringUtils.isBlank(path) || path.endsWith("/")
                ? generateSASToken(blobContainerClient, permission, expiryTime)
                : generateSASToken(blobContainerClient, path, permission, expiryTime);
    }

    public String generateSASToken(final BlobContainerClient blobContainerClient,
                                   final String permission,
                                   final OffsetDateTime expiryTime) {
        final BlobContainerSasPermission blobContainerSasPermission = BlobContainerSasPermission.parse(permission);
        final BlobServiceSasSignatureValues sasSignatureValues = new BlobServiceSasSignatureValues(expiryTime,
                blobContainerSasPermission)
                .setStartTime(OffsetDateTime.now());
        try {
            final UserDelegationKey userDelegationKey =
                    getBlobServiceClient(region, credentials).getUserDelegationKey(OffsetDateTime.now(), expiryTime);
            return blobContainerClient
                    .generateUserDelegationSas(sasSignatureValues, userDelegationKey);
        } catch (BlobStorageException e) {
            return blobContainerClient.generateSas(sasSignatureValues);
        }
    }

    public String generateSASToken(final BlobContainerClient blobContainerClient,
                                   final String blobName,
                                   final String permission,
                                   final OffsetDateTime expiryTime) {
        final BlobSasPermission blobSasPermission = BlobSasPermission.parse(permission);
        final BlobServiceSasSignatureValues sasSignatureValues = new BlobServiceSasSignatureValues(expiryTime,
                blobSasPermission)
                .setStartTime(OffsetDateTime.now());
        try {
            final UserDelegationKey userDelegationKey =
                    getBlobServiceClient(region, credentials).getUserDelegationKey(OffsetDateTime.now(), expiryTime);
            return blobContainerClient.getBlobClient(blobName)
                    .generateUserDelegationSas(sasSignatureValues, userDelegationKey);
        } catch (BlobStorageException ex) {
            return blobContainerClient.getBlobClient(blobName).generateSas(sasSignatureValues);
        }
    }
    public void addIPRangeToSASValue(final BlobServiceSasSignatureValues values) {
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

    public void deleteItem(final AzureBlobStorage dataStorage, final String path) {
        if (path.endsWith(ProviderUtils.DELIMITER)) {
            deleteFolder(dataStorage, path);
        } else {
            deleteFile(dataStorage, path);
        }
    }

    public PathDescription getDataSize(final AzureBlobStorage dataStorage,
                                       final String path,
                                       final PathDescription pathDescription) {
        final String requestPath = Optional.ofNullable(path).orElse("").trim();
        final BlobContainerClient containerClient = getBlobContainerClient(dataStorage);
        final List<BlobItem> items = containerClient
                .listBlobs(new ListBlobsOptions().setPrefix(requestPath), AZURE_STORAGE_TIMEOUT)
                .stream()
                .collect(Collectors.toList());

        ProviderUtils.getSizeByPath(items, requestPath, item -> item.getProperties().getContentLength(),
                BlobItem::getName, pathDescription);

        pathDescription.setCompleted(true);
        return pathDescription;
    }

    public static BlobServiceClient getBlobServiceClient(final AzureRegion region,
                                                         final AzureRegionCredentials credentials) {
        final String storageAccount = region.getStorageAccount();
        final String endpoint = String.format(BLOB_URL_FORMAT, storageAccount);
        final HttpLogOptions httpLogOptions = new HttpLogOptions().setLogLevel(HttpLogDetailLevel.BASIC);
        if (StringUtils.isNotBlank(region.getManagedIdentity())) {
            log.info("Authenticating with Managed Identity");
            return new BlobServiceClientBuilder()
                    .endpoint(endpoint)
                    .credential(new DefaultAzureCredentialBuilder()
                            .managedIdentityClientId(region.getManagedIdentity())
                            .build())
                    .httpLogOptions(httpLogOptions)
                    .buildClient();
        } else if (StringUtils.isNotBlank(credentials.getStorageAccountKey())) {
            log.info("Authenticating with Storage Account Key");
            return new BlobServiceClientBuilder()
                    .endpoint(endpoint)
                    .credential(new StorageSharedKeyCredential(storageAccount, credentials.getStorageAccountKey()))
                    .httpLogOptions(httpLogOptions)
                    .buildClient();

        } else {
            throw new AuthenticationException("Failed to authenticate to Azure.");
        }
    }

    public BlobContainerClient getBlobContainerClient(final AzureBlobStorage storage) {
        return getBlobServiceClient(region, credentials).getBlobContainerClient(storage.getPath());
    }

    private boolean isNotTokenFile(final BlobItem item) {
        return !StringUtils.endsWithIgnoreCase(item.getName(), ProviderUtils.FOLDER_TOKEN_FILE.toLowerCase());
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

    private DataStorageDownloadFileUrl generateResponseObject(final AzureBlobStorage dataStorage,
                                                              final String path,
                                                              final String sasToken) {
        return responseWith(blobUrl(resourcePath(dataStorage.getPath(), path), sasToken));
    }

    private String resourcePath(final String container, final String path) {
        return StringUtils.isBlank(path) || StringUtils.strip(path).equals("/") ? container  : container + "/" + path;
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

    private void deleteFolder(final AzureBlobStorage dataStorage, final String path) {
        validateDirectory(dataStorage, path, true);
        final BlobContainerClient containerClient = getBlobContainerClient(dataStorage);
        final String folderPath = getFullPath(path);
        for (BlobItem item: getBlobItemsRecursively(containerClient, folderPath)) {
            deleteBlob(dataStorage, item.getName());
        }
    }

    private static String getFullPath(final String path) {
        return ProviderUtils.withoutLeadingDelimiter(ProviderUtils.withTrailingDelimiter(path.trim()));
    }

    private void deleteFile(final AzureBlobStorage dataStorage, final String path) {
        validateBlob(dataStorage, path, true);
        deleteBlob(dataStorage, path);
    }

    private void deleteBlob(final AzureBlobStorage dataStorage, final String path) {
        events.put(new DataAccessEvent(path, DataAccessType.DELETE, dataStorage));
        try {
            getBlobClient(dataStorage, path).delete();
        } catch (BlobStorageException e) {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_BLOB_DELETE_FAILED, path), e);
        }
    }

    private BlockBlobClient getBlobClient(final AzureBlobStorage dataStorage, final String path) {
        final BlobContainerClient containerClient = getBlobContainerClient(dataStorage);
        return containerClient.getBlobClient(path).getBlockBlobClient();
    }

    private Stream<AbstractDataStorageItem> listFilesRecursively(final AzureBlobStorage storage, final String path) {
        final BlobContainerClient containerClient = getBlobContainerClient(storage);
        final PagedIterable<BlobItem> blobs = getBlobItemsRecursively(containerClient, path);
        return StreamSupport.stream(blobs.spliterator(), false).map(this::toDataStorageItem);
    }

    private static PagedIterable<BlobItem> getBlobItemsRecursively(final BlobContainerClient containerClient,
                                                                   final String path) {
        return containerClient.listBlobs(new ListBlobsOptions().setPrefix(path), AZURE_STORAGE_TIMEOUT);
    }

    private AbstractDataStorageItem toDataStorageItem(final BlobItem blobItem) {
        return Boolean.TRUE.equals(blobItem.isPrefix()) ? getDataStorageFolder(blobItem.getName())
                : createDataStorageFile(blobItem);
    }

    private void validateDirectory(final AzureBlobStorage storage, final String path, final boolean exist) {
        validatePath(storage, path, exist, this::directoryExists);
    }

    private void validateBlob(final AzureBlobStorage storage, final String path, final boolean exist) {
        validatePath(storage, path, exist, this::blobExists);
    }

    private void validatePath(final AzureBlobStorage storage, final String path, final boolean exist,
                              final BiPredicate<AzureBlobStorage, String> existence) {
        validatePath(path);
        if (exist) {
            Assert.state(existence.test(storage, path),
                    messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND,
                            path, storage.getPath()));
        } else {
            Assert.state(!existence.test(storage, path),
                    messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_ALREADY_EXISTS,
                            path, storage.getPath()));
        }
    }

    private boolean directoryExists(final AzureBlobStorage dataStorage, final String path) {
        final BlobContainerClient containerClient = getBlobContainerClient(dataStorage);
        final String pathWithTrailingDelimiter = getFullPath(path);
        return containerClient.listBlobsByHierarchy(pathWithTrailingDelimiter).iterator().hasNext();
    }

    private boolean blobExists(final AzureBlobStorage dataStorage, final String path) {
        final BlobClient blobClient = getBlobContainerClient(dataStorage).getBlobClient(path);
        return blobClient.exists();
    }

    private void validatePath(final String path) {
        Assert.state(!StringUtils.isBlank(path),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_IS_EMPTY));
    }

    private DataStorageFile createDataStorageFile(final BlobItem blobItem) {
        final String fileName = FilenameUtils.getName(blobItem.getName());
        final String filePath = blobItem.getName();
        final DataStorageFile dataStorageFile = new DataStorageFile();
        dataStorageFile.setName(fileName);
        dataStorageFile.setPath(filePath);
        final Map<String, String> labels = new HashMap<>();
        if (blobItem.getProperties().getAccessTier() != null) {
            labels.put("StorageClass", blobItem.getProperties().getAccessTier().toString());
        }
        dataStorageFile.setLabels(labels);
        dataStorageFile.setTags(blobItem.getMetadata());
        dataStorageFile.setSize(blobItem.getProperties().getContentLength());
        dataStorageFile.setChanged(dateFormat.format(Date.from(blobItem
                .getProperties().getLastModified().toInstant())));
        return dataStorageFile;
    }

    private DataStorageFile getDataStorageFile(final AzureBlobStorage storage, final String path) {
        return findFile(storage, path)
                .orElseThrow(() -> new DataStorageException(messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_AZURE_CREATE_FILE, storage.getPath())));
    }

    private DataStorageFolder getDataStorageFolder(final String folderFullPath) {
        final DataStorageFolder folder = new DataStorageFolder();
        final String[] parts = folderFullPath.split(ProviderUtils.DELIMITER);
        final String folderName = parts[parts.length - 1];
        folder.setName(folderName);
        folder.setPath(ProviderUtils.withoutTrailingDelimiter(folderFullPath));
        return folder;
    }

    private void copyBlobs(final AzureBlobStorage storage, final String sourceFolder, final String destinationFolder) {
        final BlobContainerClient containerClient = getBlobContainerClient(storage);
        for (BlobItem blobItem : getBlobItemsRecursively(containerClient, sourceFolder)) {
            final String sourceBlobName = blobItem.getName();
            final String destBlobName = destinationFolder + sourceBlobName.substring(sourceFolder.length());
            copyBlob(storage, sourceBlobName, destBlobName);
        }
    }

    private void copyBlob(final AzureBlobStorage storage, final String sourcePath, final String destinationPath) {
        events.put(new DataAccessEvent(sourcePath, DataAccessType.READ, storage),
                new DataAccessEvent(destinationPath, DataAccessType.WRITE, storage));
        try {
            final BlobContainerClient containerClient = getBlobContainerClient(storage);
            final BlobClient sourceBlob = containerClient.getBlobClient(sourcePath);
            final BlobClient destBlob = containerClient.getBlobClient(destinationPath);
            destBlob.beginCopy(sourceBlob.getBlobUrl(), Duration.ofSeconds(1));
        } catch (BlobStorageException e) {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_BLOB_COPY_FAILED, sourcePath, destinationPath), e);
        }
    }
}
