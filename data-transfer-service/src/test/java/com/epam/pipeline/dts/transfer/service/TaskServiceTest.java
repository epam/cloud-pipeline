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

import com.epam.pipeline.dts.transfer.model.StorageItem;
import com.epam.pipeline.dts.transfer.model.TaskStatus;
import com.epam.pipeline.dts.transfer.model.TransferTask;
import com.epam.pipeline.dts.transfer.repository.TaskRepository;
import com.epam.pipeline.dts.transfer.service.impl.TaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TaskServiceTest extends AbstractTransferTest {

    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final TaskService taskService = new TaskServiceImpl(taskRepository);

    @BeforeEach
    void setUp() {
        when(taskRepository.save(any())).then(returnFirstArgument());
    }

    @Test
    void createTaskShouldReturnTransferTaskWithGivenSourceAndDestination() {
        final StorageItem source = s3Item();
        final StorageItem destination = nonExistingLocalItem();

        final TransferTask task = taskService.createTask(source, destination);

        assertThat(task.getSource(), is(source));
        assertThat(task.getDestination(), is(destination));
    }

    @Test
    void createTaskShouldSetTransferTaskStatusToCreated() {
        final TransferTask task = taskService.createTask(s3Item(), nonExistingLocalItem());

        assertThat(task.getStatus(), is(TaskStatus.CREATED));
    }

    @Test
    void createTaskShouldSetTransferTaskCreatedDate() {
        final TransferTask task = taskService.createTask(s3Item(), nonExistingLocalItem());

        assertNotNull(task.getCreated());
    }

    @Test
    void createTaskShouldSetTransferTaskIncludedListIfItWasPassed() {
        final List<String> included = Arrays.asList("a", "b", "c");

        final TransferTask task = taskService.createTask(s3Item(), nonExistingLocalItem(), included);

        assertThat(task.getIncluded(), is(included));
    }

    @Test
    void createTaskShouldSaveTaskInTaskRepository() {
        final StorageItem source = s3Item();
        final StorageItem destination = nonExistingLocalItem();

        taskService.createTask(source, destination);

        verify(taskRepository).save(argThat(hasSourceAndDestination(source, destination)));
    }

    @Test
    void updateStatusShouldFailIfTransferTaskDoesNotExist() {
        final TransferTask createdTask = TransferTask.builder()
            .id(1L)
            .status(TaskStatus.CREATED)
            .build();
        when(taskRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> taskService.updateStatus(createdTask.getId(), TaskStatus.SUCCESS));
    }

    @Test
    void updateStatusShouldNotSaveTaskIfStatusHasNotChanged() {
        final TransferTask createdTask = TransferTask.builder()
            .id(1L)
            .status(TaskStatus.CREATED)
            .build();
        when(taskRepository.findById(any())).thenReturn(Optional.of(createdTask));

        taskService.updateStatus(createdTask.getId(), TaskStatus.CREATED);

        verify(taskRepository, times(0)).save(any());
    }

    @Test
    void updateStatusShouldSaveTaskIfStatusHasChanged() {
        final TransferTask createdTask = TransferTask.builder()
            .id(1L)
            .status(TaskStatus.CREATED)
            .build();
        when(taskRepository.findById(any())).thenReturn(Optional.of(createdTask));

        taskService.updateStatus(createdTask.getId(), TaskStatus.SUCCESS);

        verify(taskRepository).save(any());
    }

    @Test
    void updateStatusShouldSetFinishedTimeIfUpdatedStatusIsFinal() {
        final TransferTask createdTask = TransferTask.builder()
            .id(1L)
            .status(TaskStatus.CREATED)
            .build();
        when(taskRepository.findById(any())).thenReturn(Optional.of(createdTask));

        taskService.updateStatus(createdTask.getId(), TaskStatus.SUCCESS);

        verify(taskRepository).save(argThat(task -> task.getFinished() != null));
    }

    @Test
    void updateStatusShouldSetStartedTimeIfUpdatedStatusIsRunning() {
        final TransferTask createdTask = TransferTask.builder()
            .id(1L)
            .status(TaskStatus.CREATED)
            .build();
        when(taskRepository.findById(any())).thenReturn(Optional.of(createdTask));

        taskService.updateStatus(createdTask.getId(), TaskStatus.RUNNING);

        verify(taskRepository).save(argThat(task -> task.getStarted() != null));
    }

    @Test
    void updateStatusShouldSetTransferTaskReasonIfSpecified() {
        final TransferTask createdTask = TransferTask.builder()
            .id(1L)
            .status(TaskStatus.CREATED)
            .build();
        when(taskRepository.findById(any())).thenReturn(Optional.of(createdTask));
        final String failureReason = "reason";

        taskService.updateStatus(createdTask.getId(), TaskStatus.FAILURE, failureReason);

        verify(taskRepository).save(argThat(task -> task.getReason().equals(failureReason)));
    }

    @Test
    void updateTaskShouldFailIfTransferTaskDoesNotExist() {
        final TransferTask task = TransferTask.builder()
            .id(1L)
            .build();
        when(taskRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> taskService.updateTask(task));
    }

    @Test
    void updateTaskShouldSaveTaskInTaskRepository() {
        final TransferTask task = TransferTask.builder()
            .id(new Random().nextLong())
            .build();
        final TransferTask updatedTask = task.withStatus(TaskStatus.FAILURE).withReason("Reason");
        when(taskRepository.findById(any())).thenReturn(Optional.of(updatedTask));

        taskService.updateTask(updatedTask);

        verify(taskRepository).save(eq(updatedTask));
    }

    @Test
    void deleteTaskShouldFailIfTransferTaskDoesNotExist() {
        final Long taskId = 1L;
        when(taskRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> taskService.deleteTask(taskId));
    }

    @Test
    void deleteTaskShouldDeleteTaskInTaskRepository() {
        final Long taskId = 1L;
        final TransferTask task = TransferTask.builder()
            .id(taskId)
            .build();
        when(taskRepository.findById(any())).thenReturn(Optional.of(task));

        taskService.deleteTask(taskId);

        verify(taskRepository).deleteById(eq(taskId));
    }

    @Test
    void loadShouldFailIfTransferTaskDoesNotExist() {
        final Long taskId = 1L;
        when(taskRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> taskService.loadTask(taskId));
    }

    @Test
    void loadTaskShouldGetTransferTaskFromTaskRepository() {
        final Long taskId = 1L;
        final TransferTask expectedTask = TransferTask.builder()
            .id(taskId)
            .build();
        when(taskRepository.findById(any())).thenReturn(Optional.of(expectedTask));

        final TransferTask actualTask = taskService.loadTask(taskId);

        assertThat(actualTask, is(expectedTask));
    }

    @Test
    void loadAllShouldReturnEmptyListIfNoTasksExistsInTaskRepository() {
        when(taskRepository.findAll()).thenReturn(Collections.emptyList());

        final List<TransferTask> transferTasks = taskService.loadAll();

        assertThat(transferTasks, empty());
    }

    @Test
    void loadAllShouldReturnAllTasksFromTaskRepository() {
        final List<TransferTask> expectedTasks = Arrays.asList(
            TransferTask.builder().id(1L).build(),
            TransferTask.builder().id(2L).build(),
            TransferTask.builder().id(3L).build()
        );
        when(taskRepository.findAll()).thenReturn(expectedTasks);

        final List<TransferTask> actualTasks = taskService.loadAll();

        assertThat(actualTasks, hasSize(expectedTasks.size()));
        assertThat(actualTasks, containsInAnyOrder(expectedTasks.toArray()));
    }

    @Test
    void loadRunningTasksShouldReturnEmptyListIfNoTasksExistsInTaskRepository() {
        when(taskRepository.findAllByStatus(eq(TaskStatus.RUNNING))).thenReturn(Collections.emptyList());

        final List<TransferTask> transferTasks = taskService.loadRunningTasks();

        assertThat(transferTasks, empty());
    }

    @Test
    void loadRunningTasksShouldReturnAllTasksFromTaskRepositoryWithRunningStatus() {
        final List<TransferTask> expectedTasks = Arrays.asList(
            TransferTask.builder().id(1L).build(),
            TransferTask.builder().id(2L).build(),
            TransferTask.builder().id(3L).build()
        );
        when(taskRepository.findAllByStatus(TaskStatus.RUNNING)).thenReturn(expectedTasks);

        final List<TransferTask> actualTasks = taskService.loadRunningTasks();

        assertThat(actualTasks, hasSize(expectedTasks.size()));
        assertThat(actualTasks, containsInAnyOrder(expectedTasks.toArray()));
    }

    private ArgumentMatcher<TransferTask> hasSourceAndDestination(final StorageItem source,
                                                                  final StorageItem destination) {
        return task -> task.getSource().equals(source) && task.getDestination().equals(destination);
    }

    private Answer<TransferTask> returnFirstArgument() {
        return invocation -> invocation.getArgument(0);
    }
}
