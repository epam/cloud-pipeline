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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cmd executor that impersonates requested users while executing or launching commands.
 * 
 * It creates temporary files to store command which will impersonate users and execute commands. 
 * Also it embeds environment variables to temporary files to keep the environment during impersonation.
 * 
 * Notice that in case of any execution error with impersonated user another attempt to execute the command with
 * current user will be performed.
 */
@RequiredArgsConstructor
@Slf4j
public class ImpersonatingCmdExecutor implements CmdExecutor {
    
    private static final String BASH_TEMPLATE = "bash %s";
    private static final String IMPERSONATING_TEMPLATE = "echo \"%s %s\" | sudo -n su - %s";
    private static final String ENVIRONMENT_VARIABLE_TEMPLATE = "%s='%s'";
    private static final FileAttribute<Set<PosixFilePermission>> DEFAULT_PERMISSIONS =
            PosixFilePermissions.asFileAttribute(new HashSet<>(Arrays.asList(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE)));

    private final CmdExecutor cmdExecutor;

    @Override
    public String executeCommand(final String command,
                                 final Map<String, String> environmentVariables,
                                 final File workDir,
                                 final String username) {
        if (username == null) {
            return cmdExecutor.executeCommand(command, environmentVariables, workDir);
        }
        log.info("Executing command '{}' with impersonated user '{}'", command, username);
        Path commandScript = null;
        try {
            commandScript = Files.createTempFile("impersonated-script", ".sh", DEFAULT_PERMISSIONS);
            Files.write(commandScript, Collections.singleton(impersonating(command, environmentVariables, username)));
            return cmdExecutor.executeCommand(bash(commandScript), environmentVariables, workDir);
        } catch (CmdExecutionException | IOException e) {
            log.error(String.format(
                    "Original command '%s' execution with impersonated user '%s' went bad. " +
                    "Trying to perform the operation with the default user.", command, username), e);
            try {
                return cmdExecutor.executeCommand(command, environmentVariables, workDir);
            } catch (CmdExecutionException e1) {
                throw new CmdExecutionException(String.format(
                        "Original command '%s' execution with both impersonated user '%s' and default user went bad.", 
                        command, username), e1);
            }
        } finally {
            Optional.ofNullable(commandScript)
                    .map(Path::toFile)
                    .ifPresent(File::delete);
        }
    }

    @Override
    public Process launchCommand(final String command,
                                 final Map<String, String> environmentVariables,
                                 final File workDir,
                                 final String username) {
        log.info("Launching command '{}' with impersonated user '{}'", command, username);
        try {
            final Path commandScript = Files.createTempFile("impersonated-script", ".sh", DEFAULT_PERMISSIONS);
            Files.write(commandScript, Collections.singleton(impersonating(command, environmentVariables, username)));
            return cmdExecutor.launchCommand(bash(commandScript), environmentVariables, workDir);
        } catch (CmdExecutionException | IOException e) {
            throw new CmdExecutionException(String.format(
                    "Original command '%s' launching with impersonated user '%s' went bad", command, username
            ), e);
        }
        // TODO 02.12.18: commandScript is not deleted after the process finishes
    }

    private String impersonating(final String command,
                                 final Map<String, String> environmentVariables,
                                 final String username) {
        return String.format(IMPERSONATING_TEMPLATE, environmentString(environmentVariables), command, username);
    }

    private String environmentString(final Map<String, String> environmentVariables) {
        return MapUtils.emptyIfNull(environmentVariables)
                .entrySet()
                .stream()
                .map(it -> String.format(ENVIRONMENT_VARIABLE_TEMPLATE, it.getKey(), it.getValue()))
                .collect(Collectors.joining(" "));
    }

    private String bash(final Path commandScript) {
        return String.format(BASH_TEMPLATE, commandScript.toAbsolutePath().toString());
    }
}
