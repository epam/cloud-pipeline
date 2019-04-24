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
import com.epam.pipeline.utils.FileContentUtils;
import com.microsoft.azure.storage.blob.BlobRange;
import com.microsoft.azure.storage.blob.BlockBlobURL;
import com.microsoft.azure.storage.blob.ContainerURL;
import com.microsoft.azure.storage.blob.IPRange;
import com.microsoft.azure.storage.blob.ListBlobsOptions;
import com.microsoft.azure.storage.blob.Metadata;
import com.microsoft.azure.storage.blob.PipelineOptions;
import com.microsoft.azure.storage.blob.SASProtocol;
import com.microsoft.azure.storage.blob.SASQueryParameters;
import com.microsoft.azure.storage.blob.ServiceSASSignatureValues;
import com.microsoft.azure.storage.blob.ServiceURL;
import com.microsoft.azure.storage.blob.SharedKeyCredentials;
import com.microsoft.azure.storage.blob.StorageException;
import com.microsoft.azure.storage.blob.StorageURL;
import com.microsoft.azure.storage.blob.models.BlobFlatListSegment;
import com.microsoft.azure.storage.blob.models.BlobHierarchyListSegment;
import com.microsoft.azure.storage.blob.models.BlobItem;
import com.microsoft.azure.storage.blob.models.BlobPrefix;
import com.microsoft.azure.storage.blob.models.ContainerListBlobFlatSegmentResponse;
import com.microsoft.azure.storage.blob.models.ContainerListBlobHierarchySegmentResponse;
import com.microsoft.azure.storage.blob.models.ListBlobsFlatSegmentResponse;
import com.microsoft.azure.storage.blob.models.ListBlobsHierarchySegmentResponse;
import com.microsoft.azure.storage.blob.models.StorageErrorException;
import com.microsoft.rest.v2.http.HttpPipelineLogLevel;
import com.microsoft.rest.v2.http.HttpPipelineLogger;
import com.microsoft.rest.v2.util.FlowableUtil;
import io.reactivex.Flowable;
import io.reactivex.Single;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TimeZone;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Util class providing methods to interact with Azure Storage API.
 */
@Slf4j
public class AzureStorageHelper {

    private static final Long URL_EXPIRATION = 24 * 60 * 60 * 1000L;
    private static final String BLOB_URL_FORMAT = "https://%s.blob.core.windows.net";
    private static final int NOT_FOUND_STATUS_CODE = 404;
    private static final int RANGE_NOT_SATISFIABLE_STATUS_CODE = 416;
    private static final int MAX_PAGE_SIZE = 5000;

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
        final ContainerURL containerURL = getContainerURL(storage);
        final ListBlobsOptions options = new ListBlobsOptions().withMaxResults(pageSize).withPrefix(prefix);
        final List<AbstractDataStorageItem> items = new ArrayList<>();
        final String nextPageMarker = unwrap(
                containerURL.listBlobsHierarchySegment(marker, ProviderUtils.DELIMITER, options)
                        .flatMap(r -> listAllBlobs(containerURL, r, items, pageSize, prefix))
                        .map(r -> Optional.ofNullable(r.body().nextMarker()).orElse("")));
        return new DataStorageListing(nextPageMarker, items);
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
        validateDirectory(dataStorage, folderPath, false);
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
        final String blobUrl = String.format(BLOB_URL_FORMAT + "/%s/%s", azureRegion.getStorageAccount(),
                dataStorage.getPath(), oldPath);
        createFile(dataStorage, newPath, new byte[0], null);
        unwrap(getBlobUrl(dataStorage, newPath).toPageBlobURL().startCopyFromURL(url(blobUrl)));
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
        copyFile(dataStorage, oldPath, folder.getPath());
        copyItem(dataStorage, oldPath, folder.getPath());
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
        listFiles(dataStorage, path).forEach(item -> deleteBlob(dataStorage, item.getPath()));
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

    private Stream<AbstractDataStorageItem> listFiles(final AzureBlobStorage dataStorage, final String path) {
        return listItems(dataStorage, path)
                .filter(it -> it.getType() == DataStorageItemType.File);
    }

