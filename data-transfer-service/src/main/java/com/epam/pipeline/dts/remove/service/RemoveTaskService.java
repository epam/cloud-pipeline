/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dts.remove.service;

import com.epam.pipeline.dts.remove.model.RemoveTask;
import com.epam.pipeline.dts.transfer.model.StorageItem;
import com.epam.pipeline.dts.transfer.model.TaskStatus;

import java.util.Collections;
import java.util.List;

public interface RemoveTaskService {

    default RemoveTask create(StorageItem target) {
        return create(target, Collections.emptyList());
    }

    default RemoveTask create(StorageItem target, List<String> included) {
        return create(target, included, null);
    }

    RemoveTask create(StorageItem target, List<String> included, String user);

    default List<RemoveTask> loadCreated() {
        return loadByStatus(TaskStatus.CREATED);
    }

    RemoveTask load(Long id);
    List<RemoveTask> loadAll();
    List<RemoveTask> loadByStatus(TaskStatus status);

    default RemoveTask updateStatus(Long id, TaskStatus status) {
        return updateStatus(id, status, null);
    }

    RemoveTask updateStatus(Long id, TaskStatus status, String reason);

    void delete(Long id);
}
