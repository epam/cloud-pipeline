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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.entity.region.AzurePolicy;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.datastorage.providers.azure.AbstractListingIterator.FlatIterator;
import com.epam.pipeline.manager.datastorage.providers.azure.AbstractListingIterator.HierarchyIterator;
import com.epam.pipeline.utils.FileContentUtils;
import com.microsoft.azure.storage.blob.BlobRange;
import com.microsoft.azure.storage.blob.BlockBlobURL;
import com.microsoft.azure.storage.blob.ContainerURL;
import com.microsoft.azure.storage.blob.IPRange;
import com.microsoft.azure.storage.blob.Metadata;
import com.microsoft.azure.storage.blob.PipelineOptions;
import com.microsoft.azure.storage.blob.SASProtocol;
import com.microsoft.azure.storage.blob.SASQueryParameters;
import com.microsoft.azure.storage.blob.ServiceSASSignatureValues;
import com.microsoft.azure.storage.blob.ServiceURL;
import com.microsoft.azure.storage.blob.SharedKeyCredentials;
import com.microsoft.azure.storage.blob.StorageException;
import com.microsoft.azure.storage.blob.StorageURL;
import com.microsoft.azure.storage.blob.models.BlobItem;
import com.microsoft.azure.storage.blob.models.BlobPrefix;
import com.microsoft.azure.storage.blob.models.ListBlobsFlatSegmentResponse;
import com.microsoft.azure.storage.blob.models.ListBlobsHierarchySegmentResponse;
import com.microsoft.azure.storage.blob.models.StorageErrorException;
import com.microsoft.rest.v2.http.HttpPipelineLogLevel;
import com.microsoft.rest.v2.http.HttpPipelineLogger;
import com.microsoft.rest.v2.util.FlowableUtil;
import io.reactivex.Flowable;
import io.reactivex.Single;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.util.Assert;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Util class providing methods to interact with Azure Storage API.
 */
@Slf4j
public class AzureStorageHelper {

    private static final Long URL_EXPIRATION = 24 * 60 * 60 * 1000L;
    private static final String BLOB_URL_FORMAT = "https://%s.blob.core.windows.net";
    private static final int NOT_FOUND_STATUS_CODE = 404;
    private static final int RANGE_NOT_SATISFIABLE_STATUS_CODE = 416;
    static final int MAX_PAGE_SIZE = 5000;

    private final AzureRegion azureRegion;
    private final AzureRegionCredentials azureRegionCredentials;
    private final MessageHelper messageHelper;
    private final DateFormat dateFormat;
    private final HttpPipelineLogger httpLogger;

    public AzureStorageHelper(final AzureRegion azureRegion,
                              final AzureRegionCredentials azureRegionCredentials,
                              final MessageHelper messageHelper) {
        this.azureRegion = azureRegion;
        this.azureRegionCredentials = azureRegionCredentials;
        this.messageHelper = messageHelper;
        final TimeZone tz = TimeZone.getTimeZone("UTC");
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        this.dateFormat.setTimeZone(tz);
        this.httpLogger = new HttpPipelineLogger() {
            @Override
            public HttpPipelineLogLevel minimumLogLevel() {
                return HttpPipelineLogLevel.INFO;
            }
            @Override
            public void log(final HttpPipelineLogLevel logLevel, final String message,
                            final Object... formattedArguments) {
                log.debug(message, formattedArguments);
            }
        };
    }

    public String createBlobStorage(final AzureBlobStorage storage) {
        unwrap(getContainerURL(storage).create());
        return storage.getPath();
    }

    public void deleteStorage(final AzureBlobStorage storage) {
        unwrap(getContainerURL(storage).delete());
    }

    public DataStorageListing getItems(final AzureBlobStorage storage, final String path, final Integer pageSize,
                                       final String marker) {
        final String prefix = Optional.ofNullable(path).map(ProviderUtils::withTrailingDelimiter).orElse("");
        final int page = Optional.ofNullable(pageSize).orElse(MAX_PAGE_SIZE);
        final HierarchyIterator iterator = AbstractListingIterator.hierarchy(getContainerURL(storage), prefix, marker,
                page);
        final List<AbstractDataStorageItem> items = list(iterator)
                .filter(this::isNotTokenFile)
                .limit(page)
                .collect(Collectors.toList());
        return new DataStorageListing(iterator.getNextMarker(), items);
    }

    private boolean isNotTokenFile(final AbstractDataStorageItem item) {
        return !StringUtils.endsWithIgnoreCase(item.getName(), ProviderUtils.FOLDER_TOKEN_FILE.toLowerCase());
    }

