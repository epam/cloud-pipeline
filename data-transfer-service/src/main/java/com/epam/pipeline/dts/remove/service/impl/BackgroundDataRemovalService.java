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
import com.epam.pipeline.dts.remove.service.RemoveTaskService;
import com.epam.pipeline.dts.transfer.model.StorageItem;
import com.epam.pipeline.dts.transfer.model.StorageType;
import com.epam.pipeline.dts.transfer.model.TaskStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackgroundDataRemovalService {

    private final RemoveTaskService taskService;

    @Scheduled(fixedDelayString = "${dts.remove.poll:60000}")
    public void remove() {
        taskService.loadCreated().stream()
                .filter(this::isReadyToFire)
                .forEach(this::remove);
    }

    private boolean isReadyToFire(final RemoveTask task) {
        return Optional.ofNullable(task.getScheduled())
                .map(DateUtils.nowUTC()::isAfter)
                .orElse(true);
    }

    private void remove(final RemoveTask task) {
        final StorageItem target = task.getTarget();
        try {
            log.info(String.format("Removing storage file/directory %s...", target.getPath()));
            taskService.updateStatus(task.getId(), TaskStatus.RUNNING);
            remove(target);
            log.info(String.format("Storage file/directory has been successfully removed %s.", target.getPath()));
            taskService.updateStatus(task.getId(), TaskStatus.SUCCESS);
        } catch (RuntimeException | IOException e) {
            log.error(String.format("Storage file/directory removal %s went bad due to: %s",
                    target.getPath(), e.getMessage()), e);
            taskService.updateStatus(task.getId(), TaskStatus.FAILURE, e.getMessage());
        }
    }

    private void remove(final StorageItem item) throws IOException {
        if (item.getType() == StorageType.LOCAL) {
            final Path path = Paths.get(item.getPath()).toAbsolutePath();
            if (Files.isDirectory(path)) {
                FileUtils.deleteDirectory(path.toFile());
            } else {
                Files.delete(path);
            }
        } else {
            throw new IllegalArgumentException(String.format("Remove storage item type %s is not yet supported.",
                    item.getType()));
        }
    }
}
