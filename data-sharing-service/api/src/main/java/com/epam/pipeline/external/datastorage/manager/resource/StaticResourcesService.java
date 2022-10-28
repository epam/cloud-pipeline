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
import com.epam.pipeline.exception.ObjectNotFoundException;
import com.epam.pipeline.external.datastorage.exception.ResourceNotFoundException;
import com.epam.pipeline.external.datastorage.manager.CloudPipelineApiBuilder;
import com.epam.pipeline.external.datastorage.manager.auth.PipelineAuthManager;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
public class StaticResourcesService {

    private final PipelineAuthManager authManager;
    private final StaticResourcesClient client;
    private final CloudPipelineApiExecutor apiExecutor;

    public StaticResourcesService(final PipelineAuthManager authManager,
                                  final CloudPipelineApiBuilder builder,
                                  final CloudPipelineApiExecutor apiExecutor) {
        this.authManager = authManager;
        this.client = builder.getClient(StaticResourcesClient.class);
        this.apiExecutor = apiExecutor;
    }

    @SneakyThrows
    public InputStream getContent(final String path) {
        try {
            return apiExecutor.getResponseStream(client.getContent(
                    UriUtils.encodePath(path, StandardCharsets.UTF_8.displayName()), authManager.getHeader()));
        } catch (ObjectNotFoundException e) {
            throw new ResourceNotFoundException(String.format("Storage path '%s' does not exist.", path));
        }
    }
}
