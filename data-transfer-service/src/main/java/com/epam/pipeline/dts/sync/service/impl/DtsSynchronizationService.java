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

package com.epam.pipeline.dts.sync.service.impl;

import com.epam.pipeline.dts.common.service.CloudPipelineAPIClient;
import com.epam.pipeline.dts.sync.model.AutonomousSyncCronDetails;
import com.epam.pipeline.dts.sync.service.PreferenceService;
import com.epam.pipeline.dts.sync.service.ShutdownService;
import com.epam.pipeline.dts.sync.model.AutonomousSyncRule;
import com.epam.pipeline.dts.transfer.model.StorageItem;
import com.epam.pipeline.dts.transfer.model.StorageType;
import com.epam.pipeline.dts.transfer.model.TransferTask;
import com.epam.pipeline.dts.transfer.model.pipeline.PipelineCredentials;
import com.epam.pipeline.dts.transfer.repository.TaskRepository;
import com.epam.pipeline.dts.transfer.service.TransferService;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronSequenceGenerator;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Service
@Slf4j
@EnableScheduling
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class DtsSynchronizationService {

    private static final String SCHEMA_DELIMITER = "://";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final TaskRepository taskRepository;
    private final TransferService transferService;
    private final PreferenceService preferenceService;
    private final ShutdownService shutdownService;
    private final PipelineCredentials pipeCredentials;
    private final DtsRuleExpanderService dtsRuleExpander;
    private final Map<AutonomousSyncRule, AutonomousSyncCronDetails> activeSyncRules;
    private final Map<AutonomousSyncRule, TransferTask> activeTransferTasks;
    private final String defaultCronExpression;
    private final String syncToken;
    private final CloudPipelineAPIClient apiClient;
    private final IlluminaValidator illuminaValidator;

    @Autowired
    public DtsSynchronizationService(final @Value("${dts.api.url}") String pipeApiUrl,
                                     final @Value("${dts.api.token}") String pipeApiToken,
                                     final @Value("${dts.autonomous.sync.cron}") String defaultCronExpression,
                                     final @Value("${dts.sync.token.name:dts-sync-complete}") String syncToken,
                                     final TransferService autonomousTransferService,
                                     final TaskRepository taskRepository,
                                     final PreferenceService preferenceService,
                                     final ShutdownService shutdownService,
                                     final DtsRuleExpanderService dtsRuleExpander,
                                     final CloudPipelineAPIClient apiClient,
                                     final IlluminaValidator illuminaValidator) {
        this.apiClient = apiClient;
        this.pipeCredentials = new PipelineCredentials(pipeApiUrl, pipeApiToken);
        this.taskRepository = taskRepository;
        this.transferService = autonomousTransferService;
        this.shutdownService = shutdownService;
        this.preferenceService = preferenceService;
        this.activeSyncRules = new ConcurrentHashMap<>();
        this.activeTransferTasks = new ConcurrentHashMap<>();
        this.dtsRuleExpander = dtsRuleExpander;
        this.illuminaValidator = illuminaValidator;
        this.syncToken = syncToken;
        this.defaultCronExpression = Optional.of(defaultCronExpression)
            .filter(CronSequenceGenerator::isValidExpression)
            .orElseThrow(() -> new IllegalStateException("Default FS sync cron is invalid!"));
    }

    @Scheduled(fixedDelayString = "${dts.sync.poll:60000}")
    public void synchronizeDtsState() {
        checkForShutdown();
        updateSyncRules();
        synchronizeFilesystem();
    }

    private void synchronizeFilesystem() {
        processActiveTasks();
        submitTasksForAwaitingRules();
    }

    private void checkForShutdown() {
        if (preferenceService.isShutdownRequired()) {
            shutdownService.shutdown();
        }
    }

    private void updateSyncRules() {
        preferenceService.getSyncRules().ifPresent(newSyncRules -> {
            final Map<AutonomousSyncRule, AutonomousSyncCronDetails> rulesToUpdate = newSyncRules.stream()
                .collect(Collectors.toMap(this::mapToRuleWithoutCron, this::mapRuleToCronDetails));
            activeSyncRules.putAll(rulesToUpdate);
            final List<AutonomousSyncRule> newRulesWithoutCron = newSyncRules.stream()
                .map(this::mapToRuleWithoutCron)
                .collect(Collectors.toList());
            activeSyncRules.keySet().removeIf(rule -> !newRulesWithoutCron.contains(rule));
        });
    }

    private AutonomousSyncRule mapToRuleWithoutCron(final AutonomousSyncRule rule) {
        return new AutonomousSyncRule(rule.getSource(), rule.getDestination(), null,
                rule.getDeleteSource(), ListUtils.emptyIfNull(rule.getTransferTriggers()),
                rule.getCheckSyncToken(), rule.getCheckIllumina());
    }

    private AutonomousSyncCronDetails mapRuleToCronDetails(final AutonomousSyncRule newRule) {
        final String newExpression = getCronOrDefault(newRule);
        return activeSyncRules.entrySet().stream()
            .filter(entry -> newRule.isSameSyncPaths(entry.getKey()))
            .findAny()
            .map(Map.Entry::getValue)
            .map(existingRule -> updateCronExpressionIfRequired(newRule.getCron(), existingRule))
            .orElseGet(() -> new AutonomousSyncCronDetails(newExpression, getCurrentDate()));
    }

    private String getCronOrDefault(final AutonomousSyncRule newRule) {
        return Optional.ofNullable(newRule.getCron())
            .filter(CronSequenceGenerator::isValidExpression)
            .orElse(defaultCronExpression);
    }

    private AutonomousSyncCronDetails updateCronExpressionIfRequired(final String newExpression,
                                                                     final AutonomousSyncCronDetails existingRule) {
        return Optional.ofNullable(newExpression)
            .filter(expression -> !expression.equals(existingRule.getExpression()))
            .map(expression -> new AutonomousSyncCronDetails(expression, existingRule.getLastExecution()))
            .orElse(existingRule);
    }

    private void processActiveTasks() {
        final List<AutonomousSyncRule> completed = activeTransferTasks
                .entrySet()
                .stream()
                .filter(entry -> taskRepository.findById(entry.getValue().getId())
                        .filter(loadedTask -> {
                            final boolean finished = loadedTask.getStatus().isFinalStatus();
                            if (finished && shouldCheckToken(entry.getKey(), entry.getValue().getDestination())) {
                                createSyncToken(loadedTask);
                            }
                            return finished;
                        })
                        .isPresent())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        completed.forEach(activeTransferTasks::remove);
    }

    private void submitTasksForAwaitingRules() {
        final Date now = getCurrentDate();
        final Map<AutonomousSyncRule, TransferTask> newSubmittedTasks = activeSyncRules.entrySet().stream()
            .filter(this::syncSourceExists)
            .flatMap(dtsRuleExpander::expandSyncEntry)
            .filter(entry -> shouldBeTriggered(now, entry))
            .filter(entry -> noMatchingActiveTransferTask(entry.getKey()))
            .map(Map.Entry::getKey)
            .map(rule -> Pair.of(rule, runTransferTask(rule)))
            .filter(entry -> Objects.nonNull(entry.getValue()))
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
        newSubmittedTasks.keySet().stream()
            .map(task -> {
                final AutonomousSyncRule keyRule = Optional.ofNullable(task.getParentRule()).orElse(task);
                return activeSyncRules.get(keyRule);
            })
            .map(AutonomousSyncCronDetails::getLastExecution)
            .forEach(execution -> execution.setTime(now.getTime()));
        activeTransferTasks.putAll(newSubmittedTasks);
    }

    private boolean syncSourceExists(final Map.Entry<AutonomousSyncRule, ?> entry) {
        return Optional.of(entry.getKey())
            .map(AutonomousSyncRule::getSource)
            .map(Paths::get)
            .filter(Files::exists)
            .isPresent();
    }

    private boolean shouldBeTriggered(final Date now,
                                      final Map.Entry<AutonomousSyncRule, AutonomousSyncCronDetails> entry) {
        final AutonomousSyncRule rule = entry.getKey();
        final AutonomousSyncCronDetails cronDetails = entry.getValue();
        final Date lastExecution = cronDetails.getLastExecution();
        final boolean shouldBeTriggered = cronDetails.getGenerator().next(lastExecution).before(now);
        if (shouldBeTriggered) {
            log.info("Transfer from `{}` to `{}` should be triggered [cron: `{}`, lastExecution:`{}`]",
                     rule.getSource(), rule.getDestination(),
                     cronDetails.getExpression(), cronDetails.getLastExecution());
        }
        return shouldBeTriggered;
    }

    private Date getCurrentDate() {
        return Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant());
    }

    private boolean noMatchingActiveTransferTask(final AutonomousSyncRule rule) {
        final TransferTask existingTask = activeTransferTasks.get(rule);
        if (existingTask != null) {
            log.info("Running transfer task from `{}` to `{}` exists already [id=`{}`, startedOn=`{}`]",
                     rule.getSource(), rule.getDestination(), existingTask.getId(), existingTask.getCreated());
            return false;
        } else {
            log.info("No active transfer task found for sync from `{}` to `{}`",
                     rule.getSource(), rule.getDestination());
            return true;
        }
    }

    private TransferTask runTransferTask(final AutonomousSyncRule rule) {
        return buildTransferDestination(rule)
            .map(transferDestination -> {
                if (shouldCheckToken(rule, transferDestination) && isSyncTokenPresent(transferDestination)) {
                    log.info("Skipping transfer from {} to {} as synchronisation token is present.",
                            rule.getSource(), rule.getDestination());
                    return null;
                }
                if (!runAdditionalChecks(rule)) {
                    return null;
                }
                return trySubmitTransferTask(buildTransferSource(rule), transferDestination,
                        rule.getDeleteSource());
            })
            .map(submittedTask -> {
                log.info("Transfer task from `{}` to `{}` submitted successfully [id=`{}`]!",
                         rule.getSource(), rule.getDestination(), submittedTask.getId());
                return submittedTask;
            })
            .orElse(null);
    }

    private boolean runAdditionalChecks(final AutonomousSyncRule key) {
        if (Boolean.TRUE.equals(key.getCheckIllumina())) {
            return illuminaValidator.validateIlluminaFolder(key.getSource());
        }
        return true;
    }

    private TransferTask trySubmitTransferTask(final StorageItem transferSource,
                                               final StorageItem transferDestination,
                                               final Boolean deleteTransferSource) {
        try {
            transferDestination.setCredentials(getPipeCredentialsAsString());
            return transferService.runTransferTask(transferSource, transferDestination,
                    Collections.emptyList(),
                    Optional.ofNullable(deleteTransferSource).orElse(preferenceService.isSourceDeletionEnabled()));
        } catch (JsonProcessingException e) {
            log.warn("Error parsing PIPE credentials!");
        } catch (Exception e) {
            log.warn("Error during transfer submission from `{}` to `{}`: {}",
                     transferSource, transferDestination, e.getMessage());
        }
        return null;
    }

    private Optional<StorageItem> buildTransferDestination(final AutonomousSyncRule rule) {
        final String destinationPath = rule.getDestination();
        return Optional.of(destinationPath)
            .map(path -> path.split(SCHEMA_DELIMITER, 2)[0])
            .map(StringUtils::upperCase)
            .map(schema -> EnumUtils.getEnum(StorageType.class, schema))
            .map(type -> {
                final StorageItem destinationItem = new StorageItem();
                destinationItem.setType(type);
                destinationItem.setPath(destinationPath);
                return destinationItem;
            });
    }

    private StorageItem buildTransferSource(final AutonomousSyncRule rule) {
        final StorageItem sourceItem = new StorageItem();
        sourceItem.setPath(rule.getSource());
        sourceItem.setType(StorageType.LOCAL);
        return sourceItem;
    }

    private String getPipeCredentialsAsString() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        return mapper.writeValueAsString(pipeCredentials);
    }

    private boolean shouldCheckToken(final AutonomousSyncRule rule, final StorageItem destination) {
        return Boolean.TRUE.equals(rule.getCheckSyncToken()) &&
                !StorageType.LOCAL.equals(destination.getType());
    }

    private void createSyncToken(final TransferTask task) {
        final StorageItem destination = task.getDestination();
        final String tokenPath = buildTokenPath(destination);
        log.info("Creating synchronisation token {}", tokenPath);
        try {
            final URI uri = new URI(tokenPath);
            final AbstractDataStorage storage = findStorage(uri);
            apiClient.createStorageItem(storage.getId(), uri.getPath().replaceFirst("/", ""),
                    DATE_FORMATTER.format(task.getStarted()));
        } catch (Exception e) {
            log.warn("Failed to create synchronisation token {}: {}", tokenPath, e.getMessage());
        }
    }

    private boolean isSyncTokenPresent(final StorageItem destination) {
        final String tokenPath = buildTokenPath(destination);
        log.info("Checking synchronisation token {}", tokenPath);
        try {
            final URI uri = new URI(tokenPath);
            final AbstractDataStorage storage = findStorage(uri);
            return apiClient.getStorageItemContent(storage.getId(),
                    uri.getPath().replaceFirst("/", "")) != null;
        } catch (Exception e) {
            log.warn("Failed to find synchronisation token {}: {}", tokenPath, e.getMessage());
            return false;
        }
    }

    @SneakyThrows
    private AbstractDataStorage findStorage(final URI destination) {
        final String path = destination.getHost() + destination.getPath();
        return apiClient.findStorageByPath(path);
    }

    final String buildTokenPath(final StorageItem destination) {
        final String delimiter = destination.getPath().endsWith("/") ? "" : "/";
        return destination.getPath() + delimiter + syncToken;
    }
}
