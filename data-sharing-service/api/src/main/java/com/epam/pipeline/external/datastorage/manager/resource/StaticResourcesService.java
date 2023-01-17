/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.external.datastorage.manager.resource;

import com.epam.pipeline.client.pipeline.CloudPipelineApiExecutor;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.exception.ObjectNotFoundException;
import com.epam.pipeline.external.datastorage.exception.InvalidPathException;
import com.epam.pipeline.external.datastorage.exception.ResourceNotFoundException;
import com.epam.pipeline.external.datastorage.manager.CloudPipelineApiBuilder;
import com.epam.pipeline.external.datastorage.manager.auth.PipelineAuthManager;
import com.epam.pipeline.external.datastorage.manager.datastorage.PipelineDataStorageClient;
import lombok.SneakyThrows;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;
import retrofit2.Response;

import java.nio.charset.StandardCharsets;

@Service
public class StaticResourcesService {

    public static final String DELIMITER = "/";
    private final PipelineAuthManager authManager;
    private final StaticResourcesClient client;
    private final PipelineDataStorageClient storageClient;
    private final CloudPipelineApiExecutor apiExecutor;

    public StaticResourcesService(final PipelineAuthManager authManager,
                                  final CloudPipelineApiBuilder builder,
                                  final CloudPipelineApiExecutor apiExecutor) {
        this.authManager = authManager;
        this.client = builder.getClient(StaticResourcesClient.class);
        this.storageClient = builder.getClient(PipelineDataStorageClient.class);
        this.apiExecutor = apiExecutor;
    }

    @SneakyThrows
    public Response<ResponseBody> getContent(final String path) {
        try {
            final String header = authManager.getHeader();
            if (!path.endsWith(DELIMITER)) {
                validateStorageItem(path, header);
            }
            return apiExecutor.getResponse(client.getContent(
                    UriUtils.encodePath(path, StandardCharsets.UTF_8.displayName()), header));
        } catch (ObjectNotFoundException e) {
            throw new ResourceNotFoundException(String.format("Storage path '%s' does not exist.", path));
        }
    }

    private void validateStorageItem(final String requestPath, final String header) {
        final AbstractDataStorage storage = apiExecutor.execute(storageClient.getStorage(requestPath, header));
        final String[] split = requestPath.split(DELIMITER, 2);
        final String path = split.length == 1 ? "" : split[1];
        final DataStorageItemType type = apiExecutor.execute(storageClient.getItemType(storage.getId(), path, header));
        if (type == DataStorageItemType.Folder && !requestPath.endsWith(DELIMITER)) {
            throw new InvalidPathException("Folder path shall end with slash.");
        }
    }
}
