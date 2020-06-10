/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dts.transfer.service.impl;


import com.epam.pipeline.cmd.PipelineCLI;
import com.epam.pipeline.dts.transfer.model.StorageItem;
import com.epam.pipeline.dts.transfer.model.pipeline.PipelineCredentials;
import com.epam.pipeline.dts.transfer.service.PipelineCliProvider;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public abstract class AbstractPipeCliDataUploader extends AbstractDataUploader {

    private final PipelineCliProvider pipelineCliProvider;

    @Override
    public void upload(final StorageItem source,
                       final StorageItem destination,
                       final List<String> included,
                       final String username) {
        final PipelineCredentials credentials = PipelineCredentials.from(destination.getCredentials());
        final PipelineCLI pipelineCLI =
                pipelineCliProvider.getPipelineCLI(credentials.getApi(), credentials.getApiToken());
        pipelineCLI.uploadData(source.getPath(), destination.getPath(), included, username);
    }

    @Override
    public void download(final StorageItem source,
                         final StorageItem destination,
                         final List<String> included,
                         final String username) {
        final PipelineCredentials credentials = PipelineCredentials.from(source.getCredentials());
        final PipelineCLI pipelineCLI =
                pipelineCliProvider.getPipelineCLI(credentials.getApi(), credentials.getApiToken());
        pipelineCLI.downloadData(source.getPath(), destination.getPath(), included, username);
    }
}
