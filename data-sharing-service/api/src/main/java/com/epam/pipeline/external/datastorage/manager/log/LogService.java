/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.external.datastorage.manager.log;

import com.epam.pipeline.client.pipeline.CloudPipelineApiExecutor;
import com.epam.pipeline.entity.log.LogEntry;
import com.epam.pipeline.external.datastorage.manager.CloudPipelineApiBuilder;
import com.epam.pipeline.external.datastorage.manager.auth.PipelineAuthManager;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LogService {

    private final LogClient client;
    private final CloudPipelineApiExecutor executor;
    private final PipelineAuthManager authManager;

    public LogService(final CloudPipelineApiBuilder builder,
                      final CloudPipelineApiExecutor executor,
                      final PipelineAuthManager authManager) {
        this.client = builder.getClient(LogClient.class);
        this.executor = executor;
        this.authManager = authManager;
    }

    public void save(final List<LogEntry> entries) {
        executor.execute(client.save(authManager.getHeader(), entries));
    }
}