    public DataStorageFile createFile(final AzureBlobStorage dataStorage, final String path, final byte[] contents,
                                      final String owner) {
        validatePath(path);
        unwrap(getBlobUrl(dataStorage, path)
                .upload(Flowable.just(ByteBuffer.wrap(contents)), contents.length, null,
                        StringUtils.isBlank(owner) ? null
                                : new Metadata(Collections.singletonMap("CP_OWNER", owner)),
                        null, null));
        return getDataStorageFile(dataStorage, path);
    }

    public DataStorageFile createFile(final AzureBlobStorage dataStorage,
                                      final String path,
                                      final InputStream dataStream,
                                      final String owner) {
        return createFile(dataStorage, path, toByteArray(dataStream), owner);
    }

    @SneakyThrows
    private byte[] toByteArray(final InputStream dataStream) {
        return IOUtils.toByteArray(dataStream);
    }

    public DataStorageFolder createFolder(final AzureBlobStorage dataStorage, final String path) {
        String folderPath = ProviderUtils.withoutLeadingDelimiter(ProviderUtils.withTrailingDelimiter(path.trim()));
        if (directoryExists(dataStorage, folderPath)) {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_FOLDER_ALREADY_EXISTS));
        }
        final String folderFullPath = folderPath.substring(0, folderPath.length() - 1);
        folderPath += ProviderUtils.FOLDER_TOKEN_FILE;
        final String[] parts = folderPath.split(ProviderUtils.DELIMITER);
        final String folderName = parts[parts.length - 2];
        unwrap(getBlobUrl(dataStorage, folderPath).upload(Flowable.just(ByteBuffer.wrap("".getBytes())), 0));
        return getDataStorageFolder(folderFullPath, folderName);
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
        final String oldPath = ProviderUtils.withTrailingDelimiter(oldRawPath);
        final String newPath = ProviderUtils.withTrailingDelimiter(newRawPath);
        validateDirectory(dataStorage, oldPath, true);
        validateDirectory(dataStorage, newPath, false);
        final String folderFullPath = newPath.substring(0, newPath.length() - 1);
        final String[] parts = newPath.split(ProviderUtils.DELIMITER);
        final String folderName = parts[parts.length - 1];
        final DataStorageFolder folder = createFolder(dataStorage, newPath);
        copyBlobs(dataStorage, oldPath, folder.getPath());
        deleteItem(dataStorage, oldPath);
        return getDataStorageFolder(folderFullPath, folderName);
    }

    public boolean checkStorage(final AzureBlobStorage storage) {
        return unwrap(getContainerURL(storage).getProperties().map(r -> true), this::falseIfNotFound);
    }

    public Map<String, String> updateObjectTags(final AzureBlobStorage dataStorage,
                                                final String path,
                                                final Map<String, String> tags) {
        validateBlob(dataStorage, path, true);
        unwrap(getBlobUrl(dataStorage, path).setMetadata(new Metadata(tags)));
        return tags;
    }

    public Map<String, String> listObjectTags(final AzureBlobStorage dataStorage, final String path) {
        validateBlob(dataStorage, path, true);
        return unwrap(getBlobUrl(dataStorage, path).getProperties()
                        .map(r -> r.headers().metadata()),
                this::emptyIfNotFound);
    }

    public Map<String, String> deleteObjectTags(final AzureBlobStorage dataStorage,
                                                final String path,
                                                final Set<String> tagsToDelete) {
        validateBlob(dataStorage, path, true);
        final BlockBlobURL blockBlobURL = getBlobUrl(dataStorage, path);
        return unwrap(blockBlobURL.getProperties()
                        .map(r -> r.headers().metadata())
                        .flatMap(tags -> {
                            tagsToDelete.forEach(tag ->
                                    Assert.state(tags.containsKey(tag), messageHelper.getMessage(
                                            MessageConstants.ERROR_DATASTORAGE_FILE_TAG_NOT_EXIST, tag)));
                            tags.keySet().removeAll(tagsToDelete);
                            return blockBlobURL.setMetadata(new Metadata(tags)).map(r -> tags);
                        }),
                this::emptyIfNotFound);
    }

    private Map<String, String> emptyIfNotFound(final Integer code) {
        return code.equals(NOT_FOUND_STATUS_CODE) ? Collections.emptyMap() : null;
    }

    private Boolean falseIfNotFound(final Integer code) {
        return code.equals(NOT_FOUND_STATUS_CODE) ? false : null;
    }

    public DataStorageItemContent getFile(final AzureBlobStorage dataStorage,
                                          final String path,
                                          final Long maxDownloadSize) {
        validateBlob(dataStorage, path, true);
        final Long fileSize = getDataStorageFile(dataStorage, path).getSize();
        final BlobRange blobRange = new BlobRange().withCount(maxDownloadSize);
        return unwrap(getBlobUrl(dataStorage, path).download(blobRange, null, false, null)
                        .flatMap(response -> FlowableUtil.collectBytesInArray(response.body(null))
                                .map(bytes -> {
                                    final DataStorageItemContent content = new DataStorageItemContent();
                                    content.setContent(bytes);
                                    content.setContentType(response.headers().contentType());
                                    content.setTruncated(fileSize > maxDownloadSize);
                                    content.setMayBeBinary(FileContentUtils.isBinaryContent(bytes));
                                    return content;
                                })),
            code -> reviveIfPageRangeIsInvalid(code, new DataStorageItemContent()));
    }

    public DataStorageStreamingContent getStream(final AzureBlobStorage dataStorage, final String path) {
        //TODO: can be reason of error
        validateBlob(dataStorage, path, true);
        return unwrap(getBlobUrl(dataStorage, path).download()
                        .map(r -> r.body(null))
                        .flatMap(FlowableUtil::collectBytesInArray)
                        .map(ByteArrayInputStream::new)
                        .map(inputStream -> new DataStorageStreamingContent(inputStream, path)),
            code -> reviveIfPageRangeIsInvalid(code,
                        new DataStorageStreamingContent(new ByteArrayInputStream(new byte[0]), path)));
    }

    private <T> T reviveIfPageRangeIsInvalid(final Integer code, final T t) {
        return code.equals(RANGE_NOT_SATISFIABLE_STATUS_CODE) ? t : null;
    }

    public DataStorageDownloadFileUrl generatePresignedUrl(final AzureBlobStorage dataStorage,
                                                           final String path,
                                                           final String permission) {
        validateBlob(dataStorage, path, true);
        final ServiceSASSignatureValues values = new ServiceSASSignatureValues()
            .withProtocol(SASProtocol.HTTPS_ONLY)
            .withExpiryTime(OffsetDateTime.now().plusDays(1))
            .withContainerName(dataStorage.getPath())
            .withBlobName(path)
            .withContentType("blob")
            .withPermissions(permission);
        addIPRangeToSASValue(values);
        final SharedKeyCredentials credential = getStorageCredential();

        final SASQueryParameters params = values.generateSASQueryParameters(credential);
        final String encodedParams = params.encode();
        final String blobUrl = String.format(BLOB_URL_FORMAT + "/%s/%s%s", azureRegion.getStorageAccount(),
                dataStorage.getPath(), path, encodedParams);
        final DataStorageDownloadFileUrl dataStorageDownloadFileUrl = new DataStorageDownloadFileUrl();
        dataStorageDownloadFileUrl.setUrl(blobUrl);
        dataStorageDownloadFileUrl.setExpires(new Date((new Date()).getTime() + URL_EXPIRATION));
        return dataStorageDownloadFileUrl;
    }

    public void addIPRangeToSASValue(final ServiceSASSignatureValues values) {
        final AzurePolicy policy = azureRegion.getAzurePolicy();
        if (policy != null &&
                (StringUtils.isNotBlank(policy.getIpMin()) || StringUtils.isNotBlank(policy.getIpMax()))) {
            if (StringUtils.isNotBlank(policy.getIpMin()) && StringUtils.isNotBlank(policy.getIpMax())) {
                values.withIpRange(new IPRange().withIpMin(policy.getIpMin()).withIpMax(policy.getIpMax()));
                return;
            }
            final String ipValue = Optional.ofNullable(policy.getIpMin()).orElse(policy.getIpMax());
            values.withIpRange(new IPRange().withIpMin(ipValue).withIpMax(ipValue));
        }
    }

    public void deleteItem(final AzureBlobStorage dataStorage, final String path) {
        if (path.endsWith(ProviderUtils.DELIMITER)) {
            deleteFolder(dataStorage, path);
        } else {
            deleteFile(dataStorage, path);
        }
    }

    private void deleteFolder(final AzureBlobStorage dataStorage, final String path) {
        validateDirectory(dataStorage, path, true);
        while (true) {
            // Files should be deleted page by page because we can't rely on the pagination.
            final List<AbstractDataStorageItem> files = listFilesRecursively(dataStorage, path)
                    .limit(MAX_PAGE_SIZE)
                    .collect(Collectors.toList());
            files.forEach(item -> deleteBlob(dataStorage, item.getPath()));
            if (files.size() < MAX_PAGE_SIZE) {
                return;
            }
        }
    }

    private void deleteFile(final AzureBlobStorage dataStorage, final String path) {
        validateBlob(dataStorage, path, true);
        deleteBlob(dataStorage, path);
    }

    private void deleteBlob(final AzureBlobStorage dataStorage, final String path) {
        unwrap(getBlobUrl(dataStorage, path).delete());
    }

    public SharedKeyCredentials getStorageCredential() {
        try {
            return new SharedKeyCredentials(azureRegion.getStorageAccount(),
                    azureRegionCredentials.getStorageAccountKey());
        } catch (InvalidKeyException e) {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_AZURE_INVALID_ACCOUNT_KEY, azureRegion.getStorageAccount()), e);
        }
    }

    private ContainerURL getContainerURL(final AzureBlobStorage storage) {
        final SharedKeyCredentials credential = getStorageCredential();

        final ServiceURL serviceURL = new ServiceURL(
                url(String.format(BLOB_URL_FORMAT, azureRegion.getStorageAccount())),
                StorageURL.createPipeline(credential, new PipelineOptions()
                        .withLogger(httpLogger)));
        return serviceURL.createContainerURL(storage.getPath());
    }

    private BlockBlobURL getBlobUrl(final AzureBlobStorage dataStorage, final String path) {
        final ContainerURL containerURL = getContainerURL(dataStorage);
        return containerURL.createBlockBlobURL(path);
    }

    private Stream<AbstractDataStorageItem> listFilesRecursively(final AzureBlobStorage storage, final String path) {
        return listFlat(storage, path);
    }

    private Stream<AbstractDataStorageItem> listFolders(final AzureBlobStorage storage, final String path) {
        return listHierarchy(storage, path)
                .filter(it -> it.getType() == DataStorageItemType.Folder);
    }

    private Stream<AbstractDataStorageItem> listFlat(final AzureBlobStorage storage, final String path) {
        return list(AbstractListingIterator.flat(getContainerURL(storage), path));
    }

    private Stream<AbstractDataStorageItem> listHierarchy(final AzureBlobStorage storage, final String path) {
        return list(AbstractListingIterator.hierarchy(getContainerURL(storage), path));
    }

    private Stream<AbstractDataStorageItem> list(final HierarchyIterator iterator) {
        return iterator.stream()
                .map(response -> Optional.of(response.body())
                        .map(ListBlobsHierarchySegmentResponse::segment)
                        .map(segment -> Stream.concat(folders(segment.blobPrefixes()),
                                files(segment.blobItems(), response.body().prefix()))))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .flatMap(Function.identity());
    }

    private Stream<AbstractDataStorageItem> list(final FlatIterator iterator) {
        return iterator.stream()
                .map(response -> Optional.of(response.body())
                        .map(ListBlobsFlatSegmentResponse::segment)
                        .map(segment -> files(segment.blobItems(), null)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .flatMap(Function.identity());
    }

    private Stream<AbstractDataStorageItem> folders(final List<BlobPrefix> prefixes) {
        return Optional.ofNullable(prefixes)
                .orElseGet(Collections::emptyList)
                .stream()
                .map(blobPrefix -> getDataStorageFolder(blobPrefix.name(), folderName(blobPrefix)));
    }

    private Stream<AbstractDataStorageItem> files(final List<BlobItem> items, final String prefix) {
        return Optional.ofNullable(items)
                .orElseGet(Collections::emptyList)
                .stream()
                .filter(blob -> !Objects.equals(blob.name(), prefix))
                .map(blob -> createDataStorageFile(blob, prefix));
    }

    private String folderName(final BlobPrefix blobPrefix) {
        final String[] parts = blobPrefix.name().split(ProviderUtils.DELIMITER);
        return parts[parts.length - 1];
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
        final String pathWithoutTrailingDelimiter = ProviderUtils.withoutTrailingDelimiter(path);
        return listFolders(dataStorage, pathWithoutTrailingDelimiter)
                .anyMatch(it -> it.getPath().equals(pathWithoutTrailingDelimiter));
    }

    private boolean blobExists(final AzureBlobStorage dataStorage, final String path) {
        return list(AbstractListingIterator.flat(getContainerURL(dataStorage), path, null, 1))
                .anyMatch(it -> it.getPath().equals(path));
    }

    private void validatePath(final String path) {
        Assert.state(!StringUtils.isBlank(path),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_IS_EMPTY));
    }

    private DataStorageFile createDataStorageFile(final BlobItem blob, final String path) {
        final String fileName = FilenameUtils.getName(blob.name());
        final String filePath = computeFilePath(path, fileName, blob);
        final DataStorageFile dataStorageFile = new DataStorageFile();
        dataStorageFile.setName(fileName);
        dataStorageFile.setPath(filePath);
        final Map<String, String> labels = new HashMap<>();
        if (blob.properties().accessTier() != null) {
            labels.put("StorageClass", blob.properties().accessTier().toString());
        }
        dataStorageFile.setLabels(labels);
        dataStorageFile.setTags(blob.metadata());
        dataStorageFile.setSize(blob.properties().contentLength());

        dataStorageFile.setChanged(dateFormat.format(Date.from(blob.properties().lastModified().toInstant())));
        return dataStorageFile;
    }

    private String computeFilePath(final String path, final String fileName, final BlobItem blob) {
        return StringUtils.isNotBlank(path) ? ProviderUtils.withTrailingDelimiter(path) + fileName : blob.name();
    }

    private DataStorageFile getDataStorageFile(final AzureBlobStorage storage, final String path) {
        return list(AbstractListingIterator.flat(getContainerURL(storage), path, null, 1))
                .findFirst()
                .filter(DataStorageFile.class::isInstance)
                .map(DataStorageFile.class::cast)
                .orElseThrow(() -> new DataStorageException(messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_AZURE_CREATE_FILE, storage.getPath())));
    }

    private DataStorageFolder getDataStorageFolder(final String folderFullPath, final String folderName) {
        final DataStorageFolder folder = new DataStorageFolder();
        folder.setName(folderName);
        final String relativePath = Optional.ofNullable(folderFullPath).orElse(folderName);
        folder.setPath(ProviderUtils.withoutTrailingDelimiter(relativePath));
        return folder;
    }

    private void copyBlobs(final AzureBlobStorage storage, final String sourceFolder, final String destinationFolder) {
        listFilesRecursively(storage, sourceFolder)
                .forEach(item -> {
                    final String relativePath = StringUtils.removeStart(item.getPath(), sourceFolder);
                    final String newPath = String.format("%s/%s", destinationFolder, relativePath);
                    copyBlob(storage, item.getPath(), newPath);
                });
    }

    private void copyBlob(final AzureBlobStorage storage, final String sourcePath, final String destinationPath) {
        final String sourceBlobUrl = String.format(BLOB_URL_FORMAT + "/%s/%s", azureRegion.getStorageAccount(),
                storage.getPath(), sourcePath);
        unwrap(getBlobUrl(storage, destinationPath).toPageBlobURL().startCopyFromURL(url(sourceBlobUrl)));
    }

    @SneakyThrows
    private URL url(final String blobUrl) {
        return new URL(blobUrl);
    }

    static <T> T unwrap(final Single<T> single, final Function<Integer, T> reviveFromErrorCode) {
        final Pair<T, Throwable> pair = single.map(AzureStorageHelper::success)
                .onErrorReturn(e ->
                        Optional.of(e)
                                .filter(StorageException.class::isInstance)
                                .map(StorageException.class::cast)
                                .map(StorageException::statusCode)
                                .map(reviveFromErrorCode)
                                .map(AzureStorageHelper::success)
                                .orElseGet(() -> failure(e)))
                .blockingGet();
        return pair.getLeft() != null ? pair.getLeft() : throwException(pair.getRight());
    }

    static <T> T unwrap(final Single<T> single) {
        final Pair<T, Throwable> pair = single
                .map(AzureStorageHelper::success)
                .onErrorReturn(AzureStorageHelper::failure)
                .blockingGet();
        return pair.getLeft() != null ? pair.getLeft() : throwException(pair.getRight());
    }

    private static <T> Pair<T, Throwable> success(final T t) {
        return Pair.of(t, null);
    }

    private static <T> Pair<T, Throwable> failure(final Throwable e) {
        return Pair.of(null, e);
    }

    private static <T> T throwException(final Throwable e) {
        log.debug("Exception occurred while calling Azure API.", e);
        if (e instanceof StorageException) {
            throw new DataStorageException(((StorageException) e).message(), e);
        } else if (e instanceof StorageErrorException) {
            throw new DataStorageException(((StorageErrorException) e).body().message(), e);
        } else {
            throw new DataStorageException(e.getMessage(), e);
        }
    }
}
