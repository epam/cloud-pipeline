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

import com.epam.pipeline.dts.common.service.CloudPipelineAPIClient;
import com.epam.pipeline.dts.security.service.SecurityService;
import com.epam.pipeline.dts.transfer.model.StorageItem;
import com.epam.pipeline.dts.transfer.model.StorageType;
import com.epam.pipeline.dts.transfer.model.TaskStatus;
import com.epam.pipeline.dts.transfer.model.TransferTask;
import com.epam.pipeline.dts.transfer.model.pipeline.PipelineCredentials;
import com.epam.pipeline.dts.transfer.service.DataUploaderProviderManager;
import com.epam.pipeline.dts.transfer.service.TaskService;
import com.epam.pipeline.dts.transfer.service.TransferService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Slf4j
public class ImpersonatingTransferServiceImpl implements TransferService {
    private final TaskService taskService;
    private final DataUploaderProviderManager dataUploaderProviderManager;
    private final SecurityService securityService;
    private final String dtsNameMetadataKey;

    @Override
    public TransferTask runTransferTask(@NonNull final StorageItem source,
                                        @NonNull final StorageItem destination,
                                        final List<String> included,
                                        final boolean deleteSource,
                                        boolean logEnabled,
                                        String pipeCmd,
                                        String pipeCmdSuffix) {
        final String impersonatingUser = getImpersonatingUser(source, destination);
        final TransferTask transferTask = taskService.createTask(source, destination, included, impersonatingUser,
                deleteSource);
        taskService.updateStatus(transferTask.getId(), TaskStatus.RUNNING);
        dataUploaderProviderManager.transferData(transferTask, logEnabled, pipeCmd, pipeCmdSuffix);
        return transferTask;
    }

    private String getImpersonatingUser(@NonNull final StorageItem source, @NonNull final StorageItem destination) {
        return Stream.of(source, destination)
                .filter(it -> it.getType() == StorageType.LOCAL)
                .map(StorageItem::getCredentials)
                .filter(Objects::nonNull)
                .map(PipelineCredentials::from)
                .findFirst()
                .filter(PipelineCredentials::isComplete)
                .map(CloudPipelineAPIClient::from)
                .flatMap(apiClient -> apiClient.getUserMetadataValueByKey(dtsNameMetadataKey))
                .orElseGet(securityService::getImpersonatingUser);
    }

    @Override
    public void failRunningTasks() {
        List<TransferTask> runningTasks = taskService.loadRunningTasks();
        if (CollectionUtils.isEmpty(runningTasks)) {
            return;
        }
        log.info(String.format("%s running tasks have been found. All of them will be failed.", runningTasks.size()));
        runningTasks.forEach(task -> taskService.updateStatus(task.getId(), TaskStatus.FAILURE,
                "Operation has been aborted due to server was stopped.")
        );
    }
}
