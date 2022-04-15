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

import com.epam.pipeline.dts.sync.model.AutonomousSyncCronDetails;
import com.epam.pipeline.dts.sync.model.TransferTrigger;
import com.epam.pipeline.dts.sync.service.PreferenceService;
import com.epam.pipeline.dts.sync.service.ShutdownService;
import com.epam.pipeline.dts.sync.model.AutonomousSyncRule;
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
import org.apache.commons.collections4.CollectionUtils;
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
import org.springframework.util.AntPathMatcher;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Service
@Slf4j
@EnableScheduling
public class DtsSynchronizationService {

    private static final String SCHEMA_DELIMITER = "://";
    private static final String SYNC_DEST_DELIMITER = "/";

    private final TaskRepository taskRepository;
    private final TransferService transferService;
    private final PreferenceService preferenceService;
    private final ShutdownService shutdownService;
    private final PipelineCredentials pipeCredentials;
    private final Map<AutonomousSyncRule, AutonomousSyncCronDetails> activeSyncRules;
    private final Map<AutonomousSyncRule, TransferTask> activeTransferTasks;
    private final String defaultCronExpression;
    private final Integer maxSearchDepth;

    @Autowired
    public DtsSynchronizationService(final @Value("${dts.api.url}") String pipeApiUrl,
                                     final @Value("${dts.api.token}") String pipeApiToken,
                                     final @Value("${dts.autonomous.sync.cron}") String defaultCronExpression,
                                     final @Value("${dts.sync.transfer.triggers.max.depth:3}") Integer maxSearchDepth,
                                     final TransferService autonomousTransferService,
                                     final TaskRepository taskRepository,
                                     final PreferenceService preferenceService,
                                     final ShutdownService shutdownService) {
        this.pipeCredentials = new PipelineCredentials(pipeApiUrl, pipeApiToken);
        this.taskRepository = taskRepository;
        this.transferService = autonomousTransferService;
        this.shutdownService = shutdownService;
        this.preferenceService = preferenceService;
        this.activeSyncRules = new ConcurrentHashMap<>();
        this.activeTransferTasks = new ConcurrentHashMap<>();
        this.maxSearchDepth = maxSearchDepth;
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
                                      ListUtils.emptyIfNull(rule.getTransferTriggers()));
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
        activeTransferTasks.values()
            .removeIf(task -> taskRepository.findById(task.getId())
                .filter(loadedTask -> loadedTask.getStatus().isFinalStatus())
                .isPresent());
    }

    private void submitTasksForAwaitingRules() {
        final Date now = getCurrentDate();
        final Map<AutonomousSyncRule, TransferTask> newSubmittedTasks = activeSyncRules.entrySet().stream()
            .filter(this::syncSourceExists)
            .flatMap(this::expandSyncEntry)
            .filter(entry -> shouldBeTriggered(now, entry))
            .filter(entry -> noMatchingActiveTransferTask(entry.getKey()))
            .map(Map.Entry::getKey)
            .map(rule -> Pair.of(rule, runTransferTask(rule)))
            .filter(entry -> Objects.nonNull(entry.getValue()))
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
        newSubmittedTasks.keySet().stream()
            .map(activeSyncRules::get)
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

    private Stream<Map.Entry<AutonomousSyncRule, AutonomousSyncCronDetails>> expandSyncEntry(
        final Map.Entry<AutonomousSyncRule, AutonomousSyncCronDetails> entry) {
        final AutonomousSyncRule syncRule = entry.getKey();
        final String syncSource = syncRule.getSource();
        if (!Files.isDirectory(Paths.get(syncSource))) {
            log.debug("File sync source [{}] can't be expanded, skip triggers checking...", syncSource);
            return Stream.of(entry);
        }
        final List<TransferTrigger> transferTriggers = syncRule.getTransferTriggers().stream()
            .filter(Objects::nonNull)
            .map(this::processGlobMatchers)
            .filter(trigger -> CollectionUtils.isNotEmpty(trigger.getGlobMatchers()))
            .collect(Collectors.toList());
        return CollectionUtils.isNotEmpty(transferTriggers)
               ? expandSyncEntryWithTriggers(entry.getKey(), entry.getValue(), transferTriggers)
               : Stream.of(entry);
    }

    private TransferTrigger processGlobMatchers(final TransferTrigger trigger) {
        final List<String> globMatchers = ListUtils.emptyIfNull(trigger.getGlobMatchers()).stream()
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toList());
        return new TransferTrigger(trigger.getMaxSearchDepth(), globMatchers);
    }

    private Stream<Map.Entry<AutonomousSyncRule, AutonomousSyncCronDetails>> expandSyncEntryWithTriggers(
        final AutonomousSyncRule syncRule,
        final AutonomousSyncCronDetails cronDetails,
        final List<TransferTrigger> transferTriggers) {
        final String syncDestination = syncRule.getDestination();
        final String syncSource = syncRule.getSource();
        return transferTriggers.stream()
            .map(trigger -> searchForGivenTrigger(syncSource, trigger))
            .flatMap(Collection::stream)
            .map(absolutePath -> absolutePath.substring(syncSource.length() + 1))
            .map(relativePath -> new AutonomousSyncRule(Paths.get(syncSource, relativePath).toString(),
                                                        String.join(SYNC_DEST_DELIMITER, syncDestination, relativePath),
                                                        null, null))
            .map(rule -> new AbstractMap.SimpleEntry<>(rule, cronDetails));
    }

    private List<String> searchForGivenTrigger(final String dirPath, final TransferTrigger transferTrigger) {
        final List<String> lookupDirectories = new ArrayList<>(Collections.singletonList(dirPath));
        final List<String> matchingDirectories = new ArrayList<>();
        final CountDownLatch searchLatch = extractSearchLatch(transferTrigger);
        while (CollectionUtils.isNotEmpty(lookupDirectories) && searchLatch.getCount() > -1) {
            lookupTargetDirectories(lookupDirectories, matchingDirectories, transferTrigger.getGlobMatchers());
            searchLatch.countDown();
        }
        return matchingDirectories;
    }

    private void lookupTargetDirectories(final List<String> lookupDirectories, final List<String> matchingDirectories,
                                         final List<String> globMatchers) {
        final List<String> nestedDirs = lookupDirectories.stream()
            .flatMap(directory -> {
                if (directoryHasAnyMatch(directory, globMatchers)) {
                    matchingDirectories.add(directory);
                    return Stream.empty();
                } else {
                    return getNestedDirStream(directory);
                }
            })
            .collect(Collectors.toList());
        lookupDirectories.clear();
        lookupDirectories.addAll(nestedDirs);
    }

    private CountDownLatch extractSearchLatch(final TransferTrigger transferTrigger) {
        return new CountDownLatch(Optional.ofNullable(transferTrigger.getMaxSearchDepth()).orElse(maxSearchDepth));
    }

    private Stream<String> getNestedDirStream(final String directory) {
        return Stream.of(new File(directory).listFiles())
            .filter(File::isDirectory)
            .map(File::getAbsolutePath);
    }

    private boolean directoryHasAnyMatch(final String directory, final List<String> expressions) {
        final AntPathMatcher pathMatcher = new AntPathMatcher();
        return Stream.of(new File(directory).listFiles())
            .filter(File::isFile)
            .map(File::getName)
            .anyMatch(name -> nameMatchingGlob(name, pathMatcher, expressions));
    }

    private boolean nameMatchingGlob(final String name, final AntPathMatcher matcher, final List<String> expressions) {
        return CollectionUtils.emptyIfNull(expressions)
            .stream()
            .anyMatch(glob -> matcher.match(glob, name));
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
            .map(transferDestination -> trySubmitTransferTask(buildTransferSource(rule), transferDestination))
            .map(submittedTask -> {
                log.info("Transfer task from `{}` to `{}` submitted successfully [id=`{}`]!",
                         rule.getSource(), rule.getDestination(), submittedTask.getId());
                return submittedTask;
            })
            .orElse(null);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private TransferTask trySubmitTransferTask(final StorageItem transferSource,
                                               final StorageItem transferDestination) {
        try {
            transferDestination.setCredentials(getPipeCredentialsAsString());
            return transferService.runTransferTask(transferSource, transferDestination, Collections.emptyList());
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
            .filter(Objects::nonNull)
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
}
