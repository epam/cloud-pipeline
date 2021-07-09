/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.dts.transfer.model.AutonomousSyncRule;
import com.epam.pipeline.dts.transfer.model.StorageItem;
import com.epam.pipeline.dts.transfer.model.StorageType;
import com.epam.pipeline.dts.transfer.model.TransferTask;
import com.epam.pipeline.dts.transfer.model.pipeline.PipelineCredentials;
import com.epam.pipeline.dts.transfer.repository.TaskRepository;
import com.epam.pipeline.dts.transfer.service.TransferService;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@EnableScheduling
public class AutonomousSynchronizationService {

    private static final String SCHEMA_DELIMITER = "://";

    private final TaskRepository taskRepository;
    private final TransferService transferService;
    private final PipelineCredentials pipeCredentials;
    private final Set<AutonomousSyncRule> activeSyncRules;
    private final Map<AutonomousSyncRule, TransferTask> activeTransferTasks;

    @Autowired
    public AutonomousSynchronizationService(final @Value("${dts.local.pipe.api.url}") String pipeApiUrl,
                                            final @Value("${dts.local.pipe.api.token}") String pipeApiToken,
                                            final TransferService transferService,
                                            final TaskRepository taskRepository) {
        this.pipeCredentials = new PipelineCredentials(pipeApiUrl, pipeApiToken);
        this.taskRepository = taskRepository;
        this.transferService = transferService;
        this.activeSyncRules = ConcurrentHashMap.newKeySet();
        this.activeTransferTasks = new HashMap<>();
    }

    @Scheduled(cron = "${dts.autonomous.sync.cron}")
    public void synchronizeFilesystem() {
        processActiveTasks();
        submitTasksForAwaitingRules();
    }

    public void updateSyncRules(final List<AutonomousSyncRule> newSyncRules) {
        activeSyncRules.clear();
        activeSyncRules.addAll(newSyncRules);
    }

    private void processActiveTasks() {
        activeTransferTasks.values()
            .removeIf(task -> taskRepository.findById(task.getId())
                .filter(loadedTask -> loadedTask.getStatus().isFinalStatus())
                .isPresent());
    }

    private void submitTasksForAwaitingRules() {
        final Map<AutonomousSyncRule, TransferTask> newSubmittedTasks = activeSyncRules.stream()
            .filter(this::noMatchingActiveTransferTask)
            .map(rule -> Pair.of(rule, runTransferTask(rule)))
            .filter(entry -> Objects.nonNull(entry.getValue()))
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
        activeTransferTasks.putAll(newSubmittedTasks);
    }

    private boolean noMatchingActiveTransferTask(final AutonomousSyncRule rule) {
        return !activeTransferTasks.containsKey(rule);
    }

    private TransferTask runTransferTask(final AutonomousSyncRule rule) {
        final StorageItem sourceItem = new StorageItem();
        sourceItem.setPath(rule.getSource());
        sourceItem.setType(StorageType.LOCAL);
        final StorageItem destinationItem = new StorageItem();
        final String destinationPath = rule.getDestination();
        destinationItem.setType(parseTypeFromPath(destinationPath));
        destinationItem.setPath(destinationPath);
        try {
            destinationItem.setCredentials(getPipeCredentialsAsString());
            return transferService.runTransferTask(sourceItem, destinationItem, Collections.emptyList(), true);
        } catch (JsonProcessingException e) {
            log.warn("Error parsing PIPE credentials");
            return null;
        }
    }

    private StorageType parseTypeFromPath(final String path) {
        final String schema = path.split(SCHEMA_DELIMITER, 2)[0];
        return StorageType.valueOf(schema.toUpperCase());
    }

    private String getPipeCredentialsAsString() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        return mapper.writeValueAsString(pipeCredentials);
    }
}
