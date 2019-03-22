/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dts.transfer.service;

import com.epam.pipeline.dts.transfer.model.TaskStatus;
import com.epam.pipeline.dts.transfer.model.TransferTask;
import java.time.LocalDateTime;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DataUploaderProviderManagerTest extends AbstractTransferTest {

    private final DataUploaderProvider dataUploaderProvider = mock(DataUploaderProvider.class);
    private final DataUploader dataUploader = mock(DataUploader.class);
    private final TaskService taskService = mock(TaskService.class);
    private final DataUploaderProviderManager manager =
        new DataUploaderProviderManager(dataUploaderProvider, taskService);
    private final TransferTask transferTask = getTransferTask();
    private final RuntimeException exception = new RuntimeException("error message");

    @BeforeEach
    void setUp() {
        when(dataUploaderProvider.getStorageUploader(transferTask))
            .thenReturn(dataUploader);
    }

    @Test
    void transferDataShouldSetSuccessTaskStatusAfterItFinishes() {
        manager.transferData(transferTask);

        verify(taskService).updateStatus(eq(transferTask.getId()), eq(TaskStatus.SUCCESS));
    }

    @Test
    void transferDataShouldSetFailureStatusAndFailureReasonIfItFails() {
        doThrow(exception).when(dataUploader).transfer(eq(transferTask));

        manager.transferData(transferTask);

        verify(taskService).updateStatus(eq(transferTask.getId()), eq(TaskStatus.FAILURE), eq(exception.getMessage()));
    }

    private TransferTask getTransferTask() {
        return TransferTask.builder()
            .id(1L)
            .status(TaskStatus.CREATED)
            .created(LocalDateTime.MIN)
            .source(localItem("/path/to/file"))
            .destination(localItem("/path/to/file"))
            .included(Collections.emptyList())
            .build();
    }
}
