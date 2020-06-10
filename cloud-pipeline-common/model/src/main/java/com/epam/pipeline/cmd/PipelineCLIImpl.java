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


package com.epam.pipeline.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Value
@Slf4j
@AllArgsConstructor
@Builder
public class PipelineCLIImpl implements PipelineCLI {

    private static final String PIPE_CP_TEMPLATE = "%s storage cp %s %s %s";
    private static final String PIPE_LS_TEMPLATE = "%s storage ls %s -l";
    private static final String SPACE = " ";
    private static final String FOLDER = "Folder";
    private static final String SEPARATOR = "/";

    private final String pipelineCliExecutable;
    private final String pipeCpSuffix;
    private final boolean forceUpload;
    private final int retryCount;
    private final CmdExecutor cmdExecutor;

    @Override
    public String uploadData(final String source,
                             final String destination) {
        return uploadData(source, destination, Collections.emptyList());
    }

    @Override
    public String uploadData(String source, String destination, List<String> include, String username) {
        if (!forceUpload && isFileAlreadyLoaded(source, destination)) {
            log.info(String.format("Skip file %s uploading. " +
                    "It already exists in the remote bucket: %s", source, destination));
            return destination;
        } else {
            log.info(String.format("Upload from %s to %s", source, destination));
            int attempts = 0;

            while (attempts < retryCount) {
                try {
                    cmdExecutor.executeCommand(pipeCP(source, destination, include), username);
                    log.info(String.format("Successfully uploaded from %s to %s", source, destination));
                    return destination;
                } catch (CmdExecutionException e) {
                    log.error(String.format("Failed to upload from %s to %s. Error: %s",
                            source, destination, e.getMessage()));
                    attempts++;
                }
            }
            throw new CmdExecutionException(String.format("Exceeded attempts count to upload %s to %s",
                    source, destination));
        }
    }

    @Override
    public void downloadData(final String source,
                             final String destination) {
        downloadData(source, destination, Collections.emptyList());
    }

    @Override
    public void downloadData(String source, String destination, List<String> include, String username) {
        log.info(String.format("Download from %s to %s", source, destination));
        int attempts = 0;

        while (attempts < retryCount) {
            try {
                cmdExecutor.executeCommand(pipeCP(source, destination, include), username);
                log.info(String.format("Successfully downloaded from %s to %s", source, destination));
                return;
            } catch (CmdExecutionException e) {
                log.error(String.format("Failed to download from %s to %s. Error: %s",
                        source, destination, e.getMessage()));
                attempts++;
            }
        }
        throw new CmdExecutionException(String.format("Exceeded attempts count to upload %s to %s",
                source, destination));
    }

    private boolean isFileAlreadyLoaded(final String localFilePath,
                                        final String remoteFilePath) {
        return retrieveDescription(remoteFilePath)
            .filter(file -> !file.getType().equals(FOLDER) && file.getSize().equals(new File(localFilePath).length()))
            .isPresent();
    }

    @Override
    public Optional<RemoteFileDescription> retrieveDescription(final String targetPath) {
        final String targetPathWithoutTrailingSeparator = removeTrailingSeparator(targetPath);
        final String pipeLsOutput = cmdExecutor.executeCommand(pipeLS(targetPathWithoutTrailingSeparator));
        final String[] outputLines = pipeLsOutput.split("\n");

        if (outputLines.length < 2) {
            return Optional.empty();
        }

        final String headerLine = outputLines[0];
        if (StringUtils.isBlank(headerLine)) {
            return Optional.empty();
        }

        final String[] filesLines = Arrays.copyOfRange(outputLines, 1, outputLines.length);
        final String targetFileBaseName = FilenameUtils.getName(targetPathWithoutTrailingSeparator);
        return Arrays.stream(filesLines)
            .map(parsePipeLsOutputLine(headerLine))
            .filter(remoteFileDescription ->
                removeTrailingSeparator(remoteFileDescription.getName()).equals(targetFileBaseName))
            .findFirst();
    }

    private String removeTrailingSeparator(final String targetPath) {
        return StringUtils.removeEnd(targetPath, SEPARATOR);
    }

    private Function<String, RemoteFileDescription> parsePipeLsOutputLine(final String headerLine) {
        return fileLine -> {
            final PipelineLsColumns[] columns = PipelineLsColumns.values();
            final List<Integer> columnIndexes = Arrays.stream(columns)
                .map(column -> headerLine.indexOf(column.name))
                .collect(Collectors.toList());

            final RemoteFileDescription remoteFileDescription = new RemoteFileDescription();
            for (int i = 0; i < columnIndexes.size(); i++) {
                final int cellBeginIndex = columnIndexes.get(i);
                final int cellEndIndex = i < columnIndexes.size() - 1
                    ? columnIndexes.get(i + 1)
                    : headerLine.length();
                final String cellValue = fileLine.substring(cellBeginIndex, cellEndIndex).trim();
                columns[i].append(remoteFileDescription, cellValue);
            }
            return remoteFileDescription;
        };
    }

    private String pipeCP(final String source,
                          final String destination,
                          final List<String> include) {
        String command = String.format(PIPE_CP_TEMPLATE, pipelineCliExecutable, source, destination, pipeCpSuffix);
        if (CollectionUtils.isEmpty(include)) {
            return command;
        }
        command = command + SPACE + include.stream()
                .map(s -> String.format("--include '%s'", s))
                .collect(Collectors.joining(SPACE));

        return command;
    }

    private String pipeLS(final String destination) {
        return String.format(PIPE_LS_TEMPLATE, pipelineCliExecutable, destination);
    }

    /**
     * Pipeline {@link #PIPE_LS_TEMPLATE} output columns.
     */
    private enum PipelineLsColumns {
        TYPE("Type", RemoteFileDescription::withType),
        LABELS("Labels", RemoteFileDescription::withLabels),
        MODIFIED("Modified", RemoteFileDescription::withModified),
        SIZE("Size", (file, value) -> file.withSize(StringUtils.isBlank(value) ? 0L : Long.valueOf(value))),
        NAME("Name", RemoteFileDescription::withName);

        final String name;
        final BiFunction<RemoteFileDescription, String, RemoteFileDescription> appender;

        PipelineLsColumns(final String name,
                          final BiFunction<RemoteFileDescription, String, RemoteFileDescription> appender) {
            this.name = name;
            this.appender = appender;
        }

        void append(final RemoteFileDescription remoteFileDescription,
                    final String value) {
            appender.apply(remoteFileDescription, value);
        }
    }

}
