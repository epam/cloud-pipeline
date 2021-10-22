/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.external.datastorage.manager.user;

import com.epam.pipeline.external.datastorage.entity.user.PipelineUser;
import com.epam.pipeline.external.datastorage.manager.CloudPipelineApiBuilder;
import com.epam.pipeline.external.datastorage.manager.QueryUtils;
import com.epam.pipeline.external.datastorage.manager.auth.PipelineAuthManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import retrofit2.Retrofit;

@Service
public class UserManager {
    private final PipelineAuthManager pipelineAuthManager;
    private final PipelineUserClient userClient;

    public UserManager(@Value("${pipeline.api.base.url}") final String pipelineBaseUrl,
                       @Value("${pipeline.client.connect.timeout}") final long connectTimeout,
                       @Value("${pipeline.client.read.timeout}") final long readTimeout,
                       final PipelineAuthManager pipelineAuthManager) {
        this.pipelineAuthManager = pipelineAuthManager;
        final Retrofit retrofit = new CloudPipelineApiBuilder(connectTimeout, readTimeout, pipelineBaseUrl)
                .buildClient();
        this.userClient = retrofit.create(PipelineUserClient.class);
    }

    public PipelineUser getCurrentUser() {
        return QueryUtils.execute(userClient.getCurrentUser(pipelineAuthManager.getHeader()));
    }
}
