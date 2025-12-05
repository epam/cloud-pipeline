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

import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;

import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public interface AzureStorageHelper {
    Long URL_EXPIRATION = 24 * 60 * 60 * 1000L;
    String BLOB_URL_FORMAT = "https://%s.blob.core.windows.net";
    String DATALAKE_URL_FORMAT = "https://%s.dfs.core.windows.net";
    int RANGE_NOT_SATISFIABLE_STATUS_CODE = 416;
    Duration FALLBACK_EXPIRATION_DURATION = Duration.ofDays(1);
    int MAX_PAGE_SIZE = 5000;
    Duration AZURE_STORAGE_TIMEOUT = Duration.ofSeconds(30);
    byte[] EMPTY_CONTENT = new byte[0];
    String STORAGE_CLASS = "StorageClass";
    String FAILED_TO_GET_STORAGE_CREDENTIALS = "Failed to get Azure Datastorage credentials. " +
            "Storage account key or Managed Identity Client ID should be provided.";

    String createBlobStorage(AzureBlobStorage storage);

    String deleteStorage(AzureBlobStorage storage);

    Stream<DataStorageFile> listDataStorageFiles(AzureBlobStorage storage, String path);

    DataStorageListing getItems(AzureBlobStorage storage, String path, Integer pageSize, String continuationToken);

    Optional<DataStorageFile> findFile(AzureBlobStorage storage, String path);

    DataStorageFile createFile(AzureBlobStorage storage, String path, byte[] contents, String owner);

    DataStorageFile createFile(AzureBlobStorage storage, String path, InputStream dataStream, String owner);

    DataStorageFolder createFolder(AzureBlobStorage storage, String path);

    DataStorageFile moveFile(AzureBlobStorage storage, String oldPath, String newPath);

    DataStorageFolder moveFolder(AzureBlobStorage storage, String oldRawPath, String newRawPath);

    boolean checkStorage(AzureBlobStorage storage);

    Map<String, String> updateObjectTags(AzureBlobStorage storage, String path, Map<String, String> tags);

    Map<String, String> listObjectTags(AzureBlobStorage storage, String path);

    Map<String, String> deleteObjectTags(AzureBlobStorage storage, String path, Set<String> tagsToDelete);

    DataStorageItemContent getFile(AzureBlobStorage storage, String path, Long maxDownloadSize);

    DataStorageStreamingContent getStream(AzureBlobStorage storage, String path);

    DataStorageDownloadFileUrl generatePresignedUrl(AzureBlobStorage storage, String path,
                                                    String permission, boolean exist);

    DataStorageDownloadFileUrl generatePresignedUrl(AzureBlobStorage storage, String path, String permission);

    DataStorageDownloadFileUrl generateGenericPresignedUrl(AzureBlobStorage storage, String path,
                                                           String permission, Duration duration);

    void deleteFolder(AzureBlobStorage storage, String path);

    void deleteFile(AzureBlobStorage storage, String path);

    PathDescription getDataSize(AzureBlobStorage storage, String path, PathDescription pathDescription);

    String generateSASToken(AzureBlobStorage storage, List<DataStorageAction> actions, OffsetDateTime expiryTime);
}
