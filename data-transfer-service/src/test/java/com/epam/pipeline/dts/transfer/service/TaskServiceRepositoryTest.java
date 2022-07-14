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

package com.epam.pipeline.dts.transfer.service;

import com.epam.pipeline.dts.transfer.model.StorageItem;
import com.epam.pipeline.dts.transfer.model.TransferTask;
import com.epam.pipeline.dts.transfer.repository.TaskRepository;
import com.epam.pipeline.dts.util.Utils;
import com.epam.pipeline.entity.dts.transfer.StorageType;
import com.epam.pipeline.entity.dts.transfer.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@DataJpaTest
@TestPropertySource(value={"classpath:test-application.properties"})
public class TaskServiceRepositoryTest extends AbstractTransferTest {

    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private TaskService taskService;

    @Test
    void shouldGetLastUpdatedTasks() {
        final TransferTask transferTaskCreated = TransferTask.builder()
                .source(new StorageItem(StorageType.LOCAL, "source", ""))
                .destination(new StorageItem(StorageType.LOCAL, "dest", ""))
                .created(LocalDateTime.now())
                .status(TaskStatus.CREATED)
                .created(Utils.now())
                .build();
        final TransferTask transferTaskStarted = TransferTask.builder()
                .source(new StorageItem(StorageType.LOCAL, "source", ""))
                .destination(new StorageItem(StorageType.LOCAL, "dest", ""))
                .started(LocalDateTime.now())
                .status(TaskStatus.CREATED)
                .created(Utils.now())
                .build();
        final TransferTask transferTaskFinished = TransferTask.builder()
                .source(new StorageItem(StorageType.LOCAL, "source", ""))
                .destination(new StorageItem(StorageType.LOCAL, "dest", ""))
                .finished(LocalDateTime.now())
                .status(TaskStatus.CREATED)
                .created(Utils.now())
                .build();
        taskRepository.save(transferTaskCreated);
        taskRepository.save(transferTaskStarted);
        taskRepository.save(transferTaskFinished);
        final List<TransferTask> transferTasks = taskService.loadUpdated(LocalDateTime.now().minusDays(1));
        assertEquals(3, transferTasks.size());
    }
}
