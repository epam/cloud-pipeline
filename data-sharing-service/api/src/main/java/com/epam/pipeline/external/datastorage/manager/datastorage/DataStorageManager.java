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

package com.epam.pipeline.external.datastorage.manager.datastorage;

import com.epam.pipeline.client.pipeline.CloudPipelineApiExecutor;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.external.datastorage.manager.CloudPipelineApiBuilder;
import com.epam.pipeline.external.datastorage.manager.auth.PipelineAuthManager;
import com.epam.pipeline.vo.GenerateDownloadUrlVO;
import com.epam.pipeline.vo.data.storage.UpdateDataStorageItemVO;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DataStorageManager {
    private final PipelineAuthManager authManager;
    private final PipelineDataStorageClient storageClient;
    private final CloudPipelineApiExecutor apiExecutor;

    public DataStorageManager(final CloudPipelineApiBuilder builder,
                              final CloudPipelineApiExecutor apiExecutor,
                              final PipelineAuthManager pipelineAuthManager) {
        this.authManager = pipelineAuthManager;
        this.storageClient = builder.getClient(PipelineDataStorageClient.class);
        this.apiExecutor = apiExecutor;
    }

    public AbstractDataStorage loadStorage(long storageId) {
        return apiExecutor.execute(storageClient.getStorage(storageId, authManager.getHeader()));
    }

    public List<AbstractDataStorageItem> listStorage(long storageId, String path, Boolean showVersion) {
        return apiExecutor.execute(storageClient.getStorageContent(storageId, path, 
                showVersion, authManager.getHeader()));
    }

    public DataStorageListing listStorage(long storageId, String path, Boolean showVersion,
                                          Integer pageSize, String marker) {
        return apiExecutor.execute(storageClient.getStorageContent(storageId, path, showVersion, pageSize, marker,
                authManager.getHeader()));
    }

    public AbstractDataStorageItem getItemWithTags(long storageId, String path, Boolean showVersion) {
        return apiExecutor.execute(storageClient.getItemWithTags(storageId, path, showVersion, 
                authManager.getHeader()));
    }

    public Map<String, String> getItemTags(long storageId, String path, String version) {
        return apiExecutor.execute(storageClient.getItemTags(storageId, path, version, 
                authManager.getHeader()));
    }

    public Map<String, String> deleteItemTags(long storageId, String path, Set<String> tags, String version) {
        return apiExecutor.execute(storageClient.deleteItemTags(storageId, path, tags, version,
                authManager.getHeader()));
    }

    public TemporaryCredentials generateCredentials(long storageId, List<DataStorageAction> operations) {
        Set<Long> notPermittedBuckets = operations.stream()
                .filter(dataStorageAction -> !dataStorageAction.getId().equals(storageId))
                .map(DataStorageAction::getId)
                .collect(Collectors.toSet());
        Assert.isTrue(notPermittedBuckets.size() == 0,
                "Operation with bucket: " + notPermittedBuckets.stream().map(String::valueOf)
                        .collect(Collectors.joining(",")));
        return apiExecutor.execute(storageClient.generateCredentials(operations,
                authManager.getHeader()));
    }

    public Map<String, String> updateItemsTags(long storageId, String path, Map<String, String> tags,
                                               String version, Boolean rewrite) {
        return apiExecutor.execute(storageClient.updateItemTags(storageId, path, tags, version, rewrite,
                authManager.getHeader()));
    }

    public List<AbstractDataStorageItem> updateDataStorageItems(long storageId, List<UpdateDataStorageItemVO> items) {
        return apiExecutor.execute(storageClient.updateItems(storageId, items, authManager.getHeader()));
    }

    public DataStorageItemContent downloadItem(long storageId, String path, String version) {
        return apiExecutor.execute(storageClient.downloadItem(storageId, path, version, authManager.getHeader()));
    }

    public Integer deleteDataStorageItems(long storageId, List<UpdateDataStorageItemVO> items, boolean totally) {
        return apiExecutor.execute(storageClient.deleteItems(storageId, totally, items, authManager.getHeader()));
    }

    public DataStorageDownloadFileUrl generateDownloadUrl(long storageId, String path, String version) {
        return apiExecutor.execute(storageClient.generateDownloadUrl(
                storageId, path, version, authManager.getHeader()));
    }

    public List<DataStorageDownloadFileUrl> generateDataStorageItemUrls(long storageId, GenerateDownloadUrlVO paths) {
        return apiExecutor.execute(storageClient.generateDownloadUrl(storageId, paths, authManager.getHeader()));
    }

    public DataStorageDownloadFileUrl generateUploadUrl(long storageId, String path) {
        return apiExecutor.execute(storageClient.generateUploadUrl(storageId, path, authManager.getHeader()));
    }

    public DataStorageFile createDataStorageFile(final Long id,
                                                 final String path,
                                                 final String content) {
        return apiExecutor.execute(storageClient.createDataStorageFile(id, path, content, authManager.getHeader()));
    }

    public InputStream downloadFile(final Long id, final String path, final String version) {
        return apiExecutor.getResponseStream(storageClient.downloadFile(id, path, version, authManager.getHeader()));
    }
}
