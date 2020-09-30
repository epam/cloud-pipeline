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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes all passed command using qsub in sync mode.
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
@RequiredArgsConstructor
@Slf4j
public class QsubCmdExecutor implements CmdExecutor {

    private final CmdExecutor cmdExecutor;
    private final String qsubTemplate;

    @Override
    public String executeCommand(final String originalCommand,
                                 final Map<String, String> environmentVariables,
                                 final File workDir,
                                 final String username) {
        log.info("Executing command '{}' using qsub", originalCommand);
        Path outputLog = null;
        Path errorLog = null;
        Path originalCommandScript = null;
        try {
            outputLog = createFile("qsub-output", ".log");
            errorLog = createFile("qsub-error", ".log");
            originalCommandScript = createFile("qsub-script", ".sh");
            Files.write(originalCommandScript, Collections.singleton(originalCommand));

            final String qsubCommand = String.format(
                qsubTemplate,
                outputLog.toAbsolutePath().toString(),
                errorLog.toAbsolutePath().toString(),
                originalCommandScript.toAbsolutePath().toString()
            );

            cmdExecutor.executeCommand(qsubCommand, environmentVariables, workDir, username);

            return Files.lines(outputLog).collect(Collectors.joining("\n"));
        } catch (CmdExecutionException | IOException e) {
            throw new CmdExecutionException(String.format(
                    "Qsub execution of original command '%s' went bad", originalCommand), e);
        } finally {
            Stream.of(outputLog, errorLog, originalCommandScript)
                .filter(Objects::nonNull)
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    @Override
    public Process launchCommand(final String originalCommand,
                                 final Map<String, String> environmentVariables,
                                 final File workDir,
                                 final String username) {
        log.info("Executing command '{}' using qsub", originalCommand);
        try {
            final Path outputLog = createFile("qsub-output", ".log");
            final Path errorLog = createFile("qsub-error", ".log");
            final Path originalCommandScript = createFile("qsub-script", ".sh");
            Files.write(originalCommandScript, Collections.singleton(originalCommand));

            final String qsubCommand = String.format(
                qsubTemplate,
                outputLog.toAbsolutePath().toString(),
                errorLog.toAbsolutePath().toString(),
                originalCommandScript.toAbsolutePath().toString()
            );

            return cmdExecutor.launchCommand(qsubCommand, environmentVariables, workDir, username);
        } catch (CmdExecutionException | IOException e) {
            throw new CmdExecutionException(String.format(
                    "Qsub launching of original command '%s' went bad", originalCommand), e);
        }
        // TODO 02.12.18: outputLog, errorLog and originalCommandScript are not being deleted after the process finishes
    }

    protected Path createFile(final String prefix, final String extension) throws IOException {
        return Files.createTempFile(prefix, extension);
    }
}
