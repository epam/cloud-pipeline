/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.external.datastorage.app.JsonMapper;
import com.epam.pipeline.external.datastorage.entity.datastorage.DataStorage;
import com.epam.pipeline.external.datastorage.entity.credentials.AbstractTemporaryCredentials;
import com.epam.pipeline.external.datastorage.entity.credentials.DataStorageAction;
import com.epam.pipeline.external.datastorage.entity.item.*;
import com.epam.pipeline.external.datastorage.manager.auth.PipelineAuthManager;
import com.epam.pipeline.external.datastorage.manager.QueryUtils;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class DataStorageManager {

    private final PipelineAuthManager pipelineAuthManager;
    private PipelineDataStorageClient storageClient;


    @Autowired
    public DataStorageManager(@Value("${pipeline.api.base.url}") String pipelineBaseUrl,
                              @Value("${pipeline.client.connect.timeout}") long connectTimeout,
                              @Value("${pipeline.client.read.timeout}") long readTimeout,
                              PipelineAuthManager pipelineAuthManager) {

        this.pipelineAuthManager = pipelineAuthManager;
        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .hostnameVerifier((s, sslSession) -> true)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(pipelineBaseUrl)
                .addConverterFactory(JacksonConverterFactory.create(new JsonMapper()))
                .client(client)
                .build();

        storageClient = retrofit.create(PipelineDataStorageClient.class);
    }

    public DataStorage loadStorage(long storageId) {
        return QueryUtils.execute(storageClient.getStorage(storageId, getToken()));
    }

    public List<AbstractDataStorageItem> listStorage(long storageId, String path, Boolean showVersion) {
        return QueryUtils.execute(storageClient.getStorageContent(storageId, path, showVersion, getToken()));
    }

    public DataStorageListing listStorage(long storageId, String path, Boolean showVersion,
                                          Integer pageSize, String marker) {
        return QueryUtils.execute(storageClient.getStorageContent(storageId, path, showVersion, pageSize, marker,
                getToken()));
    }

    public AbstractDataStorageItem getItemWithTags(long storageId, String path, Boolean showVersion) {
        return QueryUtils.execute(storageClient.getItemWithTags(storageId, path, showVersion, getToken()));
    }

    public Map<String, String> getItemTags(long storageId, String path, String version) {
        return QueryUtils.execute(storageClient.getItemTags(storageId, path, version, getToken()));
    }

    public Map<String, String> deleteItemTags(long storageId, String path, Set<String> tags, String version) {
        return QueryUtils.execute(storageClient.deleteItemTags(storageId, path, tags, version, getToken()));
    }

    public AbstractTemporaryCredentials generateCredentials(long storageId, List<DataStorageAction> operations) {
        Set<Long> notPermittedBuckets = operations.stream()
                .filter(dataStorageAction -> !dataStorageAction.getId().equals(storageId))
                .map(DataStorageAction::getId)
                .collect(Collectors.toSet());
        Assert.isTrue(notPermittedBuckets.size() == 0,
                "Operation with bucket: " + notPermittedBuckets.stream().map(String::valueOf)
                        .collect(Collectors.joining(",")));
        return QueryUtils.execute(storageClient.generateCredentials(operations,
                getToken()));
    }

    public Map<String, String> updateItemsTags(long storageId, String path, Map<String, String> tags,
                                               String version, Boolean rewrite) {
        return QueryUtils.execute(storageClient.updateItemTags(storageId, path, tags, version, rewrite,
                getToken()));
    }

    public List<AbstractDataStorageItem> updateDataStorageItems(long storageId, List<UpdateDataStorageItemVO> items) {
        return QueryUtils.execute(storageClient.updateItems(storageId, items, getToken()));
    }

    public DataStorageItemContent downloadItem(long storageId, String path, String version) {
        return QueryUtils.execute(storageClient.downloadItem(storageId, path, version, getToken()));
    }

    public Integer deleteDataStorageItems(long storageId, List<UpdateDataStorageItemVO> items, boolean totally) {
        return QueryUtils.execute(storageClient.deleteItems(storageId, totally, items, getToken()));
    }

    public DataStorageDownloadFileUrl generateDownloadUrl(long storageId, String path, String version) {
        return QueryUtils.execute(storageClient.generateDownloadUrl(storageId, path, version, getToken()));
    }

    public List<DataStorageDownloadFileUrl> generateDataStorageItemUrls(long storageId, GenerateDownloadUrlVO paths) {
        return QueryUtils.execute(storageClient.generateDownloadUrl(storageId, paths, getToken()));
    }

    public DataStorageDownloadFileUrl generateUploadUrl(long storageId, String path) {
        return QueryUtils.execute(storageClient.generateUploadUrl(storageId, path, getToken()));
    }

    private String getToken() {
        return "Bearer " + pipelineAuthManager.getToken();
    }
}
