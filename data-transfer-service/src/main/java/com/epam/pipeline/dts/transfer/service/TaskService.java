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

package com.epam.pipeline.dts.transfer.service;

import com.epam.pipeline.dts.transfer.model.StorageItem;
import com.epam.pipeline.dts.transfer.model.TaskStatus;
import com.epam.pipeline.dts.transfer.model.TransferTask;

import java.util.Collections;
import java.util.List;

public interface TaskService {

    default TransferTask create(StorageItem source, StorageItem destination) {
        return create(source, destination, Collections.emptyList());
    }

    default TransferTask create(StorageItem source, StorageItem destination, List<String> included) {
        return create(source, destination, included, null);
    }

    TransferTask create(StorageItem source, StorageItem destination, List<String> included, String user);

    default List<TransferTask> loadRunning() {
        return loadByStatus(TaskStatus.RUNNING);
    }

    TransferTask load(Long id);
    List<TransferTask> loadByStatus(TaskStatus running);
    List<TransferTask> loadAll();

    default TransferTask updateStatus(Long id, TaskStatus status) {
        return updateStatus(id, status, null);
    }

    TransferTask updateStatus(Long id, TaskStatus status, String reason);
    TransferTask update(TransferTask transferTask);

    void deleteTask(Long id);
}
