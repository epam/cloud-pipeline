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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class DtsRuleExpanderService {

    private static final String SYNC_DEST_DELIMITER = "/";
    private final Integer defaultMaxSearchDepth;

    public DtsRuleExpanderService(final @Value("${dts.sync.transfer.triggers.max.depth:3}") Integer maxSearchDepth) {
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
        Integer searchDepth = Optional.ofNullable(transferTrigger.getMaxSearchDepth())
            .orElse(defaultMaxSearchDepth);
        while (CollectionUtils.isNotEmpty(lookupDirectories) && searchDepth > -1) {
            lookupTargetDirectories(lookupDirectories, matchingDirectories, transferTrigger.getGlobMatchers());
            searchDepth--;
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

    private Stream<String> getNestedDirStream(final String directory) {
        return directoryElementsStream(directory)
            .filter(File::isDirectory)
            .map(File::getAbsolutePath);
    }

    private boolean directoryHasAnyMatch(final String directory, final List<String> expressions) {
        final AntPathMatcher pathMatcher = new AntPathMatcher();
        return directoryElementsStream(directory)
            .filter(File::isFile)
            .map(File::getName)
            .anyMatch(name -> nameMatchingGlob(name, pathMatcher, expressions));
    }

    private Stream<File> directoryElementsStream(final String directory) {
        return Optional.ofNullable(new File(directory).listFiles())
            .map(Stream::of)
            .orElseGet(Stream::empty);
    }

    private boolean nameMatchingGlob(final String name, final AntPathMatcher matcher, final List<String> expressions) {
        return CollectionUtils.emptyIfNull(expressions)
            .stream()
            .anyMatch(glob -> matcher.match(glob, name));
    }
}
