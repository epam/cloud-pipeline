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

import com.epam.pipeline.dts.security.service.SecurityService;
import com.epam.pipeline.dts.transfer.model.StorageItem;
import com.epam.pipeline.dts.transfer.model.TaskStatus;
import com.epam.pipeline.dts.transfer.model.TransferTask;
import com.epam.pipeline.dts.transfer.service.DataUploaderProviderManager;
import com.epam.pipeline.dts.transfer.service.TaskService;
import com.epam.pipeline.dts.transfer.service.TransferService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Service
@ConditionalOnProperty(value = "dts.impersonation.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
@RequiredArgsConstructor
public class ImpersonatingTransferServiceImpl implements TransferService {
    private final TaskService taskService;
    private final DataUploaderProviderManager dataUploaderProviderManager;
    private final SecurityService securityService;

    @Override
    public TransferTask runTransferTask(@NonNull StorageItem source, @NonNull StorageItem destination,
                                        List<String> included) {
        String transferUser = securityService.getLocalUser();
        TransferTask transferTask = taskService.createTask(source, destination, included, transferUser);
        taskService.updateStatus(transferTask.getId(), TaskStatus.RUNNING);
        dataUploaderProviderManager.transferData(transferTask);
        return transferTask;
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
