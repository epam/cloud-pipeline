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

package com.epam.pipeline.manager.dts;

import com.epam.pipeline.controller.vo.dts.TransferTask;
import com.epam.pipeline.controller.vo.dts.TransferTaskFilter;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.entity.dts.TransferTaskEntity;
import com.epam.pipeline.mapper.dts.TransferTaskMapper;
import com.epam.pipeline.repository.dts.TaskRepository;
import com.epam.pipeline.utils.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferTaskService {
    private final TaskRepository taskRepository;
    private final TransferTaskMapper taskMapper;
    private final DtsRegistryManager registryManager;

    @Transactional
    public List<TransferTask> create(final String registryId, final List<TransferTask> transferTasks) {
        final DtsRegistry registry = registryManager.loadByNameOrId(registryId);
        final List<TransferTaskEntity> entities = new ArrayList<>();
        for (TransferTask task : transferTasks) {
            task.setRegistryId(registry.getId());
            TransferTaskEntity entity= taskMapper.transferTaskToEntity(task);
            entities.add(entity);
        }
        return CommonUtils.iterableToList(taskRepository.save(entities))
                .stream()
                .map(taskMapper::transferTaskToDto)
                .collect(Collectors.toList());
    }

    public void deleteTask(final Long id) {
        loadTask(id);
        taskRepository.delete(id);
    }

    public TransferTaskEntity loadTask(final Long id) {
        return taskRepository.findOne(id);
    }

    public List<TransferTask> loadAll() {
        return CommonUtils.iterableToList(taskRepository.findAll())
                .stream()
                .map(taskMapper::transferTaskToDto)
                .collect(Collectors.toList());
    }

    public Page<TransferTask> filter(final TransferTaskFilter filter) {
        final Pageable pageable = new PageRequest(filter.getPageNum(), filter.getPageSize());
        return taskRepository.filter(filter.getRegistryId(),
                filter.getCreatedFrom(),
                filter.getCreatedTo(),
                filter.getStartedFrom(),
                filter.getStartedTo(),
                filter.getFinishedFrom(),
                filter.getFinishedTo(),
                filter.getStatus(),
                pageable).map(taskMapper::transferTaskToDto);
    }
}
