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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {
    private final TaskRepository taskRepository;

    @Override
    public TransferTask createTask(@NonNull StorageItem source,
                                   @NonNull StorageItem destination,
                                   List<String> included,
                                   String user) {
        TransferTask transferTask = TransferTask.builder()
                .source(source)
                .destination(destination)
                .status(TaskStatus.CREATED)
                .created(Utils.now())
                .reason("New transfer task created")
                .included(included)
                .user(user)
                .build();
        return taskRepository.save(transferTask);
    }

    @Override
    public TransferTask updateStatus(Long id, TaskStatus status) {
        return updateStatus(id, status, null);
    }

    @Override
    public TransferTask updateStatus(Long id, TaskStatus status, String reason) {
        TransferTask task = loadTask(id);
        if (task.getStatus() == status) {
            log.debug("Status is already set");
            return task;
        }
        buildTaskWithStatus(task, status);
        task.setReason(reason);
        return taskRepository.save(task);
    }

    @Override
    public TransferTask updateTask(final TransferTask transferTask) {
        TransferTask loaded = loadTask(transferTask.getId());
        if (loaded.getStatus() != transferTask.getStatus()) {
            buildTaskWithStatus(transferTask, transferTask.getStatus());
        }
        return taskRepository.save(transferTask);
    }

    @Override
    public void deleteTask(Long id) {
        loadTask(id);
        taskRepository.deleteById(id);
    }

    @Override
    public TransferTask loadTask(Long id) {
        Optional<TransferTask> task = taskRepository.findById(id);
        return task.orElseThrow(() -> new IllegalArgumentException("Failed to find task"));
    }

    @Override
    public List<TransferTask> loadRunningTasks() {
        return Utils.iterableToList(taskRepository.findAllByStatus(TaskStatus.RUNNING));
    }

    @Override
    public List<TransferTask> loadAll() {
        return Utils.iterableToList(taskRepository.findAll());
    }

    private TransferTask buildTaskWithStatus(TransferTask task, TaskStatus status) {
        task.setStatus(status);
        if (status.isFinalStatus()) {
            task.setFinished(Utils.now());
        } else if (status == TaskStatus.RUNNING) {
            task.setStarted(Utils.now());
        }
        return task;
    }
}