    private Stream<AbstractDataStorageItem> listFolders(final AzureBlobStorage dataStorage, final String path) {
        return listItems(dataStorage, path)
                .filter(it -> it.getType() == DataStorageItemType.Folder);
    }

    private Stream<AbstractDataStorageItem> listItems(final AzureBlobStorage dataStorage, final String path) {
        return items(dataStorage, path)
                .map(response -> Optional.of(response.body())
                        .map(ListBlobsHierarchySegmentResponse::segment)
                        .map(segment -> {
                            final Stream<AbstractDataStorageItem> prefixes = Optional.ofNullable(segment.blobPrefixes())
                                    .orElseGet(Collections::emptyList)
                                    .stream()
                                    .map(blobPrefix -> getDataStorageFolder(blobPrefix.name(), folderName(blobPrefix)));
                            final Stream<AbstractDataStorageItem> blobs = Optional.ofNullable(segment.blobItems())
                                    .orElseGet(Collections::emptyList)
                                    .stream()
                                    .filter(blob -> !Objects.equals(blob.name(), response.body().prefix()))
                                    .map(blob -> createDataStorageFile(blob, response.body().prefix()));
                            return Stream.concat(prefixes, blobs);
                        }))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .flatMap(Function.identity());
    }

    private Stream<ContainerListBlobHierarchySegmentResponse> items(final AzureBlobStorage dataStorage,
                                                                    final String path) {
        final HierarchyListingIterator iterator = new HierarchyListingIterator(dataStorage, path);
        final Spliterator<ContainerListBlobHierarchySegmentResponse> spliterator =
                Spliterators.spliteratorUnknownSize(iterator, 0);
        return StreamSupport.stream(spliterator, false);
    }

    private String folderName(final BlobPrefix blobPrefix) {
        final String[] parts = blobPrefix.name().split(ProviderUtils.DELIMITER);
        return parts[parts.length - 1];
    }

    private Single<ContainerListBlobHierarchySegmentResponse> listAllBlobs(
            final ContainerURL containerURL,
            final ContainerListBlobHierarchySegmentResponse response,
            final List<AbstractDataStorageItem> items,
            final Integer pageSize,
            final String prefixPath) {
        if (response.body().segment() == null) {
            return Single.just(response);
        }
        if (response.body().segment().blobPrefixes() != null) {
            for (final BlobPrefix blobPrefix : response.body().segment().blobPrefixes()) {
                final String[] parts = blobPrefix.name().split(ProviderUtils.DELIMITER);
                final String folderName = parts[parts.length - 1];
                items.add(getDataStorageFolder(blobPrefix.name(), folderName));
            }
        }
        final List<BlobItem> blobItems = response.body().segment().blobItems();
        if (blobItems != null) {
            for (final BlobItem blob : blobItems) {
                if (Objects.equals(blob.name(), response.body().prefix()) ||
                        StringUtils.endsWithIgnoreCase(blob.name(), ProviderUtils.FOLDER_TOKEN_FILE.toLowerCase())) {
                    continue;
                }
                items.add(createDataStorageFile(blob, response.body().prefix()));
            }
            if (pageSize == null || items.size() == pageSize) {
                return Single.just(response);
            }
        }
        if (response.body().nextMarker() == null) {
            return Single.just(response);
        } else {
            final String nextMarker = response.body().nextMarker();
            final int remainingItems = items == null ? pageSize : pageSize - items.size();
            return containerURL
                    .listBlobsHierarchySegment(nextMarker, ProviderUtils.DELIMITER,
                            new ListBlobsOptions().withPrefix(prefixPath).withMaxResults(remainingItems))
                    .flatMap(containersListBlobFlatSegmentResponse ->
                            listAllBlobs(containerURL, containersListBlobFlatSegmentResponse, items, pageSize,
                                    prefixPath));
        }
    }

    private void validateDirectory(final AzureBlobStorage storage, final String path, final boolean exist) {
        validatePath(storage, path, exist, this::directoryExists);
    }

