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

package com.epam.pipeline.dts.remove.service.impl;

import com.epam.pipeline.dts.remove.model.RemoveTask;
import com.epam.pipeline.dts.remove.repository.RemoveTaskRepository;
import com.epam.pipeline.dts.remove.service.RemoveTaskService;
import com.epam.pipeline.dts.transfer.model.StorageItem;
import com.epam.pipeline.dts.transfer.model.TaskStatus;
import com.epam.pipeline.dts.util.Utils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class RemoveTaskServiceImpl implements RemoveTaskService {

    private final RemoveTaskRepository repository;

    @Override
    public RemoveTask create(@NonNull final StorageItem target,
                             final List<String> included,
                             final String user) {
        return repository.save(RemoveTask.builder()
                .target(target)
                .status(TaskStatus.CREATED)
                .created(Utils.now())
                .reason("New remove task created")
                .included(included)
                .user(user)
                .build());
    }

    @Override
    public RemoveTask load(final Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(String.format("Failed to find remove task #%s", id)));
    }

    @Override
    public List<RemoveTask> loadByStatus(final TaskStatus status) {
        return repository.findAllByStatus(status);
    }

    @Override
    public List<RemoveTask> loadAll() {
        return Utils.iterableToList(repository.findAll());
    }

    @Override
    public RemoveTask updateStatus(final Long id, final TaskStatus status, final String reason) {
        final RemoveTask task = load(id);
        if (task.getStatus() == status) {
            log.debug(String.format("Status %s is already set for remove task #%s", status, id));
            return task;
        }
        return repository.save(withStatus(task, status, reason));
    }

    @Override
    public void delete(final Long id) {
        repository.delete(load(id));
    }

    private RemoveTask withStatus(final RemoveTask task, final TaskStatus status, final String reason) {
        task.setStatus(status);
        if (StringUtils.isNotBlank(reason)) task.setReason(reason);
        if (status == TaskStatus.RUNNING) task.setStarted(Utils.now());
        if (status.isFinalStatus()) task.setFinished(Utils.now());
        return task;
    }
}
