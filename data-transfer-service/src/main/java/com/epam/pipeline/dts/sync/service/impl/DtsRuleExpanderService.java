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
import com.epam.pipeline.dts.sync.model.AutonomousSyncRule;
import com.epam.pipeline.dts.sync.model.TransferTrigger;
import com.epam.pipeline.dts.sync.model.TransferMatcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class DtsRuleExpanderService {

    private static final String SYNC_DEST_DELIMITER = "/";

    private final AntPathMatcher pathMatcher;
    private final Integer defaultMaxSearchDepth;

    public DtsRuleExpanderService(final @Value("${dts.sync.transfer.triggers.max.depth:3}") Integer maxSearchDepth) {
        this.pathMatcher =  new AntPathMatcher();
        this.defaultMaxSearchDepth = maxSearchDepth;
    }

    public Stream<Map.Entry<AutonomousSyncRule, AutonomousSyncCronDetails>> expandSyncEntry(
        final Map.Entry<AutonomousSyncRule, AutonomousSyncCronDetails> entry) {
        final AutonomousSyncRule syncRule = entry.getKey();
        final String syncSource = syncRule.getSource();
        if (!Files.isDirectory(Paths.get(syncSource))) {
            log.debug("File sync source [{}] can't be expanded, skip triggers checking...", syncSource);
            return Stream.of(entry);
        }
        final List<TransferTrigger> transferTriggers = syncRule.getTransferTriggers().stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(TransferTrigger::getMaxSearchDepth, Function.identity(),
                                      TransferTrigger::addAllMatchers))
            .values()
            .stream()
            .filter(trigger -> CollectionUtils.isNotEmpty(trigger.getGlobMatchers()))
            .map(this::processGlobMatchers)
            .collect(Collectors.toList());
        return CollectionUtils.isNotEmpty(transferTriggers)
               ? expandSyncEntryWithTriggers(entry.getKey(), entry.getValue(), transferTriggers)
               : Stream.of(entry);
    }

    private TransferTrigger processGlobMatchers(final TransferTrigger trigger) {
        final List<String> globMatchers = ListUtils.emptyIfNull(trigger.getGlobMatchers()).stream()
            .filter(StringUtils::isNotBlank)
            .distinct()
            .sorted(this::compareGlobsByPriority)
            .collect(Collectors.toList());
        return new TransferTrigger(trigger.getMaxSearchDepth(), globMatchers);
    }

    private int compareGlobsByPriority(final String expression1, final String expression2) {
        if (expression1.endsWith(SYNC_DEST_DELIMITER)) {
            return -1;
        } else if (expression2.endsWith(SYNC_DEST_DELIMITER)) {
            return 1;
        } else {
            return 0;
        }
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
            .map(absolutePath -> buildNestedDirRelativePath(syncSource, absolutePath))
            .map(relativePath -> buildRelativeSyncRule(syncSource, syncDestination, relativePath,
                                                       syncRule.getDeleteSource(), syncRule))
            .map(rule -> new AbstractMap.SimpleEntry<>(rule, cronDetails));
    }

    private String buildNestedDirRelativePath(final String absoluteRootPath, final String absoluteNestedPath) {
        return absoluteRootPath.length() < absoluteNestedPath.length()
               ? absoluteNestedPath.substring(absoluteRootPath.length() + 1)
               : StringUtils.EMPTY;
    }

    private AutonomousSyncRule buildRelativeSyncRule(final String syncSourceRoot, final String syncDestinationRoot,
                                                     final String relativePath, final Boolean deleteSource,
                                                     final AutonomousSyncRule syncRule) {
        final String syncSource = Paths.get(syncSourceRoot, relativePath).toString();
        final String syncDestination = Optional.of(relativePath)
            .filter(StringUtils::isNotBlank)
            .map(suffix -> String.join(SYNC_DEST_DELIMITER, syncDestinationRoot, suffix))
            .orElse(syncDestinationRoot);
        return new AutonomousSyncRule(syncSource, syncDestination, null, deleteSource,
                null, syncRule.getCheckSyncToken(), syncRule);
    }

    private List<String> searchForGivenTrigger(final String dirPath, final TransferTrigger transferTrigger) {
        final List<String> lookupDirectories = new ArrayList<>(Collections.singletonList(dirPath));
        final List<String> matchingDirectories = new ArrayList<>();
        final List<TransferMatcher> transferMatchers = transferTrigger.getGlobMatchers().stream()
            .map(TransferMatcher::new)
            .sorted(Comparator.comparing(TransferMatcher::getSearchDepth))
            .collect(Collectors.toList());
        Integer searchDepth = Optional.ofNullable(transferTrigger.getMaxSearchDepth())
            .orElse(defaultMaxSearchDepth);
        while (CollectionUtils.isNotEmpty(lookupDirectories) && searchDepth > -1) {
            lookupTargetDirectories(lookupDirectories, matchingDirectories, transferMatchers);
            searchDepth--;
        }
        return matchingDirectories;
    }

    private void lookupTargetDirectories(final List<String> lookupDirectories, final List<String> matchingDirectories,
                                         final List<TransferMatcher> transferMatchers) {
        final List<String> nestedDirs = lookupDirectories.stream()
            .flatMap(directory -> {
                if (directoryHasAnyMatch(directory, transferMatchers)) {
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

    private Stream<String> getNestedDirStream(final String directory) {
        return directoryElementsStream(directory)
            .filter(File::isDirectory)
            .map(File::getAbsolutePath);
    }

    private boolean directoryHasAnyMatch(final String directory, final List<TransferMatcher> transferMatchers) {
        final int searchDepth = getMaxSearchDepth(transferMatchers);
        final List<String> triggerMatchers = normalizeGlobMatchers(directory, transferMatchers);
        final Path rootPath = Paths.get(directory);
        try (Stream<Path> pathStream = Files.find(rootPath, searchDepth, (path, basicFileAttributes) ->
                !rootPath.equals(path) && isPathMatchingGlob(path, triggerMatchers))) {
            return pathStream
                .findAny()
                .isPresent();
        } catch (IOException e) {
            log.error("An error occurred during [{}] directory traversing, skipping: {}", directory, e.getMessage());
            return false;
        }
    }

    private int getMaxSearchDepth(final List<TransferMatcher> transferMatchers) {
        return transferMatchers.stream()
            .mapToInt(TransferMatcher::getSearchDepth)
            .max()
            .orElse(1);
    }

    private List<String> normalizeGlobMatchers(final String directory, final List<TransferMatcher> transferMatchers) {
        final String normalizedDirRoot = normalizePath(directory);
        return transferMatchers.stream()
            .map(TransferMatcher::getExpression)
            .map(path -> normalizedDirRoot + SYNC_DEST_DELIMITER + path)
            .collect(Collectors.toList());
    }

    private Stream<File> directoryElementsStream(final String directory) {
        return Optional.ofNullable(new File(directory).listFiles())
            .map(Stream::of)
            .orElseGet(Stream::empty);
    }

    private String normalizePath(final String path) {
        return path.contains("\\") ? path.replaceAll("\\\\", SYNC_DEST_DELIMITER) : path;
    }

    private boolean isPathMatchingGlob(final Path path, final List<String> expressions) {
        final String nameSuffix = path.toFile().isDirectory() ? SYNC_DEST_DELIMITER : StringUtils.EMPTY;
        final String name = normalizePath(path.toString()) + nameSuffix;
        return CollectionUtils.emptyIfNull(expressions)
            .stream()
            .anyMatch(glob -> pathMatcher.match(glob, name));
    }
}