    private void validateBlob(final AzureBlobStorage storage, final String path, final boolean exist) {
        validatePath(storage, path, exist, this::blobExists);
    }

    private void validatePath(final AzureBlobStorage storage, final String path, final boolean exist,
                              final BiPredicate<AzureBlobStorage, String> existenceCheck) {
        validatePath(path);
        if (exist) {
            Assert.state(existenceCheck.test(storage, path),
                    messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND,
                            path, storage.getPath()));
        } else {
            Assert.state(!existenceCheck.test(storage, path),
                    messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_ALREADY_EXISTS,
                            path, storage.getPath()));
        }
    }

    private boolean directoryExists(final AzureBlobStorage dataStorage, final String path) {
        final String pathWithoutSeparator = ProviderUtils.withoutTrailingDelimiter(path);
        final String pathWithSeparator = pathWithoutSeparator + ProviderUtils.DELIMITER;
        final ListBlobsOptions options = new ListBlobsOptions()
                .withMaxResults(MAX_PAGE_SIZE)
                .withPrefix(pathWithoutSeparator);
        String marker = null;
        while (true) {
            final ContainerListBlobHierarchySegmentResponse response = unwrap(
                    getContainerURL(dataStorage).listBlobsHierarchySegment(marker, ProviderUtils.DELIMITER, options));
            final Optional<ListBlobsHierarchySegmentResponse> body = Optional.ofNullable(response.body());
            final boolean directoryFound = body
                    .map(ListBlobsHierarchySegmentResponse::segment)
                    .map(BlobHierarchyListSegment::blobPrefixes)
                    .map(List::stream)
                    .orElseGet(Stream::empty)
                    .anyMatch(it -> it.name().equals(pathWithSeparator));
            marker = body.map(ListBlobsHierarchySegmentResponse::nextMarker).orElse(null);
            if (directoryFound || marker == null) {
                return directoryFound;
            }
        }
    }

    private boolean blobExists(final AzureBlobStorage dataStorage, final String path) {
        final ListBlobsOptions listOptions = new ListBlobsOptions().withMaxResults(1).withPrefix(path);
        final ContainerListBlobFlatSegmentResponse response = unwrap(
                getContainerURL(dataStorage).listBlobsFlatSegment(null, listOptions));
        return Optional.ofNullable(response.body())
                .map(ListBlobsFlatSegmentResponse::segment)
                .map(BlobFlatListSegment::blobItems)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .anyMatch(it -> it.name().equals(path));
    }

    private void validatePath(final String path) {
        Assert.state(!StringUtils.isBlank(path),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_IS_EMPTY));
    }

    private DataStorageFile createDataStorageFile(final BlobItem blob, final String path) {
        final String fileName = StringUtils.isBlank(path) ? blob.name() : FilenameUtils.getName(blob.name());
        final DataStorageFile dataStorageFile = new DataStorageFile();
        dataStorageFile.setName(fileName);
        dataStorageFile.setPath(computeFilePath(fileName, path));
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

    private String computeFilePath(final String fileName, final String path) {
        return StringUtils.isNotBlank(path) ? ProviderUtils.withTrailingDelimiter(path) + fileName : fileName;
    }

    private DataStorageFile getDataStorageFile(final AzureBlobStorage storage, final String path) {
        final ListBlobsOptions listOptions = new ListBlobsOptions().withMaxResults(1).withPrefix(path);
        final ContainerListBlobFlatSegmentResponse response = unwrap(
                getContainerURL(storage).listBlobsFlatSegment(null, listOptions));
        if (response.body().segment() == null || CollectionUtils.isEmpty(response.body().segment().blobItems())) {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_AZURE_CREATE_FILE, storage.getPath()));
        }
        return createDataStorageFile(response.body().segment().blobItems().get(0),
                FilenameUtils.getPath(response.body().prefix()));
    }

    private DataStorageFolder getDataStorageFolder(final String folderFullPath, final String folderName) {
        final DataStorageFolder folder = new DataStorageFolder();
        folder.setName(folderName);
        final String relativePath = Optional.ofNullable(folderFullPath).orElse(folderName);
        folder.setPath(ProviderUtils.withoutTrailingDelimiter(relativePath));
        return folder;
    }

    private void copyItem(final AzureBlobStorage dataStorage, final String oldPath, final String newPath) {
        listFolders(dataStorage, oldPath)
                .filter(item -> item.getType() == DataStorageItemType.Folder)
                .forEach(item -> {
                    final String path = String.format("%s/%s", newPath, item.getName());
                    final DataStorageFolder folder = createFolder(dataStorage, path);
                    copyFile(dataStorage, item.getPath(), folder.getPath());
                    copyItem(dataStorage, item.getPath(), folder.getPath());
                });
    }

    private void copyFile(final AzureBlobStorage storage, final String oldPath, final String newPath) {
        listFiles(storage, oldPath)
                .forEach(item -> {
                    final String blobUrl = String.format(BLOB_URL_FORMAT + "/%s/%s%s", azureRegion.getStorageAccount(),
                            storage.getPath(), ProviderUtils.withTrailingDelimiter(oldPath), item.getName());
                    final String destinationPath = ProviderUtils.withTrailingDelimiter(newPath) + item.getName();
                    unwrap(getBlobUrl(storage, destinationPath).toPageBlobURL().startCopyFromURL(url(blobUrl)));
                });
    }

    @SneakyThrows
    private URL url(final String blobUrl) {
        return new URL(blobUrl);
    }

    private <T> T unwrap(final Single<T> single, final Function<Integer, T> reviveFromErrorCode) {
        final Pair<T, Throwable> pair = single.map(this::success)
                .onErrorReturn(e ->
                        Optional.of(e)
                                .filter(StorageException.class::isInstance)
                                .map(StorageException.class::cast)
                                .map(StorageException::statusCode)
                                .map(reviveFromErrorCode)
                                .map(this::success)
                                .orElseGet(() -> failure(e)))
                .blockingGet();
        return pair.getLeft() != null ? pair.getLeft() : throwException(pair.getRight());
    }

    private <T> T unwrap(final Single<T> single) {
        final Pair<T, Throwable> pair = single.map(this::success).onErrorReturn(this::failure).blockingGet();
        return pair.getLeft() != null ? pair.getLeft() : throwException(pair.getRight());
    }

    private <T> Pair<T, Throwable> success(final T t) {
        return Pair.of(t, null);
    }

    private <T> Pair<T, Throwable> failure(final Throwable e) {
        return Pair.of(null, e);
    }

    private <T> T throwException(final Throwable e) {
        log.debug("Exception occurred while calling Azure API.", e);
        if (e instanceof StorageException) {
            throw new DataStorageException(((StorageException) e).message(), e);
        } else if (e instanceof StorageErrorException) {
            throw new DataStorageException(((StorageErrorException) e).body().message(), e);
        } else {
            throw new DataStorageException(e.getMessage(), e);
        }
    }

    @RequiredArgsConstructor
    private class HierarchyListingIterator implements Iterator<ContainerListBlobHierarchySegmentResponse> {

        private final AzureBlobStorage dataStorage;
        private final String path;

        private ContainerListBlobHierarchySegmentResponse response;

        @Override
        public boolean hasNext() {
            return response == null || response.body().nextMarker() != null;
        }

        @Override
        public ContainerListBlobHierarchySegmentResponse next() {
            return response = loadNextResponse();
        }

        private ContainerListBlobHierarchySegmentResponse loadNextResponse() {
            final String nextMarker = Optional.ofNullable(response)
                    .map(ContainerListBlobHierarchySegmentResponse::body)
                    .map(ListBlobsHierarchySegmentResponse::nextMarker)
                    .orElse(null);
            final ListBlobsOptions options = new ListBlobsOptions().withPrefix(path).withMaxResults(MAX_PAGE_SIZE);
            return unwrap(getContainerURL(dataStorage)
                    .listBlobsHierarchySegment(nextMarker, ProviderUtils.DELIMITER, options));
        }
    }
}
