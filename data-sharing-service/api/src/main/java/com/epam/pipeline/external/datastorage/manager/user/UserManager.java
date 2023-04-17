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

import com.epam.pipeline.client.pipeline.CloudPipelineApiExecutor;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.external.datastorage.manager.CloudPipelineApiBuilder;
import com.epam.pipeline.external.datastorage.manager.auth.PipelineAuthManager;
import org.springframework.stereotype.Service;

@Service
public class UserManager {
    private final PipelineAuthManager authManager;
    private final CloudPipelineApiExecutor apiExecutor;
    private final PipelineUserClient userClient;

    public UserManager(final CloudPipelineApiBuilder builder,
                       final CloudPipelineApiExecutor apiExecutor,
                       final PipelineAuthManager pipelineAuthManager) {
        this.authManager = pipelineAuthManager;
        this.userClient = builder.getClient(PipelineUserClient.class);
        this.apiExecutor = apiExecutor;
    }

    public PipelineUser getCurrentUser() {
        return apiExecutor.execute(userClient.getCurrentUser(authManager.getHeader()));
    }
}
