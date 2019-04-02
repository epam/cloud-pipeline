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
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.cloud.gcp.GCPClient;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.CopyWriter;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageClass;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class GSBucketStorageHelper {
    private static final String EMPTY_PREFIX = "";
    private static final int REGION_ZONE_LENGTH = -2;
    private static final String TEXT_CONTENT_TYPE = "text/plain";
    private static final byte[] EMPTY_FILE_CONTENT = new byte[0];

    private final MessageHelper messageHelper;
    private final GCPRegion region;
    private final GCPClient gcpClient;

    public String createGoogleStorage(final GSBucketStorage storage) {
        final Storage client = gcpClient.buildStorageClient(region);
        final Bucket bucket = client.create(BucketInfo.newBuilder(storage.getPath())
                .setStorageClass(StorageClass.REGIONAL)
                .setLocation(trimRegionZone(region.getRegionCode()))
                .build());
        return bucket.getName();
    }

    public void deleteGoogleStorage(final String bucketName) {
        final Storage client = gcpClient.buildStorageClient(region);
        final Iterable<Blob> blobs = client.list(bucketName, Storage.BlobListOption.prefix(EMPTY_PREFIX)).iterateAll();
        blobs.forEach(blob -> blob.delete());
        deleteBucket(bucketName, client);
    }

    public DataStorageListing listItems(final GSBucketStorage storage, final String path, final Boolean showVersion,
                                        final Integer pageSize, final String marker) {
        final List<AbstractDataStorageItem> items = new ArrayList<>();

        String requestPath = Optional.ofNullable(path).orElse(EMPTY_PREFIX);
        if (StringUtils.isNotBlank(requestPath)) {
            requestPath = normalizeFolderPath(requestPath);
        }
        final Storage client = gcpClient.buildStorageClient(region);
        final String bucketName = storage.getPath();
        checkBucketExists(bucketName, client);

        final Page<Blob> blobs = client.list(bucketName,
                Storage.BlobListOption.currentDirectory(),
                Storage.BlobListOption.prefix(requestPath),
                Storage.BlobListOption.pageToken(Optional.ofNullable(marker).orElse(EMPTY_PREFIX)),
                Storage.BlobListOption.pageSize(Optional.ofNullable(pageSize).orElse(Integer.MAX_VALUE)));
        blobs.iterateAll().forEach(blob ->
            items.add(blob.isDirectory()
                    ? createDataStorageFolder(blob.getName())
                    : createDataStorageFile(blob))
        );
        return new DataStorageListing(blobs.getNextPageToken(), items);
    }

    public DataStorageFile createFile(final GSBucketStorage storage, final String path, final byte[] contents) {
        final Storage client = gcpClient.buildStorageClient(region);

        final String bucketName = storage.getPath();
        checkBucketExists(bucketName, client);
        checkBlobDoesNotExist(bucketName, path, client);

        final BlobId blobId = BlobId.of(bucketName, path);
        final BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(TEXT_CONTENT_TYPE).build();
        final Blob blob = client.create(blobInfo, contents);
        return createDataStorageFile(blob);
    }

    @SneakyThrows
    public DataStorageFile createFile(final GSBucketStorage storage, final String path,
                                      final InputStream dataStream) {
        return createFile(storage, path, IOUtils.toByteArray(dataStream));
    }

    public DataStorageFolder createFolder(final GSBucketStorage storage, final String path) {
        String folderPath = path.trim();
        folderPath = normalizeFolderPath(folderPath);
        final String tokenFilePath = folderPath + ProviderUtils.FOLDER_TOKEN_FILE;

        createFile(storage, tokenFilePath, EMPTY_FILE_CONTENT);
        return createDataStorageFolder(folderPath);
    }

    public void deleteFile(final GSBucketStorage dataStorage, final String path, final String version,
                           final Boolean totally) {
        Assert.isTrue(StringUtils.isNotBlank(path), messageHelper
                .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_IS_EMPTY));

        final Storage client = gcpClient.buildStorageClient(region);
        final String bucketName = dataStorage.getPath();
        checkBucketExists(bucketName, client);
        final Blob blob = checkBlobExists(bucketName, path, client);
        blob.delete();
    }

    public void deleteFolder(final GSBucketStorage dataStorage, final String path, final Boolean totally) {
        Assert.isTrue(StringUtils.isNotBlank(path), messageHelper
                .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_IS_EMPTY));

        final String folderPath = normalizeFolderPath(path);

        final Storage client = gcpClient.buildStorageClient(region);
        final Page<Blob> blobs = client.list(dataStorage.getPath(), Storage.BlobListOption.prefix(folderPath));
        blobs.iterateAll().forEach(Blob::delete);
    }

    public DataStorageFile moveFile(final GSBucketStorage storage, final String oldPath, final String newPath) {
        Assert.isTrue(StringUtils.isNotBlank(oldPath), messageHelper
                .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_IS_EMPTY));
        Assert.isTrue(StringUtils.isNotBlank(newPath), messageHelper
                .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_IS_EMPTY));

        final Storage client = gcpClient.buildStorageClient(region);
        final String bucketName = storage.getPath();
        checkBucketExists(bucketName, client);
        final Blob oldBlob = checkBlobExists(bucketName, oldPath, client);
        checkBlobDoesNotExist(bucketName, newPath, client);

        final CopyWriter copyWriter = client.get(bucketName).get(oldPath).copyTo(bucketName, newPath);
        final Blob newBlob = copyWriter.getResult();
        oldBlob.delete();

        return createDataStorageFile(newBlob);
    }

    public DataStorageFolder moveFolder(final GSBucketStorage storage, final String oldPath, final String newPath) {
        Assert.isTrue(StringUtils.isNotBlank(oldPath), messageHelper
                .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_IS_EMPTY));
        Assert.isTrue(StringUtils.isNotBlank(newPath), messageHelper
                .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_IS_EMPTY));

        final Storage client = gcpClient.buildStorageClient(region);
        final String bucketName = storage.getPath();
        checkBucketExists(bucketName, client);

        final String oldFolderPath = normalizeFolderPath(oldPath);
        final String newFolderPath = normalizeFolderPath(newPath);

        checkBlobExists(bucketName, oldFolderPath, client);
        checkBlobDoesNotExist(bucketName, newFolderPath, client);

        final Page<Blob> blobs = client.list(storage.getPath(), Storage.BlobListOption.prefix(oldFolderPath));
        blobs.iterateAll().forEach(oldBlob -> {
            final String oldBlobName = oldBlob.getName();
            final String newBlobName = newFolderPath + oldBlobName.substring(oldFolderPath.length());
            final CopyWriter copyWriter = oldBlob.copyTo(BlobId.of(bucketName, newBlobName));
            final Blob newBlob = copyWriter.getResult();
            Assert.notNull(newBlob, "Created blob should not be empty");
            oldBlob.delete();
        });

        return createDataStorageFolder(newFolderPath);
    }

    public boolean checkStorageExists(final String bucketName) {
        final Storage client = gcpClient.buildStorageClient(region);
        return Objects.nonNull(client.get(bucketName));
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
        final String[] parts = blob.getName().split(ProviderUtils.DELIMITER);
        final String fileName = parts[parts.length - 1];
        final DataStorageFile file = new DataStorageFile();
        file.setName(fileName);
        file.setPath(blob.getName());
        file.setSize(blob.getSize());
        return file;
    }

    private void checkBucketExists(final String bucketName, final Storage client) {
        Assert.notNull(client.get(bucketName), messageHelper.getMessage(
                MessageConstants.ERROR_DATASTORAGE_NOT_FOUND_BY_NAME, bucketName));
    }

    private Blob checkBlobExists(final String bucketName, final String blobPath, final Storage client) {
        final Blob blob = client.get(bucketName, blobPath);
        Assert.notNull(blob, messageHelper.getMessage(MessageConstants.ERROR_GCP_STORAGE_PATH_NOT_FOUND, blobPath,
                bucketName));
        return blob;
    }

    private void checkBlobDoesNotExist(final String bucketName, final String blobPath, final Storage client) {
        final Blob blob = client.get(bucketName, blobPath);
        Assert.isNull(blob, messageHelper.getMessage(MessageConstants.ERROR_GCP_STORAGE_PATH_ALREADY_EXISTS, blobPath,
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
}
