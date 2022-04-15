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

import com.epam.pipeline.dts.transfer.model.StorageItem;
import com.epam.pipeline.dts.transfer.model.TaskStatus;
import com.epam.pipeline.dts.transfer.model.TransferTask;
import com.epam.pipeline.dts.transfer.repository.TaskRepository;
import com.epam.pipeline.dts.transfer.service.TaskService;
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
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;

    @Override
    public TransferTask create(@NonNull final StorageItem source,
                               @NonNull final StorageItem destination,
                               final List<String> included,
                               final String user) {
        return taskRepository.save(TransferTask.builder()
                .source(source)
                .destination(destination)
                .status(TaskStatus.CREATED)
                .created(Utils.now())
                .reason("New transfer task created")
                .included(included)
                .user(user)
                .build());
    }

    @Override
    public TransferTask load(final Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(String.format("Failed to find transfer task #%s", id)));
    }

    @Override
    public List<TransferTask> loadByStatus(final TaskStatus status) {
        return taskRepository.findAllByStatus(status);
    }

    @Override
    public List<TransferTask> loadAll() {
        return Utils.iterableToList(taskRepository.findAll());
    }

    @Override
    public TransferTask updateStatus(final Long id, final TaskStatus status, final String reason) {
        final TransferTask loaded = load(id);
        if (loaded.getStatus() == status) {
            log.debug(String.format("Status %s is already set for transfer task #%s", status, id));
            return loaded;
        }
        return taskRepository.save(withStatus(loaded, status, reason));
    }

    @Override
    public TransferTask update(final TransferTask task) {
        final TransferTask loaded = load(task.getId());
        return taskRepository.save(loaded.getStatus() != task.getStatus()
                ? withStatus(task, task.getStatus())
                : task);
    }

    @Override
    public void deleteTask(final Long id) {
        taskRepository.delete(load(id));
    }

    private TransferTask withStatus(final TransferTask task, final TaskStatus status) {
        return withStatus(task, status, null);
    }

    private TransferTask withStatus(final TransferTask task, final TaskStatus status, final String reason) {
        task.setStatus(status);
        if (StringUtils.isNotBlank(reason)) task.setReason(reason);
        if (status == TaskStatus.RUNNING) task.setStarted(Utils.now());
        if (status.isFinalStatus()) task.setFinished(Utils.now());
        return task;
    }
}
