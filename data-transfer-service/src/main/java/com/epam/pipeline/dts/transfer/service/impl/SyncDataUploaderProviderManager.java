/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.dts.transfer.model.TaskStatus;
import com.epam.pipeline.dts.transfer.model.TransferTask;
import com.epam.pipeline.dts.transfer.service.DataUploaderProvider;
import com.epam.pipeline.dts.transfer.service.DataUploaderProviderManager;
import com.epam.pipeline.dts.transfer.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncDataUploaderProviderManager implements DataUploaderProviderManager {

    private final DataUploaderProvider dataUploaderProvider;
    private final TaskService taskService;

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void transferData(final TransferTask transferTask, boolean logEnabled,
                             String pipeCmd, String pipeCmdSuffix) {
        try {
            dataUploaderProvider.getStorageUploader(transferTask).transfer(transferTask, logEnabled,
                    pipeCmd, pipeCmdSuffix);
            log.info(String.format("File has been successfully transferred from %s to %s.",
                    transferTask.getSource().getPath(), transferTask.getDestination().getPath()));
            taskService.updateStatus(transferTask.getId(), TaskStatus.SUCCESS);
        } catch (Exception e) {
            taskService.updateStatus(transferTask.getId(), TaskStatus.FAILURE, e.getMessage());
            log.error(String.format("Transfer data went bad due to: %s", e.getMessage()), e);
        }
    }
}
